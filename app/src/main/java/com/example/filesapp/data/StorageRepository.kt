package com.example.filesapp.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class StorageBreakdownModel(
    val totalSpaceBytes: Long,
    val freeSpaceBytes: Long,
    val usedSpaceBytes: Long,
    val imageSizeBytes: Long,
    val videoSizeBytes: Long,
    val audioSizeBytes: Long,
    val docSizeBytes: Long,
    val apkSizeBytes: Long,
    val archiveSizeBytes: Long,
    val otherSizeBytes: Long,
    val totalFileCount: Int
)

data class TrashItemModel(
    val trashedFile: File,
    val originalName: String,
    val originalParentPath: String,
    val trashedAtTimestamp: Long,
    val size: Long
)

data class AndroidFileModel(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val dateModified: Long,
    val isDirectory: Boolean,
    val isHidden: Boolean = false,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false
)

data class InstalledAppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val sizeBytes: Long
)

data class ScanProgress(
    val scannedCount: Int,
    val totalCount: Int,
    val currentFileName: String
)

/**
 * Storage Repository for Android 10 (API 29).
 * Operates on real files directly in place with Trash Bin, Categories, Hidden files, ZIP compression/extraction, and Background scanning.
 */
class StorageRepository(private val context: Context) {

    private val trashDirectory: File by lazy {
        File(context.filesDir, ".trash").apply {
            if (!exists()) mkdirs()
        }
    }

    private val trashMetaFile: File by lazy {
        File(trashDirectory, "trash_metadata.json")
    }

