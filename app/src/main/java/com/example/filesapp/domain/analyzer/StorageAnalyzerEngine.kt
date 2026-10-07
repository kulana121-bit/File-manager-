package com.example.filesapp.domain.analyzer

import android.content.Context
import android.os.Environment
import com.example.filesapp.data.AndroidFileModel
import com.example.filesapp.data.DuplicateFileGroup
import com.example.filesapp.data.DuplicateScanProgress
import com.example.filesapp.data.StorageBreakdownModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Empty folder item descriptor.
 */
data class EmptyFolderItem(
    val folder: File,
    val path: String,
    val name: String,
    val dateModified: Long
)

/**
 * Comprehensive Storage Analysis report.
 */
data class FullStorageAnalysisReport(
    val breakdown: StorageBreakdownModel,
    val largestFiles: List<AndroidFileModel>,
    val emptyFolders: List<EmptyFolderItem>,
    val duplicateGroups: List<DuplicateFileGroup>,
    val totalDuplicateWastedBytes: Long,
    val allIndexedFiles: List<AndroidFileModel>
)

/**
 * Google Files-style Storage Analyzer & 3-Tier Progressive Duplicate Detection Engine.
 */
class StorageAnalyzerEngine(private val context: Context) {

    /**
     * Computes deep storage metrics, largest files, and empty folders in a single I/O pass.
     */
    suspend fun analyzeStorage(
        rootDirectory: File = Environment.getExternalStorageDirectory(),
        showHidden: Boolean = false
    ): FullStorageAnalysisReport = withContext(Dispatchers.IO) {
        val totalSpace = rootDirectory.totalSpace
        val freeSpace = rootDirectory.freeSpace
        val usedSpace = (totalSpace - freeSpace).coerceAtLeast(0L)

        var img = 0L; var vid = 0L; var aud = 0L; var doc = 0L; var apk = 0L; var arc = 0L; var oth = 0L
        var count = 0

        val allFiles = mutableListOf<AndroidFileModel>()
        val emptyFolders = mutableListOf<EmptyFolderItem>()

        fun scan(dir: File): Boolean {
            val list = dir.listFiles() ?: return false
            var hasFiles = false

            for (f in list) {
                if (!showHidden && f.name.startsWith(".")) continue

                if (f.isDirectory) {
                    val childHasFiles = scan(f)
                    if (childHasFiles) {
                        hasFiles = true
                    } else {
                        emptyFolders.add(
                            EmptyFolderItem(
                                folder = f,
                                path = f.absolutePath,
                                name = f.name,
                                dateModified = f.lastModified()
                            )
                        )
                    }
                } else if (f.isFile) {
                    hasFiles = true
                    count++
                    val len = f.length()
                    val ext = f.extension.lowercase()
                    val mime = resolveMime(f)

                    when {
                        mime.startsWith("image/") || ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg") -> img += len
                        mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> vid += len
                        mime.startsWith("audio/") || ext in listOf("mp3", "wav", "m4a", "flac", "ogg") -> aud += len
                        mime.contains("pdf") || mime.startsWith("text/") || ext in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "json", "xml", "csv", "kt", "java") -> doc += len
                        ext == "apk" || mime.contains("package-archive") -> apk += len
                        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> arc += len
                        else -> oth += len
                    }

                    allFiles.add(
                        AndroidFileModel(
                            id = f.absolutePath,
                            name = f.name,
                            path = f.absolutePath,
                            size = len,
                            mimeType = mime,
                            dateModified = f.lastModified(),
                            isDirectory = false,
                            isHidden = f.name.startsWith(".")
                        )
                    )
                }
            }
            return hasFiles
        }

        scan(rootDirectory)

        val breakdown = StorageBreakdownModel(
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

        val largestFiles = allFiles
            .sortedByDescending { it.size }
            .take(30)

        FullStorageAnalysisReport(
            breakdown = breakdown,
            largestFiles = largestFiles,
            emptyFolders = emptyFolders.sortedBy { it.name.lowercase() },
            duplicateGroups = emptyList(),
            totalDuplicateWastedBytes = 0L,
            allIndexedFiles = allFiles
        )
    }

