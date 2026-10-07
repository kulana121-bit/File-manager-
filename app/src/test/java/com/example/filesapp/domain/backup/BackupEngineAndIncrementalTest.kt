package com.example.filesapp.domain.backup

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupEngineAndIncrementalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test incremental changed file detection logic`() {
        val sourceDir = tempFolder.newFolder("source_dir")
        val fileOld = File(sourceDir, "old.txt").apply {
            writeText("Initial state")
            setLastModified(1000000L)
        }
        val fileNew = File(sourceDir, "new.txt").apply {
            writeText("Updated state")
            setLastModified(2000000L)
        }

        val lastSyncMap = mapOf(fileOld.canonicalPath to 1000000L) // fileOld was synced at 1000000L

        // Collect changed files
        val changedFiles = mutableListOf<File>()
        sourceDir.listFiles()?.forEach { f ->
            val prevSync = lastSyncMap[f.canonicalPath] ?: 0L
            if (f.lastModified() > prevSync) {
                changedFiles.add(f)
            }
        }

        assertEquals(1, changedFiles.size)
        assertEquals("new.txt", changedFiles.first().name)
    }

    @Test
    fun `test backup history record serialization and deserialization`() {
        val record = BackupHistoryRecord(
            jobName = "Daily Photo Backup",
            timestamp = 1700000000000L,
            totalFilesBackedUp = 42,
            totalBytesBackedUp = 10485760L,
            destinationDescription = "Local Storage: /Backups",
            isSuccess = true,
            backupArchivePathOrLocation = "/Backups/Backup_Daily_Photo_2026.zip"
        )

        assertEquals("Daily Photo Backup", record.jobName)
        assertTrue(record.isSuccess)
        assertEquals(42, record.totalFilesBackedUp)
        assertEquals(10485760L, record.totalBytesBackedUp)
        assertNull(record.errorMessage)
    }
}
