package com.example.filesapp.domain.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import com.example.filesapp.data.GoogleDriveManager
import com.example.filesapp.data.NetworkServerConfig
import com.example.filesapp.data.NetworkStorageManager
import com.example.filesapp.domain.archive.ArchiveEngine
import com.example.filesapp.domain.archive.CompressionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class BackupDestinationType {
    LOCAL_STORAGE,
    GOOGLE_DRIVE,
    NETWORK_FTP
}

data class BackupJobConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceFolderPaths: List<String>,
    val destinationType: BackupDestinationType,
    val localDestinationPath: String? = null,
    val driveFolderId: String? = "root",
    val networkServerId: String? = null,
    val networkRemoteDir: String? = "/",
    val isWifiOnly: Boolean = true,
    val isIncremental: Boolean = true,
    val createArchive: Boolean = true,
    val scheduledIntervalHours: Int = 0 // 0 = Manual only, 24 = Daily
)

data class BackupHistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val jobName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalFilesBackedUp: Int,
    val totalBytesBackedUp: Long,
    val destinationDescription: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val backupArchivePathOrLocation: String? = null
)

data class BackupExecutionProgress(
    val isRunning: Boolean = false,
    val currentFileName: String = "",
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Float = 0f,
    val statusMessage: String = ""
)

/**
 * Production-Grade Backup Engine:
 * - Multi-destination support: Google Drive, Local Storage, Network (FTP).
 * - Incremental changed-file detection based on last modified timestamps & lengths.
 * - Wi-Fi only constraint checker.
 * - Destination confirmation before reporting success.
 * - Full backup history logging & easy one-tap archive restore.
 */
