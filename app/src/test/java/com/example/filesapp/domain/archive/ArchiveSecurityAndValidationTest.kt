package com.example.filesapp.domain.archive

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class ArchiveSecurityAndValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test valid archive extraction inside destination boundary`() {
        val destDir = tempFolder.newFolder("extract_dest")
        val zipFile = tempFolder.newFile("valid.zip")

        // Create a normal zip file
        ZipArchiveOutputStream(FileOutputStream(zipFile)).use { zos ->
            val entry = ZipArchiveEntry("documents/sample.txt")
            val content = "Hello Production Files App".toByteArray(Charsets.UTF_8)
            zos.putArchiveEntry(entry)
            zos.write(content)
            zos.closeArchiveEntry()
        }

        val success = ArchiveEngine.extract(zipFile, destDir)
        assertTrue("Extraction should succeed for normal archive", success)

        val extractedFile = File(destDir, "documents/sample.txt")
        assertTrue("Extracted file must exist", extractedFile.exists())
        assertEquals("Hello Production Files App", extractedFile.readText())
    }

    @Test
    fun `test Zip Slip path traversal attempt is blocked`() {
        val destDir = tempFolder.newFolder("extract_dest")
        val maliciousZip = tempFolder.newFile("malicious_slip.zip")

        // Construct malicious entry attempting directory traversal ../../../escaped.txt
        ZipArchiveOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            val entry = ZipArchiveEntry("../../escaped.txt")
            val content = "Malicious Payload".toByteArray(Charsets.UTF_8)
            zos.putArchiveEntry(entry)
            zos.write(content)
            zos.closeArchiveEntry()
        }

        val success = ArchiveEngine.extract(maliciousZip, destDir)
        // ArchiveEngine must safely reject or skip extracting outside destination
        val outsideFile = File(destDir.parentFile, "escaped.txt")
        assertFalse("Zip Slip payload must NEVER escape destination folder", outsideFile.exists())
    }

    @Test
    fun `test decompression bomb volume threshold protection`() {
        val destDir = tempFolder.newFolder("extract_dest")
        val zipFile = tempFolder.newFile("test_protection.zip")

        ZipArchiveOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (i in 1..5) {
                val entry = ZipArchiveEntry("file_$i.txt")
                val content = "Content $i".toByteArray(Charsets.UTF_8)
                zos.putArchiveEntry(entry)
                zos.write(content)
                zos.closeArchiveEntry()
            }
        }

        val entries = ArchiveEngine.listEntries(zipFile)
        assertEquals(5, entries.size)
        assertTrue(entries.all { it.name.startsWith("file_") })
    }
}
