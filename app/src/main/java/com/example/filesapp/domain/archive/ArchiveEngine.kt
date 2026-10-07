package com.example.filesapp.domain.archive

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.*
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Archive Compression Level options.
 */
enum class CompressionLevel(val title: String, val zipLevel: Int, val lzmaLevel: Int) {
    STORE("Store (Fastest / No Compression)", Deflater.NO_COMPRESSION, 0),
    NORMAL("Normal (Balanced)", Deflater.DEFAULT_COMPRESSION, 5),
    MAXIMUM("Maximum (Smallest Size)", Deflater.BEST_COMPRESSION, 9)
}

/**
 * Archive Type supported.
 */
enum class ArchiveFormat(val extension: String, val displayName: String) {
    ZIP("zip", "ZIP Archive"),
    SEVEN_Z("7z", "7-Zip Archive"),
    RAR("rar", "RAR Archive"),
    TAR("tar", "TAR Archive"),
    TAR_GZ("tar.gz", "TAR GZ Archive"),
    TAR_BZ2("tar.bz2", "TAR BZ2 Archive"),
    TAR_XZ("tar.xz", "TAR XZ Archive"),
    GZ("gz", "GZ Compressed File"),
    XZ("xz", "XZ Compressed File"),
    BZ2("bz2", "BZ2 Compressed File"),
    UNKNOWN("", "Unknown Archive")
}

/**
 * Metadata for a single entry inside an archive.
 */
data class ArchiveEntryDetails(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long = 0L,
    val lastModified: Long = 0L,
    val crc: Long = 0L
)

/**
 * Production-grade universal archive engine with:
 * - ZArchiver-level multi-format support (ZIP, 7Z, RAR, TAR, TAR.GZ, TGZ, TAR.BZ2, GZ, XZ)
 * - In-memory hierarchy browsing without extracting
 * - Selective, batch, and full extraction (Extract Here, Extract to folder)
 * - High-speed compression for ZIP and 7Z with STORE, NORMAL, and MAXIMUM levels
 * - Critical Security: Zip Slip protection, Path Traversal barrier, and Decompression Bomb limiters.
 */
object ArchiveEngine {

    private const val MAX_EXPANSION_RATIO = 150L // Block decompression bombs exceeding 150x original file size
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 15L * 1024 * 1024 * 1024 // 15 GB ceiling

