package com.example.filesapp.domain.storage

import android.net.Uri
import com.example.filesapp.data.AndroidFileModel
import java.io.File

/**
 * Clean domain model for any file or directory across local, SAF, MediaStore, Cloud, and Network providers.
 * Decouples the UI and domain logic from raw java.io.File.
 */
data class StorageItem(
    val id: String,
    val name: String,
    val path: String,
    val uriString: String? = null,
    val sizeBytes: Long = 0L,
    val mimeType: String = "application/octet-stream",
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val locationType: StorageLocationType = StorageLocationType.LOCAL_PRIMARY,
    val isHidden: Boolean = false,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
    val canDelete: Boolean = true,
    val childCount: Int = 0
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "")

    val nameWithoutExtension: String
        get() = if (isDirectory) name else name.substringBeforeLast('.', name)

    val uri: Uri?
        get() = uriString?.let { Uri.parse(it) }

    val parentPath: String
        get() {
            if (path == "/" || path.isEmpty()) return "/"
            val trimmed = path.trimEnd('/')
            val idx = trimmed.lastIndexOf('/')
            return if (idx <= 0) "/" else trimmed.substring(0, idx)
        }

    /**
     * Converts to AndroidFileModel for full backward compatibility with existing UI components.
     */
    fun toAndroidFileModel(): AndroidFileModel {
        return AndroidFileModel(
            id = id,
            name = name,
            path = path,
            size = sizeBytes,
            mimeType = mimeType,
            dateModified = lastModified,
            isDirectory = isDirectory,
            isHidden = isHidden,
            isStarred = isStarred,
            isPinned = isPinned
        )
    }

    /**
     * Safe conversion to java.io.File if this is a local primary or app internal item.
     */
    fun toFileOrNull(): File? {
        return if (locationType == StorageLocationType.LOCAL_PRIMARY || 
                   locationType == StorageLocationType.LOCAL_APP_INTERNAL ||
                   path.startsWith("/")) {
            File(path)
        } else {
            null
        }
    }

    companion object {
        /**
         * Creates a StorageItem from a standard java.io.File.
         */
        fun fromFile(
            file: File,
            locationType: StorageLocationType = StorageLocationType.LOCAL_PRIMARY,
            mimeTypeResolver: (File) -> String = { "application/octet-stream" },
            isStarred: Boolean = false,
            isPinned: Boolean = false
        ): StorageItem {
            val isDir = file.isDirectory
            val isHid = file.name.startsWith(".")
            return StorageItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                uriString = Uri.fromFile(file).toString(),
                sizeBytes = if (isDir) 0L else file.length(),
                mimeType = if (isDir) "vnd.android.document/directory" else mimeTypeResolver(file),
                lastModified = file.lastModified(),
                isDirectory = isDir,
                locationType = locationType,
                isHidden = isHid,
                isStarred = isStarred,
                isPinned = isPinned,
                canRead = file.canRead(),
                canWrite = file.canWrite(),
                canDelete = file.canWrite()
            )
        }

        /**
         * Creates a StorageItem from an existing AndroidFileModel.
         */
        fun fromAndroidFileModel(
            model: AndroidFileModel,
            locationType: StorageLocationType = StorageLocationType.LOCAL_PRIMARY
        ): StorageItem {
            return StorageItem(
                id = model.id,
                name = model.name,
                path = model.path,
                uriString = if (model.path.startsWith("/")) Uri.fromFile(File(model.path)).toString() else null,
                sizeBytes = model.size,
                mimeType = model.mimeType,
                lastModified = model.dateModified,
                isDirectory = model.isDirectory,
                locationType = locationType,
                isHidden = model.isHidden,
                isStarred = model.isStarred,
                isPinned = model.isPinned,
                canRead = true,
                canWrite = true,
                canDelete = true
            )
        }
    }
}
