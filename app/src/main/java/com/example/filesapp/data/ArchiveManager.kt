package com.example.filesapp.data

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Universal Unified Archive Manager.
 * Supports ZIP, 7Z, RAR, TAR, TAR.GZ, TGZ, TAR.BZ2, TAR.XZ.
 * Enables browsing internal hierarchy without extraction, fast listing,
 * single file extraction, and full extraction.
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
        val name = file.name.lowercase()
        return when {
            name.endsWith(".zip") -> ArchiveType.ZIP
            name.endsWith(".7z") -> ArchiveType.SEVEN_Z
            name.endsWith(".rar") -> ArchiveType.RAR
            name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz") ||
            name.endsWith(".tar.bz2") || name.endsWith(".tar.xz") || name.endsWith(".tbz2") -> ArchiveType.TAR
            else -> ArchiveType.UNKNOWN
        }
    }

    fun isArchive(file: File): Boolean {
        return detectType(file) != ArchiveType.UNKNOWN
    }

    /**
     * Lists all archive entries in memory without extracting to disk.
     */
    fun listEntries(archiveFile: File): List<ArchiveEntryModel> {
        if (!archiveFile.exists()) return emptyList()
        return try {
            when (detectType(archiveFile)) {
                ArchiveType.ZIP -> listZipEntries(archiveFile)
                ArchiveType.SEVEN_Z -> listSevenZEntries(archiveFile)
                ArchiveType.RAR -> listRarEntries(archiveFile)
                ArchiveType.TAR -> listTarEntries(archiveFile)
                ArchiveType.UNKNOWN -> emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun listZipEntries(file: File): List<ArchiveEntryModel> {
        val list = mutableListOf<ArchiveEntryModel>()
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val cleanName = entry.name.replace('\\', '/').trimStart('/')
                if (cleanName.isNotEmpty()) {
                    list.add(
                        ArchiveEntryModel(
                            name = cleanName,
                            isDirectory = entry.isDirectory,
                            size = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.compressedSize.coerceAtLeast(0L),
                            timeModified = entry.time
                        )
                    )
                }
            }
        }
        return list
    }

    private fun listSevenZEntries(file: File): List<ArchiveEntryModel> {
        val list = mutableListOf<ArchiveEntryModel>()
        SevenZFile(file).use { sevenZ ->
            for (entry in sevenZ.entries) {
                val cleanName = entry.name.replace('\\', '/').trimStart('/')
                if (cleanName.isNotEmpty()) {
                    list.add(
                        ArchiveEntryModel(
                            name = cleanName,
                            isDirectory = entry.isDirectory,
                            size = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.size.coerceAtLeast(0L),
                            timeModified = entry.lastModifiedDate?.time ?: 0L
                        )
                    )
                }
            }
        }
        return list
    }

    private fun listRarEntries(file: File): List<ArchiveEntryModel> {
        val list = mutableListOf<ArchiveEntryModel>()
        Archive(file).use { rar ->
            for (header in rar.fileHeaders) {
                val cleanName = header.fileName.replace('\\', '/').trimStart('/')
                if (cleanName.isNotEmpty()) {
                    list.add(
                        ArchiveEntryModel(
                            name = cleanName,
                            isDirectory = header.isDirectory,
                            size = header.unpSize.coerceAtLeast(0L),
                            compressedSize = header.packSize.coerceAtLeast(0L),
                            timeModified = header.mTime?.time ?: 0L
                        )
                    )
                }
            }
        }
        return list
    }

    private fun createTarInputStream(file: File): TarArchiveInputStream {
        val name = file.name.lowercase()
        val bis = BufferedInputStream(FileInputStream(file))
        val decompressed: InputStream = when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> GzipCompressorInputStream(bis)
            name.endsWith(".tar.bz2") || name.endsWith(".tbz2") -> BZip2CompressorInputStream(bis)
            name.endsWith(".tar.xz") -> XZCompressorInputStream(bis)
            else -> bis
        }
        return TarArchiveInputStream(decompressed)
    }

    private fun listTarEntries(file: File): List<ArchiveEntryModel> {
        val list = mutableListOf<ArchiveEntryModel>()
        createTarInputStream(file).use { tis ->
            var entry = tis.nextEntry
            while (entry != null) {
                val cleanName = entry.name.replace('\\', '/').trimStart('/')
                if (cleanName.isNotEmpty()) {
                    list.add(
                        ArchiveEntryModel(
                            name = cleanName,
                            isDirectory = entry.isDirectory,
                            size = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.size.coerceAtLeast(0L),
                            timeModified = entry.modTime?.time ?: 0L
                        )
                    )
                }
                entry = tis.nextEntry
            }
        }
        return list
    }

    /**
     * Extracts a single entry directly from the archive into destFile.
     */
    fun extractSingleEntry(archiveFile: File, entryPath: String, destFile: File): Boolean {
        destFile.parentFile?.mkdirs()
        return try {
            when (detectType(archiveFile)) {
                ArchiveType.ZIP -> extractZipSingle(archiveFile, entryPath, destFile)
                ArchiveType.SEVEN_Z -> extractSevenZSingle(archiveFile, entryPath, destFile)
                ArchiveType.RAR -> extractRarSingle(archiveFile, entryPath, destFile)
                ArchiveType.TAR -> extractTarSingle(archiveFile, entryPath, destFile)
                ArchiveType.UNKNOWN -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun extractZipSingle(archiveFile: File, entryPath: String, destFile: File): Boolean {
        ZipFile(archiveFile).use { zip ->
            val entry = zip.getEntry(entryPath)
                ?: zip.getEntry("/$entryPath")
                ?: zip.entries().asSequence().firstOrNull { it.name.replace('\\', '/').trimStart('/') == entryPath }
                ?: return false
            zip.getInputStream(entry).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return true
        }
    }

    private fun extractSevenZSingle(archiveFile: File, entryPath: String, destFile: File): Boolean {
        SevenZFile(archiveFile).use { sevenZ ->
            var entry = sevenZ.nextEntry
            while (entry != null) {
                val cleanName = entry.name.replace('\\', '/').trimStart('/')
                if (cleanName == entryPath && !entry.isDirectory) {
                    FileOutputStream(destFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (sevenZ.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                    return true
                }
                entry = sevenZ.nextEntry
            }
        }
        return false
    }

    private fun extractRarSingle(archiveFile: File, entryPath: String, destFile: File): Boolean {
        Archive(archiveFile).use { rar ->
            for (header in rar.fileHeaders) {
                val cleanName = header.fileName.replace('\\', '/').trimStart('/')
                if (cleanName == entryPath && !header.isDirectory) {
                    FileOutputStream(destFile).use { fos ->
                        rar.extractFile(header, fos)
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun extractTarSingle(archiveFile: File, entryPath: String, destFile: File): Boolean {
        createTarInputStream(archiveFile).use { tis ->
            var entry = tis.nextEntry
            while (entry != null) {
                val cleanName = entry.name.replace('\\', '/').trimStart('/')
                if (cleanName == entryPath && !entry.isDirectory) {
                    FileOutputStream(destFile).use { fos ->
                        tis.copyTo(fos)
                    }
                    return true
                }
                entry = tis.nextEntry
            }
        }
        return false
    }

    /**
     * Extracts all files in the archive to destDir.
     */
    fun extractAll(archiveFile: File, destDir: File): Boolean {
        if (!destDir.exists()) destDir.mkdirs()
        return try {
            when (detectType(archiveFile)) {
                ArchiveType.ZIP -> {
                    ZipFile(archiveFile).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val cleanName = entry.name.replace('\\', '/').trimStart('/')
                            val target = File(destDir, cleanName)
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(target).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                    true
                }
                ArchiveType.SEVEN_Z -> {
                    SevenZFile(archiveFile).use { sevenZ ->
                        var entry = sevenZ.nextEntry
                        while (entry != null) {
                            val cleanName = entry.name.replace('\\', '/').trimStart('/')
                            val target = File(destDir, cleanName)
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { fos ->
                                    val buffer = ByteArray(8192)
                                    var bytesRead: Int
                                    while (sevenZ.read(buffer).also { bytesRead = it } != -1) {
                                        fos.write(buffer, 0, bytesRead)
                                    }
                                }
                            }
                            entry = sevenZ.nextEntry
                        }
                    }
                    true
                }
                ArchiveType.RAR -> {
                    Archive(archiveFile).use { rar ->
                        for (header in rar.fileHeaders) {
                            val cleanName = header.fileName.replace('\\', '/').trimStart('/')
                            val target = File(destDir, cleanName)
                            if (header.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { fos ->
                                    rar.extractFile(header, fos)
                                }
                            }
                        }
                    }
                    true
                }
                ArchiveType.TAR -> {
                    createTarInputStream(archiveFile).use { tis ->
                        var entry = tis.nextEntry
                        while (entry != null) {
                            val cleanName = entry.name.replace('\\', '/').trimStart('/')
                            val target = File(destDir, cleanName)
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { fos ->
                                    tis.copyTo(fos)
                                }
                            }
                            entry = tis.nextEntry
                        }
                    }
                    true
                }
                ArchiveType.UNKNOWN -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
