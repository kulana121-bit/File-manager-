package com.example.filesapp.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filesapp.data.*
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val pinnedFolders: Set<String> = emptySet(),
    val starredFiles: Set<String> = emptySet(),
    val driveStatusMessage: String? = null
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("files_app_prefs", Context.MODE_PRIVATE)
    private val repository = StorageRepository(application)
    private val vaultManager = PrivateVaultManager(application)
    val driveManager = GoogleDriveManager(application)

    private var signedInAccount: GoogleSignInAccount? = null

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
                vaultManager.encryptAndMoveToVault(file, pin)
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
                vaultManager.decryptAndRestoreFile(vaultFile, pin, currentDir)
                loadDirectory(currentDir)
                loadVaultFiles()
                loadStorageBreakdown()
                onResult(true, "File decrypted and restored")
            } catch (e: Exception) {
                onResult(false, "Decryption error: ${e.message}")
            }
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

    fun loadDriveFiles() {
        val account = signedInAccount ?: return
        viewModelScope.launch {
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
                _uiState.value = _uiState.value.copy(driveFiles = mappedModels)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(driveStatusMessage = "Drive error: ${e.message}")
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
            repository.extractZipArchive(zipFile, targetDir)
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
}
