package com.example.filesapp.domain.storage

import com.example.filesapp.domain.analyzer.StorageBreakdownModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

class StorageCalculationsAndValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test path sanitization prevents directory traversal attacks`() {
        val rootDir = tempFolder.newFolder("safe_root")
        val subDir = File(rootDir, "documents").apply { mkdirs() }

        val safePath = File(subDir, "report.pdf").canonicalPath
        assertTrue("Safe path must start with root canonical path", safePath.startsWith(rootDir.canonicalPath))

        // Attempt path traversal using relative parent paths
        val traversalPath = File(subDir, "../../system/etc/hosts").canonicalPath
        assertFalse("Traversed path must not start with subDir root path", traversalPath.startsWith(subDir.canonicalPath))
    }

    @Test
    fun `test storage breakdown byte percentage calculations`() {
        val totalBytes = 100_000_000_000L // 100 GB
        val usedBytes = 45_000_000_000L  // 45 GB
        val freeBytes = 55_000_000_000L  // 55 GB

        val breakdown = StorageBreakdownModel(
            totalSpaceBytes = totalBytes,
            usedSpaceBytes = usedBytes,
            freeSpaceBytes = freeBytes,
            imagesBytes = 10_000_000_000L,
            videosBytes = 20_000_000_000L,
            audioBytes = 5_000_000_000L,
            docsBytes = 5_000_000_000L,
            apksBytes = 2_000_000_000L,
            archivesBytes = 3_000_000_000L,
            othersBytes = 0L
        )

        assertEquals(45, ((breakdown.usedSpaceBytes.toDouble() / breakdown.totalSpaceBytes) * 100).toInt())
        assertEquals(55, ((breakdown.freeSpaceBytes.toDouble() / breakdown.totalSpaceBytes) * 100).toInt())
        assertEquals(usedBytes, breakdown.imagesBytes + breakdown.videosBytes + breakdown.audioBytes + breakdown.docsBytes + breakdown.apksBytes + breakdown.archivesBytes)
    }

    @Test
    fun `test human readable file size formatting`() {
        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

        assertEquals("0 B", formatSize(0L))
        assertEquals("512.0 B", formatSize(512L))
        assertEquals("1.0 KB", formatSize(1024L))
        assertEquals("1.0 MB", formatSize(1024L * 1024L))
        assertEquals("1.5 GB", formatSize((1.5 * 1024L * 1024L * 1024L).toLong()))
    }
}
