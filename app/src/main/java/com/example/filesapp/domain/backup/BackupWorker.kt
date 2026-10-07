package com.example.filesapp.domain.backup

import android.content.Context
import androidx.work.*
import com.example.filesapp.data.GoogleDriveManager
import com.example.filesapp.data.NetworkStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Production WorkManager Worker for Scheduled & Background Backups.
 * Handles background execution, unmetered network constraints, and exponential backoff retries.
 */
class BackupWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return@withContext Result.failure()
        val jobName = inputData.getString(KEY_JOB_NAME) ?: "Scheduled Backup"
        val folderPaths = inputData.getStringArray(KEY_FOLDERS)?.toList() ?: emptyList()
        val destTypeStr = inputData.getString(KEY_DEST_TYPE) ?: BackupDestinationType.LOCAL_STORAGE.name
        val localDestPath = inputData.getString(KEY_LOCAL_DEST)
        val driveFolderId = inputData.getString(KEY_DRIVE_FOLDER) ?: "root"
        val networkServerId = inputData.getString(KEY_NETWORK_SERVER)
        val isWifiOnly = inputData.getBoolean(KEY_WIFI_ONLY, true)
        val isIncremental = inputData.getBoolean(KEY_INCREMENTAL, true)

        val destType = try {
            BackupDestinationType.valueOf(destTypeStr)
        } catch (e: Exception) {
            BackupDestinationType.LOCAL_STORAGE
        }

        val config = BackupJobConfig(
            id = jobId,
            name = jobName,
            sourceFolderPaths = folderPaths,
            destinationType = destType,
            localDestinationPath = localDestPath,
            driveFolderId = driveFolderId,
            networkServerId = networkServerId,
            isWifiOnly = isWifiOnly,
            isIncremental = isIncremental
        )

        val driveManager = GoogleDriveManager(appContext)
        val networkManager = NetworkStorageManager(appContext)
        val backupEngine = BackupEngine(appContext, driveManager, networkManager)

        val backupResult = backupEngine.executeBackup(config)

        if (backupResult.isSuccess) {
            Result.success()
        } else {
            val exception = backupResult.exceptionOrNull()
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (exception?.message ?: "Backup failed")))
            }
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_JOB_NAME = "job_name"
        const val KEY_FOLDERS = "folders"
        const val KEY_DEST_TYPE = "dest_type"
        const val KEY_LOCAL_DEST = "local_dest"
        const val KEY_DRIVE_FOLDER = "drive_folder"
        const val KEY_NETWORK_SERVER = "network_server"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_INCREMENTAL = "incremental"
        private const val MAX_RETRIES = 3

        /**
         * Schedules periodic or immediate backup execution via WorkManager.
         */
        fun scheduleBackupJob(context: Context, config: BackupJobConfig) {
            val workManager = WorkManager.getInstance(context)
            val workName = "backup_job_${config.id}"

            if (config.scheduledIntervalHours <= 0) {
                workManager.cancelUniqueWork(workName)
                return
            }

            val inputData = Data.Builder()
                .putString(KEY_JOB_ID, config.id)
                .putString(KEY_JOB_NAME, config.name)
                .putStringArray(KEY_FOLDERS, config.sourceFolderPaths.toTypedArray())
                .putString(KEY_DEST_TYPE, config.destinationType.name)
                .putString(KEY_LOCAL_DEST, config.localDestinationPath)
                .putString(KEY_DRIVE_FOLDER, config.driveFolderId)
                .putString(KEY_NETWORK_SERVER, config.networkServerId)
                .putBoolean(KEY_WIFI_ONLY, config.isWifiOnly)
                .putBoolean(KEY_INCREMENTAL, config.isIncremental)
                .build()

            val constraintsBuilder = Constraints.Builder()
                .setRequiresStorageNotLow(true)

            if (config.isWifiOnly) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
            } else if (config.destinationType != BackupDestinationType.LOCAL_STORAGE) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            }

            val repeatIntervalHours = config.scheduledIntervalHours.toLong().coerceAtLeast(1L)

            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatIntervalHours, TimeUnit.HOURS
            )
                .setConstraints(constraintsBuilder.build())
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
