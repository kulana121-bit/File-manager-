package com.example.filesapp.domain.storage

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

/**
 * Production-grade Local Storage Provider operating on local filesystem paths.
 * Executes all heavy I/O strictly on Dispatchers.IO.
 */
class LocalStorageProvider(
    private val context: Context,
    override val location: StorageLocation = StorageLocation.localPrimary(
        Environment.getExternalStorageDirectory().absolutePath
    )
) : StorageProvider {

    override suspend fun list(path: String, showHidden: Boolean): Result<List<StorageItem>> = withContext(Dispatchers.IO) {
        try {
            val targetFile = resolveFile(path)
            if (!targetFile.exists() || !targetFile.isDirectory) {
                return@withContext Result.failure(FileNotFoundException("Directory not found at $path"))
            }

            val files = targetFile.listFiles() ?: arrayOf()
            val items = files.mapNotNull { file ->
                if (!showHidden && file.name.startsWith(".")) return@mapNotNull null
                StorageItem.fromFile(
                    file = file,
                    locationType = location.type,
                    mimeTypeResolver = { resolveMimeType(it) }
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getItem(path: String): Result<StorageItem?> = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(path)
            if (!file.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    StorageItem.fromFile(
                        file = file,
                        locationType = location.type,
                        mimeTypeResolver = { resolveMimeType(it) }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        try {
            val parent = resolveFile(parentPath)
            if (!parent.exists()) parent.mkdirs()
            val newFolder = File(parent, name)
            if (newFolder.exists()) {
                return@withContext Result.failure(FileAlreadyExistsException(newFolder, reason = "Folder already exists"))
            }
            if (newFolder.mkdirs()) {
                Result.success(StorageItem.fromFile(newFolder, location.type, { "vnd.android.document/directory" }))
            } else {
                Result.failure(IOException("Failed to create folder $name in $parentPath"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFile(parentPath: String, name: String, mimeType: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        try {
            val parent = resolveFile(parentPath)
            if (!parent.exists()) parent.mkdirs()
            val newFile = File(parent, name)
            if (newFile.exists()) {
                return@withContext Result.failure(FileAlreadyExistsException(newFile, reason = "File already exists"))
            }
            if (newFile.createNewFile()) {
                Result.success(StorageItem.fromFile(newFile, location.type, { resolveMimeType(it) }))
            } else {
                Result.failure(IOException("Failed to create file $name in $parentPath"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rename(item: StorageItem, newName: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        try {
            val currentFile = resolveFile(item.path)
            if (!currentFile.exists()) {
                return@withContext Result.failure(FileNotFoundException("Source file does not exist: ${item.path}"))
            }
            val parent = currentFile.parentFile ?: return@withContext Result.failure(IOException("Cannot determine parent directory"))
            val newFile = File(parent, newName)
            if (newFile.exists()) {
                return@withContext Result.failure(FileAlreadyExistsException(newFile, reason = "Target name already exists"))
            }
            if (currentFile.renameTo(newFile)) {
                Result.success(StorageItem.fromFile(newFile, location.type, { resolveMimeType(it) }))
            } else {
                Result.failure(IOException("Failed to rename ${item.name} to $newName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(item: StorageItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(item.path)
            if (!file.exists()) return@withContext Result.success(true)
            val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        resolveFile(path).exists()
    }

    override suspend fun openInputStream(item: StorageItem): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(item.path)
            if (!file.exists()) return@withContext Result.failure(FileNotFoundException("File not found: ${item.path}"))
            Result.success(FileInputStream(file).buffered())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openOutputStream(item: StorageItem, append: Boolean): Result<OutputStream> = withContext(Dispatchers.IO) {
        try {
            val file = resolveFile(item.path)
            file.parentFile?.mkdirs()
            Result.success(FileOutputStream(file, append).buffered())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTotalSpace(path: String): Long = withContext(Dispatchers.IO) {
        resolveFile(path).totalSpace.coerceAtLeast(0L)
    }

    override suspend fun getFreeSpace(path: String): Long = withContext(Dispatchers.IO) {
        resolveFile(path).freeSpace.coerceAtLeast(0L)
    }

    private fun resolveFile(path: String): File {
        return if (path.isEmpty() || path == "/") {
            File(location.rootPath)
        } else if (path.startsWith("/")) {
            File(path)
        } else {
            File(location.rootPath, path)
        }
    }

    private fun resolveMimeType(file: File): String {
        val extension = file.extension.lowercase()
        val mimeFromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (!mimeFromMap.isNullOrEmpty()) return mimeFromMap

        return when (extension) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "json" -> "application/json"
            "txt", "kt", "java", "xml", "js", "css", "md" -> "text/plain"
            "html", "htm" -> "text/html"
            else -> "application/octet-stream"
        }
    }
}