    private fun loadTrashMeta(): JSONObject {
        return try {
            if (trashMetaFile.exists()) {
                JSONObject(trashMetaFile.readText())
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun saveTrashMeta(meta: JSONObject) {
        try {
            trashMetaFile.writeText(meta.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Compute real storage metrics for Storage Analyzer.
     */
    fun computeStorageBreakdown(): StorageBreakdownModel {
        val rootDir = Environment.getExternalStorageDirectory()
        val totalSpace = rootDir.totalSpace
        val freeSpace = rootDir.freeSpace
        val usedSpace = (totalSpace - freeSpace).coerceAtLeast(0L)

        var img = 0L; var vid = 0L; var aud = 0L; var doc = 0L; var apk = 0L; var arc = 0L; var oth = 0L
        var count = 0

        fun scan(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    scan(f)
                } else {
                    count++
                    val ext = f.extension.lowercase()
                    val len = f.length()
                    when (ext) {
                        "png", "jpg", "jpeg", "webp", "gif" -> img += len
                        "mp4", "mkv", "avi", "mov" -> vid += len
                        "mp3", "wav", "m4a", "flac" -> aud += len
                        "pdf", "doc", "docx", "txt", "json", "kt", "java", "xml" -> doc += len
                        "apk" -> apk += len
                        "zip", "rar", "7z", "tar", "gz" -> arc += len
                        else -> oth += len
                    }
                }
            }
        }

        scan(rootDir)

        return StorageBreakdownModel(
            totalSpaceBytes = totalSpace,
            freeSpaceBytes = freeSpace,
            usedSpaceBytes = usedSpace,
            imageSizeBytes = img,
            videoSizeBytes = vid,
            audioSizeBytes = aud,
            docSizeBytes = doc,
            apkSizeBytes = apk,
            archiveSizeBytes = arc,
            otherSizeBytes = oth,
            totalFileCount = count
        )
    }

    /**
     * Auto-empty trash files older than 30 days. Called on app start.
     */
    fun autoCleanOldTrash() {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val meta = loadTrashMeta()

        val files = trashDirectory.listFiles() ?: return
        for (file in files) {
            if (file.name == "trash_metadata.json") continue

            val fileMeta = meta.optJSONObject(file.name)
            val trashedAt = fileMeta?.optLong("trashedAt") ?: file.lastModified()

            if ((now - trashedAt) > thirtyDaysMs) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
                meta.remove(file.name)
            }
        }
        saveTrashMeta(meta)
    }

    /**
     * Background scanning flow reporting indexed count and progress to UI.
     */
    fun scanDeviceStorageFlow(showHidden: Boolean = false): Flow<ScanProgress> = flow {
        val rootDir = Environment.getExternalStorageDirectory()
        val allFiles = mutableListOf<File>()

        fun collectFiles(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (!showHidden && f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    collectFiles(f)
                } else {
                    allFiles.add(f)
                }
            }
        }

        collectFiles(rootDir)
        val total = allFiles.size.coerceAtLeast(1)

        if (total == 0) {
            emit(ScanProgress(0, 0, "No files found"))
            return@flow
        }

        allFiles.forEachIndexed { index, file ->
            emit(ScanProgress(index + 1, total, file.name))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Creates a ZIP archive natively in Kotlin from selected files.
     */
    fun createZipArchive(filesToZip: List<File>, zipName: String, targetDir: File): File {
        val zipFile = File(targetDir, if (zipName.endsWith(".zip")) zipName else "$zipName.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            filesToZip.forEach { file ->
                if (file.exists() && file.isFile) {
                    FileInputStream(file).use { fi ->
                        BufferedInputStream(fi).use { origin ->
                            val entry = ZipEntry(file.name)
                            out.putNextEntry(entry)
                            origin.copyTo(out)
                        }
                    }
                }
            }
        }
        return zipFile
    }

    /**
     * Extracts a ZIP archive natively in Kotlin into target directory.
     */
    fun extractZipArchive(zipFile: File, targetDir: File): List<File> {
        val extracted = mutableListOf<File>()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { out ->
                        zis.copyTo(out)
                    }
                    extracted.add(file)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extracted
    }

    /**
     * Backs up an installed app's APK to /Downloads directory.
     */
    fun backupAppApk(appInfo: InstalledAppInfo): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val sourceApk = File(appInfo.apkPath)
        if (!sourceApk.exists()) {
            return null
        }

        val targetApk = File(downloadsDir, "${appInfo.name.replace(" ", "_")}_v${appInfo.versionName}.apk")
        sourceApk.copyTo(targetApk, overwrite = true)
        return targetApk
    }

    /**
     * Soft-deletes a real file by moving it to .trash with exact original filename metadata.
     */
    fun moveToTrash(file: File): File? {
        if (!file.exists()) return null

        val now = System.currentTimeMillis()
        val trashedName = "${now}__TRASH__${file.name}"
        val trashedFile = File(trashDirectory, trashedName)

        if (file.renameTo(trashedFile)) {
            val meta = loadTrashMeta()
            val fileObj = JSONObject().apply {
                put("originalName", file.name)
                put("originalParentPath", file.parentFile?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath)
                put("trashedAt", now)
            }
            meta.put(trashedName, fileObj)
            saveTrashMeta(meta)
            return trashedFile
        }
        return null
    }

    /**
     * Restores a file from Trash Bin back to its exact original filename and directory.
     */
    fun restoreFromTrash(trashedFile: File): File? {
        if (!trashedFile.exists()) return null

        val meta = loadTrashMeta()
        val fileMeta = meta.optJSONObject(trashedFile.name)

        val originalName = fileMeta?.optString("originalName")
            ?: trashedFile.name.substringAfter("__TRASH__", trashedFile.name.substringAfter("_"))
        val parentPath = fileMeta?.optString("originalParentPath")
            ?: Environment.getExternalStorageDirectory().absolutePath

        val targetDir = File(parentPath)
        if (!targetDir.exists()) targetDir.mkdirs()

        val restoredFile = File(targetDir, originalName)
        if (trashedFile.renameTo(restoredFile)) {
            meta.remove(trashedFile.name)
            saveTrashMeta(meta)
            return restoredFile
        }
        return null
    }

    /**
     * Permanently deletes a single file in trash.
     */
    fun deletePermanentlyFromTrash(trashedFile: File): Boolean {
        if (!trashedFile.exists()) return false
        val success = if (trashedFile.isDirectory) trashedFile.deleteRecursively() else trashedFile.delete()
        if (success) {
            val meta = loadTrashMeta()
            meta.remove(trashedFile.name)
            saveTrashMeta(meta)
        }
        return success
    }

    /**
     * Empties Trash Bin permanently.
     */
    fun emptyTrash(): Boolean {
        val files = trashDirectory.listFiles() ?: return true
        var allDeleted = true
        for (f in files) {
            if (f.isDirectory) {
                if (!f.deleteRecursively()) allDeleted = false
            } else {
                if (!f.delete()) allDeleted = false
            }
        }
        trashMetaFile.delete()
        return allDeleted
    }

    /**
     * Lists all items in trash with their original name and metadata.
     */
    fun listTrashItems(): List<TrashItemModel> {
        val meta = loadTrashMeta()
        val files = trashDirectory.listFiles()?.filter { it.name != "trash_metadata.json" } ?: emptyList()

        return files.map { file ->
            val fileMeta = meta.optJSONObject(file.name)
            val originalName = fileMeta?.optString("originalName")
                ?: file.name.substringAfter("__TRASH__", file.name.substringAfter("_"))
            val parentPath = fileMeta?.optString("originalParentPath")
                ?: Environment.getExternalStorageDirectory().absolutePath
            val trashedAt = fileMeta?.optLong("trashedAt") ?: file.lastModified()

            TrashItemModel(
                trashedFile = file,
                originalName = originalName,
                originalParentPath = parentPath,
                trashedAtTimestamp = trashedAt,
                size = file.length()
            )
        }.sortedByDescending { it.trashedAtTimestamp }
    }

    /**
     * Copies a real file in place securely on a background thread.
     * Implements collision safety to never truncate the source when copying onto itself.
     */
    suspend fun copyFileInPlace(file: File, targetDir: File): File? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            if (!targetDir.exists()) targetDir.mkdirs()

            var destFile = File(targetDir, file.name)
            // If copying to the same exact folder and path, append _copy suffix to prevent critical truncation data loss
            if (destFile.absolutePath == file.absolutePath) {
                val baseName = file.nameWithoutExtension
                val extension = file.extension
                val suffix = if (extension.isNotEmpty()) ".$extension" else ""
                var counter = 1
                do {
                    destFile = File(targetDir, "${baseName}_copy$counter$suffix")
                    counter++
                } while (destFile.exists())
            }

            if (file.isDirectory) {
                if (file.copyRecursively(destFile, overwrite = true)) destFile else null
            } else {
                file.copyTo(destFile, overwrite = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Permanently deletes a real file from storage securely on a background thread.
     */
    suspend fun deleteFileInPlace(file: File): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Renames a real file in place securely on a background thread.
     */
    suspend fun renameFileInPlace(file: File, newName: String): File? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val newFile = File(file.parentFile, newName)
            if (file.renameTo(newFile)) newFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Moves a real file in place securely on a background thread.
     */
    suspend fun moveFileInPlace(file: File, targetDir: File): File? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val destFile = File(targetDir, file.name)
            if (file.renameTo(destFile)) destFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a new folder in place on a background thread.
     */
    suspend fun createFolder(parentDir: File, name: String): File? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val folder = File(parentDir, name)
            if (!folder.exists() && folder.mkdirs()) folder else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates an empty new file in place on a background thread.
     */
    suspend fun createNewFile(parentDir: File, name: String): File? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val file = File(parentDir, name)
            if (!file.exists() && file.createNewFile()) file else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