    /**
     * 3-Tier Progressive Duplicate Finder:
     * Tier 1: Group files by exact byte length (filters out ~95% of non-matching files with 0 hashing).
     * Tier 2: For size matches, compute 4KB partial hash (head, middle, tail chunks) in milliseconds.
     * Tier 3: Full SHA-256 hash only for items with identical partial hash.
     */
    fun findDuplicatesProgressiveFlow(
        rootDirectory: File = Environment.getExternalStorageDirectory()
    ): Flow<DuplicateScanProgress> = flow {
        emit(DuplicateScanProgress(isScanning = true, scannedFiles = 0, totalFiles = 0, currentFileName = "Collecting storage files..."))

        val allFiles = mutableListOf<File>()
        fun collect(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    collect(f)
                } else if (f.isFile && f.length() > 0) {
                    allFiles.add(f)
                }
            }
        }
        collect(rootDirectory)

        // Tier 1: Group by Size
        val sizeBuckets = allFiles.groupBy { it.length() }.filter { it.value.size > 1 }
        val candidateFiles = sizeBuckets.values.flatten()
        val totalCandidates = candidateFiles.size

        if (totalCandidates == 0) {
            emit(DuplicateScanProgress(isScanning = false, scannedFiles = 0, totalFiles = 0, currentFileName = "No duplicates found"))
            return@flow
        }

        emit(DuplicateScanProgress(isScanning = true, scannedFiles = 0, totalFiles = totalCandidates, currentFileName = "Analyzing partial hashes..."))

        // Tier 2: Partial Hashes (Head + Middle + Tail 4KB samples)
        val partialHashBuckets = mutableMapOf<String, MutableList<File>>()
        var processed = 0

        for (file in candidateFiles) {
            processed++
            emit(
                DuplicateScanProgress(
                    isScanning = true,
                    scannedFiles = processed,
                    totalFiles = totalCandidates,
                    currentFileName = "Sample hash: ${file.name}"
                )
            )

            val pHash = computePartialHash(file)
            val key = "${file.length()}_$pHash"
            partialHashBuckets.getOrPut(key) { mutableListOf() }.add(file)
        }

        // Tier 3: Full SHA-256 for Partial Hash Collisions
        val partialCollisions = partialHashBuckets.values.filter { it.size > 1 }.flatten()
        val fullHashBuckets = mutableMapOf<String, MutableList<File>>()
        var fullProcessed = 0

        for (file in partialCollisions) {
            fullProcessed++
            emit(
                DuplicateScanProgress(
                    isScanning = true,
                    scannedFiles = fullProcessed,
                    totalFiles = partialCollisions.size,
                    currentFileName = "Full verification: ${file.name}"
                )
            )

            val fullHash = computeFullSha256(file)
            val fullKey = "${file.length()}_$fullHash"
            fullHashBuckets.getOrPut(fullKey) { mutableListOf() }.add(file)
        }

        val duplicateGroups = fullHashBuckets.values
            .filter { it.size > 1 }
            .map { list ->
                val f = list.first()
                DuplicateFileGroup(
                    hash = f.name,
                    fileSize = f.length(),
                    files = list.sortedBy { it.lastModified() }
                )
            }
            .sortedByDescending { it.wastedSizeBytes }

        val totalWasted = duplicateGroups.sumOf { it.wastedSizeBytes }

        emit(
            DuplicateScanProgress(
                isScanning = false,
                scannedFiles = totalCandidates,
                totalFiles = totalCandidates,
                currentFileName = "Scan Complete",
                duplicateGroups = duplicateGroups,
                totalWastedBytes = totalWasted
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Fast partial hash reading up to three 4KB slices (beginning, center, end).
     */
    private fun computePartialHash(file: File): String {
        return try {
            val length = file.length()
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(4096)

            RandomAccessFile(file, "r").use { raf ->
                // 1. Head
                val headRead = raf.read(buffer, 0, buffer.size.coerceAtMost(length.toInt()))
                if (headRead > 0) digest.update(buffer, 0, headRead)

                // 2. Middle
                if (length > 16384) {
                    raf.seek(length / 2)
                    val midRead = raf.read(buffer, 0, buffer.size)
                    if (midRead > 0) digest.update(buffer, 0, midRead)
                }

                // 3. Tail
                if (length > 32768) {
                    raf.seek(length - 4096)
                    val tailRead = raf.read(buffer, 0, buffer.size)
                    if (tailRead > 0) digest.update(buffer, 0, tailRead)
                }
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            "${file.length()}_err"
        }
    }

    /**
     * Full SHA-256 streaming hash.
     */
    private fun computeFullSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            "${file.name}_${file.length()}"
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * Cleans/deletes empty folders safely.
     */
    suspend fun cleanEmptyFolders(folders: List<EmptyFolderItem>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (item in folders) {
            try {
                if (item.folder.exists() && item.folder.isDirectory) {
                    val contents = item.folder.listFiles()
                    if (contents.isNullOrEmpty()) {
                        if (item.folder.delete()) deleted++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        deleted
    }

    private fun resolveMime(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "txt", "json", "kt", "java", "xml" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
