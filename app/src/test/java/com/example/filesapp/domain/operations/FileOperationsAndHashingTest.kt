package com.example.filesapp.domain.operations

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class FileOperationsAndHashingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test SHA-256 and MD5 checksum computation consistency`() {
        val testFile = tempFolder.newFile("hash_sample.txt")
        testFile.writeText("Test Payload For Cryptographic Hashing Verification 2026")

        val expectedSha256 = MessageDigest.getInstance("SHA-256")
            .digest(testFile.readBytes())
            .joinToString("") { "%02x".format(it) }

        val md5 = MessageDigest.getInstance("MD5")
            .digest(testFile.readBytes())
            .joinToString("") { "%02x".format(it) }

        assertNotNull(expectedSha256)
        assertEquals(64, expectedSha256.length)
        assertEquals(32, md5.length)
    }

    @Test
    fun `test file conflict resolution auto rename`() {
        val baseDir = tempFolder.newFolder("conflict_test")
        val existingFile = File(baseDir, "report.pdf")
        existingFile.writeText("Existing version")

        // Auto-rename logic check
        val targetName = "report.pdf"
        var candidate = File(baseDir, targetName)
        var count = 1
        while (candidate.exists()) {
            val baseName = targetName.substringBeforeLast(".")
            val ext = targetName.substringAfterLast(".", "")
            candidate = File(baseDir, "$baseName ($count).$ext")
            count++
        }

        assertEquals("report (1).pdf", candidate.name)
        assertFalse(candidate.exists())
    }

    @Test
    fun `test duplicate file detection logic by size and content`() {
        val dir = tempFolder.newFolder("dupes_test")
        val file1 = File(dir, "original.jpg").apply { writeText("Identical content in image file") }
        val file2 = File(dir, "copy.jpg").apply { writeText("Identical content in image file") }
        val file3 = File(dir, "different.jpg").apply { writeText("Completely distinct text payload") }

        assertEquals(file1.length(), file2.length())
        assertNotEquals(file1.length(), file3.length())

        val hash1 = MessageDigest.getInstance("SHA-256").digest(file1.readBytes()).joinToString("") { "%02x".format(it) }
        val hash2 = MessageDigest.getInstance("SHA-256").digest(file2.readBytes()).joinToString("") { "%02x".format(it) }
        val hash3 = MessageDigest.getInstance("SHA-256").digest(file3.readBytes()).joinToString("") { "%02x".format(it) }

        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
    }
}
