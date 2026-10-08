package com.example.filesapp.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filesapp.data.*
import com.example.filesapp.domain.analyzer.*
import com.example.filesapp.domain.archive.*
import com.example.filesapp.domain.backup.*
import com.example.filesapp.domain.nearby.NearbyShareManager
import com.example.filesapp.domain.operations.*
import com.example.filesapp.domain.search.*
import com.example.filesapp.domain.storage.*
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

data class FileManagerUiState(
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val currentPath: String = "/",
    val realFiles: List<AndroidFileModel> = emptyList(),
    val realFolders: List<AndroidFileModel> = emptyList(),
    val vaultFiles: List<AndroidFileModel> = emptyList(),
    val trashItems: List<TrashItemModel> = emptyList(),
    val storageBreakdown: StorageBreakdownModel? = null,
    val selectedFiles: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isPrivateVaultUnlocked: Boolean = false,
    val isDriveConnected: Boolean = false,
    val driveUserEmail: String? = null,
    val driveFiles: List<AndroidFileModel> = emptyList(),
    val isDarkMode: Boolean = false,
    val isGridView: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val searchQuery: String = "",
    val activeCategory: String? = null,
    val categoryFiles: List<AndroidFileModel> = emptyList(),
    val pinnedFolders: Set<String> = emptySet(),
    val starredFiles: Set<String> = emptySet(),
    val driveStatusMessage: String? = null,
    // Advanced Tools State
    val duplicateScanProgress: DuplicateScanProgress? = null,
    val duplicateGroups: List<DuplicateFileGroup> = emptyList(),
    val totalDuplicateWastedBytes: Long = 0L,
    val installedApps: List<InstalledAppDetails> = emptyList(),
    val isLoadingApps: Boolean = false,
    val isComputingChecksum: Boolean = false,
    val checksumProgress: Float = 0f,
    val currentChecksumResult: FileChecksumResult? = null,
    // 3 More Advanced Tools State
    val isWifiServerRunning: Boolean = false,
    val wifiServerUrl: String = "",
    val wifiServerLogs: List<String> = emptyList(),
    val savedNetworkServers: List<NetworkServerConfig> = emptyList(),
    val currentRemoteFiles: List<RemoteFileItem> = emptyList(),
    val currentRemotePath: String = "/",
    val activeRemoteServer: NetworkServerConfig? = null,
    val isConnectingRemote: Boolean = false,
    val remoteStatusMessage: String? = null,
    val isProcessingImage: Boolean = false,
    val imageProcessProgress: Float = 0f,
    // File Operations Transfer State
    val activeTransfer: TransferProgress? = null,
    val activeConflict: FileConflict? = null,
    // Phase 2 Search & Storage Analyzer State
    val searchCategory: SearchCategoryFilter = SearchCategoryFilter.ALL,
    val searchResults: List<AndroidFileModel> = emptyList(),
    val largestFiles: List<AndroidFileModel> = emptyList(),
    val emptyFolders: List<EmptyFolderItem> = emptyList(),
    // Phase 3 Trash & Google Drive & Network state
    val autoCleanTrashDays: Int = 30,
    val currentDriveFolderId: String = "root",
    val driveBreadcrumbs: List<Pair<String, String>> = listOf("root" to "Google Drive"),
    val driveSearchQuery: String = "",
    // Backup Engine State
    val backupProgress: BackupExecutionProgress = BackupExecutionProgress(),
    val backupHistory: List<BackupHistoryRecord> = emptyList()
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("files_app_prefs", Context.MODE_PRIVATE)
    private val repository = StorageRepository(application)
    private val vaultManager = PrivateVaultManager(application)
    // DIAGNOSTIC: Lazy init (ChatGPT suggestion) - isolates startup crash.
    // If R8 release launches with lazy managers, the crash was in eager init.
    val driveManager by lazy { GoogleDriveManager(application) }
    val storageRegistry by lazy { StorageProviderRegistry(application) }
    val fileOpsEngine by lazy { FileOperationsEngine(application, storageRegistry) }
    val searchEngine = SearchEngine()
    val storageAnalyzerEngine by lazy { StorageAnalyzerEngine(application) }
    val archiveEngine = ArchiveEngine
    val networkStorageManager by lazy { NetworkStorageManager(application) }
    val wifiManager by lazy {
        WifiTransferManager(
            context = application,
            rootDirProvider = { Environment.getExternalStorageDirectory() },
            onLogMessage = { log ->
                val updated = (_uiState.value.wifiServerLogs + log).takeLast(20)
                _uiState.value = _uiState.value.copy(wifiServerLogs = updated)
            }
        )
    }
    val imageToolsManager by lazy { ImageToolsManager(application) }
    val backupEngine by lazy { BackupEngine(application, driveManager, networkStorageManager) }
    private var activeTransferController: TransferOperationController? = null

    private var signedInAccount: GoogleSignInAccount? = null
    private var directoryObserver: FileObserver? = null

    private val _uiState = MutableStateFlow(
        FileManagerUiState(
            starredFiles = prefs.getStringSet("starred_files", null)?.toSet() ?: emptySet(),
            pinnedFolders = prefs.getStringSet("pinned_folders", null)?.toSet() ?: emptySet(),
            isDarkMode = prefs.getBoolean("is_dark_mode", false),
            isGridView = prefs.getBoolean("is_grid_view", false)
        )
    )
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        repository.autoCleanOldTrash()
        loadDirectory(Environment.getExternalStorageDirectory())
        loadVaultFiles()
        loadTrashItems()
        loadStorageBreakdown()
        // DIAGNOSTIC: Temporarily disabled (ChatGPT suggestion).
        // If R8 release launches without this, Google Sign-In graph is the culprit.
        // checkLastSignedInAccount()
        // Securely clean up any leftover decrypted preview files from previous sessions on startup
        try {
            File(application.cacheDir, "vault_previews").deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        viewModelScope.launch {
            fileOpsEngine.currentProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(activeTransfer = progress)
            }
        }
    }

    fun checkLastSignedInAccount() {
        val account = driveManager.getLastSignedInAccount()
        if (account != null) {
            onGoogleSignInSuccess(account)
        }
    }

    fun setDriveStatusMessage(msg: String?) {
        _uiState.value = _uiState.value.copy(driveStatusMessage = msg)
    }

    fun startBackgroundScan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            repository.scanDeviceStorageFlow(_uiState.value.showHiddenFiles).collect { progress ->
                _uiState.value = _uiState.value.copy(scanProgress = progress)
            }
            _uiState.value = _uiState.value.copy(isScanning = false)
            loadDirectory(File(_uiState.value.currentPath.ifEmpty { Environment.getExternalStorageDirectory().absolutePath }))
            loadStorageBreakdown()
        }
    }

    fun loadStorageBreakdown() {
        viewModelScope.launch {
            val report = storageAnalyzerEngine.analyzeStorage(showHidden = _uiState.value.showHiddenFiles)
            _uiState.value = _uiState.value.copy(
                storageBreakdown = report.breakdown,
                largestFiles = report.largestFiles,
                emptyFolders = report.emptyFolders
            )
            searchEngine.updateIndex(report.allIndexedFiles)
        }
    }

    fun isVaultPinSet(): Boolean = vaultManager.isPinSet()

    fun getVaultManager(): PrivateVaultManager = vaultManager

    fun saveVaultPin(pin: String) {
        vaultManager.savePin(pin)
    }

    fun verifyVaultPin(pin: String): Boolean = vaultManager.verifyPin(pin)

    fun wipeVaultPreviewCache() {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.wipeAllTempPreviews()
        }
    }

    fun loadVaultFiles() {
        viewModelScope.launch {
            val encFiles = vaultManager.listVaultFiles()
            val mapped = encFiles.map { f ->
                val cleanName = f.name.removeSuffix(".enc")
                AndroidFileModel(
                    id = f.absolutePath,
                    name = cleanName,
                    path = f.absolutePath,
                    size = f.length(),
                    mimeType = getMimeType(File(cleanName)),
                    dateModified = f.lastModified(),
                    isDirectory = false
                )
            }
            _uiState.value = _uiState.value.copy(vaultFiles = mapped)
        }
    }

    fun lockFileInVault(file: File, pin: String, onResult: (Boolean, String) -> Unit) {
        if (!verifyVaultPin(pin)) {
            onResult(false, "Incorrect PIN")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    vaultManager.encryptAndMoveToVault(file, pin)
                }
                loadDirectory(File(_uiState.value.currentPath))
                loadVaultFiles()
                loadStorageBreakdown()
                onResult(true, "File encrypted and moved to Private Vault")
            } catch (e: Exception) {
                onResult(false, "Encryption error: ${e.message}")
            }
        }
    }

    fun decryptAndRestoreVaultFile(vaultFile: File, pin: String, onResult: (Boolean, String) -> Unit) {
        if (!verifyVaultPin(pin)) {
            onResult(false, "Incorrect PIN")
            return
        }
        viewModelScope.launch {
            try {
                val currentDir = File(_uiState.value.currentPath.ifEmpty { Environment.getExternalStorageDirectory().absolutePath })
                withContext(Dispatchers.IO) {
                    vaultManager.decryptAndRestoreFile(vaultFile, pin, currentDir)
                }
                loadDirectory(currentDir)
                loadVaultFiles()
                loadStorageBreakdown()
                onResult(true, "File decrypted and restored")
            } catch (e: Exception) {
                onResult(false, "Decryption error: ${e.message}")
            }
        }
    }

    fun decryptVaultFileForPreview(vaultFile: File, pin: String, onResult: (File?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val decryptedFile = withContext(Dispatchers.IO) {
                    vaultManager.decryptToTempCacheFile(vaultFile, pin)
                }
                if (decryptedFile != null && decryptedFile.exists()) {
                    onResult(decryptedFile, null)
                } else {
                    onResult(null, "Failed to decrypt: File missing or corrupted")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null, e.message ?: "Decryption error occurred")
            }
        }
    }

    suspend fun decryptVaultFileToCacheAsync(vaultFile: File, pin: String): File? = withContext(Dispatchers.IO) {
        try {
            vaultManager.decryptToTempCacheFile(vaultFile, pin)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decryptVaultFileToCache(vaultFile: File, pin: String): File? {
        return try {
            vaultManager.decryptToTempCacheFile(vaultFile, pin)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadTrashItems() {
        viewModelScope.launch {
            val items = repository.listTrashItems()
            _uiState.value = _uiState.value.copy(trashItems = items)
        }
    }

    fun restoreFromTrash(trashedFile: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val restored = repository.restoreFromTrash(trashedFile)
            if (restored != null) {
                loadDirectory(File(_uiState.value.currentPath))
                loadTrashItems()
                loadStorageBreakdown()
                onResult(true, "Restored '${restored.name}' successfully")
            } else {
                onResult(false, "Restore failed")
            }
        }
    }

    fun deletePermanentlyFromTrash(trashedFile: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repository.deletePermanentlyFromTrash(trashedFile)
            if (success) {
                loadTrashItems()
                loadStorageBreakdown()
                onResult(true, "Permanently deleted")
            } else {
                onResult(false, "Delete failed")
            }
        }
    }

    fun emptyTrash(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repository.emptyTrash()
            loadTrashItems()
            loadStorageBreakdown()
            if (success) {
                onResult(true, "Trash emptied")
            } else {
                onResult(false, "Error emptying trash")
            }
        }
    }

    fun renameFileInPlace(file: File, newName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val renamed = repository.renameFileInPlace(file, newName)
            if (renamed != null) {
                loadDirectory(File(_uiState.value.currentPath))
                onResult(true, "Renamed to $newName")
            } else {
                onResult(false, "Rename failed")
            }
        }
    }

    fun cancelCurrentTransfer() {
        activeTransferController?.cancel()
    }

    fun moveFileInPlace(
        file: File,
        targetDir: File,
        strategy: ConflictStrategy = ConflictStrategy.AUTO_RENAME,
        onResult: (Boolean, String) -> Unit
    ) {
        val srcItem = StorageItem.fromFile(file)
        val targetItem = StorageItem.fromFile(targetDir)
        activeTransferController = fileOpsEngine.moveItems(
            coroutineScope = viewModelScope,
            sources = listOf(srcItem),
            targetDirectory = targetItem,
            conflictStrategy = strategy,
            onComplete = { result ->
                loadDirectory(targetDir)
                loadDirectory(File(_uiState.value.currentPath))
                loadStorageBreakdown()
                if (result.success && result.successCount > 0) {
                    onResult(true, "Moved '${file.name}' to ${targetDir.name}")
                } else {
                    onResult(false, result.errorMessage ?: "Move failed")
                }
            }
        )
    }

    fun copyFileInPlace(
        file: File,
        targetDir: File,
        strategy: ConflictStrategy = ConflictStrategy.AUTO_RENAME,
        onResult: (Boolean, String) -> Unit
    ) {
        val srcItem = StorageItem.fromFile(file)
        val targetItem = StorageItem.fromFile(targetDir)
        activeTransferController = fileOpsEngine.copyItems(
            coroutineScope = viewModelScope,
            sources = listOf(srcItem),
            targetDirectory = targetItem,
            conflictStrategy = strategy,
            onComplete = { result ->
                loadDirectory(targetDir)
                loadDirectory(File(_uiState.value.currentPath))
                loadStorageBreakdown()
                if (result.success && result.successCount > 0) {
                    onResult(true, "Copied '${file.name}' to ${targetDir.name}")
                } else {
                    onResult(false, result.errorMessage ?: "Copy failed")
                }
            }
        )
    }

    fun copyMultipleItems(
        sources: List<StorageItem>,
        targetDir: StorageItem,
        strategy: ConflictStrategy = ConflictStrategy.AUTO_RENAME,
        onResult: (Boolean, String) -> Unit
    ) {
        activeTransferController = fileOpsEngine.copyItems(
            coroutineScope = viewModelScope,
            sources = sources,
            targetDirectory = targetDir,
            conflictStrategy = strategy,
            onComplete = { result ->
                val targetFile = targetDir.toFileOrNull()
                if (targetFile != null) loadDirectory(targetFile)
                loadDirectory(File(_uiState.value.currentPath))
                loadStorageBreakdown()
                if (result.success) {
                    onResult(true, "Copied ${result.successCount} item(s)")
                } else {
                    onResult(false, result.errorMessage ?: "Failed to copy some items")
                }
            }
        )
    }

    fun moveMultipleItems(
        sources: List<StorageItem>,
        targetDir: StorageItem,
        strategy: ConflictStrategy = ConflictStrategy.AUTO_RENAME,
        onResult: (Boolean, String) -> Unit
    ) {
        activeTransferController = fileOpsEngine.moveItems(
            coroutineScope = viewModelScope,
            sources = sources,
            targetDirectory = targetDir,
            conflictStrategy = strategy,
            onComplete = { result ->
                val targetFile = targetDir.toFileOrNull()
                if (targetFile != null) loadDirectory(targetFile)
                loadDirectory(File(_uiState.value.currentPath))
                loadStorageBreakdown()
                if (result.success) {
                    onResult(true, "Moved ${result.successCount} item(s)")
                } else {
                    onResult(false, result.errorMessage ?: "Failed to move some items")
                }
            }
        )
    }

    fun onGoogleSignInSuccess(account: GoogleSignInAccount) {
        signedInAccount = account
        driveManager.currentAccount = account
        _uiState.value = _uiState.value.copy(
            isDriveConnected = true,
            driveUserEmail = account.email ?: "Google Account",
            currentDriveFolderId = "root",
            driveBreadcrumbs = listOf("root" to "Google Drive")
        )
        loadDriveFiles("root")
    }

    fun signOutGoogleDrive(onComplete: () -> Unit) {
        driveManager.signOut {
            signedInAccount = null
            driveManager.currentAccount = null
            _uiState.value = _uiState.value.copy(
                isDriveConnected = false,
                driveUserEmail = null,
                driveFiles = emptyList(),
                currentDriveFolderId = "root",
                driveBreadcrumbs = listOf("root" to "Google Drive"),
                driveStatusMessage = null
            )
            onComplete()
        }
    }

    fun loadDriveFiles(folderId: String = _uiState.value.currentDriveFolderId) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, driveStatusMessage = null)
            val result = driveManager.listFiles(folderId)
            result.fold(
                onSuccess = { cloudFiles ->
                    val mappedModels = cloudFiles.map { cFile ->
                        AndroidFileModel(
                            id = cFile.id,
                            name = cFile.name,
                            path = "drive://${cFile.id}",
                            size = cFile.sizeBytes,
                            mimeType = cFile.mimeType,
                            dateModified = cFile.lastModified,
                            isDirectory = cFile.isDirectory
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        driveFiles = mappedModels,
                        currentDriveFolderId = folderId,
                        isScanning = false
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        driveStatusMessage = err.message ?: "Failed to load Drive files",
                        isScanning = false
                    )
                }
            )
        }
    }

    fun navigateDriveFolder(folderId: String, folderName: String) {
        val currentCrumbs = _uiState.value.driveBreadcrumbs.toMutableList()
        currentCrumbs.add(folderId to folderName)
        _uiState.value = _uiState.value.copy(
            currentDriveFolderId = folderId,
            driveBreadcrumbs = currentCrumbs
        )
        loadDriveFiles(folderId)
    }

    fun navigateDriveBack() {
        val currentCrumbs = _uiState.value.driveBreadcrumbs.toMutableList()
        if (currentCrumbs.size > 1) {
            currentCrumbs.removeAt(currentCrumbs.size - 1)
            val parent = currentCrumbs.last()
            _uiState.value = _uiState.value.copy(
                currentDriveFolderId = parent.first,
                driveBreadcrumbs = currentCrumbs
            )
            loadDriveFiles(parent.first)
        }
    }

    fun searchDriveFiles(query: String) {
        _uiState.value = _uiState.value.copy(driveSearchQuery = query)
        if (query.isBlank()) {
            loadDriveFiles(_uiState.value.currentDriveFolderId)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, driveStatusMessage = null)
            val result = driveManager.searchFiles(query)
            result.fold(
                onSuccess = { cloudFiles ->
                    val mappedModels = cloudFiles.map { cFile ->
                        AndroidFileModel(
                            id = cFile.id,
                            name = cFile.name,
                            path = "drive://${cFile.id}",
                            size = cFile.sizeBytes,
                            mimeType = cFile.mimeType,
                            dateModified = cFile.lastModified,
                            isDirectory = cFile.isDirectory
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        driveFiles = mappedModels,
                        isScanning = false
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        driveStatusMessage = err.message ?: "Search failed",
                        isScanning = false
                    )
                }
            )
        }
    }

    fun createDriveFolder(name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = driveManager.createFolder(_uiState.value.currentDriveFolderId, name)
            result.fold(
                onSuccess = {
                    loadDriveFiles(_uiState.value.currentDriveFolderId)
                    onResult(true, "Folder '$name' created in Google Drive")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Failed to create Drive folder")
                }
            )
        }
    }

    fun renameDriveFile(fileId: String, newName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = driveManager.rename(fileId, newName)
            result.fold(
                onSuccess = {
                    loadDriveFiles(_uiState.value.currentDriveFolderId)
                    onResult(true, "Renamed to '$newName'")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Rename failed")
                }
            )
        }
    }

    fun deleteDriveFile(fileId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = driveManager.delete(fileId)
            result.fold(
                onSuccess = {
                    loadDriveFiles(_uiState.value.currentDriveFolderId)
                    onResult(true, "Deleted item from Google Drive")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Delete failed")
                }
            )
        }
    }

    fun uploadLocalFileToDrive(file: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = driveManager.uploadFile(file, _uiState.value.currentDriveFolderId)
            result.fold(
                onSuccess = { uploaded ->
                    loadDriveFiles(_uiState.value.currentDriveFolderId)
                    onResult(true, "Successfully uploaded ${uploaded.name} to Google Drive")
                },
                onFailure = { err ->
                    onResult(false, "Upload failed: ${err.message}")
                }
            )
        }
    }

    fun downloadDriveFileToLocal(driveFileId: String, fileName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = File(downloadsDir, fileName)
            val result = driveManager.downloadFile(driveFileId, targetFile)
            result.fold(
                onSuccess = {
                    loadDirectory(File(_uiState.value.currentPath))
                    onResult(true, "Downloaded $fileName to Downloads folder")
                },
                onFailure = { err ->
                    onResult(false, "Download failed: ${err.message}")
                }
            )
        }
    }

    fun loadDirectory(directory: File) {
        viewModelScope.launch {
            val targetDir = if (directory.exists() && directory.isDirectory) directory else Environment.getExternalStorageDirectory()
            val children = targetDir.listFiles()?.toList() ?: emptyList()

            val folders = mutableListOf<AndroidFileModel>()
            val files = mutableListOf<AndroidFileModel>()

            for (file in children) {
                if (!_uiState.value.showHiddenFiles && file.name.startsWith(".")) continue

                val model = AndroidFileModel(
                    id = file.absolutePath,
                    name = file.name,
                    path = file.absolutePath,
                    size = if (file.isDirectory) 0L else file.length(),
                    mimeType = getMimeType(file),
                    dateModified = file.lastModified(),
                    isDirectory = file.isDirectory,
                    isHidden = file.name.startsWith("."),
                    isStarred = _uiState.value.starredFiles.contains(file.absolutePath),
                    isPinned = _uiState.value.pinnedFolders.contains(file.absolutePath)
                )

                if (file.isDirectory) {
                    folders.add(model)
                } else {
                    files.add(model)
                }
            }

            _uiState.value = _uiState.value.copy(
                currentPath = targetDir.absolutePath,
                realFolders = folders.sortedBy { it.name.lowercase() },
                realFiles = files.sortedByDescending { it.dateModified }
            )
            startWatchingDirectory(targetDir)
        }
    }

    private fun startWatchingDirectory(dir: File) {
        try {
            directoryObserver?.stopWatching()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                directoryObserver = object : FileObserver(dir, ALL_EVENTS) {
                    override fun onEvent(event: Int, path: String?) {
                        val mask = event and ALL_EVENTS
                        if (mask and (CREATE or DELETE or MODIFY or MOVED_FROM or MOVED_TO) != 0) {
                            viewModelScope.launch {
                                loadDirectory(dir)
                            }
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                directoryObserver = object : FileObserver(dir.absolutePath, ALL_EVENTS) {
                    override fun onEvent(event: Int, path: String?) {
                        val mask = event and ALL_EVENTS
                        if (mask and (CREATE or DELETE or MODIFY or MOVED_FROM or MOVED_TO) != 0) {
                            viewModelScope.launch {
                                loadDirectory(dir)
                            }
                        }
                    }
                }
            }
            directoryObserver?.startWatching()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            directoryObserver?.stopWatching()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            wifiManager.stopServer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "json" -> "application/json"
            "txt", "kt", "java", "xml", "js", "css", "md" -> "text/plain"
            "html", "htm" -> "text/html"
            else -> "application/octet-stream"
        }
    }

    fun toggleDarkMode() {
        val newMode = !_uiState.value.isDarkMode
        _uiState.value = _uiState.value.copy(isDarkMode = newMode)
        prefs.edit().putBoolean("is_dark_mode", newMode).apply()
    }

    fun toggleGridView() {
        val newG = !_uiState.value.isGridView
        _uiState.value = _uiState.value.copy(isGridView = newG)
        prefs.edit().putBoolean("is_grid_view", newG).apply()
    }

    fun toggleShowHiddenFiles() {
        val newShowHidden = !_uiState.value.showHiddenFiles
        _uiState.value = _uiState.value.copy(showHiddenFiles = newShowHidden)
        loadDirectory(File(_uiState.value.currentPath))
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        performSearch()
    }

    fun setSearchCategory(category: SearchCategoryFilter) {
        _uiState.value = _uiState.value.copy(searchCategory = category)
        performSearch()
    }

    fun performSearch() {
        viewModelScope.launch {
            val query = SearchQuery(
                queryText = _uiState.value.searchQuery,
                category = _uiState.value.searchCategory,
                showHidden = _uiState.value.showHiddenFiles
            )
            val results = searchEngine.search(query)
            _uiState.value = _uiState.value.copy(searchResults = results)
        }
    }

    fun setCategoryFilter(category: String?) {
        _uiState.value = _uiState.value.copy(activeCategory = category)
        if (category == null) {
            _uiState.value = _uiState.value.copy(categoryFiles = emptyList())
        } else if (category == "downloads") {
            viewModelScope.launch(Dispatchers.IO) {
                val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val files = downloadsFolder.listFiles()?.filter { it.isFile && !it.isHidden }?.map { f ->
                    AndroidFileModel(
                        id = f.absolutePath,
                        name = f.name,
                        path = f.absolutePath,
                        size = f.length(),
                        mimeType = getMimeType(f),
                        dateModified = f.lastModified(),
                        isDirectory = false
                    )
                } ?: emptyList()
                _uiState.value = _uiState.value.copy(categoryFiles = files)
            }
        } else {
            val filter = when (category) {
                "images" -> SearchCategoryFilter.IMAGES
                "videos" -> SearchCategoryFilter.VIDEOS
                "audio" -> SearchCategoryFilter.AUDIO
                "docs" -> SearchCategoryFilter.DOCUMENTS
                "apks" -> SearchCategoryFilter.APKS
                "archives" -> SearchCategoryFilter.ARCHIVES
                "large" -> SearchCategoryFilter.LARGE_FILES
                "recent" -> SearchCategoryFilter.RECENT
                else -> SearchCategoryFilter.ALL
            }
            viewModelScope.launch {
                val results = searchEngine.search(SearchQuery(category = filter, showHidden = _uiState.value.showHiddenFiles))
                _uiState.value = _uiState.value.copy(categoryFiles = results)
            }
        }
    }

    private fun loadCategoryFilesRecursive(category: String) {
        _uiState.value = _uiState.value.copy(isScanning = true)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val root = Environment.getExternalStorageDirectory()
            val result = mutableListOf<AndroidFileModel>()
            
            fun scan(dir: File) {
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.name.startsWith(".")) continue
                    if (f.isDirectory) {
                        scan(f)
                    } else {
                        val extension = f.extension.lowercase()
                        val mime = getMimeType(f)
                        val matches = when (category) {
                            "images" -> mime.startsWith("image/")
                            "docs" -> mime.contains("pdf") || mime.startsWith("text/") || extension in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")
                            "audio" -> mime.startsWith("audio/")
                            "apks" -> extension == "apk" || mime.contains("android.package-archive")
                            "archives" -> extension in listOf("zip", "rar", "7z", "tar", "gz")
                            else -> true
                        }
                        if (matches) {
                            result.add(
                                AndroidFileModel(
                                    id = f.absolutePath,
                                    name = f.name,
                                    path = f.absolutePath,
                                    size = f.length(),
                                    mimeType = mime,
                                    dateModified = f.lastModified(),
                                    isDirectory = false,
                                    isStarred = _uiState.value.starredFiles.contains(f.absolutePath)
                                )
                            )
                        }
                    }
                }
            }
            scan(root)
            
            _uiState.value = _uiState.value.copy(
                categoryFiles = result.sortedByDescending { it.dateModified },
                isScanning = false
            )
        }
    }

    fun setCurrentPath(path: String) {
        _uiState.value = _uiState.value.copy(activeCategory = null, searchQuery = "")
        loadDirectory(File(path))
    }

    fun toggleFileSelection(fileId: String) {
        val current = _uiState.value.selectedFiles.toMutableSet()
        if (current.contains(fileId)) current.remove(fileId) else current.add(fileId)
        _uiState.value = _uiState.value.copy(selectedFiles = current)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedFiles = emptySet(), isMultiSelectMode = false)
    }

    fun toggleMultiSelectMode() {
        _uiState.value = _uiState.value.copy(isMultiSelectMode = !_uiState.value.isMultiSelectMode)
    }

    fun toggleStar(filePath: String) {
        val current = _uiState.value.starredFiles.toMutableSet()
        if (current.contains(filePath)) current.remove(filePath) else current.add(filePath)
        val newSet = HashSet(current)
        _uiState.value = _uiState.value.copy(starredFiles = newSet)
        prefs.edit().remove("starred_files").putStringSet("starred_files", newSet).apply()
        loadDirectory(File(_uiState.value.currentPath))
    }

    fun togglePinFolder(folderPath: String) {
        val current = _uiState.value.pinnedFolders.toMutableSet()
        if (current.contains(folderPath)) current.remove(folderPath) else current.add(folderPath)
        val newSet = HashSet(current)
        _uiState.value = _uiState.value.copy(pinnedFolders = newSet)
        prefs.edit().remove("pinned_folders").putStringSet("pinned_folders", newSet).apply()
        loadDirectory(File(_uiState.value.currentPath))
    }

    fun moveToTrash(file: File) {
        viewModelScope.launch {
            repository.moveToTrash(file)
            loadDirectory(File(_uiState.value.currentPath))
            loadTrashItems()
            loadStorageBreakdown()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            loadDirectory(File(_uiState.value.currentPath))
            loadTrashItems()
            loadStorageBreakdown()
        }
    }

    fun createZipArchive(
        filesToZip: List<File>,
        zipName: String,
        targetDir: File,
        level: CompressionLevel = CompressionLevel.NORMAL,
        password: String? = null,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val targetFile = File(targetDir, if (zipName.endsWith(".zip")) zipName else "$zipName.zip")
            val success = withContext(Dispatchers.IO) {
                if (password != null) {
                    archiveEngine.createEncryptedZipArchive(filesToZip, targetFile, password, level)
                } else {
                    archiveEngine.createZipArchive(filesToZip, targetFile, level)
                }
            }
            loadDirectory(File(_uiState.value.currentPath))
            loadStorageBreakdown()
            onResult?.invoke(success)
        }
    }

    fun create7zArchive(
        filesToZip: List<File>,
        sevenZName: String,
        targetDir: File,
        level: CompressionLevel = CompressionLevel.NORMAL,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val targetFile = File(targetDir, if (sevenZName.endsWith(".7z")) sevenZName else "$sevenZName.7z")
            val success = withContext(Dispatchers.IO) {
                archiveEngine.createSevenZArchive(filesToZip, targetFile, level)
            }
            loadDirectory(File(_uiState.value.currentPath))
            loadStorageBreakdown()
            onResult?.invoke(success)
        }
    }

    fun extractZipArchive(
        zipFile: File,
        targetDir: File,
        selectedPaths: Set<String>? = null,
        password: String? = null,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                archiveEngine.extract(zipFile, targetDir, selectedPaths, password)
            }
            loadDirectory(File(_uiState.value.currentPath))
            loadStorageBreakdown()
            onResult?.invoke(success)
        }
    }

    /**
     * Checks if an archive is password-protected.
     */
    fun isArchivePasswordProtected(archiveFile: File): Boolean {
        return archiveEngine.isPasswordProtected(archiveFile)
    }

    // ===== Nearby Share (Google Files parity) =====
    val nearbyShareManager by lazy { NearbyShareManager(getApplication()) }

    private val _nearbyDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val nearbyDevices: StateFlow<List<Pair<String, String>>> = _nearbyDevices.asStateFlow()

    private val _nearbyLogs = MutableStateFlow<List<String>>(emptyList())
    val nearbyLogs: StateFlow<List<String>> = _nearbyLogs.asStateFlow()

    private val _nearbyConnected = MutableStateFlow<Map<String, String>>(emptyMap())
    val nearbyConnected: StateFlow<Map<String, String>> = _nearbyConnected.asStateFlow()

    fun initNearbyShare() {
        val mgr = nearbyShareManager
        mgr.onEndpointFound = { id, name ->
            _nearbyDevices.value = _nearbyDevices.value.filter { it.first != id } + (id to name)
        }
        mgr.onEndpointLost = { id ->
            _nearbyDevices.value = _nearbyDevices.value.filter { it.first != id }
        }
        mgr.onConnectionRequest = { id, name, accept ->
            // Auto-accept for simplicity; UI can override
            accept(true)
        }
        mgr.onConnected = { id, name ->
            _nearbyConnected.value = _nearbyConnected.value + (id to name)
        }
        mgr.onDisconnected = { id ->
            _nearbyConnected.value = _nearbyConnected.value - id
        }
        mgr.onFileReceived = { file, from ->
            _uiState.value = _uiState.value.copy(
                driveStatusMessage = "Received ${file.name} from $from"
            )
            loadDirectory(File(_uiState.value.currentPath))
        }
        mgr.onLog = { log ->
            _nearbyLogs.value = (_nearbyLogs.value + log).takeLast(30)
        }
    }

    fun startNearbyAdvertising(deviceName: String) {
        initNearbyShare()
        nearbyShareManager.startAdvertising(deviceName)
    }

    fun startNearbyDiscovery() {
        initNearbyShare()
        nearbyShareManager.startDiscovery()
    }

    fun stopNearby() {
        nearbyShareManager.stopAll()
        _nearbyDevices.value = emptyList()
        _nearbyConnected.value = emptyMap()
    }

    fun connectNearby(endpointId: String, deviceName: String) {
        nearbyShareManager.connectTo(endpointId, deviceName)
    }

    fun sendFileNearby(endpointId: String, file: File) {
        nearbyShareManager.sendFile(endpointId, file)
    }

    fun cleanEmptyFolders(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val deleted = storageAnalyzerEngine.cleanEmptyFolders(_uiState.value.emptyFolders)
            loadStorageBreakdown()
            loadDirectory(File(_uiState.value.currentPath))
            onResult(deleted)
        }
    }

    fun backupAppApk(appInfo: InstalledAppInfo, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val backedUp = repository.backupAppApk(appInfo)
            if (backedUp != null) {
                loadDirectory(File(_uiState.value.currentPath))
                loadStorageBreakdown()
                onResult(true, "Backed up ${appInfo.name} APK to Downloads")
            } else {
                onResult(false, "Source APK missing at ${appInfo.apkPath}")
            }
        }
    }

    fun createFolder(parentDirPath: String, name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val parentDir = File(parentDirPath)
            val created = repository.createFolder(parentDir, name)
            if (created != null) {
                loadDirectory(parentDir)
                onResult(true, "Folder '$name' created successfully")
            } else {
                onResult(false, "Failed to create folder. It might already exist.")
            }
        }
    }

    fun createNewFile(parentDirPath: String, name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val parentDir = File(parentDirPath)
            val finalName = if (!name.contains(".")) "$name.txt" else name
            val created = repository.createNewFile(parentDir, finalName)
            if (created != null) {
                loadDirectory(parentDir)
                onResult(true, "File '$finalName' created successfully")
            } else {
                onResult(false, "Failed to create file. It might already exist.")
            }
        }
    }

    // ==========================================
    // 1. DUPLICATE FINDER ENGINE (3-Tier Progressive)
    // ==========================================

    fun scanDuplicateFiles() {
        viewModelScope.launch {
            val root = Environment.getExternalStorageDirectory()
            storageAnalyzerEngine.findDuplicatesProgressiveFlow(root).collect { progress ->
                _uiState.value = _uiState.value.copy(
                    duplicateScanProgress = progress,
                    duplicateGroups = progress.duplicateGroups,
                    totalDuplicateWastedBytes = progress.totalWastedBytes
                )
            }
        }
    }

    fun deleteSelectedDuplicates(filesToDelete: List<File>, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            var successCount = 0
            withContext(Dispatchers.IO) {
                for (file in filesToDelete) {
                    try {
                        if (file.exists() && file.delete()) {
                            successCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Refresh duplicates list
            val updatedGroups = _uiState.value.duplicateGroups.mapNotNull { group ->
                val remaining = group.files.filter { it.exists() }
                if (remaining.size > 1) {
                    group.copy(files = remaining)
                } else null
            }
            val totalWasted = updatedGroups.sumOf { it.wastedSizeBytes }

            _uiState.value = _uiState.value.copy(
                duplicateGroups = updatedGroups,
                totalDuplicateWastedBytes = totalWasted
            )

            loadDirectory(File(_uiState.value.currentPath))
            loadStorageBreakdown()
            onResult(true, "Successfully deleted $successCount duplicate file(s)")
        }
    }

    // ==========================================
    // 2. FILE CHECKSUM CALCULATOR
    // ==========================================

    fun calculateFileChecksum(file: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isComputingChecksum = true,
                checksumProgress = 0f,
                currentChecksumResult = null
            )
            try {
                val result = AdvancedToolsManager.calculateChecksums(file) { progress ->
                    _uiState.value = _uiState.value.copy(checksumProgress = progress)
                }
                _uiState.value = _uiState.value.copy(
                    isComputingChecksum = false,
                    currentChecksumResult = result
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isComputingChecksum = false,
                    currentChecksumResult = null
                )
            }
        }
    }

    fun clearChecksumResult() {
        _uiState.value = _uiState.value.copy(
            isComputingChecksum = false,
            checksumProgress = 0f,
            currentChecksumResult = null
        )
    }

    // ==========================================
    // 3. APP MANAGER
    // ==========================================

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingApps = true)
            val apps = AdvancedToolsManager.getInstalledApps(getApplication())
            _uiState.value = _uiState.value.copy(
                installedApps = apps,
                isLoadingApps = false
            )
        }
    }

    fun backupAppDetailsApk(app: InstalledAppDetails, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val appInfo = InstalledAppInfo(
                name = app.name,
                packageName = app.packageName,
                versionName = app.versionName,
                apkPath = app.apkPath,
                sizeBytes = app.sizeBytes
            )
            val backedUp = repository.backupAppApk(appInfo)
            if (backedUp != null) {
                loadDirectory(File(_uiState.value.currentPath))
                loadStorageBreakdown()
                onResult(true, "Exported '${app.name}' APK to Downloads folder")
            } else {
                onResult(false, "Could not access source APK for '${app.name}'")
            }
        }
    }

    // ==========================================
    // 4. WIFI FILE TRANSFER
    // ==========================================

    fun startWifiServer(port: Int = 8080) {
        viewModelScope.launch {
            val success = wifiManager.startServer(port)
            if (success) {
                _uiState.value = _uiState.value.copy(
                    isWifiServerRunning = true,
                    wifiServerUrl = wifiManager.getServerUrl()
                )
            } else {
                _uiState.value = _uiState.value.copy(isWifiServerRunning = false)
            }
        }
    }

    fun stopWifiServer() {
        wifiManager.stopServer()
        _uiState.value = _uiState.value.copy(
            isWifiServerRunning = false,
            wifiServerUrl = ""
        )
    }

    // ==========================================
    // 5. IMAGE TOOLS (Compress, Resize, Convert)
    // ==========================================

    fun processImage(
        inputFile: File,
        options: ImageProcessOptions,
        onResult: (Boolean, ImageProcessResult?, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingImage = true)
            val res = imageToolsManager.processImage(inputFile, options)
            _uiState.value = _uiState.value.copy(isProcessingImage = false)
            res.fold(
                onSuccess = { result ->
                    loadDirectory(File(_uiState.value.currentPath))
                    loadStorageBreakdown()
                    onResult(true, result, "Saved as ${result.outputFile.name}")
                },
                onFailure = { err ->
                    onResult(false, null, err.message ?: "Failed to process image")
                }
            )
        }
    }

    // ==========================================
    // 6. FTP/SMB NETWORK STORAGE
    // ==========================================

    fun loadSavedNetworkServers() {
        val servers = networkStorageManager.getSavedServers()
        _uiState.value = _uiState.value.copy(savedNetworkServers = servers)
    }

    fun saveNetworkServer(config: NetworkServerConfig) {
        networkStorageManager.saveServer(config)
        loadSavedNetworkServers()
    }

    fun deleteNetworkServer(serverId: String) {
        networkStorageManager.deleteServer(serverId)
        loadSavedNetworkServers()
        if (_uiState.value.activeRemoteServer?.id == serverId) {
            disconnectRemoteServer()
        }
    }

    fun connectToNetworkServer(server: NetworkServerConfig, path: String = "/") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnectingRemote = true,
                remoteStatusMessage = "Connecting to ${server.host}..."
            )
            val result = networkStorageManager.listRemoteFiles(server, path)
            result.fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        activeRemoteServer = server,
                        currentRemoteFiles = files,
                        currentRemotePath = path,
                        isConnectingRemote = false,
                        remoteStatusMessage = null
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isConnectingRemote = false,
                        remoteStatusMessage = "Error: ${err.message}"
                    )
                }
            )
        }
    }

    fun navigateRemoteFolder(path: String) {
        val server = _uiState.value.activeRemoteServer ?: return
        connectToNetworkServer(server, path)
    }

    fun disconnectRemoteServer() {
        _uiState.value = _uiState.value.copy(
            activeRemoteServer = null,
            currentRemoteFiles = emptyList(),
            currentRemotePath = "/",
            isConnectingRemote = false,
            remoteStatusMessage = null
        )
    }

    fun downloadRemoteFile(remoteFile: RemoteFileItem, onResult: (Boolean, String) -> Unit) {
        val server = _uiState.value.activeRemoteServer ?: return
        viewModelScope.launch {
            val destDir = File(_uiState.value.currentPath)
            val result = networkStorageManager.downloadRemoteFile(server, remoteFile.path, destDir) { }
            result.fold(
                onSuccess = { localFile ->
                    loadDirectory(File(_uiState.value.currentPath))
                    loadStorageBreakdown()
                    onResult(true, "Downloaded ${localFile.name}")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Download failed")
                }
            )
        }
    }

    fun setAutoCleanTrashDays(days: Int) {
        prefs.edit().putInt("auto_clean_trash_days", days).apply()
        _uiState.value = _uiState.value.copy(autoCleanTrashDays = days)
        repository.autoCleanOldTrash(days)
        loadTrashItems()
    }

    fun batchBackupAppDetailsApks(apps: List<InstalledAppDetails>, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch {
            var successCount = 0
            var failedCount = 0
            withContext(Dispatchers.IO) {
                for (app in apps) {
                    val appInfo = InstalledAppInfo(
                        name = app.name,
                        packageName = app.packageName,
                        versionName = app.versionName,
                        apkPath = app.apkPath,
                        sizeBytes = app.sizeBytes
                    )
                    val backedUp = repository.backupAppApk(appInfo)
                    if (backedUp != null) {
                        successCount++
                    } else {
                        failedCount++
                    }
                }
            }
            loadDirectory(File(_uiState.value.currentPath))
            loadStorageBreakdown()
            onResult(successCount, failedCount)
        }
    }

    fun testNetworkServer(config: NetworkServerConfig, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = networkStorageManager.testConnection(config)
            result.fold(
                onSuccess = { msg ->
                    onResult(true, msg)
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Connection failed")
                }
            )
        }
    }

    // ==========================================
    // 7. BACKUP ENGINE & RESTORE
    // ==========================================

    fun loadBackupHistory() {
        val history = backupEngine.getBackupHistory()
        _uiState.value = _uiState.value.copy(backupHistory = history)
    }

    fun executeBackupJob(config: BackupJobConfig, onResult: (Boolean, String) -> Unit) {
        if (config.scheduledIntervalHours > 0) {
            BackupWorker.scheduleBackupJob(getApplication(), config)
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                backupProgress = BackupExecutionProgress(isRunning = true, statusMessage = "Starting backup '${config.name}'...")
            )
            val res = backupEngine.executeBackup(config) { prog ->
                _uiState.value = _uiState.value.copy(backupProgress = prog)
            }
            loadBackupHistory()
            res.fold(
                onSuccess = { rec ->
                    loadDirectory(File(_uiState.value.currentPath))
                    loadStorageBreakdown()
                    onResult(true, "Backup '${config.name}' completed (${rec.totalFilesBackedUp} files)")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Backup failed")
                }
            )
        }
    }

    fun restoreBackupArchive(archiveFile: File, targetDir: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = backupEngine.restoreBackupArchive(archiveFile, targetDir)
            res.fold(
                onSuccess = {
                    loadDirectory(targetDir)
                    loadStorageBreakdown()
                    onResult(true, "Restored backup archive to ${targetDir.name}")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Restore failed")
                }
            )
        }
    }
}