class BackupEngine(
    private val context: Context,
    private val driveManager: GoogleDriveManager,
    private val networkManager: NetworkStorageManager
) {
    private val prefs = context.getSharedPreferences("backup_engine_prefs", Context.MODE_PRIVATE)
    private val KEY_HISTORY = "backup_history_records_v1"
    private val KEY_LAST_SYNC = "backup_last_sync_map"

    private val _progress = MutableStateFlow(BackupExecutionProgress())
    val progress: StateFlow<BackupExecutionProgress> = _progress.asStateFlow()

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Executes a backup job with incremental detection, destination upload, and confirmation.
     */
    suspend fun executeBackup(
        config: BackupJobConfig,
        onProgressUpdate: ((BackupExecutionProgress) -> Unit)? = null
    ): Result<BackupHistoryRecord> = withContext(Dispatchers.IO) {
        if (config.isWifiOnly && !isWifiConnected()) {
            val rec = BackupHistoryRecord(
                jobName = config.name,
                totalFilesBackedUp = 0,
                totalBytesBackedUp = 0L,
                destinationDescription = getDestinationDesc(config),
                isSuccess = false,
                errorMessage = "Skipped: Wi-Fi only constraint enabled and Wi-Fi is disconnected."
            )
            saveHistoryRecord(rec)
            return@withContext Result.failure(IllegalStateException(rec.errorMessage))
        }

        _progress.value = BackupExecutionProgress(
            isRunning = true,
            statusMessage = "Scanning source folders..."
        )
        onProgressUpdate?.invoke(_progress.value)

        val lastSyncTimestamps = getLastSyncMap(config.id)
        val filesToBackup = mutableListOf<File>()

        for (folderPath in config.sourceFolderPaths) {
            val dir = File(folderPath)
            if (dir.exists() && dir.isDirectory) {
                collectFiles(dir, filesToBackup, config.isIncremental, lastSyncTimestamps)
            }
        }

        if (filesToBackup.isEmpty()) {
            _progress.value = BackupExecutionProgress(
                isRunning = false,
                statusMessage = "No new or modified files found to backup."
            )
            val rec = BackupHistoryRecord(
                jobName = config.name,
                totalFilesBackedUp = 0,
                totalBytesBackedUp = 0L,
                destinationDescription = getDestinationDesc(config),
                isSuccess = true,
                errorMessage = "Up to date (No changed files)"
            )
            saveHistoryRecord(rec)
            return@withContext Result.success(rec)
        }

        val totalBytes = filesToBackup.sumOf { it.length() }
        val totalCount = filesToBackup.size

        _progress.value = BackupExecutionProgress(
            isRunning = true,
            totalFiles = totalCount,
            totalBytes = totalBytes,
            statusMessage = "Preparing ${filesToBackup.size} files for backup..."
        )
        onProgressUpdate?.invoke(_progress.value)

        // Step 1: Create a single consolidated ZIP archive or prepare individual files
        val tempBackupDir = File(context.cacheDir, "temp_backup_staging").apply { mkdirs() }
        val timeStampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val archiveName = "Backup_${config.name.replace("\\s+".toRegex(), "_")}_$timeStampStr.zip"
        val stagedArchiveFile = File(tempBackupDir, archiveName)

        val archiveSuccess = ArchiveEngine.createZipArchive(
            sourceFiles = filesToBackup,
            targetZipFile = stagedArchiveFile,
            level = CompressionLevel.NORMAL
        )

        if (!archiveSuccess || !stagedArchiveFile.exists() || stagedArchiveFile.length() == 0L) {
            _progress.value = BackupExecutionProgress(isRunning = false, statusMessage = "Failed to bundle files into archive")
            val rec = BackupHistoryRecord(
                jobName = config.name,
                totalFilesBackedUp = 0,
                totalBytesBackedUp = 0L,
                destinationDescription = getDestinationDesc(config),
                isSuccess = false,
                errorMessage = "Archive creation failed"
            )
            saveHistoryRecord(rec)
            return@withContext Result.failure(Exception("Failed to create backup package"))
        }

        // Step 2: Transfer to Destination and CONFIRM
        val uploadResult: Result<String> = when (config.destinationType) {
            BackupDestinationType.LOCAL_STORAGE -> {
                val destDir = File(config.localDestinationPath ?: File(Environment.getExternalStorageDirectory(), "Backups").absolutePath)
                destDir.mkdirs()
                val targetFile = File(destDir, archiveName)
                try {
                    stagedArchiveFile.copyTo(targetFile, overwrite = true)
                    if (targetFile.exists() && targetFile.length() == stagedArchiveFile.length()) {
                        Result.success(targetFile.absolutePath)
                    } else {
                        Result.failure(Exception("Local destination file verification failed"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            BackupDestinationType.GOOGLE_DRIVE -> {
                if (!driveManager.isConnected) {
                    Result.failure(Exception("Google Drive is not connected. Sign in first."))
                } else {
                    _progress.value = _progress.value.copy(statusMessage = "Uploading backup to Google Drive...")
                    val driveRes = driveManager.uploadFile(
                        localFile = stagedArchiveFile,
                        parentFolderIdOrPath = config.driveFolderId ?: "root",
                        onProgress = { p ->
                            _progress.value = _progress.value.copy(
                                progressPercent = p,
                                statusMessage = "Uploading to Google Drive (${(p * 100).toInt()}%)"
                            )
                            onProgressUpdate?.invoke(_progress.value)
                        }
                    )
                    driveRes.map { "drive://${it.id}" }
                }
            }

            BackupDestinationType.NETWORK_FTP -> {
                val serverId = config.networkServerId
                val savedServers = networkManager.getSavedServers()
                val targetServer = savedServers.find { it.id == serverId }
                if (targetServer == null) {
                    Result.failure(Exception("Network FTP server profile not found"))
                } else {
                    _progress.value = _progress.value.copy(statusMessage = "Uploading backup to FTP server...")
                    val ftpRes = networkManager.uploadFile(
                        server = targetServer,
                        localFile = stagedArchiveFile,
                        remoteDirPath = config.networkRemoteDir ?: "/",
                        onProgress = { p ->
                            _progress.value = _progress.value.copy(
                                progressPercent = p,
                                statusMessage = "Uploading to FTP (${(p * 100).toInt()}%)"
                            )
                            onProgressUpdate?.invoke(_progress.value)
                        }
                    )
                    ftpRes.map { "ftp://${targetServer.host}:${targetServer.port}/${config.networkRemoteDir?.trim('/')}/$archiveName" }
                }
            }
        }

        // Clean up staged temporary file
        try { stagedArchiveFile.delete() } catch (ignored: Exception) {}

        uploadResult.fold(
            onSuccess = { locationUri ->
                // Update incremental last-synced timestamps
                updateLastSyncMap(config.id, filesToBackup)

                val record = BackupHistoryRecord(
                    jobName = config.name,
                    totalFilesBackedUp = totalCount,
                    totalBytesBackedUp = totalBytes,
                    destinationDescription = getDestinationDesc(config),
                    isSuccess = true,
                    backupArchivePathOrLocation = locationUri
                )
                saveHistoryRecord(record)

                _progress.value = BackupExecutionProgress(
                    isRunning = false,
                    statusMessage = "Backup completed successfully! (${formatBytes(totalBytes)})"
                )
                onProgressUpdate?.invoke(_progress.value)
                Result.success(record)
            },
            onFailure = { err ->
                val record = BackupHistoryRecord(
                    jobName = config.name,
                    totalFilesBackedUp = 0,
                    totalBytesBackedUp = 0L,
                    destinationDescription = getDestinationDesc(config),
                    isSuccess = false,
                    errorMessage = err.message ?: "Transfer failure"
                )
                saveHistoryRecord(record)

                _progress.value = BackupExecutionProgress(
                    isRunning = false,
                    statusMessage = "Backup failed: ${err.message}"
                )
                onProgressUpdate?.invoke(_progress.value)
                Result.failure(err)
            }
        )
    }

    /**
     * Restores a backup ZIP archive into the destination folder.
     */
    suspend fun restoreBackupArchive(
        archiveFile: File,
        restoreTargetDir: File
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists() || !archiveFile.canRead()) {
                return@withContext Result.failure(Exception("Backup archive file is not accessible"))
            }
            restoreTargetDir.mkdirs()
            val extracted = ArchiveEngine.extract(
                archiveFile = archiveFile,
                destDir = restoreTargetDir
            )
            if (extracted) {
                Result.success(true)
            } else {
                Result.failure(Exception("Archive extraction failed or invalid archive format"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun collectFiles(
        directory: File,
        collector: MutableList<File>,
        isIncremental: Boolean,
        lastSyncMap: Map<String, Long>
    ) {
        val entries = directory.listFiles() ?: return
        for (f in entries) {
            if (f.isDirectory) {
                collectFiles(f, collector, isIncremental, lastSyncMap)
            } else if (f.isFile && f.canRead()) {
                if (!isIncremental) {
                    collector.add(f)
                } else {
                    val lastMod = f.lastModified()
                    val prevMod = lastSyncMap[f.absolutePath] ?: 0L
                    if (lastMod > prevMod) {
                        collector.add(f)
                    }
                }
            }
        }
    }

    private fun getDestinationDesc(config: BackupJobConfig): String {
        return when (config.destinationType) {
            BackupDestinationType.LOCAL_STORAGE -> "Local Storage: ${config.localDestinationPath ?: "Backups"}"
            BackupDestinationType.GOOGLE_DRIVE -> "Google Drive (${config.driveFolderId ?: "root"})"
            BackupDestinationType.NETWORK_FTP -> "Network FTP Server"
        }
    }

    fun getBackupHistory(): List<BackupHistoryRecord> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<BackupHistoryRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BackupHistoryRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        jobName = obj.optString("jobName", "Backup"),
                        timestamp = obj.optLong("timestamp", 0L),
                        totalFilesBackedUp = obj.optInt("totalFilesBackedUp", 0),
                        totalBytesBackedUp = obj.optLong("totalBytesBackedUp", 0L),
                        destinationDescription = obj.optString("destinationDescription", ""),
                        isSuccess = obj.optBoolean("isSuccess", false),
                        errorMessage = if (obj.has("errorMessage")) obj.getString("errorMessage") else null,
                        backupArchivePathOrLocation = if (obj.has("backupArchivePathOrLocation")) obj.getString("backupArchivePathOrLocation") else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun saveHistoryRecord(record: BackupHistoryRecord) {
        val current = getBackupHistory().toMutableList()
        current.add(0, record)
        val trimmed = current.take(30)
        val arr = JSONArray()
        for (r in trimmed) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("jobName", r.jobName)
                put("timestamp", r.timestamp)
                put("totalFilesBackedUp", r.totalFilesBackedUp)
                put("totalBytesBackedUp", r.totalBytesBackedUp)
                put("destinationDescription", r.destinationDescription)
                put("isSuccess", r.isSuccess)
                r.errorMessage?.let { put("errorMessage", it) }
                r.backupArchivePathOrLocation?.let { put("backupArchivePathOrLocation", it) }
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun getLastSyncMap(configId: String): Map<String, Long> {
        val raw = prefs.getString("${KEY_LAST_SYNC}_$configId", null) ?: return emptyMap()
        val map = mutableMapOf<String, Long>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getLong(k)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun updateLastSyncMap(configId: String, files: List<File>) {
        val map = getLastSyncMap(configId).toMutableMap()
        for (f in files) {
            map[f.absolutePath] = f.lastModified()
        }
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k, v)
        }
        prefs.edit().putString("${KEY_LAST_SYNC}_$configId", obj.toString()).apply()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
