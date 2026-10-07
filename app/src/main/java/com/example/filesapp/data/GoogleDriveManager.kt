package com.example.filesapp.data

import android.content.Context
import com.example.filesapp.domain.storage.CloudFileItem
import com.example.filesapp.domain.storage.CloudProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Production-grade Google Drive Manager implementing CloudProvider interface.
 * Supports:
 * - Subdirectory tree navigation by parent folder ID ('root' or folder ID)
 * - File search by name query
 * - Streaming upload and download with progress callback
 * - File/Folder creation, renaming, moving, and deletion
 * - Error categorization: expired session, quota exceeded, revoked permissions, network timeout.
 * - Secure OAuth2 handled by Play Services (zero manual token storage).
 */
class GoogleDriveManager(private val context: Context) : CloudProvider {

    override val providerName: String = "Google Drive"

    var currentAccount: GoogleSignInAccount? = null

    override val isConnected: Boolean
        get() = currentAccount != null

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Files App").build()
    }

    /**
     * Lists files in a specific Google Drive directory by folder ID (default: "root").
     */
    override suspend fun listFiles(folderIdOrPath: String): Result<List<CloudFileItem>> = withContext(Dispatchers.IO) {
        val account = currentAccount ?: getLastSignedInAccount() ?: return@withContext Result.failure(
            IllegalStateException("Google Drive not signed in")
        )
        currentAccount = account

        try {
            val service = getDriveService(account)
            val parentId = if (folderIdOrPath.isBlank() || folderIdOrPath == "/" || folderIdOrPath == "drive://root") "root" else folderIdOrPath.removePrefix("drive://")
            val query = "'$parentId' in parents and trashed = false"

            val result = service.files().list()
                .setQ(query)
                .setPageSize(100)
                .setFields("files(id, name, mimeType, size, modifiedTime, webViewLink, iconLink)")
                .setOrderBy("folder,name")
                .execute()

            val items = (result.files ?: emptyList()).map { gf ->
                val isDir = gf.mimeType == "application/vnd.google-apps.folder"
                CloudFileItem(
                    id = gf.id ?: "",
                    name = gf.name ?: "Untitled",
                    pathOrParentId = parentId,
                    mimeType = gf.mimeType ?: "application/octet-stream",
                    sizeBytes = gf.getSize()?.toLong() ?: 0L,
                    lastModified = gf.modifiedTime?.value ?: System.currentTimeMillis(),
                    isDirectory = isDir,
                    webViewLink = gf.webViewLink,
                    iconUrl = gf.iconLink
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(mapDriveException(e))
        }
    }

    /**
     * Searches Google Drive files across all folders by name keyword.
     */
    override suspend fun searchFiles(query: String): Result<List<CloudFileItem>> = withContext(Dispatchers.IO) {
        val account = currentAccount ?: getLastSignedInAccount() ?: return@withContext Result.failure(
            IllegalStateException("Google Drive not signed in")
        )
        currentAccount = account

        try {
            val service = getDriveService(account)
            val cleanQ = query.replace("'", "\\'")
            val searchQuery = "name contains '$cleanQ' and trashed = false"

            val result = service.files().list()
                .setQ(searchQuery)
                .setPageSize(50)
                .setFields("files(id, name, mimeType, size, modifiedTime, webViewLink, iconLink)")
                .execute()

            val items = (result.files ?: emptyList()).map { gf ->
                val isDir = gf.mimeType == "application/vnd.google-apps.folder"
                CloudFileItem(
                    id = gf.id ?: "",
                    name = gf.name ?: "Untitled",
                    pathOrParentId = "root",
                    mimeType = gf.mimeType ?: "application/octet-stream",
                    sizeBytes = gf.getSize()?.toLong() ?: 0L,
                    lastModified = gf.modifiedTime?.value ?: System.currentTimeMillis(),
                    isDirectory = isDir,
                    webViewLink = gf.webViewLink,
                    iconUrl = gf.iconLink
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(mapDriveException(e))
        }
    }

    /**
     * Creates a new folder on Google Drive.
     */
    override suspend fun createFolder(parentFolderIdOrPath: String, folderName: String): Result<CloudFileItem> = withContext(Dispatchers.IO) {
        val account = currentAccount ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        try {
            val service = getDriveService(account)
            val parentId = if (parentFolderIdOrPath.isBlank() || parentFolderIdOrPath == "/") "root" else parentFolderIdOrPath.removePrefix("drive://")

            val folderMeta = com.google.api.services.drive.model.File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentId)
            }

            val created = service.files().create(folderMeta)
                .setFields("id, name, mimeType, size, modifiedTime")
                .execute()

            Result.success(
                CloudFileItem(
                    id = created.id ?: "",
                    name = created.name ?: folderName,
                    pathOrParentId = parentId,
                    mimeType = "application/vnd.google-apps.folder",
                    sizeBytes = 0L,
                    lastModified = created.modifiedTime?.value ?: System.currentTimeMillis(),
                    isDirectory = true
                )
            )
        } catch (e: Exception) {
            Result.failure(mapDriveException(e))
        }
    }

    /**
     * Uploads a local file to Google Drive.
     */
    override suspend fun uploadFile(
        localFile: File,
        parentFolderIdOrPath: String,
        onProgress: (Float) -> Unit
    ): Result<CloudFileItem> = withContext(Dispatchers.IO) {
        val account = currentAccount ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        if (!localFile.exists()) return@withContext Result.failure(IOException("Local file not found: ${localFile.name}"))

        try {
            val service = getDriveService(account)
            val parentId = if (parentFolderIdOrPath.isBlank() || parentFolderIdOrPath == "/") "root" else parentFolderIdOrPath.removePrefix("drive://")

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = localFile.name
                parents = listOf(parentId)
            }

            onProgress(0.2f)
            val mediaContent = FileContent(null, localFile)
            val uploaded = service.files().create(fileMetadata, mediaContent)
                .setFields("id, name, mimeType, size, modifiedTime, webViewLink")
                .execute()

            onProgress(1f)
            Result.success(
                CloudFileItem(
                    id = uploaded.id ?: "",
                    name = uploaded.name ?: localFile.name,
                    pathOrParentId = parentId,
                    mimeType = uploaded.mimeType ?: "application/octet-stream",
                    sizeBytes = uploaded.getSize()?.toLong() ?: localFile.length(),
                    lastModified = uploaded.modifiedTime?.value ?: System.currentTimeMillis(),
                    isDirectory = false,
                    webViewLink = uploaded.webViewLink
                )
            )
        } catch (e: Exception) {
            Result.failure(mapDriveException(e))
        }
    }

    /**
     * Downloads a file from Google Drive to local storage.
     */
    override suspend fun downloadFile(
        fileIdOrPath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val account = currentAccount ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        val fileId = fileIdOrPath.removePrefix("drive://")

        try {
            val service = getDriveService(account)
            destinationFile.parentFile?.mkdirs()

            onProgress(0.2f)
            FileOutputStream(destinationFile).use { outputStream ->
                service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            }
            onProgress(1f)
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(mapDriveException(e))
        }
    }

    /**
     * Renames a file or folder on Google Drive.
     */
    override suspend fun rename(fileIdOrPath: String, newName: String): Result<CloudFileItem> = withContext(Dispatchers.IO) {
        val account = currentAccount ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        val fileId = fileIdOrPath.removePrefix("drive://")

        try {
            val service = getDriveService(account)
            val updateMeta = com.google.api.services.drive.model.File().apply {
                name = newName
            }
            val updated = service.files().update(fileId, updateMeta)
                .setFields("id, name, mimeType, size, modifiedTime")
                .execute()

            Result.success(
                CloudFileItem(
                    id = updated.id ?: fileId,
                    name = updated.name ?: newName,
                    pathOrParentId = "root",
                    mimeType = updated.mimeType ?: "application/octet-stream",
                    sizeBytes = updated.getSize()?.toLong() ?: 0L,
                    lastModified = updated.modifiedTime?.value ?: System.currentTimeMillis(),
                    isDirectory = updated.mimeType == "application/vnd.google-apps.folder"
                )
            )
        } catch (e: Exception) {
            Result.failure(mapDriveException(e))
        }
    }

    /**
     * Moves a Google Drive file to another folder.
     */
    override suspend fun move(fileIdOrPath: String, newParentIdOrPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val account = currentAccount ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        val fileId = fileIdOrPath.removePrefix("drive://")
        val newParentId = if (newParentIdOrPath.isBlank() || newParentIdOrPath == "/") "root" else newParentIdOrPath.removePrefix("drive://")

        try {
            val service = getDriveService(account)
            val currentFile = service.files().get(fileId).setFields("parents").execute()
            val previousParents = currentFile.parents?.joinToString(",") ?: ""

            service.files().update(fileId, null)
                .setAddParents(newParentId)
                .setRemoveParents(previousParents)
                .setFields("id, parents")
                .execute()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(mapDriveException(e))
        }
    }

    /**
     * Deletes a file or folder on Google Drive.
     */
    override suspend fun delete(fileIdOrPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val account = currentAccount ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        val fileId = fileIdOrPath.removePrefix("drive://")

        try {
            val service = getDriveService(account)
            service.files().delete(fileId).execute()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(mapDriveException(e))
        }
    }

    /**
     * Categorizes and normalizes Google Drive / HTTP exceptions for user feedback.
     */
    private fun mapDriveException(e: Exception): Exception {
        return when (e) {
            is UserRecoverableAuthIOException -> Exception("Session expired or permission requested. Please re-authenticate.", e)
            is GoogleJsonResponseException -> {
                when (e.statusCode) {
                    401 -> Exception("Authentication expired. Please sign in again.", e)
                    403 -> Exception("Access forbidden or Drive storage quota exceeded.", e)
                    404 -> Exception("File not found on Google Drive.", e)
                    429 -> Exception("Rate limit exceeded. Please wait a moment and try again.", e)
                    else -> Exception("Google Drive Error (${e.statusCode}): ${e.details?.message ?: e.message}", e)
                }
            }
            is SocketTimeoutException -> Exception("Connection timed out while communicating with Google Drive.", e)
            is IOException -> Exception("Network error: ${e.message}", e)
            else -> e
        }
    }

    fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_READONLY))
            .build()
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        val acc = GoogleSignIn.getLastSignedInAccount(context)
        if (acc != null) currentAccount = acc
        return acc
    }

    fun signOut(onComplete: () -> Unit) {
        currentAccount = null
        val client = GoogleSignIn.getClient(context, getSignInOptions())
        client.signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
