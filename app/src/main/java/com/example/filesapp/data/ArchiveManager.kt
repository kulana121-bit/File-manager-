package com.example.filesapp.data

import com.example.filesapp.domain.archive.ArchiveEngine
import com.example.filesapp.domain.archive.ArchiveFormat
import com.example.filesapp.domain.archive.CompressionLevel
import java.io.File

/**
 * Universal Unified Archive Manager.
 * Supports ZIP, 7Z, RAR, TAR, TAR.GZ, TGZ, TAR.BZ2, TAR.XZ, GZ, XZ, BZ2.
 * Enables browsing internal hierarchy without extraction, fast listing,
 * selective extraction, full extraction, and multi-level compression (Store/Normal/Max).
 */
data class ArchiveEntryModel(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long = 0L,
    val timeModified: Long = 0L
)

object ArchiveManager {

    enum class ArchiveType {
        ZIP, SEVEN_Z, RAR, TAR, UNKNOWN
    }

    fun detectType(file: File): ArchiveType {
        return when (ArchiveEngine.detectFormat(file)) {
            ArchiveFormat.ZIP -> ArchiveType.ZIP
            ArchiveFormat.SEVEN_Z -> ArchiveType.SEVEN_Z
            ArchiveFormat.RAR -> ArchiveType.RAR
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ,
            ArchiveFormat.GZ, ArchiveFormat.XZ, ArchiveFormat.BZ2 -> ArchiveType.TAR
            ArchiveFormat.UNKNOWN -> ArchiveType.UNKNOWN
        }
    }

    fun isArchive(file: File): Boolean = ArchiveEngine.isSupportedArchive(file)

    /**
     * Lists all archive entries in memory without extracting to disk.
     * @param password Optional password for encrypted archives.
     */
    fun listEntries(archiveFile: File, password: String? = null): List<ArchiveEntryModel> {
        return ArchiveEngine.listEntries(archiveFile, password).map { entry ->
            ArchiveEntryModel(
                name = entry.path,
                isDirectory = entry.isDirectory,
                size = entry.uncompressedSize,
                compressedSize = entry.compressedSize,
                timeModified = entry.lastModified
            )
        }
    }

    /**
     * Checks if an archive is password-protected.
     */
    fun isPasswordProtected(archiveFile: File): Boolean {
        return ArchiveEngine.isPasswordProtected(archiveFile)
    }

    /**
     * Extracts a single entry directly from the archive into destFile with Zip Slip protection.
     */
    fun extractSingleEntry(archiveFile: File, entryPath: String, destFile: File): Boolean {
        return try {
            val parent = destFile.parentFile ?: return false
            ArchiveEngine.extract(
                archiveFile = archiveFile,
                destDir = parent,
                selectedPaths = setOf(entryPath.replace('\\', '/').trimStart('/'))
            )
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Extracts all files in the archive to destDir with Zip Slip, symlink, and decompression bomb protection.
     */
    fun extractAll(
        archiveFile: File,
        destDir: File,
        password: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean {
        return ArchiveEngine.extract(
            archiveFile = archiveFile,
            destDir = destDir,
            selectedPaths = null,
            password = password,
            onProgress = onProgress
        )
    }

    /**
     * Extracts selected entries to destDir with Zip Slip security.
     */
    fun extractSelected(
        archiveFile: File,
        selectedPaths: Set<String>,
        destDir: File,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean {
        return ArchiveEngine.extract(
            archiveFile = archiveFile,
            destDir = destDir,
            selectedPaths = selectedPaths,
            onProgress = onProgress
        )
    }

    /**
     * Creates a ZIP archive with compression level (Store, Normal, Maximum).
     */
    fun createZip(
        sources: List<File>,
        targetZip: File,
        level: CompressionLevel = CompressionLevel.NORMAL,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean {
        return ArchiveEngine.createZipArchive(sources, targetZip, level, onProgress)
    }

    /**
     * Creates a 7Z archive with compression level (Store, Normal, Maximum).
     */
    fun create7z(
        sources: List<File>,
        target7z: File,
        level: CompressionLevel = CompressionLevel.NORMAL,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean {
        return ArchiveEngine.createSevenZArchive(sources, target7z, level, onProgress)
    }
}
