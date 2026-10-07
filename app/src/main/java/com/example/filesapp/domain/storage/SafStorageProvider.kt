package com.example.filesapp.domain.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Storage Provider implementation backed by Android Storage Access Framework (SAF).
 * Operates on tree Document URIs with persistent read/write permissions.
 */
class SafStorageProvider(
    private val context: Context,
    val treeUri: Uri,
    override val location: StorageLocation = StorageLocation(
        type = StorageLocationType.SAF_DOCUMENT_TREE,
        rootPath = treeUri.toString(),
        displayName = "SAF Storage",
        isRemovable = false,
        isReadOnly = false
    )
) : StorageProvider {

    private val contentResolver = context.contentResolver

    private fun getRootDocument(): DocumentFile? {
        return DocumentFile.fromTreeUri(context, treeUri)
    }

    private fun findDocument(path: String): DocumentFile? {
        val root = getRootDocument() ?: return null
        if (path.isEmpty() || path == "/" || path == treeUri.toString()) return root

        val segments = path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        var current: DocumentFile = root
        for (segment in segments) {
            val next = current.findFile(segment) ?: return null
            current = next
        }
        return current
    }

    override suspend fun list(path: String, showHidden: Boolean): Result<List<StorageItem>> = withContext(Dispatchers.IO) {
        try {
            val doc = findDocument(path) ?: return@withContext Result.failure(FileNotFoundException("SAF path not found: $path"))
            val files = doc.listFiles()
            val items = files.mapNotNull { df ->
                val name = df.name ?: return@mapNotNull null
                if (!showHidden && name.startsWith(".")) return@mapNotNull null
                val isDir = df.isDirectory
                StorageItem(
                    id = df.uri.toString(),
                    name = name,
                    path = df.uri.toString(),
                    uriString = df.uri.toString(),
                    sizeBytes = if (isDir) 0L else df.length(),
                    mimeType = df.type ?: if (isDir) "vnd.android.document/directory" else "application/octet-stream",
                    lastModified = df.lastModified(),
                    isDirectory = isDir,
                    locationType = StorageLocationType.SAF_DOCUMENT_TREE,
                    isHidden = name.startsWith("."),
                    canRead = df.canRead(),
                    canWrite = df.canWrite(),
                    canDelete = df.canWrite()
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getItem(path: String): Result<StorageItem?> = withContext(Dispatchers.IO) {
        try {
            val df = findDocument(path)
            if (df == null || !df.exists()) {
                Result.success(null)
            } else {
                val isDir = df.isDirectory
                Result.success(
                    StorageItem(
                        id = df.uri.toString(),
                        name = df.name ?: "Untitled",
                        path = df.uri.toString(),
                        uriString = df.uri.toString(),
                        sizeBytes = if (isDir) 0L else df.length(),
                        mimeType = df.type ?: if (isDir) "vnd.android.document/directory" else "application/octet-stream",
                        lastModified = df.lastModified(),
                        isDirectory = isDir,
                        locationType = StorageLocationType.SAF_DOCUMENT_TREE,
                        canRead = df.canRead(),
                        canWrite = df.canWrite(),
                        canDelete = df.canWrite()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        try {
            val parentDoc = findDocument(parentPath) ?: return@withContext Result.failure(FileNotFoundException("Parent doc not found"))
            val created = parentDoc.createDirectory(name) ?: return@withContext Result.failure(IOException("Failed to create SAF directory: $name"))
            Result.success(
                StorageItem(
                    id = created.uri.toString(),
                    name = name,
                    path = created.uri.toString(),
                    uriString = created.uri.toString(),
                    sizeBytes = 0L,
                    mimeType = "vnd.android.document/directory",
                    lastModified = System.currentTimeMillis(),
                    isDirectory = true,
                    locationType = StorageLocationType.SAF_DOCUMENT_TREE
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFile(parentPath: String, name: String, mimeType: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        try {
            val parentDoc = findDocument(parentPath) ?: return@withContext Result.failure(FileNotFoundException("Parent doc not found"))
            val created = parentDoc.createFile(mimeType, name) ?: return@withContext Result.failure(IOException("Failed to create SAF file: $name"))
            Result.success(
                StorageItem(
                    id = created.uri.toString(),
                    name = created.name ?: name,
                    path = created.uri.toString(),
                    uriString = created.uri.toString(),
                    sizeBytes = 0L,
                    mimeType = mimeType,
                    lastModified = System.currentTimeMillis(),
                    isDirectory = false,
                    locationType = StorageLocationType.SAF_DOCUMENT_TREE
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rename(item: StorageItem, newName: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        try {
            val doc = findDocument(item.path) ?: return@withContext Result.failure(FileNotFoundException("Item not found: ${item.path}"))
            val success = doc.renameTo(newName)
            if (success) {
                Result.success(item.copy(name = newName))
            } else {
                Result.failure(IOException("SAF rename failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(item: StorageItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val doc = findDocument(item.path) ?: return@withContext Result.success(true)
            Result.success(doc.delete())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        findDocument(path)?.exists() == true
    }

    override suspend fun openInputStream(item: StorageItem): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(item.uriString ?: item.path)
            val stream = contentResolver.openInputStream(uri) ?: return@withContext Result.failure(FileNotFoundException("Could not open stream for $uri"))
            Result.success(stream.buffered())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openOutputStream(item: StorageItem, append: Boolean): Result<OutputStream> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(item.uriString ?: item.path)
            val mode = if (append) "wa" else "w"
            val stream = contentResolver.openOutputStream(uri, mode) ?: return@withContext Result.failure(IOException("Could not open output stream for $uri"))
            Result.success(stream.buffered())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTotalSpace(path: String): Long = 0L

    override suspend fun getFreeSpace(path: String): Long = 0L
}
