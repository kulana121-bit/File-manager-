package com.example.filesapp.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Google Drive Integration using Google Play Services Auth & Drive v3 API.
 */
class GoogleDriveManager(private val context: Context) {

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

    suspend fun fetchDriveFiles(account: GoogleSignInAccount): List<com.google.api.services.drive.model.File> =
        withContext(Dispatchers.IO) {
            val service = getDriveService(account)
            val result = service.files().list()
                .setPageSize(30)
                .setFields("files(id, name, mimeType, size, modifiedTime)")
                .execute()
            result.files ?: emptyList()
        }

    suspend fun uploadFile(account: GoogleSignInAccount, localFile: File): com.google.api.services.drive.model.File =
        withContext(Dispatchers.IO) {
            val service = getDriveService(account)
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = localFile.name
            }
            val mediaContent = FileContent(null, localFile)
            service.files().create(fileMetadata, mediaContent)
                .setFields("id, name, mimeType, size, modifiedTime")
                .execute()
        }

    suspend fun downloadFile(account: GoogleSignInAccount, driveFileId: String, targetFile: File): File =
        withContext(Dispatchers.IO) {
            val service = getDriveService(account)
            FileOutputStream(targetFile).use { outputStream ->
                service.files().get(driveFileId).executeMediaAndDownloadTo(outputStream)
            }
            targetFile
        }

    fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_READONLY))
            .build()
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun signOut(onComplete: () -> Unit) {
        val client = GoogleSignIn.getClient(context, getSignInOptions())
        client.signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
