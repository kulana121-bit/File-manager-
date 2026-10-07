package com.example.filesapp.domain.storage

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Common abstraction for Cloud & Network Storage providers
 * (Google Drive, FTP, SFTP, WebDAV, etc.)
 */
interface CloudProvider {
    val providerName: String
    val isConnected: Boolean

    suspend fun listFiles(folderIdOrPath: String = "root"): Result<List<CloudFileItem>>
    suspend fun createFolder(parentFolderIdOrPath: String, folderName: String): Result<CloudFileItem>
    suspend fun uploadFile(
        localFile: File,
        parentFolderIdOrPath: String,
        onProgress: (Float) -> Unit = {}
    ): Result<CloudFileItem>

    suspend fun downloadFile(
        fileIdOrPath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File>

    suspend fun rename(fileIdOrPath: String, newName: String): Result<CloudFileItem>
    suspend fun move(fileIdOrPath: String, newParentIdOrPath: String): Result<Boolean>
    suspend fun delete(fileIdOrPath: String): Result<Boolean>
    suspend fun searchFiles(query: String): Result<List<CloudFileItem>>
}

data class CloudFileItem(
    val id: String,
    val name: String,
    val pathOrParentId: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val webViewLink: String? = null,
    val iconUrl: String? = null
)
