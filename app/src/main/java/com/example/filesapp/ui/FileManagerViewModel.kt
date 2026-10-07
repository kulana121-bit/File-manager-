package com.example.filesapp.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filesapp.data.*
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
    val imageProcessProgress: Float = 0f
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("files_app_prefs", Context.MODE_PRIVATE)
    private val repository = StorageRepository(application)
    private val vaultManager = PrivateVaultManager(application)
    val driveManager = GoogleDriveManager(application)
    val wifiManager = WifiTransferManager(
        context = application,
        rootDirProvider = { Environment.getExternalStorageDirectory() },
        onLogMessage = { log ->
            val updated = (_uiState.value.wifiServerLogs + log).takeLast(20)
            _uiState.value = _uiState.value.copy(wifiServerLogs = updated)
        }
    )
    val imageToolsManager = ImageToolsManager(application)
    val networkStorageManager = NetworkStorageManager(application)

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
        checkLastSignedInAccount()
        // Securely clean up any leftover decrypted preview files from previous sessions on startup
        try {
            File(application.cacheDir, "vault_previews").deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
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
            val breakdown = repository.computeStorageBreakdown()
            _uiState.value = _uiState.value.copy(storageBreakdown = breakdown)
        }
    }

    fun isVaultPinSet(): Boolean = vaultManager.isPinSet()

    fun saveVaultPin(pin: String) {
        vaultManager.savePin(pin)
    }

    fun verifyVaultPin(pin: String): Boolean = vaultManager.verifyPin(pin)

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

    fun moveFileInPlace(file: File, targetDir: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val moved = repository.moveFileInPlace(file, targetDir)
            if (moved != null) {
                loadDirectory(File(_uiState.value.currentPath))
                onResult(true, "Moved to ${targetDir.name}")
            } else {
                onResult(false, "Move failed")
            }
        }
    }

    fun copyFileInPlace(file: File, targetDir: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val copied = repository.copyFileInPlace(file, targetDir)
            if (copied != null) {
                loadDirectory(File(_uiState.value.currentPath))
                onResult(true, "Copied to ${targetDir.name}")
            } else {
                onResult(false, "Copy failed")
            }
        }
    }

    fun onGoogleSignInSuccess(account: GoogleSignInAccount) {
        signedInAccount = account
        _uiState.value = _uiState.value.copy(
            isDriveConnected = true,
            driveUserEmail = account.email ?: "Google Account"
        )
        loadDriveFiles()
    }

    fun signOutGoogleDrive(onComplete: () -> Unit) {
        driveManager.signOut {
            signedInAccount = null
            _uiState.value = _uiState.value.copy(
                isDriveConnected = false,
                driveUserEmail = null,
                driveFiles = emptyList(),
                driveStatusMessage = null
            )
            onComplete()
        }
    }

    fun loadDriveFiles() {
        val account = signedInAccount ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, driveStatusMessage = null)
            try {
                val googleFiles = driveManager.fetchDriveFiles(account)
                val mappedModels = googleFiles.map { gFile ->
                    AndroidFileModel(
                        id = gFile.id ?: "",
                        name = gFile.name ?: "Untitled",
                        path = "drive://${gFile.id}",
                        size = gFile.getSize()?.toLong() ?: 0L,
                        mimeType = gFile.mimeType ?: "application/octet-stream",
                        dateModified = gFile.modifiedTime?.value ?: System.currentTimeMillis(),
                        isDirectory = gFile.mimeType == "application/vnd.google-apps.folder"
                    )
                }
                _uiState.value = _uiState.value.copy(driveFiles = mappedModels, isScanning = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(driveStatusMessage = "Drive error: ${e.message}", isScanning = false)
            }
        }
    }

    fun uploadLocalFileToDrive(file: File, onResult: (Boolean, String) -> Unit) {
        val account = signedInAccount
        if (account == null) {
            onResult(false, "Please sign in to Google Drive first")
            return
        }
        viewModelScope.launch {
            try {
                val uploaded = driveManager.uploadFile(account, file)
                loadDriveFiles()
                onResult(true, "Successfully uploaded ${uploaded.name} to Google Drive")
            } catch (e: Exception) {
                onResult(false, "Upload failed: ${e.message}")
            }
        }
    }

    fun downloadDriveFileToLocal(driveFileId: String, fileName: String, onResult: (Boolean, String) -> Unit) {
        val account = signedInAccount
        if (account == null) {
            onResult(false, "Please sign in to Google Drive first")
            return
        }
        viewModelScope.launch {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                driveManager.downloadFile(account, driveFileId, targetFile)
                loadDirectory(File(_uiState.value.currentPath))
                onResult(true, "Downloaded $fileName to Downloads")
            } catch (e: Exception) {
                onResult(false, "Download failed: ${e.message}")
            }
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
    }

    fun setCategoryFilter(category: String?) {
        _uiState.value = _uiState.value.copy(activeCategory = category)
        if (category == null) {
            _uiState.value = _uiState.value.copy(categoryFiles = emptyList())
        } else {
            loadCategoryFilesRecursive(category)
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

    fun createZipArchive(filesToZip: List<File>, zipName: String, targetDir: File) {
        viewModelScope.launch {
            repository.createZipArchive(filesToZip, zipName, targetDir)
            loadDirectory(File(_uiState.value.currentPath))
            loadStorageBreakdown()
        }
    }

    fun extractZipArchive(zipFile: File, targetDir: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    ArchiveManager.extractAll(zipFile, targetDir)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            loadDirectory(File(_uiState.value.currentPath))
            loadStorageBreakdown()
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
    // 1. DUPLICATE FINDER ENGINE
    // ==========================================

    fun scanDuplicateFiles() {
        viewModelScope.launch {
            val root = Environment.getExternalStorageDirectory()
            AdvancedToolsManager.scanDuplicatesFlow(root).collect { progress ->
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

    fun uploadLocalFileToRemote(localFile: File, onResult: (Boolean, String) -> Unit) {
        val server = _uiState.value.activeRemoteServer ?: return
        viewModelScope.launch {
            val remoteDir = _uiState.value.currentRemotePath
            val result = networkStorageManager.uploadFile(server, localFile, remoteDir) { }
            result.fold(
                onSuccess = {
                    connectToNetworkServer(server, remoteDir)
                    onResult(true, "Uploaded ${localFile.name} to remote server")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Upload failed")
                }
            )
        }
    }
}
