package com.example.filesapp.domain.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * Storage Provider implementation backed by Android MediaStore ContentProvider.
 * Queries indexed Images, Audio, Video, and Download media on Android 10+ (API 29).
 */
class MediaStoreProvider(
    private val context: Context,
    override val location: StorageLocation = StorageLocation(
        type = StorageLocationType.MEDIA_STORE,
        rootPath = "content://media",
        displayName = "Media Library",
        isReadOnly = false
    )
) : StorageProvider {

    private val contentResolver = context.contentResolver

    override suspend fun list(path: String, showHidden: Boolean): Result<List<StorageItem>> = withContext(Dispatchers.IO) {
        try {
            val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            )

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            val items = mutableListOf<StorageItem>()

            contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Media_$id"
                    if (!showHidden && name.startsWith(".")) continue

                    val itemUri = ContentUris.withAppendedId(collectionUri, id)
                    val filePath = if (dataCol >= 0) cursor.getString(dataCol) ?: itemUri.toString() else itemUri.toString()
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val dateMod = cursor.getLong(dateCol) * 1000L

                    items.add(
                        StorageItem(
                            id = itemUri.toString(),
                            name = name,
                            path = filePath,
                            uriString = itemUri.toString(),
                            sizeBytes = size,
                            mimeType = mime,
                            lastModified = dateMod,
                            isDirectory = false,
                            locationType = StorageLocationType.MEDIA_STORE
                        )
                    )
                }
            }

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getItem(path: String): Result<StorageItem?> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(path)
            Result.success(
                StorageItem(
                    id = uri.toString(),
                    name = uri.lastPathSegment ?: "Media Item",
                    path = path,
                    uriString = uri.toString(),
                    locationType = StorageLocationType.MEDIA_STORE
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<StorageItem> = 
        Result.failure(UnsupportedOperationException("MediaStore folders are managed via relative paths"))

    override suspend fun createFile(parentPath: String, name: String, mimeType: String): Result<StorageItem> =
        Result.failure(UnsupportedOperationException("MediaStore file creation requires ContentResolver.insert"))

    override suspend fun rename(item: StorageItem, newName: String): Result<StorageItem> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use MediaStore update or SAF for renaming"))
    }

    override suspend fun delete(item: StorageItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(item.uriString ?: item.path)
            val rows = contentResolver.delete(uri, null, null)
            Result.success(rows > 0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(path: String): Boolean = true

    override suspend fun openInputStream(item: StorageItem): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(item.uriString ?: item.path)
            val isStream = contentResolver.openInputStream(uri) ?: return@withContext Result.failure(FileNotFoundException("Cannot open $uri"))
            Result.success(isStream.buffered())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openOutputStream(item: StorageItem, append: Boolean): Result<OutputStream> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(item.uriString ?: item.path)
            val mode = if (append) "wa" else "w"
            val osStream = contentResolver.openOutputStream(uri, mode) ?: return@withContext Result.failure(FileNotFoundException("Cannot write to $uri"))
            Result.success(osStream.buffered())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTotalSpace(path: String): Long = 0L

    override suspend fun getFreeSpace(path: String): Long = 0L
}
