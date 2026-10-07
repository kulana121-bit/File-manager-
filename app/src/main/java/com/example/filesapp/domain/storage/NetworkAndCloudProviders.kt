package com.example.filesapp.domain.storage

import android.content.Context
import com.example.filesapp.data.GoogleDriveManager
import com.example.filesapp.data.NetworkServerConfig
import com.example.filesapp.data.NetworkStorageManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

/**
 * Cloud Storage Provider implementation for Google Drive.
 */
class GoogleDriveStorageProvider(
    private val context: Context,
    private val driveManager: GoogleDriveManager,
    var currentAccount: GoogleSignInAccount? = null
) : StorageProvider {

    override val location: StorageLocation
        get() = StorageLocation.googleDrive(currentAccount?.email ?: "Not signed in")

    override suspend fun list(path: String, showHidden: Boolean): Result<List<StorageItem>> = withContext(Dispatchers.IO) {
        val folderId = if (path.isBlank() || path == "/") "root" else path.removePrefix("drive://")
        val result = driveManager.listFiles(folderId)
        result.map { cloudFiles ->
            cloudFiles.map { cf ->
                StorageItem(
                    id = cf.id,
                    name = cf.name,
                    path = "drive://${cf.id}",
                    uriString = "drive://${cf.id}",
                    sizeBytes = cf.sizeBytes,
                    mimeType = cf.mimeType,
                    lastModified = cf.lastModified,
                    isDirectory = cf.isDirectory,
                    locationType = StorageLocationType.GOOGLE_DRIVE
                )
            }
        }
    }

    override suspend fun getItem(path: String): Result<StorageItem?> = withContext(Dispatchers.IO) {
        Result.success(null)
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        val folderId = if (parentPath.isBlank() || parentPath == "/") "root" else parentPath.removePrefix("drive://")
        driveManager.createFolder(folderId, name).map { cf ->
            StorageItem(
                id = cf.id,
                name = cf.name,
                path = "drive://${cf.id}",
                uriString = "drive://${cf.id}",
                sizeBytes = 0L,
                mimeType = "application/vnd.google-apps.folder",
                lastModified = cf.lastModified,
                isDirectory = true,
                locationType = StorageLocationType.GOOGLE_DRIVE
            )
        }
    }

    override suspend fun createFile(parentPath: String, name: String, mimeType: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("File creation on Drive via uploadFile"))
    }

    override suspend fun rename(item: StorageItem, newName: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        driveManager.rename(item.id, newName).map { cf ->
            item.copy(name = cf.name)
        }
    }

    override suspend fun delete(item: StorageItem): Result<Boolean> = withContext(Dispatchers.IO) {
        driveManager.delete(item.id)
    }

    override suspend fun exists(path: String): Boolean = true

    override suspend fun openInputStream(item: StorageItem): Result<InputStream> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use driveManager.downloadFile"))
    }

    override suspend fun openOutputStream(item: StorageItem, append: Boolean): Result<OutputStream> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use driveManager.uploadFile"))
    }

    override suspend fun getTotalSpace(path: String): Long = 15L * 1024 * 1024 * 1024
    override suspend fun getFreeSpace(path: String): Long = 10L * 1024 * 1024 * 1024
}

/**
 * Network Storage Provider implementation for FTP / FTPS servers.
 */
class FtpStorageProvider(
    private val context: Context,
    private val networkStorageManager: NetworkStorageManager,
    var serverConfig: NetworkServerConfig
) : StorageProvider {

    override val location: StorageLocation
        get() = StorageLocation.ftpServer(serverConfig.host, serverConfig.port)

    override suspend fun list(path: String, showHidden: Boolean): Result<List<StorageItem>> = withContext(Dispatchers.IO) {
        val res = networkStorageManager.listRemoteFiles(serverConfig, path)
        res.map { remoteList ->
            remoteList.map { rf ->
                StorageItem(
                    id = rf.path,
                    name = rf.name,
                    path = rf.path,
                    sizeBytes = rf.size,
                    mimeType = if (rf.isDirectory) "vnd.android.document/directory" else "application/octet-stream",
                    lastModified = rf.lastModified,
                    isDirectory = rf.isDirectory,
                    locationType = StorageLocationType.FTP_NETWORK
                )
            }
        }
    }

    override suspend fun getItem(path: String): Result<StorageItem?> = withContext(Dispatchers.IO) {
        Result.success(null)
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("FTP remote folder creation"))
    }

    override suspend fun createFile(parentPath: String, name: String, mimeType: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("FTP remote file creation"))
    }

    override suspend fun rename(item: StorageItem, newName: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("FTP remote rename"))
    }

    override suspend fun delete(item: StorageItem): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("FTP remote delete"))
    }

    override suspend fun exists(path: String): Boolean = true

    override suspend fun openInputStream(item: StorageItem): Result<InputStream> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use networkStorageManager.downloadRemoteFile"))
    }

    override suspend fun openOutputStream(item: StorageItem, append: Boolean): Result<OutputStream> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use networkStorageManager.uploadFile"))
    }

    override suspend fun getTotalSpace(path: String): Long = 0L
    override suspend fun getFreeSpace(path: String): Long = 0L
}

/**
 * Network Storage Provider implementation for SMB / Samba Windows Network Shares.
 */
class SmbStorageProvider(
    private val context: Context,
    private val networkStorageManager: NetworkStorageManager,
    var serverConfig: NetworkServerConfig
) : StorageProvider {

    override val location: StorageLocation
        get() = StorageLocation.smbServer(serverConfig.host, serverConfig.initialPath)

    override suspend fun list(path: String, showHidden: Boolean): Result<List<StorageItem>> = withContext(Dispatchers.IO) {
        val res = networkStorageManager.listRemoteFiles(serverConfig, path)
        res.map { remoteList ->
            remoteList.map { rf ->
                StorageItem(
                    id = rf.path,
                    name = rf.name,
                    path = rf.path,
                    sizeBytes = rf.size,
                    mimeType = if (rf.isDirectory) "vnd.android.document/directory" else "application/octet-stream",
                    lastModified = rf.lastModified,
                    isDirectory = rf.isDirectory,
                    locationType = StorageLocationType.SMB_NETWORK
                )
            }
        }
    }

    override suspend fun getItem(path: String): Result<StorageItem?> = withContext(Dispatchers.IO) {
        Result.success(null)
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("SMB remote folder creation"))
    }

    override suspend fun createFile(parentPath: String, name: String, mimeType: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("SMB remote file creation"))
    }

    override suspend fun rename(item: StorageItem, newName: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("SMB remote rename"))
    }

    override suspend fun delete(item: StorageItem): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("SMB remote delete"))
    }

    override suspend fun exists(path: String): Boolean = true

    override suspend fun openInputStream(item: StorageItem): Result<InputStream> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use networkStorageManager.downloadRemoteFile"))
    }

    override suspend fun openOutputStream(item: StorageItem, append: Boolean): Result<OutputStream> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use networkStorageManager.uploadFile"))
    }

    override suspend fun getTotalSpace(path: String): Long = 0L
    override suspend fun getFreeSpace(path: String): Long = 0L
}