    /**
     * Detects archive format from filename extension.
     */
    fun detectFormat(file: File): ArchiveFormat {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".zip") -> ArchiveFormat.ZIP
            name.endsWith(".7z") -> ArchiveFormat.SEVEN_Z
            name.endsWith(".rar") -> ArchiveFormat.RAR
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            name.endsWith(".tar.bz2") || name.endsWith(".tbz2") -> ArchiveFormat.TAR_BZ2
            name.endsWith(".tar.xz") || name.endsWith(".txz") -> ArchiveFormat.TAR_XZ
            name.endsWith(".tar") -> ArchiveFormat.TAR
            name.endsWith(".gz") -> ArchiveFormat.GZ
            name.endsWith(".xz") -> ArchiveFormat.XZ
            name.endsWith(".bz2") -> ArchiveFormat.BZ2
            else -> ArchiveFormat.UNKNOWN
        }
    }

    fun isSupportedArchive(file: File): Boolean = detectFormat(file) != ArchiveFormat.UNKNOWN

    /**
     * Lists all archive entries in memory without extracting to disk.
     */
    fun listEntries(archiveFile: File): List<ArchiveEntryDetails> {
        if (!archiveFile.exists()) return emptyList()
        return try {
            when (detectFormat(archiveFile)) {
                ArchiveFormat.ZIP -> listZip(archiveFile)
                ArchiveFormat.SEVEN_Z -> listSevenZ(archiveFile)
                ArchiveFormat.RAR -> listRar(archiveFile)
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ -> listTar(archiveFile)
                ArchiveFormat.GZ, ArchiveFormat.XZ, ArchiveFormat.BZ2 -> listSingleStream(archiveFile)
                ArchiveFormat.UNKNOWN -> emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun listZip(file: File): List<ArchiveEntryDetails> {
        val entries = mutableListOf<ArchiveEntryDetails>()
        ZipFile(file).use { zip ->
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val ze = enumeration.nextElement()
                val clean = ze.name.replace('\\', '/').trimStart('/')
                if (clean.isNotEmpty()) {
                    val name = if (clean.endsWith("/")) clean.dropLast(1).substringAfterLast('/') else clean.substringAfterLast('/')
                    entries.add(
                        ArchiveEntryDetails(
                            path = clean,
                            name = name,
                            isDirectory = ze.isDirectory,
                            uncompressedSize = ze.size.coerceAtLeast(0L),
                            compressedSize = ze.compressedSize.coerceAtLeast(0L),
                            lastModified = ze.time,
                            crc = ze.crc
                        )
                    )
                }
            }
        }
        return entries
    }

    private fun listSevenZ(file: File): List<ArchiveEntryDetails> {
        val entries = mutableListOf<ArchiveEntryDetails>()
        SevenZFile(file).use { sevenZ ->
            for (entry in sevenZ.entries) {
                val clean = entry.name.replace('\\', '/').trimStart('/')
                if (clean.isNotEmpty()) {
                    val name = if (clean.endsWith("/")) clean.dropLast(1).substringAfterLast('/') else clean.substringAfterLast('/')
                    entries.add(
                        ArchiveEntryDetails(
                            path = clean,
                            name = name,
                            isDirectory = entry.isDirectory,
                            uncompressedSize = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.size.coerceAtLeast(0L),
                            lastModified = entry.lastModifiedDate?.time ?: 0L,
                            crc = entry.crcValue
                        )
                    )
                }
            }
        }
        return entries
    }

    private fun listRar(file: File): List<ArchiveEntryDetails> {
        val entries = mutableListOf<ArchiveEntryDetails>()
        Archive(file).use { rar ->
            for (header in rar.fileHeaders) {
                val clean = header.fileName.replace('\\', '/').trimStart('/')
                if (clean.isNotEmpty()) {
                    val name = if (clean.endsWith("/")) clean.dropLast(1).substringAfterLast('/') else clean.substringAfterLast('/')
                    entries.add(
                        ArchiveEntryDetails(
                            path = clean,
                            name = name,
                            isDirectory = header.isDirectory,
                            uncompressedSize = header.unpSize.coerceAtLeast(0L),
                            compressedSize = header.packSize.coerceAtLeast(0L),
                            lastModified = header.mTime?.time ?: 0L
                        )
                    )
                }
            }
        }
        return entries
    }

    private fun createTarInputStream(file: File): TarArchiveInputStream {
        val name = file.name.lowercase()
        val bis = BufferedInputStream(FileInputStream(file))
        val decompressed: InputStream = when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> GzipCompressorInputStream(bis)
            name.endsWith(".tar.bz2") || name.endsWith(".tbz2") -> BZip2CompressorInputStream(bis)
            name.endsWith(".tar.xz") || name.endsWith(".txz") -> XZCompressorInputStream(bis)
            else -> bis
        }
        return TarArchiveInputStream(decompressed)
    }

    private fun listTar(file: File): List<ArchiveEntryDetails> {
        val entries = mutableListOf<ArchiveEntryDetails>()
        createTarInputStream(file).use { tis ->
            var entry = tis.nextEntry
            while (entry != null) {
                val clean = entry.name.replace('\\', '/').trimStart('/')
                if (clean.isNotEmpty()) {
                    val name = if (clean.endsWith("/")) clean.dropLast(1).substringAfterLast('/') else clean.substringAfterLast('/')
                    entries.add(
                        ArchiveEntryDetails(
                            path = clean,
                            name = name,
                            isDirectory = entry.isDirectory,
                            uncompressedSize = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.size.coerceAtLeast(0L),
                            lastModified = entry.modTime?.time ?: 0L
                        )
                    )
                }
                entry = tis.nextEntry
            }
        }
        return entries
    }

    private fun listSingleStream(file: File): List<ArchiveEntryDetails> {
        val cleanName = file.nameWithoutExtension
        return listOf(
            ArchiveEntryDetails(
                path = cleanName,
                name = cleanName,
                isDirectory = false,
                uncompressedSize = file.length(),
                compressedSize = file.length(),
                lastModified = file.lastModified()
            )
        )
    }

    /**
     * Security: Validates and canonicalizes every extraction path.
     * Throws SecurityException if a Zip Slip / Path Traversal attempt is detected.
     */
    fun sanitizeAndValidateDestinationPath(destDir: File, entryPath: String): File {
        val cleanRel = entryPath.replace('\\', '/').trimStart('/')
        val targetFile = File(destDir, cleanRel)
        val canonicalDest = destDir.canonicalPath
        val canonicalTarget = targetFile.canonicalPath

        if (!canonicalTarget.startsWith(canonicalDest + File.separator) && canonicalTarget != canonicalDest) {
            throw SecurityException("Security Alert: Blocked Zip Slip / Path Traversal attempt: '$entryPath'")
        }
        return targetFile
    }

    /**
     * Extracts all or selected entries to target directory with Zip Slip security validation.
     */
    fun extract(
        archiveFile: File,
        destDir: File,
        selectedPaths: Set<String>? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean {
        if (!destDir.exists()) destDir.mkdirs()
        val archiveSize = archiveFile.length().coerceAtLeast(1L)
        val maxAllowedDecompressed = (archiveSize * MAX_EXPANSION_RATIO).coerceAtMost(MAX_TOTAL_UNCOMPRESSED_BYTES)
        var cumulativeBytesExtracted = 0L

        fun checkBomb(bytesRead: Long) {
            cumulativeBytesExtracted += bytesRead
            if (cumulativeBytesExtracted > maxAllowedDecompressed) {
                throw SecurityException("Security Alert: Decompression bomb limit exceeded.")
            }
        }

        return try {
            when (detectFormat(archiveFile)) {
                ArchiveFormat.ZIP -> {
                    ZipFile(archiveFile).use { zip ->
                        val entries = zip.entries().toList()
                        val filtered = if (selectedPaths != null) entries.filter { selectedPaths.contains(it.name.replace('\\', '/').trimStart('/')) } else entries
                        val total = filtered.size.coerceAtLeast(1)

                        filtered.forEachIndexed { idx, entry ->
                            val cleanPath = entry.name.replace('\\', '/').trimStart('/')
                            val target = sanitizeAndValidateDestinationPath(destDir, cleanPath)
                            onProgress(idx.toFloat() / total, target.name)

                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(target).use { output ->
                                        val buffer = ByteArray(8192)
                                        var read: Int
                                        while (input.read(buffer).also { read = it } != -1) {
                                            checkBomb(read.toLong())
                                            output.write(buffer, 0, read)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    onProgress(1f, "Done")
                    true
                }
                ArchiveFormat.SEVEN_Z -> {
                    SevenZFile(archiveFile).use { sevenZ ->
                        val entries = sevenZ.entries.toList()
                        val total = entries.size.coerceAtLeast(1)
                        var idx = 0

                        var entry = sevenZ.nextEntry
                        while (entry != null) {
                            val cleanPath = entry.name.replace('\\', '/').trimStart('/')
                            idx++
                            if (selectedPaths == null || selectedPaths.contains(cleanPath)) {
                                val target = sanitizeAndValidateDestinationPath(destDir, cleanPath)
                                onProgress(idx.toFloat() / total, target.name)
                                if (entry.isDirectory) {
                                    target.mkdirs()
                                } else {
                                    target.parentFile?.mkdirs()
                                    FileOutputStream(target).use { fos ->
                                        val buffer = ByteArray(8192)
                                        var read: Int
                                        while (sevenZ.read(buffer).also { read = it } != -1) {
                                            checkBomb(read.toLong())
                                            fos.write(buffer, 0, read)
                                        }
                                    }
                                }
                            }
                            entry = sevenZ.nextEntry
                        }
                    }
                    onProgress(1f, "Done")
                    true
                }
                ArchiveFormat.RAR -> {
                    Archive(archiveFile).use { rar ->
                        val headers = rar.fileHeaders
                        val total = headers.size.coerceAtLeast(1)

                        headers.forEachIndexed { idx, header ->
                            val cleanPath = header.fileName.replace('\\', '/').trimStart('/')
                            if (selectedPaths == null || selectedPaths.contains(cleanPath)) {
                                val target = sanitizeAndValidateDestinationPath(destDir, cleanPath)
                                onProgress(idx.toFloat() / total, target.name)
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
                    }
                    onProgress(1f, "Done")
                    true
                }
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ -> {
                    createTarInputStream(archiveFile).use { tis ->
                        var entry = tis.nextEntry
                        while (entry != null) {
                            val cleanPath = entry.name.replace('\\', '/').trimStart('/')
                            if (selectedPaths == null || selectedPaths.contains(cleanPath)) {
                                val target = sanitizeAndValidateDestinationPath(destDir, cleanPath)
                                onProgress(0.5f, target.name)
                                if (entry.isDirectory) {
                                    target.mkdirs()
                                } else {
                                    target.parentFile?.mkdirs()
                                    FileOutputStream(target).use { fos ->
                                        val buffer = ByteArray(8192)
                                        var read: Int
                                        while (tis.read(buffer).also { read = it } != -1) {
                                            checkBomb(read.toLong())
                                            fos.write(buffer, 0, read)
                                        }
                                    }
                                }
                            }
                            entry = tis.nextEntry
                        }
                    }
                    onProgress(1f, "Done")
                    true
                }
                ArchiveFormat.GZ, ArchiveFormat.XZ, ArchiveFormat.BZ2 -> {
                    val outName = archiveFile.nameWithoutExtension
                    val target = sanitizeAndValidateDestinationPath(destDir, outName)
                    target.parentFile?.mkdirs()

                    val bis = BufferedInputStream(FileInputStream(archiveFile))
                    val stream: InputStream = when (detectFormat(archiveFile)) {
                        ArchiveFormat.GZ -> GzipCompressorInputStream(bis)
                        ArchiveFormat.XZ -> XZCompressorInputStream(bis)
                        ArchiveFormat.BZ2 -> BZip2CompressorInputStream(bis)
                        else -> bis
                    }

                    stream.use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                    onProgress(1f, "Done")
                    true
                }
                ArchiveFormat.UNKNOWN -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Creates a ZIP archive with configurable compression level (STORE, NORMAL, MAXIMUM).
     */
    fun createZipArchive(
        sourceFiles: List<File>,
        targetZipFile: File,
        level: CompressionLevel = CompressionLevel.NORMAL,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean {
        targetZipFile.parentFile?.mkdirs()
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZipFile))).use { zos ->
                zos.setLevel(level.zipLevel)
                if (level == CompressionLevel.STORE) {
                    zos.setMethod(ZipOutputStream.DEFLATED)
                }

                val allFilesToCompress = mutableListOf<Pair<File, String>>()
                fun collect(f: File, parentRel: String) {
                    val rel = if (parentRel.isEmpty()) f.name else "$parentRel/${f.name}"
                    if (f.isDirectory) {
                        val children = f.listFiles() ?: return
                        for (child in children) collect(child, rel)
                    } else {
                        allFilesToCompress.add(Pair(f, rel))
                    }
                }

                for (f in sourceFiles) {
                    collect(f, "")
                }

                val total = allFilesToCompress.size.coerceAtLeast(1)
                allFilesToCompress.forEachIndexed { index, (file, relPath) ->
                    onProgress(index.toFloat() / total, file.name)
                    val entry = ZipEntry(relPath)
                    entry.time = file.lastModified()
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
            onProgress(1f, "Done")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Creates a 7Z archive with configurable compression level (STORE, NORMAL, MAXIMUM).
     */
    fun createSevenZArchive(
        sourceFiles: List<File>,
        target7zFile: File,
        level: CompressionLevel = CompressionLevel.NORMAL,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean {
        target7zFile.parentFile?.mkdirs()
        return try {
            SevenZOutputFile(target7zFile).use { sevenZOut ->
                when (level) {
                    CompressionLevel.STORE -> {
                        sevenZOut.setContentCompression(SevenZMethod.COPY)
                    }
                    CompressionLevel.NORMAL -> {
                        sevenZOut.setContentCompression(SevenZMethod.LZMA2)
                    }
                    CompressionLevel.MAXIMUM -> {
                        sevenZOut.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2, 9)))
                    }
                }

                val allFilesToCompress = mutableListOf<Pair<File, String>>()
                fun collect(f: File, parentRel: String) {
                    val rel = if (parentRel.isEmpty()) f.name else "$parentRel/${f.name}"
                    if (f.isDirectory) {
                        val children = f.listFiles() ?: return
                        for (child in children) collect(child, rel)
                    } else {
                        allFilesToCompress.add(Pair(f, rel))
                    }
                }

                for (f in sourceFiles) {
                    collect(f, "")
                }

                val total = allFilesToCompress.size.coerceAtLeast(1)
                allFilesToCompress.forEachIndexed { index, (file, relPath) ->
                    onProgress(index.toFloat() / total, file.name)
                    val entry = sevenZOut.createArchiveEntry(file, relPath)
                    sevenZOut.putArchiveEntry(entry)

                    FileInputStream(file).use { fis ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            sevenZOut.write(buffer, 0, read)
                        }
                    }
                    sevenZOut.closeArchiveEntry()
                }
            }
            onProgress(1f, "Done")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
