package com.example.filesapp.domain.storage

import java.io.InputStream
import java.io.OutputStream

/**
 * Clean domain abstraction for file storage operations.
 * Allows transparent interaction with Local filesystem, SAF Documents, MediaStore, Google Drive, SMB, and FTP.
 */
interface StorageProvider {
    /**
     * Unique identifier and type for this storage provider.
     */
    val location: StorageLocation

    /**
     * Lists child items at the given relative or absolute path.
     */
    suspend fun list(path: String, showHidden: Boolean = false): Result<List<StorageItem>>

    /**
     * Retrieves metadata for a specific path.
     */
    suspend fun getItem(path: String): Result<StorageItem?>

    /**
     * Creates a new directory at the specified parent path.
     */
    suspend fun createFolder(parentPath: String, name: String): Result<StorageItem>

    /**
     * Creates a new empty file at the specified parent path.
     */
    suspend fun createFile(parentPath: String, name: String, mimeType: String = "text/plain"): Result<StorageItem>

    /**
     * Renames an item to a new name.
     */
    suspend fun rename(item: StorageItem, newName: String): Result<StorageItem>

    /**
     * Deletes an item (recursively if directory).
     */
    suspend fun delete(item: StorageItem): Result<Boolean>

    /**
     * Checks if a file or folder exists at the given path.
     */
    suspend fun exists(path: String): Boolean

    /**
     * Opens an InputStream for reading the item.
     */
    suspend fun openInputStream(item: StorageItem): Result<InputStream>

    /**
     * Opens an OutputStream for writing to the item.
     */
    suspend fun openOutputStream(item: StorageItem, append: Boolean = false): Result<OutputStream>

    /**
     * Returns total capacity in bytes for this storage location.
     */
    suspend fun getTotalSpace(path: String = ""): Long

    /**
     * Returns available/free capacity in bytes for this storage location.
     */
    suspend fun getFreeSpace(path: String = ""): Long
}
