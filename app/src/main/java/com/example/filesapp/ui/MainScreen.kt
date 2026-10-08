package com.example.filesapp.ui

import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.composed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.filesapp.data.AndroidFileModel
import com.example.filesapp.data.InstalledAppInfo
import com.example.filesapp.data.TrashItemModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Modifier.bounceClick(scaleDown: Float = 0.96f) = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                        isPressed = true
                    } else if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Release || event.type == androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                        isPressed = false
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: FileManagerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Folders") } // "Folders", "Recent" inside Files tab
    var activeBottomNav by remember { mutableStateOf("Dashboard") } // "Dashboard", "Files", "Vault", "Drive", "More"

    // Dialog & In-App Viewer States
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinUnlockDialog by remember { mutableStateOf(false) }
    var showVaultBrowserDialog by remember { mutableStateOf(false) }
    var showMoveToVaultDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showRestoreVaultFileDialog by remember { mutableStateOf<AndroidFileModel?>(null) }

    var showTrashDialog by remember { mutableStateOf(false) }
    var showStorageAnalyzerDialog by remember { mutableStateOf(false) }
    var showDuplicateFinderDialog by remember { mutableStateOf(false) }
    var showAppManagerDialog by remember { mutableStateOf(false) }
    var showChecksumDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showWifiTransferDialog by remember { mutableStateOf(false) }
    var showNetworkStorageDialog by remember { mutableStateOf(false) }
    var showBackupEngineDialog by remember { mutableStateOf(false) }
    var showImageToolsDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showCreateArchiveDialog by remember { mutableStateOf<List<AndroidFileModel>?>(null) }
    var showNearbyShareDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showDriveBrowserDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showOptionsMenuDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showRenameFileDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showMoveFileDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showCopyFileDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var createFolderNameInput by remember { mutableStateOf("") }
    var createFileNameInput by remember { mutableStateOf("") }

    var showFileDetailsDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showOpenWithDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showApkInstallerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showTextEditorDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var textEditorContent by remember { mutableStateOf("") }

    // Text inputs for Rename, Move, Copy, PIN
    var renameInput by remember { mutableStateOf("") }
    var targetFolderPathInput by remember { mutableStateOf("") }
    var pinSetupInput by remember { mutableStateOf("") }
    var pinSetupConfirm by remember { mutableStateOf("") }
    var pinUnlockInput by remember { mutableStateOf("") }
    var pinActionInput by remember { mutableStateOf("") }

    // Cached PIN for active vault session (cleared on close)
    var cachedVaultPin by remember { mutableStateOf("") }
    // Reference to a decrypted vault file in private cache, to delete on closing the viewer
    var activeVaultPreviewFile by remember { mutableStateOf<File?>(null) }
    var isDecryptingVaultFile by remember { mutableStateOf(false) }
    var decryptingVaultFileName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Dedicated In-App Viewer Dialog States
    var showImageViewerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showVideoPlayerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showAudioPlayerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showPdfViewerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showHtmlViewerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showCsvViewerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showMarkdownViewerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showEpubReaderDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showExtractZipDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showOpenFallbackDialog by remember { mutableStateOf<AndroidFileModel?>(null) }

    // Custom NestedScroll Pull-to-Refresh implementation
    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && pullOffset > 0) {
                    val newOffset = (pullOffset + available.y).coerceAtLeast(0f)
                    val consumed = pullOffset - newOffset
                    pullOffset = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0) {
                    pullOffset = (pullOffset + available.y * 0.4f).coerceAtMost(300f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullOffset > 150f && !isRefreshing) {
                    isRefreshing = true
                    viewModel.loadDirectory(File(uiState.currentPath))
                    viewModel.loadStorageBreakdown()
                    isRefreshing = false
                }
                pullOffset = 0f
                return Velocity.Zero
            }
        }
    }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                viewModel.onGoogleSignInSuccess(account)
                Toast.makeText(context, "Signed in successfully as ${account.email}", Toast.LENGTH_LONG).show()
            } else {
                val errorMsg = "Sign-in failed: Account was null."
                viewModel.setDriveStatusMessage(errorMsg)
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            val errorMsg = when (e.statusCode) {
                10 -> "Sign-in failed (Code 10: Developer Error). Package Name '${context.packageName}' or SHA-1 missing/mismatched in Google Cloud Console."
                12500 -> "Sign-in failed (Code 12500: Sign In Failed). Ensure OAuth 2.0 Client ID and SHA-1 fingerprint are registered in Google Cloud Console."
                12501 -> "Sign-in cancelled by user."
                12502 -> "Sign-in currently in progress."
                7 -> "Network error during Google Sign-In. Check your internet connection."
                else -> "Google Sign-In Error (Code ${e.statusCode}): ${e.message}"
            }
            viewModel.setDriveStatusMessage(errorMsg)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            val errorMsg = "Sign-in error: ${e.localizedMessage ?: e.message}"
            viewModel.setDriveStatusMessage(errorMsg)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    fun startGoogleSignIn() {
        try {
            viewModel.driveManager.signOut {
                val signInClient = GoogleSignIn.getClient(context, viewModel.driveManager.getSignInOptions())
                googleSignInLauncher.launch(signInClient.signInIntent)
            }
        } catch (e: Exception) {
            val errorMsg = "Could not launch Google Sign-In: ${e.message}"
            viewModel.setDriveStatusMessage(errorMsg)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    // Real Intent Handlers
    fun launchOpenWithIntent(file: AndroidFileModel) {
        try {
            val fileObj = File(file.path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileObj)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, file.mimeType.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Open '${file.name}' with...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open ${file.name}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchApkInstallerIntent(apkFile: AndroidFileModel) {
        try {
            val fileObj = File(apkFile.path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileObj)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Package installer unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchShareIntent(file: AndroidFileModel) {
        try {
            val fileObj = File(file.path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileObj)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = file.mimeType.ifEmpty { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share '${file.name}' via...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleOpenFile(file: AndroidFileModel) {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg" -> showImageViewerDialog = file
            "mp4", "mkv", "avi", "mov", "webm" -> showVideoPlayerDialog = file
            "mp3", "wav", "m4a", "flac", "ogg", "aac" -> showAudioPlayerDialog = file
            "pdf" -> showPdfViewerDialog = file
            "html", "htm" -> showHtmlViewerDialog = file
            "csv" -> showCsvViewerDialog = file
            "md" -> showMarkdownViewerDialog = file
            "epub" -> showEpubReaderDialog = file
            "apk" -> showApkInstallerDialog = file
            "zip", "7z", "rar", "tar", "tar.gz", "tgz", "tar.bz2", "gz", "xz" -> showExtractZipDialog = file
            "txt", "json", "kt", "java", "xml", "js", "css", "ts", "py", "c", "cpp", "h" -> {
                val f = File(file.path)
                textEditorContent = if (f.exists() && f.canRead()) f.readText() else "Empty file"
                showTextEditorDialog = file
            }
            else -> showFileDetailsDialog = file
        }
    }

    // Dynamic Style Colors - Warm Cream & Liquid Glass Palette matching the app icon
    val isDark = uiState.isDarkMode
    // Custom theme color (user selectable, soft tones) - lightens in dark mode for visibility
    val customAccent = Color(uiState.themeColor)
    val appBlue = if (isDark) customAccent.copy(alpha = 1f).let {
        // Lighten the color for dark mode visibility
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()), hsv)
        hsv[2] = minOf(1f, hsv[2] + 0.25f) // Increase brightness
        hsv[1] = maxOf(0f, hsv[1] - 0.1f) // Slightly reduce saturation
        Color(android.graphics.Color.HSVToColor(hsv))
    } else customAccent
    val appGreen = if (isDark) Color(0xFF86A873) else Color(0xFF5E8B49) // Muted sage green
    val appRed = if (isDark) Color(0xFFD47366) else Color(0xFFB85347) // Soft terracotta red
    val appAmber = if (isDark) Color(0xFFDCA766) else Color(0xFFC68A40) // Warm honey amber
    val appIndigo = if (isDark) Color(0xFFA693B8) else Color(0xFF7E6B94) // Muted lavender taupe
    val appTeal = if (isDark) Color(0xFF7CAEA8) else Color(0xFF4C8780) // Muted eucalyptus
    val appPurple = if (isDark) Color(0xFFB58BA5) else Color(0xFF8F637E)

    // Warm cream background (#F7F3ED) matching the app icon; warm espresso charcoal in dark mode
    val bgColor = if (isDark) Color(0xFF191715) else Color(0xFFF7F3ED)
    // Glass card surface: translucent layered with glass border
    val cardColor = if (isDark) Color(0xFF24211D).copy(alpha = 0.88f) else Color(0xFFFFFFFF).copy(alpha = 0.90f)
    val cardSubtle = if (isDark) Color(0xFF2E2A25).copy(alpha = 0.82f) else Color(0xFFEFE8DD).copy(alpha = 0.85f)
    val textPrimary = if (isDark) Color(0xFFF5EFEB) else Color(0xFF2C2825)
    val textMuted = if (isDark) Color(0xFFA89F96) else Color(0xFF8C827A)
    // Delicate glass highlights: top light reflection with subtle borders
    val separatorColor = if (isDark) Color(0xFF38332D).copy(alpha = 0.7f) else Color(0xFFEADBCE).copy(alpha = 0.8f)

    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    val previewVaultFile: (AndroidFileModel) -> Unit = { vaultFile ->
        if (isDecryptingVaultFile) {
            // Already decrypting a file; ignore repeated taps
        } else if (cachedVaultPin.isNotBlank()) {
            isDecryptingVaultFile = true
            decryptingVaultFileName = vaultFile.name
            coroutineScope.launch {
                try {
                    val decryptedFile = viewModel.decryptVaultFileToCacheAsync(File(vaultFile.path), cachedVaultPin)
                    if (decryptedFile != null && decryptedFile.exists()) {
                        activeVaultPreviewFile = decryptedFile
                        val tempModel = AndroidFileModel(
                            id = decryptedFile.absolutePath,
                            name = decryptedFile.name,
                            path = decryptedFile.absolutePath,
                            size = decryptedFile.length(),
                            mimeType = vaultFile.mimeType,
                            dateModified = decryptedFile.lastModified(),
                            isDirectory = false
                        )
                        val ext = decryptedFile.extension.lowercase()
                        when (ext) {
                            "png", "jpg", "jpeg", "webp", "gif" -> showImageViewerDialog = tempModel
                            "mp4", "mkv" -> showVideoPlayerDialog = tempModel
                            "mp3", "wav", "m4a" -> showAudioPlayerDialog = tempModel
                            "pdf" -> showPdfViewerDialog = tempModel
                            "html", "htm" -> showHtmlViewerDialog = tempModel
                            "csv" -> showCsvViewerDialog = tempModel
                            "md" -> showMarkdownViewerDialog = tempModel
                            "epub" -> showEpubReaderDialog = tempModel
                            "apk" -> showApkInstallerDialog = tempModel
                            "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> {
                                launchOpenWithIntent(tempModel)
                            }
                            "txt", "json", "kt", "java", "xml", "js", "css" -> {
                                textEditorContent = withContext(Dispatchers.IO) {
                                    try { decryptedFile.readText() } catch (e: Exception) { "Could not load file: ${e.message}" }
                                }
                                showTextEditorDialog = tempModel
                            }
                            "zip", "rar", "7z", "tar", "tgz", "gz" -> {
                                showExtractZipDialog = tempModel
                            }
                            else -> {
                                showOpenFallbackDialog = tempModel
                            }
                        }
                    } else {
                        Toast.makeText(context, "Failed to decrypt preview: Incorrect PIN or corrupted file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Decryption error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isDecryptingVaultFile = false
                    decryptingVaultFileName = ""
                }
            }
        } else {
            Toast.makeText(context, "Session expired, please re-unlock Private Vault", Toast.LENGTH_SHORT).show()
        }
    }

    val onFileClick: (AndroidFileModel) -> Unit = { file ->
        val ext = file.name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "png", "jpg", "jpeg", "webp", "gif" -> showImageViewerDialog = file
            "mp4", "mkv" -> showVideoPlayerDialog = file
            "mp3", "wav", "m4a" -> showAudioPlayerDialog = file
            "pdf" -> showPdfViewerDialog = file
            "html", "htm" -> showHtmlViewerDialog = file
            "csv" -> showCsvViewerDialog = file
            "md" -> showMarkdownViewerDialog = file
            "epub" -> showEpubReaderDialog = file
            "zip", "rar", "7z", "tar", "tgz", "gz" -> showExtractZipDialog = file
            "apk" -> showApkInstallerDialog = file
            "txt", "json", "kt", "java", "xml", "js", "css" -> {
                val f = File(file.path)
                textEditorContent = if (f.exists() && f.canRead()) f.readText() else "Empty file"
                showTextEditorDialog = file
            }
            "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> {
                launchOpenWithIntent(file)
            }
            else -> {
                showOpenFallbackDialog = file
            }
        }
    }

    // Samsung Back Press Handling
    BackHandler(enabled = true) {
        if (uiState.activeCategory != null) {
            viewModel.setCategoryFilter(null)
            activeBottomNav = "Dashboard"
            return@BackHandler
        }
        if (activeBottomNav != "Dashboard") {
            activeBottomNav = "Dashboard"
            return@BackHandler
        }
        val currentPath = uiState.currentPath
        val parentFile = File(currentPath).parentFile

        if (currentPath != "/" && currentPath != rootPath && parentFile != null && parentFile.exists()) {
            viewModel.setCurrentPath(parentFile.absolutePath)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredFiles = remember(uiState.realFiles, uiState.categoryFiles, uiState.searchQuery, uiState.activeCategory, activeBottomNav, uiState.starredFiles) {
        val baseList = if (uiState.activeCategory != null) uiState.categoryFiles else uiState.realFiles
        baseList.filter { file ->
            if (uiState.searchQuery.isNotEmpty() && !file.name.contains(uiState.searchQuery, ignoreCase = true)) return@filter false
            if (activeBottomNav == "Starred" && !uiState.starredFiles.contains(file.path)) return@filter false
            true
        }
    }

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown"
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun isMediaFile(file: AndroidFileModel): Boolean {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return file.mimeType.startsWith("image/") ||
               file.mimeType.startsWith("video/") ||
               ext in setOf("png", "jpg", "jpeg", "webp", "gif", "mp4", "mkv", "avi", "mov")
    }

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            if (activeBottomNav == "Files" && uiState.activeCategory == null) {
                var fabExpanded by remember { mutableStateOf(false) }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (fabExpanded) {
                        // New File button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardColor,
                                shadowElevation = 4.dp,
                                border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                            ) {
                                Text(
                                    text = "New File",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            FloatingActionButton(
                                onClick = {
                                    createFileNameInput = ""
                                    showCreateFileDialog = true
                                    fabExpanded = false
                                },
                                containerColor = appIndigo,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp).bounceClick()
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = "New File", modifier = Modifier.size(20.dp))
                            }
                        }

                        // New Folder button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardColor,
                                shadowElevation = 4.dp,
                                border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                            ) {
                                Text(
                                    text = "New Folder",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            FloatingActionButton(
                                onClick = {
                                    createFolderNameInput = ""
                                    showCreateFolderDialog = true
                                    fabExpanded = false
                                },
                                containerColor = appGreen,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp).bounceClick()
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = appBlue,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp).bounceClick()
                    ) {
                        Icon(
                            imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Create Options",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // True Liquid Glass Floating Pill - Transparent with reflection (API 29 safe, no RenderEffect)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .shadow(
                        elevation = if (isDark) 8.dp else 12.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0xFF6B584D).copy(alpha = 0.08f),
                        spotColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFF6B584D).copy(alpha = 0.12f)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    // Transparent glass base - much lower alpha for true see-through
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = if (isDark) listOf(
                                Color(0xFF24211D).copy(alpha = 0.45f),
                                Color(0xFF24211D).copy(alpha = 0.55f)
                            ) else listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color(0xFFF7F3ED).copy(alpha = 0.55f)
                            )
                        )
                    )
                    // Glass reflection highlight at top
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.12f else 0.35f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 80f
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = if (isDark) listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color(0xFF38332D).copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.10f)
                            ) else listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color(0xFFEADBCE).copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.50f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val navItems = listOf(
                        Triple("Dashboard", "Dashboard", Icons.Outlined.SpaceDashboard),
                        Triple("Files", "My Files", Icons.Outlined.Folder),
                        Triple("Vault", "Vault", Icons.Outlined.Lock),
                        Triple("Drive", "Drive", Icons.Outlined.Cloud),
                        Triple("More", "More", Icons.Outlined.Menu)
                    )

                    navItems.forEach { (navKey, label, icon) ->
                        val isSelected = activeBottomNav == navKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        // Calculator-style liquid glass selection bubble
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                appBlue.copy(alpha = if (isDark) 0.28f else 0.22f),
                                                appBlue.copy(alpha = if (isDark) 0.12f else 0.08f)
                                            )
                                        )
                                    } else {
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Transparent)
                                        )
                                    }
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = if (isDark) 0.25f else 0.6f),
                                            appBlue.copy(alpha = 0.1f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .bounceClick(0.92f)
                                .clickable {
                                    activeBottomNav = navKey
                                    if (navKey == "Drive" && uiState.isDriveConnected) {
                                        showDriveBrowserDialog = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) appBlue else textMuted,
                                    modifier = Modifier.size(if (isSelected) 22.dp else 20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) appBlue else textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = activeBottomNav,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                     scaleIn(initialScale = 0.98f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(110))
                    )
                },
                modifier = Modifier.fillMaxSize(),
                label = "navTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    "Dashboard" -> {
                        // Samsung One UI Style Large Header & Dashboard with 28dp Round Cards
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Spacer(modifier = Modifier.height(44.dp))
                                Column {
                                    Text(
                                        text = "My Device",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "Everything in one clean place",
                                        fontSize = 13.sp,
                                        color = textMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // 1. Beautiful 28dp Storage Card with Pill Progress Bar
                            item {
                                val breakdown = uiState.storageBreakdown
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                ) {
                                    Column(modifier = Modifier.padding(22.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(appBlue.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Storage, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                                                }
                                                Text("Internal Storage", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                            if (breakdown != null) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = appBlue.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = "${((breakdown.usedSpaceBytes.toFloat() / breakdown.totalSpaceBytes) * 100).toInt()}% Used",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = appBlue,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        if (breakdown != null) {
                                            val usedFraction = breakdown.usedSpaceBytes.toFloat() / breakdown.totalSpaceBytes
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(10.dp)
                                                    .clip(CircleShape)
                                                    .background(separatorColor)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(usedFraction)
                                                        .background(
                                                            brush = Brush.horizontalGradient(
                                                                colors = listOf(appBlue, appIndigo)
                                                            )
                                                        )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${formatFileSize(breakdown.usedSpaceBytes)} of ${formatFileSize(breakdown.totalSpaceBytes)}",
                                                    fontSize = 12.sp,
                                                    color = textMuted,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "${formatFileSize(breakdown.freeSpaceBytes)} free",
                                                    fontSize = 12.sp,
                                                    color = appBlue,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            CircularProgressIndicator(color = appBlue, modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp), strokeWidth = 2.5.dp)
                                        }
                                    }
                                }
                            }

                            // 2. High-contrast iOS-style Grid Categories Card (28dp corners & circular icon badges)
                            item {
                                Text(
                                    text = "CATEGORIES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textMuted,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val categories = listOf(
                                    Triple("images", "Images", Icons.Outlined.Image),
                                    Triple("videos", "Videos", Icons.Outlined.VideoLibrary),
                                    Triple("audio", "Audio", Icons.Outlined.MusicNote),
                                    Triple("docs", "Documents", Icons.Outlined.Description),
                                    Triple("downloads", "Downloads", Icons.Outlined.Download),
                                    Triple("apks", "APKs", Icons.Outlined.PhoneAndroid),
                                    Triple("archives", "Archives", Icons.Outlined.FolderZip)
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        categories.chunked(4).forEach { rowList ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                rowList.forEach { (catKey, catTitle, icon) ->
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .bounceClick()
                                                            .clickable {
                                                                viewModel.setCategoryFilter(catKey)
                                                                activeBottomNav = "Files"
                                                            }
                                                            .padding(vertical = 12.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    when (catKey) {
                                                                        "images" -> appBlue.copy(alpha = 0.12f)
                                                                        "videos" -> Color(0xFF8B5CF6).copy(alpha = 0.12f)
                                                                        "audio" -> appGreen.copy(alpha = 0.12f)
                                                                        "docs" -> appIndigo.copy(alpha = 0.12f)
                                                                        "downloads" -> Color(0xFF06B6D4).copy(alpha = 0.12f)
                                                                        "apks" -> appAmber.copy(alpha = 0.12f)
                                                                        else -> appRed.copy(alpha = 0.12f)
                                                                    }
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = catTitle,
                                                                tint = when (catKey) {
                                                                    "images" -> appBlue
                                                                    "videos" -> Color(0xFF8B5CF6)
                                                                    "audio" -> appGreen
                                                                    "docs" -> appIndigo
                                                                    "downloads" -> Color(0xFF06B6D4)
                                                                    "apks" -> appAmber
                                                                    else -> appRed
                                                                },
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(catTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1)
                                                    }
                                                }
                                                if (rowList.size < 4) {
                                                    Spacer(modifier = Modifier.weight(4f - rowList.size))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Quick Actions Panel (26dp round cards)
                            item {
                                Text(
                                    text = "QUICK TOOLS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textMuted,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Analyzer Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .bounceClick()
                                                .clickable {
                                                    viewModel.loadStorageBreakdown()
                                                    showStorageAnalyzerDialog = true
                                                },
                                            shape = RoundedCornerShape(26.dp),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(appBlue.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.PieChart, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                                                }
                                                Text("Analyzer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }

                                        // Duplicate Finder Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .bounceClick()
                                                .clickable {
                                                    showDuplicateFinderDialog = true
                                                },
                                            shape = RoundedCornerShape(26.dp),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(appAmber.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = appAmber, modifier = Modifier.size(20.dp))
                                                }
                                                Text("Duplicates", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Large Files Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .bounceClick()
                                                .clickable {
                                                    viewModel.setCategoryFilter("large")
                                                    activeBottomNav = "Files"
                                                },
                                            shape = RoundedCornerShape(26.dp),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(appRed.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.FolderSpecial, contentDescription = null, tint = appRed, modifier = Modifier.size(20.dp))
                                                }
                                                Text("Large Files", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }

                                        // Recent Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .bounceClick()
                                                .clickable {
                                                    viewModel.setCategoryFilter("recent")
                                                    activeBottomNav = "Files"
                                                },
                                            shape = RoundedCornerShape(26.dp),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(appBlue.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                                                }
                                                Text("Recent", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // App Manager Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .bounceClick()
                                                .clickable {
                                                    viewModel.loadInstalledApps()
                                                    showAppManagerDialog = true
                                                },
                                            shape = RoundedCornerShape(26.dp),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(appIndigo.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Apps, contentDescription = null, tint = appIndigo, modifier = Modifier.size(20.dp))
                                                }
                                                Text("Apps", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }

                                        // Vault Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .bounceClick()
                                                .clickable {
                                                    activeBottomNav = "Vault"
                                                },
                                            shape = RoundedCornerShape(26.dp),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(appGreen.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = appGreen, modifier = Modifier.size(20.dp))
                                                }
                                                Text("Vault", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // WiFi Transfer Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .bounceClick()
                                                .clickable {
                                                    showWifiTransferDialog = true
                                                },
                                            shape = RoundedCornerShape(26.dp),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF10B981).copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Wifi, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                                }
                                                Text("WiFi Transfer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }

                                        // Network Storage Card
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .bounceClick()
                                                .clickable {
                                                    showNetworkStorageDialog = true
                                                },
                                            shape = RoundedCornerShape(26.dp),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(appBlue.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Dns, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                                                }
                                                Text("FTP / SMB", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }
                                    }

                                    // Backup & Restore Engine Card
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bounceClick()
                                            .clickable {
                                                showBackupEngineDialog = true
                                            },
                                        shape = RoundedCornerShape(26.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(appIndigo.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = appIndigo, modifier = Modifier.size(20.dp))
                                            }
                                            Column {
                                                Text("Backup & Restore", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Incremental snapshots to Drive, Local, or FTP", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Recent Files List (28dp card)
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RECENT FILES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textMuted,
                                        letterSpacing = 1.2.sp
                                    )
                                    TextButton(
                                        onClick = { activeBottomNav = "Files" },
                                        shape = CircleShape
                                    ) {
                                        Text("See All", fontSize = 12.sp, color = appBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                val recents = uiState.realFiles.take(4)
                                if (recents.isEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(28.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(28.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No recent files found", fontSize = 13.sp, color = textMuted, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(28.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            recents.forEachIndexed { index, file ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(18.dp))
                                                        .bounceClick()
                                                        .clickable {
                                                            val ext = file.name.substringAfterLast('.', "").lowercase()
                                                            when (ext) {
                                                                "png", "jpg", "jpeg", "webp", "gif" -> showImageViewerDialog = file
                                                                "mp4", "mkv" -> showVideoPlayerDialog = file
                                                                "mp3", "wav", "m4a" -> showAudioPlayerDialog = file
                                                                "pdf" -> showPdfViewerDialog = file
                                                                "html", "htm" -> showHtmlViewerDialog = file
                                                                "apk" -> showApkInstallerDialog = file
                                                                "txt", "json", "kt", "java", "xml", "js", "css", "md" -> {
                                                                    val f = File(file.path)
                                                                    textEditorContent = if (f.exists() && f.canRead()) f.readText() else "Empty file"
                                                                    showTextEditorDialog = file
                                                                }
                                                                else -> showFileDetailsDialog = file
                                                            }
                                                        }
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                                ) {
                                                    // Thumbnail Box with Circular container
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (file.name.endsWith(".pdf")) appRed.copy(alpha = 0.12f)
                                                                else if (file.name.endsWith(".apk")) appAmber.copy(alpha = 0.12f)
                                                                else appBlue.copy(alpha = 0.12f)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isMediaFile(file)) {
                                                            AsyncImage(
                                                                model = File(file.path),
                                                                contentDescription = file.name,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                                error = rememberVectorPainter(Icons.Default.InsertDriveFile),
                                                                placeholder = rememberVectorPainter(Icons.Default.Image)
                                                            )
                                                        } else {
                                                            Text(
                                                                text = file.name.substringAfterLast('.', "FILE").uppercase().take(3),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = if (file.name.endsWith(".pdf")) appRed else if (file.name.endsWith(".apk")) appAmber else appBlue
                                                            )
                                                        }
                                                    }

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = file.name,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = textPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${formatFileSize(file.size)} • ${formatDate(file.dateModified)}",
                                                            fontSize = 11.sp,
                                                            color = textMuted
                                                        )
                                                    }
                                                }
                                                if (index < recents.size - 1) {
                                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = separatorColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    "Files" -> {
                        // Standard Directory File Browser in elegant One UI theme with custom robust NestedScroll Pull-To-Refresh
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                            item {
                                Spacer(modifier = Modifier.height(48.dp))
                                if (uiState.activeCategory != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                viewModel.setCategoryFilter(null)
                                                activeBottomNav = "Dashboard"
                                            },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(cardColor)
                                                .bounceClick()
                                        ) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = appBlue, modifier = Modifier.size(20.dp))
                                        }
                                        Column {
                                            Text(
                                                text = uiState.activeCategory?.capitalize(Locale.ROOT) ?: "Category",
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = textPrimary,
                                                letterSpacing = (-0.5).sp
                                            )
                                            Text(
                                                text = "Recursive category view",
                                                fontSize = 13.sp,
                                                color = textMuted,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Explorer",
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = textPrimary,
                                                letterSpacing = (-0.5).sp
                                            )
                                            Text(
                                                text = "Manage your internal storage",
                                                fontSize = 13.sp,
                                                color = textMuted,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.toggleGridView() },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(cardColor)
                                                .bounceClick()
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                                contentDescription = "Toggle Grid/List",
                                                tint = appBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Sleek horizontal scrollable categories pill chip bar under big bold header
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val chips = listOf(
                                        Triple(null, "All Files", Icons.Default.AllInbox),
                                        Triple("images", "Images", Icons.Default.Image),
                                        Triple("docs", "Documents", Icons.Default.Description),
                                        Triple("audio", "Audio", Icons.Default.MusicNote),
                                        Triple("apks", "APKs", Icons.Default.PhoneAndroid),
                                        Triple("archives", "Archives", Icons.Default.FolderZip)
                                    )
                                    chips.forEach { (catKey, catTitle, icon) ->
                                        val isSelected = uiState.activeCategory == catKey
                                        val chipBg = if (isSelected) appBlue else cardColor
                                        val chipContentColor = if (isSelected) Color.White else textPrimary
                                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f))

                                        Surface(
                                            onClick = { viewModel.setCategoryFilter(catKey) },
                                            shape = CircleShape,
                                            color = chipBg,
                                            contentColor = chipContentColor,
                                            border = borderStroke,
                                            shadowElevation = if (isSelected) 3.dp else 1.dp,
                                            modifier = Modifier.bounceClick().animateContentSize()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else appBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = catTitle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Directory Path Bar (Pill Shaped)
                            if (uiState.activeCategory == null) {
                                item {
                                    val isAtRoot = uiState.currentPath == "/" || uiState.currentPath == rootPath
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!isAtRoot) {
                                                IconButton(
                                                    onClick = {
                                                        val parent = File(uiState.currentPath).parentFile
                                                        if (parent != null && parent.exists()) {
                                                            viewModel.setCurrentPath(parent.absolutePath)
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(cardSubtle).bounceClick()
                                                ) {
                                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = appBlue, modifier = Modifier.size(18.dp))
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(appBlue.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Folder, contentDescription = null, tint = appBlue, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))

                                            val displayPath = if (isAtRoot) "Internal Storage" else "Storage / " + uiState.currentPath.removePrefix(rootPath).trimStart('/')
                                            Text(
                                                text = displayPath,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Folders Selection Header & Tab Panel (Pill Shaped)
                            item {
                                if (uiState.activeCategory == null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = CircleShape,
                                        color = cardSubtle,
                                        border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.4f)) else null
                                    ) {
                                        Row(modifier = Modifier.padding(4.dp)) {
                                            listOf("Folders", "Recent").forEach { tab ->
                                                val isSelected = selectedTab == tab
                                                Surface(
                                                    onClick = { selectedTab = tab },
                                                    modifier = Modifier.weight(1f),
                                                    shape = CircleShape,
                                                    color = if (isSelected) cardColor else Color.Transparent,
                                                    shadowElevation = if (isSelected) 2.dp else 0.dp
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = tab,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) textPrimary else textMuted
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Subfolders Single Horizontal Scrollable Row (LazyRow)
                            if (selectedTab == "Folders" && uiState.activeCategory == null) {
                                item {
                                    if (uiState.realFolders.isNotEmpty()) {
                                        Text(
                                            text = "FOLDERS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textMuted,
                                            letterSpacing = 1.2.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(uiState.realFolders) { folder ->
                                                Card(
                                                    modifier = Modifier
                                                        .width(108.dp)
                                                        .height(86.dp)
                                                        .shadow(1.dp, RoundedCornerShape(22.dp))
                                                        .bounceClick()
                                                        .clickable { viewModel.setCurrentPath(folder.path) },
                                                    shape = RoundedCornerShape(22.dp),
                                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                                    border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(8.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(38.dp)
                                                                    .clip(CircleShape)
                                                                    .background(appBlue.copy(alpha = 0.12f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Folder,
                                                                    contentDescription = folder.name,
                                                                    tint = appBlue,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(5.dp))
                                                            Text(
                                                                text = folder.name,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = textPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(horizontal = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Scanned Files List Section
                            item {
                                Text(
                                    text = if (uiState.searchQuery.isNotEmpty()) "SEARCH RESULTS" else if (uiState.activeCategory != null) "CATEGORY: ${uiState.activeCategory?.uppercase()}" else "FILES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textMuted,
                                    letterSpacing = 1.2.sp
                                )
                            }

                            if (filteredFiles.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(28.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(CircleShape)
                                                        .background(cardSubtle),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = textMuted, modifier = Modifier.size(32.dp))
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("This directory is empty", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }
                                    }
                                }
                            } else if (uiState.isGridView) {
                                item {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.heightIn(max = 800.dp)
                                    ) {
                                        items(filteredFiles) { file ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(150.dp)
                                                    .bounceClick()
                                                    .clickable { onFileClick(file) },
                                                shape = RoundedCornerShape(24.dp),
                                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(12.dp),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(76.dp)
                                                            .clip(RoundedCornerShape(18.dp))
                                                            .background(
                                                                if (file.name.endsWith(".pdf")) appRed.copy(alpha = 0.12f)
                                                                else if (file.name.endsWith(".apk")) appAmber.copy(alpha = 0.12f)
                                                                else appBlue.copy(alpha = 0.12f)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isMediaFile(file)) {
                                                            AsyncImage(
                                                                model = File(file.path),
                                                                contentDescription = file.name,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                                                                error = rememberVectorPainter(Icons.Default.InsertDriveFile),
                                                                placeholder = rememberVectorPainter(Icons.Default.Image)
                                                            )
                                                        } else {
                                                            Text(
                                                                text = file.name.substringAfterLast('.', "FILE").uppercase().take(3),
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = if (file.name.endsWith(".pdf")) appRed else if (file.name.endsWith(".apk")) appAmber else appBlue
                                                            )
                                                        }
                                                    }

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = file.name,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = textPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(formatFileSize(file.size), fontSize = 10.sp, color = textMuted)
                                                        }

                                                        IconButton(
                                                            onClick = { showOptionsMenuDialog = file },
                                                            modifier = Modifier.size(28.dp).clip(CircleShape).bounceClick()
                                                        ) {
                                                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = textMuted, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(filteredFiles) { file ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(1.dp, RoundedCornerShape(24.dp))
                                            .bounceClick()
                                            .clickable { onFileClick(file) },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (file.name.endsWith(".pdf")) appRed.copy(alpha = 0.12f)
                                                        else if (file.name.endsWith(".apk")) appAmber.copy(alpha = 0.12f)
                                                        else appBlue.copy(alpha = 0.12f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isMediaFile(file)) {
                                                    AsyncImage(
                                                        model = File(file.path),
                                                        contentDescription = file.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                        error = rememberVectorPainter(Icons.Default.InsertDriveFile),
                                                        placeholder = rememberVectorPainter(Icons.Default.Image)
                                                    )
                                                } else {
                                                    Text(
                                                        text = file.name.substringAfterLast('.', "FILE").uppercase().take(3),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (file.name.endsWith(".pdf")) appRed else if (file.name.endsWith(".apk")) appAmber else appBlue
                                                    )
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatFileSize(file.size)} • ${formatDate(file.dateModified)}",
                                                    fontSize = 11.sp,
                                                    color = textMuted
                                                )
                                            }

                                            IconButton(
                                                onClick = { showOptionsMenuDialog = file },
                                                modifier = Modifier.size(36.dp).clip(CircleShape).bounceClick()
                                            ) {
                                                Icon(Icons.Default.MoreVert, contentDescription = null, tint = textMuted, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        // Pull-to-refresh Indicator Overlay (rotates and transitions beautifully)
                        if (pullOffset > 0f || isRefreshing) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = (16 + (pullOffset / 4)).dp)
                                    .size(40.dp)
                                    .shadow(4.dp, CircleShape)
                                    .background(cardColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = appBlue)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = appBlue,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer(rotationZ = pullOffset * 2.5f)
                                    )
                                }
                            }
                        }
                        }
                    }

                    "Vault" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (!viewModel.isVaultPinSet()) {
                                // PIN Setup Screen inside tab - Modern Minimalist Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(appBlue.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = appBlue, modifier = Modifier.size(40.dp))
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text("Set 4-Digit PIN", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Enter a 4-digit PIN to secure your Private Vault.", fontSize = 13.sp, color = textMuted, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(28.dp))
                                    
                                    OutlinedTextField(
                                        value = pinSetupInput,
                                        onValueChange = { if (it.length <= 4) pinSetupInput = it },
                                        label = { Text("Enter PIN") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(20.dp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedTextField(
                                        value = pinSetupConfirm,
                                        onValueChange = { if (it.length <= 4) pinSetupConfirm = it },
                                        label = { Text("Confirm PIN") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(20.dp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    )
                                    Spacer(modifier = Modifier.height(26.dp))
                                    Button(
                                        onClick = {
                                            if (pinSetupInput.length == 4 && pinSetupInput == pinSetupConfirm) {
                                                viewModel.saveVaultPin(pinSetupInput)
                                                cachedVaultPin = pinSetupInput
                                                pinSetupInput = ""
                                                pinSetupConfirm = ""
                                                viewModel.loadVaultFiles()
                                                Toast.makeText(context, "Vault PIN created successfully!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "PINs must match and be exactly 4 digits", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                                        shape = CircleShape,
                                        modifier = Modifier.fillMaxWidth(0.85f).height(50.dp).bounceClick()
                                    ) {
                                        Text("Save PIN & Unlock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            } else if (cachedVaultPin.isBlank()) {
                                // PIN Unlock Screen inside tab - Modern Minimalist Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(appBlue.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = appBlue, modifier = Modifier.size(40.dp))
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text("Private Vault", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Enter your 4-digit PIN to access encrypted files.", fontSize = 13.sp, color = textMuted, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(28.dp))
                                    
                                    OutlinedTextField(
                                        value = pinUnlockInput,
                                        onValueChange = { 
                                            if (it.length <= 4) {
                                                pinUnlockInput = it
                                                if (it.length == 4) {
                                                    if (viewModel.verifyVaultPin(it)) {
                                                        cachedVaultPin = it
                                                        pinUnlockInput = ""
                                                        viewModel.loadVaultFiles()
                                                    } else {
                                                        Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                                        pinUnlockInput = ""
                                                    }
                                                }
                                            }
                                        },
                                        label = { Text("Enter PIN") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(20.dp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    )
                                }
                            } else {
                                // Unlocked Full Screen Vault Screen
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(bgColor)
                                ) {
                                    // Elegant top bar with circular close button
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier.size(40.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Lock, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                                            }
                                            Text(
                                                text = "Private Vault",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = textPrimary,
                                                letterSpacing = (-0.5).sp
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                cachedVaultPin = ""
                                                activeBottomNav = "Dashboard"
                                                try {
                                                    File(context.cacheDir, "vault_previews").deleteRecursively()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(cardColor)
                                                .bounceClick()
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close Vault", tint = textMuted, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    Text(
                                        text = "AES-256-GCM encrypted files. Tapping any file previews it securely in-place.",
                                        fontSize = 13.sp,
                                        color = textMuted,
                                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)
                                    )

                                    // Content List
                                    if (uiState.vaultFiles.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier.size(72.dp).clip(CircleShape).background(cardSubtle),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = textMuted, modifier = Modifier.size(36.dp))
                                                }
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Text(
                                                    text = "No files in vault",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Files you encrypt will appear here safely.",
                                                    fontSize = 12.sp,
                                                    color = textMuted
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(uiState.vaultFiles) { vaultFile ->
                                                val isThisFileDecrypting = isDecryptingVaultFile && decryptingVaultFileName == vaultFile.name
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .bounceClick()
                                                        .clickable(enabled = !isDecryptingVaultFile) { previewVaultFile(vaultFile) },
                                                    shape = RoundedCornerShape(26.dp),
                                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                                    border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null,
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(14.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(46.dp)
                                                                    .clip(CircleShape)
                                                                    .background(appBlue.copy(alpha = 0.12f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                if (isThisFileDecrypting) {
                                                                    CircularProgressIndicator(
                                                                        modifier = Modifier.size(20.dp),
                                                                        color = appBlue,
                                                                        strokeWidth = 2.dp
                                                                    )
                                                                } else {
                                                                    Icon(Icons.Default.Key, contentDescription = null, tint = appBlue, modifier = Modifier.size(22.dp))
                                                                }
                                                            }
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = vaultFile.name,
                                                                    fontSize = 14.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = textPrimary,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = if (isThisFileDecrypting) "Decrypting..." else formatFileSize(vaultFile.size),
                                                                    fontSize = 11.sp,
                                                                    color = if (isThisFileDecrypting) appBlue else textMuted
                                                                )
                                                            }
                                                        }

                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Surface(
                                                                onClick = { showRestoreVaultFileDialog = vaultFile },
                                                                enabled = !isDecryptingVaultFile,
                                                                shape = CircleShape,
                                                                color = cardSubtle,
                                                                modifier = Modifier.bounceClick()
                                                            ) {
                                                                Text(
                                                                    text = "Restore",
                                                                    fontSize = 12.sp,
                                                                    color = if (isDecryptingVaultFile) textMuted else appBlue,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Drive" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp)
                        ) {
                            Spacer(modifier = Modifier.height(48.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Google Drive",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "Cloud files integration",
                                        fontSize = 13.sp,
                                        color = textMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (uiState.isDriveConnected) {
                                    Surface(
                                        onClick = {
                                            viewModel.signOutGoogleDrive {
                                                Toast.makeText(context, "Google Drive disconnected", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = CircleShape,
                                        color = appRed.copy(alpha = 0.12f),
                                        modifier = Modifier.bounceClick()
                                    ) {
                                        Text("Disconnect", color = appRed, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))

                            if (!uiState.isDriveConnected) {
                                // Signed Out State with Premium Round styling
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                                .background(appBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cloud,
                                                contentDescription = null,
                                                tint = appBlue,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(18.dp))
                                        Text(
                                            text = "Connect Google Drive",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "View and download your Drive files seamlessly, or backup local files directly to the secure cloud.",
                                            fontSize = 13.sp,
                                            color = textMuted,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))

                                        Button(
                                            onClick = { startGoogleSignIn() },
                                            colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .bounceClick()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Text(
                                                            text = "G",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = appBlue
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Connect to Google Drive",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }

                                        if (uiState.driveStatusMessage != null) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = uiState.driveStatusMessage ?: "",
                                                fontSize = 12.sp,
                                                color = appRed,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else if (uiState.isScanning) {
                                // Loading / Connecting Indicator
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        CircularProgressIndicator(color = appBlue, strokeWidth = 3.dp)
                                        Text(
                                            text = "Syncing Google Drive files...",
                                            fontSize = 14.sp,
                                            color = textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else if (uiState.driveStatusMessage != null) {
                                // Error State with Clean Retry Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier.size(64.dp).clip(CircleShape).background(appRed.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CloudOff,
                                                contentDescription = null,
                                                tint = appRed,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Connection Issue",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = uiState.driveStatusMessage ?: "An error occurred with Google Drive integration.",
                                            fontSize = 12.sp,
                                            color = textMuted,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Button(
                                            onClick = { viewModel.loadDriveFiles() },
                                            colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                                            shape = CircleShape,
                                            modifier = Modifier.bounceClick()
                                        ) {
                                            Text("Retry Connection", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                // Connected File Browser directly in the tab!
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Account: ${uiState.driveUserEmail}",
                                            fontSize = 12.sp,
                                            color = textMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = { viewModel.loadDriveFiles() },
                                            modifier = Modifier.size(34.dp).clip(CircleShape).background(cardColor).bounceClick()
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = appBlue, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    if (uiState.driveFiles.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(cardSubtle), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = textMuted, modifier = Modifier.size(32.dp))
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("No files found in Google Drive root", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(uiState.driveFiles) { driveFile ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth().bounceClick(),
                                                    shape = RoundedCornerShape(24.dp),
                                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                                    border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null,
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(14.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(46.dp)
                                                                    .clip(CircleShape)
                                                                    .background(appBlue.copy(alpha = 0.12f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (driveFile.isDirectory) Icons.Default.Folder else Icons.Default.Cloud,
                                                                    contentDescription = null,
                                                                    tint = appBlue,
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                            }
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = driveFile.name,
                                                                    fontSize = 14.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = textPrimary,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = if (driveFile.isDirectory) "Folder" else formatFileSize(driveFile.size),
                                                                    fontSize = 11.sp,
                                                                    color = textMuted
                                                                )
                                                            }
                                                        }

                                                        if (!driveFile.isDirectory) {
                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.downloadDriveFileToLocal(driveFile.id, driveFile.name) { success, msg ->
                                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                                    }
                                                                },
                                                                modifier = Modifier.size(38.dp).clip(CircleShape).background(cardSubtle).bounceClick()
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = "Download to local Downloads folder", tint = appBlue, modifier = Modifier.size(20.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "More" -> {
                        // Premium minimalist settings list with 28dp card container
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp)
                        ) {
                            Spacer(modifier = Modifier.height(48.dp))
                            Text(
                                text = "Settings",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Personalize files manager",
                                fontSize = 13.sp,
                                color = textMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                border = if (isDark) BorderStroke(1.dp, separatorColor.copy(alpha = 0.5f)) else null,
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Dark Mode Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { viewModel.toggleDarkMode() }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isDark) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay,
                                                    contentDescription = null,
                                                    tint = appBlue,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column {
                                                Text("Dark Mode", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Toggle complete dark theme", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        Switch(
                                            checked = isDark,
                                            onCheckedChange = { viewModel.toggleDarkMode() },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = appBlue)
                                        )
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = separatorColor)

                                    // Theme Color Row - Soft tone color picker
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { showThemeColorDialog = true }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(appBlue),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Palette,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column {
                                                Text("Theme Color", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Soft pastel accent colors", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        // Current color preview
                                        Box(
                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(appBlue)
                                                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                        )
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = separatorColor)

                                    // Duplicate Finder Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                showDuplicateFinderDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(appAmber.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = appAmber, modifier = Modifier.size(22.dp))
                                            }
                                            Column {
                                                Text("Duplicate Finder", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Scan & remove hash-identical files", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = separatorColor)

                                    // App Manager Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                viewModel.loadInstalledApps()
                                                showAppManagerDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(appIndigo.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.Apps, contentDescription = null, tint = appIndigo, modifier = Modifier.size(22.dp))
                                            }
                                            Column {
                                                Text("App Manager", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Export APKs, view versions & details", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = separatorColor)

                                    // Storage Analyzer Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                viewModel.loadStorageBreakdown()
                                                showStorageAnalyzerDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(appIndigo.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.PieChart, contentDescription = null, tint = appIndigo, modifier = Modifier.size(22.dp))
                                            }
                                            Column {
                                                Text("Storage Analyzer", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Deep scan space breakdown", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = separatorColor)

                                     // Trash Bin Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                viewModel.loadTrashItems()
                                                showTrashDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(appRed.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = null, tint = appRed, modifier = Modifier.size(22.dp))
                                            }
                                            Column {
                                                Text("Trash Bin Manager", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Restore soft-deleted files", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = separatorColor)

                                    // WiFi Transfer Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                showWifiTransferDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF10B981).copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.Wifi, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                                            }
                                            Column {
                                                Text("WiFi File Transfer", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Local HTTP server with QR code", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = separatorColor)

                                    // FTP / SMB Network Storage Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                showNetworkStorageDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.Dns, contentDescription = null, tint = appBlue, modifier = Modifier.size(22.dp))
                                            }
                                            Column {
                                                Text("Network Storage (FTP / SMB)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Connect remote NAS & servers", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = separatorColor)

                                    // Backup & Restore Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                showBackupEngineDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(
                                                modifier = Modifier.size(42.dp).clip(CircleShape).background(appIndigo.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = appIndigo, modifier = Modifier.size(22.dp))
                                            }
                                            Column {
                                                Text("Backup & Restore Engine", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Incremental backups to cloud, local & FTP", fontSize = 11.sp, color = textMuted)
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val dismissAndWipe: () -> Unit = {
        showImageViewerDialog = null
        showVideoPlayerDialog = null
        showAudioPlayerDialog = null
        showPdfViewerDialog = null
        showHtmlViewerDialog = null
        showCsvViewerDialog = null
        showMarkdownViewerDialog = null
        showEpubReaderDialog = null
        showExtractZipDialog = null
        showTextEditorDialog = null
        showOpenFallbackDialog = null
        activeVaultPreviewFile?.let { temp ->
            try {
                if (temp.exists()) {
                    temp.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activeVaultPreviewFile = null
        }
    }

    // Vault File Decryption Loading Indicator Overlay
    if (isDecryptingVaultFile) {
        Dialog(
            onDismissRequest = { /* Prevent dismiss during active decryption */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = appBlue,
                        strokeWidth = 3.5.dp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Decrypting...",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    if (decryptingVaultFileName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = decryptingVaultFileName,
                            fontSize = 12.sp,
                            color = textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // In-App Viewers Integration
    showImageViewerDialog?.let { file ->
        ImageViewerDialog(
            file = file,
            onDismiss = dismissAndWipe,
            onOpenImageTools = {
                val target = file
                dismissAndWipe()
                showImageToolsDialog = target
            }
        )
    }

    showVideoPlayerDialog?.let { file ->
        VideoPlayerDialog(file = file, onDismiss = dismissAndWipe)
    }

    showAudioPlayerDialog?.let { file ->
        AudioPlayerDialog(file = file, onDismiss = dismissAndWipe)
    }

    showPdfViewerDialog?.let { file ->
        PdfViewerDialog(file = file, onDismiss = dismissAndWipe)
    }

    showHtmlViewerDialog?.let { file ->
        HtmlViewerDialog(file = file, onDismiss = dismissAndWipe)
    }

    showCsvViewerDialog?.let { file ->
        CsvViewerDialog(file = file, onDismiss = dismissAndWipe)
    }

    showMarkdownViewerDialog?.let { file ->
        MarkdownViewerDialog(file = file, onDismiss = dismissAndWipe)
    }

    showEpubReaderDialog?.let { file ->
        EpubReaderDialog(file = file, onDismiss = dismissAndWipe)
    }

    showExtractZipDialog?.let { file ->
        ArchiveBrowserDialog(
            file = file,
            onDismiss = dismissAndWipe,
            onExtractAll = {
                val targetDir = File(uiState.currentPath)
                viewModel.extractZipArchive(File(file.path), targetDir)
                Toast.makeText(context, "Extracting ${file.name} to current directory...", Toast.LENGTH_SHORT).show()
                dismissAndWipe()
            }
        )
    }

    showOpenFallbackDialog?.let { file ->
        AlertDialog(
            onDismissRequest = dismissAndWipe,
            title = { Text("Open Fallback Chooser", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Could not automatically recognize the format for '${file.name}'. How would you like to handle it?", fontSize = 12.sp, color = textPrimary)
                    Text("Select one of the following actions:", fontSize = 11.sp, color = textMuted)
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val f = File(file.path)
                            textEditorContent = if (f.exists() && f.canRead()) f.readText() else "Empty file"
                            showTextEditorDialog = file
                            showOpenFallbackDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Preview as Text / Edit", color = Color.White)
                    }
                    Button(
                        onClick = {
                            launchOpenWithIntent(file)
                            dismissAndWipe()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = appIndigo),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open with System Chooser", color = Color.White)
                    }
                    TextButton(
                        onClick = dismissAndWipe,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = textMuted)
                    }
                }
            }
        )
    }

    // 1. Rename Dialog (iOS Style)
    showRenameFileDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showRenameFileDialog = null },
            title = { Text("Rename File", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameFileInPlace(File(file.path), renameInput.trim()) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) showRenameFileDialog = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Rename", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFileDialog = null }) { Text("Cancel", color = textMuted) }
            }
        )
    }

    // 2. Move File Dialog
    showMoveFileDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showMoveFileDialog = null },
            title = { Text("Move File", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter target folder path:", fontSize = 12.sp, color = textMuted)
                    OutlinedTextField(
                        value = targetFolderPathInput,
                        onValueChange = { targetFolderPathInput = it },
                        label = { Text("Target Path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetFolderPathInput.isNotBlank()) {
                            viewModel.moveFileInPlace(File(file.path), File(targetFolderPathInput.trim())) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) showMoveFileDialog = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Move", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveFileDialog = null }) { Text("Cancel", color = textMuted) }
            }
        )
    }

    // 3. Copy File Dialog
    showCopyFileDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showCopyFileDialog = null },
            title = { Text("Copy File", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter target folder path to copy file:", fontSize = 12.sp, color = textMuted)
                    OutlinedTextField(
                        value = targetFolderPathInput,
                        onValueChange = { targetFolderPathInput = it },
                        label = { Text("Target Path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetFolderPathInput.isNotBlank()) {
                            viewModel.copyFileInPlace(File(file.path), File(targetFolderPathInput.trim())) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) showCopyFileDialog = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Copy", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyFileDialog = null }) { Text("Cancel", color = textMuted) }
            }
        )
    }

    // Options Menu Dialog with Per-File Actions (Details, Open With, Share, Rename, Move, Copy, Vault, Drive, Star, Delete)
    showOptionsMenuDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showOptionsMenuDialog = null },
            title = { Text(file.name, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            showFileDetailsDialog = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("File Details & Properties", color = textPrimary)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            showChecksumDialog = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calculate Checksum (MD5/SHA)", color = textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    val isImageFile = file.name.endsWith(".jpg", true) ||
                            file.name.endsWith(".jpeg", true) ||
                            file.name.endsWith(".png", true) ||
                            file.name.endsWith(".webp", true)

                    if (isImageFile) {
                        TextButton(
                            onClick = {
                                val target = file
                                showOptionsMenuDialog = null
                                showImageToolsDialog = target
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PhotoFilter, contentDescription = null, tint = appBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Image Tools (Compress / Resize / Convert)", color = textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            launchOpenWithIntent(file)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open With System App...", color = textPrimary)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            launchShareIntent(file)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Share, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share File...", color = textPrimary)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            renameInput = file.name
                            showRenameFileDialog = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rename File", color = textPrimary)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            targetFolderPathInput = File(file.path).parent ?: Environment.getExternalStorageDirectory().absolutePath
                            showMoveFileDialog = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FolderZip, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Move File", color = textPrimary)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            targetFolderPathInput = File(file.path).parent ?: Environment.getExternalStorageDirectory().absolutePath
                            showCopyFileDialog = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy File", color = textPrimary)
                        }
                    }

                    // Compress to ZIP / 7Z
                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            showCreateArchiveDialog = listOf(file)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FolderZip, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compress (ZIP / 7-Zip)...", color = textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Nearby Share
                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            showNearbyShareDialog = file
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Devices, contentDescription = null, tint = appBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nearby Share...", color = textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    val isArchiveFile = file.name.endsWith(".zip", true) ||
                            file.name.endsWith(".7z", true) ||
                            file.name.endsWith(".rar", true) ||
                            file.name.endsWith(".tar", true) ||
                            file.name.endsWith(".tar.gz", true) ||
                            file.name.endsWith(".tgz", true) ||
                            file.name.endsWith(".gz", true) ||
                            file.name.endsWith(".xz", true)

                    if (isArchiveFile) {
                        TextButton(
                            onClick = {
                                showOptionsMenuDialog = null
                                val currentFolder = File(file.path).parentFile ?: File(uiState.currentPath)
                                viewModel.extractZipArchive(File(file.path), currentFolder) { success ->
                                    Toast.makeText(context, if (success) "Extracted '${file.name}'" else "Extraction failed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Unarchive, contentDescription = null, tint = appBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extract Here", color = textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        TextButton(
                            onClick = {
                                showOptionsMenuDialog = null
                                val baseFolderName = file.name.substringBeforeLast('.')
                                val targetSubfolder = File(File(file.path).parentFile ?: File(uiState.currentPath), baseFolderName)
                                viewModel.extractZipArchive(File(file.path), targetSubfolder) { success ->
                                    Toast.makeText(context, if (success) "Extracted to folder '$baseFolderName'" else "Extraction failed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = appBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extract to Folder...", color = textPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            if (!viewModel.isVaultPinSet()) {
                                showPinSetupDialog = true
                            } else {
                                showMoveToVaultDialog = file
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = appAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Move to Private Vault (AES-256)", color = appAmber, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            viewModel.uploadLocalFileToDrive(File(file.path)) { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = appIndigo)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload to Google Drive", color = appIndigo, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            viewModel.toggleStar(file.path)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (uiState.starredFiles.contains(file.path)) Icons.Default.Star else Icons.Outlined.StarOutline, contentDescription = null, tint = appAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.starredFiles.contains(file.path)) "Unstar File" else "Star File", color = textPrimary)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptionsMenuDialog = null
                            viewModel.moveToTrash(File(file.path))
                            Toast.makeText(context, "Moved ${file.name} to Trash", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = appRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete (Move to Trash)", color = appRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptionsMenuDialog = null }) {
                    Text("Close", color = textMuted)
                }
            }
        )
    }

    var trashItemToDeletePermanently by remember { mutableStateOf<TrashItemModel?>(null) }
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }
    var showDriveCreateFolderDialog by remember { mutableStateOf(false) }
    var driveFileToRename by remember { mutableStateOf<AndroidFileModel?>(null) }
    var driveFileToDelete by remember { mutableStateOf<AndroidFileModel?>(null) }
    var driveFileDetails by remember { mutableStateOf<AndroidFileModel?>(null) }

    // Enhanced Trash Bin Dialog with Full Safe Deletion Confirmations & Auto-Cleanup Config
    if (showTrashDialog) {
        AlertDialog(
            onDismissRequest = { showTrashDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = appRed)
                        Text("Trash Bin", fontWeight = FontWeight.Bold, color = textPrimary)
                    }

                    if (uiState.trashItems.isNotEmpty()) {
                        TextButton(
                            onClick = { showEmptyTrashConfirmDialog = true }
                        ) {
                            Text("Empty All", color = appRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    // Auto-cleanup setting selector
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardSubtle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-cleanup:", fontSize = 11.sp, color = textMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(7, 30, 60, -1).forEach { days ->
                                    val isSelected = uiState.autoCleanTrashDays == days
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) appBlue else Color.Transparent,
                                        modifier = Modifier.clickable { viewModel.setAutoCleanTrashDays(days) }
                                    ) {
                                        Text(
                                            text = if (days == -1) "Off" else "${days}d",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else textMuted,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (uiState.trashItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = textMuted, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Trash is empty", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textMuted)
                                Text("Deleted files will be retained safely here", fontSize = 11.sp, color = textMuted)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.trashItems) { trashItem ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(trashItem.originalName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("From: ${trashItem.originalParentPath}", fontSize = 10.sp, color = textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${formatDate(trashItem.trashedAtTimestamp)} • ${formatFileSize(trashItem.size)} • ${trashItem.mimeType.substringAfter('/')}", fontSize = 10.sp, color = appBlue)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.restoreFromTrash(trashItem.trashedFile) { success, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(34.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.1f)).bounceClick()
                                            ) {
                                                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = appBlue, modifier = Modifier.size(18.dp))
                                            }

                                            IconButton(
                                                onClick = { trashItemToDeletePermanently = trashItem },
                                                modifier = Modifier.size(34.dp).clip(CircleShape).background(appRed.copy(alpha = 0.1f)).bounceClick()
                                            ) {
                                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanently", tint = appRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showTrashDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = appBlue), shape = CircleShape) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // Permanent delete confirmation dialog for individual trash item
    trashItemToDeletePermanently?.let { item ->
        AlertDialog(
            onDismissRequest = { trashItemToDeletePermanently = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete Permanently?", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Text("Are you sure you want to permanently delete '${item.originalName}'? This action cannot be undone.", fontSize = 13.sp, color = textMuted)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = item.trashedFile
                        trashItemToDeletePermanently = null
                        viewModel.deletePermanentlyFromTrash(target) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appRed),
                    shape = CircleShape
                ) {
                    Text("Delete Forever", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { trashItemToDeletePermanently = null }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }

    // Empty entire trash confirmation dialog
    if (showEmptyTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Empty Entire Trash Bin?", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Text("This will permanently delete all ${uiState.trashItems.size} items from the Trash Bin. This action cannot be recovered.", fontSize = 13.sp, color = textMuted)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashConfirmDialog = false
                        viewModel.emptyTrash { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appRed),
                    shape = CircleShape
                ) {
                    Text("Empty Everything", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmDialog = false }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }

    // Complete Google Drive Browser Dialog with Navigation, Search, Folders, & Actions
    if (showDriveBrowserDialog) {
        var driveSearchText by remember { mutableStateOf("") }
        var isSearchActive by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { showDriveBrowserDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isDark) Color(0xFF191715) else Color(0xFFF7F3ED)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().systemBarsPadding()
                ) {
                    // Drive Header
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = cardColor,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconButton(
                                    onClick = { showDriveBrowserDialog = false },
                                    modifier = Modifier.size(38.dp).clip(CircleShape).background(cardSubtle)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = textPrimary)
                                }
                                Column {
                                    Text("Google Drive", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Text(uiState.driveUserEmail ?: "Cloud Storage", fontSize = 11.sp, color = textMuted)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = { showDriveCreateFolderDialog = true },
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(cardSubtle).bounceClick()
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = appBlue, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { isSearchActive = !isSearchActive },
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(cardSubtle).bounceClick()
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = appBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Breadcrumb navigation & Search bar
                    if (isSearchActive) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = cardColor
                        ) {
                            OutlinedTextField(
                                value = driveSearchText,
                                onValueChange = {
                                    driveSearchText = it
                                    viewModel.searchDriveFiles(it)
                                },
                                placeholder = { Text("Search files in Drive...", fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    if (driveSearchText.isNotEmpty()) {
                                        IconButton(onClick = {
                                            driveSearchText = ""
                                            viewModel.searchDriveFiles("")
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                        }
                    } else {
                        // Breadcrumbs bar
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = cardSubtle
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.driveBreadcrumbs.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.navigateDriveBack() },
                                        modifier = Modifier.size(28.dp).clip(CircleShape).background(cardColor)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = textPrimary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = uiState.driveBreadcrumbs.joinToString(" / ") { it.second },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // File List
                    if (uiState.isScanning) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = appBlue)
                        }
                    } else if (uiState.driveFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = textMuted, modifier = Modifier.size(44.dp))
                                Text("No files found in this Drive folder", fontSize = 13.sp, color = textMuted)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.driveFiles, key = { it.id }) { driveFile ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (driveFile.isDirectory) {
                                                viewModel.navigateDriveFolder(driveFile.id, driveFile.name)
                                            } else {
                                                // Tap file = instant preview (like real Google Drive)
                                                Toast.makeText(context, "Loading preview...", Toast.LENGTH_SHORT).show()
                                                viewModel.previewDriveFile(driveFile.id, driveFile.name) { localFile ->
                                                    if (localFile != null) {
                                                        try {
                                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                                context,
                                                                "com.example.filesapp.fileprovider",
                                                                localFile
                                                            )
                                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Cannot preview: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Preview failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (driveFile.isDirectory) appBlue.copy(alpha = 0.12f) else cardSubtle),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (driveFile.isDirectory) Icons.Default.Folder else Icons.Outlined.InsertDriveFile,
                                                    contentDescription = null,
                                                    tint = if (driveFile.isDirectory) appBlue else textMuted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column {
                                                Text(driveFile.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(if (driveFile.isDirectory) "Folder" else "${formatFileSize(driveFile.size)} • ${formatDate(driveFile.dateModified)}", fontSize = 11.sp, color = textMuted)
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (!driveFile.isDirectory) {
                                                // Preview button
                                                IconButton(
                                                    onClick = {
                                                        Toast.makeText(context, "Loading preview...", Toast.LENGTH_SHORT).show()
                                                        viewModel.previewDriveFile(driveFile.id, driveFile.name) { localFile ->
                                                            if (localFile != null) {
                                                                try {
                                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                                        context,
                                                                        "com.example.filesapp.fileprovider",
                                                                        localFile
                                                                    )
                                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                                        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                    }
                                                                    context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, "Cannot preview: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            } else {
                                                                Toast.makeText(context, "Preview failed", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.1f)).bounceClick()
                                                ) {
                                                    Icon(Icons.Outlined.Visibility, contentDescription = "Preview", tint = appBlue, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.downloadDriveFileToLocal(driveFile.id, driveFile.name) { success, msg ->
                                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.1f)).bounceClick()
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = "Download", tint = appBlue, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            IconButton(
                                                onClick = { driveFileToRename = driveFile },
                                                modifier = Modifier.size(34.dp).clip(CircleShape).background(cardSubtle).bounceClick()
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = textMuted, modifier = Modifier.size(16.dp))
                                            }

                                            IconButton(
                                                onClick = { driveFileToDelete = driveFile },
                                                modifier = Modifier.size(34.dp).clip(CircleShape).background(appRed.copy(alpha = 0.1f)).bounceClick()
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = appRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Drive Folder Dialog
    if (showDriveCreateFolderDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDriveCreateFolderDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("New Drive Folder", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            viewModel.createDriveFolder(folderNameInput.trim()) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            showDriveCreateFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                    shape = CircleShape
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDriveCreateFolderDialog = false }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }

    // Rename Drive File Dialog
    driveFileToRename?.let { driveFile ->
        var renameInput by remember { mutableStateOf(driveFile.name) }
        AlertDialog(
            onDismissRequest = { driveFileToRename = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Rename in Drive", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            val id = driveFile.id
                            driveFileToRename = null
                            viewModel.renameDriveFile(id, renameInput.trim()) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                    shape = CircleShape
                ) {
                    Text("Rename", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { driveFileToRename = null }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }

    // Delete Drive File Confirmation Dialog
    driveFileToDelete?.let { driveFile ->
        AlertDialog(
            onDismissRequest = { driveFileToDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete from Drive?", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Text("Are you sure you want to delete '${driveFile.name}' from Google Drive?", fontSize = 13.sp, color = textMuted)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = driveFile.id
                        driveFileToDelete = null
                        viewModel.deleteDriveFile(id) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appRed),
                    shape = CircleShape
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { driveFileToDelete = null }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }

    if (showPinSetupDialog) {
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text("Set Vault 4-Digit PIN", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a 4-digit PIN to secure your Private Vault. Only a SHA-256 hash is saved.", fontSize = 12.sp, color = textMuted)
                    OutlinedTextField(
                        value = pinSetupInput,
                        onValueChange = { if (it.length <= 4) pinSetupInput = it },
                        label = { Text("Enter PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinSetupConfirm,
                        onValueChange = { if (it.length <= 4) pinSetupConfirm = it },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinSetupInput.length == 4 && pinSetupInput == pinSetupConfirm) {
                            viewModel.saveVaultPin(pinSetupInput)
                            cachedVaultPin = pinSetupInput
                            showPinSetupDialog = false
                            pinSetupInput = ""
                            pinSetupConfirm = ""
                            viewModel.loadVaultFiles()
                            showVaultBrowserDialog = true
                            Toast.makeText(context, "Vault PIN created successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "PINs must match and be 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Save PIN & Unlock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) { Text("Cancel", color = textMuted) }
            }
        )
    }



    showMoveToVaultDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showMoveToVaultDialog = null },
            title = { Text("Move to Private Vault", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Encrypt '${file.name}' with AES-256-GCM and hide it from all other apps.", fontSize = 12.sp, color = textMuted)
                    OutlinedTextField(
                        value = pinActionInput,
                        onValueChange = { if (it.length <= 4) pinActionInput = it },
                        label = { Text("Enter Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.lockFileInVault(File(file.path), pinActionInput) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                showMoveToVaultDialog = null
                                pinActionInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Encrypt & Move", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveToVaultDialog = null }) { Text("Cancel", color = textMuted) }
            }
        )
    }



    showRestoreVaultFileDialog?.let { vaultFile ->
        AlertDialog(
            onDismissRequest = { showRestoreVaultFileDialog = null },
            title = { Text("Decrypt & Restore File", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Decrypt '${vaultFile.name}' and restore it to your storage folder?", fontSize = 12.sp, color = textMuted)
                    OutlinedTextField(
                        value = pinActionInput,
                        onValueChange = { if (it.length <= 4) pinActionInput = it },
                        label = { Text("Enter Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.decryptAndRestoreVaultFile(File(vaultFile.path), pinActionInput) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                showRestoreVaultFileDialog = null
                                pinActionInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Decrypt File", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreVaultFileDialog = null }) { Text("Cancel", color = textMuted) }
            }
        )
    }

    // Real Computed Storage Analyzer Dialog (Google Files Style with Overview, Largest Files, and Empty Folders)
    if (showStorageAnalyzerDialog) {
        GoogleFilesStorageAnalyzerDialog(
            viewModel = viewModel,
            onDismiss = { showStorageAnalyzerDialog = false },
            onNavigateCategory = { catId ->
                viewModel.setCategoryFilter(catId)
                activeBottomNav = "Files"
            },
            onOpenFile = { file ->
                handleOpenFile(file)
            },
            onOpenDuplicates = {
                showDuplicateFinderDialog = true
            }
        )
    }

    // Nearby Share Dialog (Google Files parity)
    showNearbyShareDialog?.let { file ->
        NearbyShareDialog(
            viewModel = viewModel,
            fileToShare = File(file.path).takeIf { it.exists() },
            onDismiss = { showNearbyShareDialog = null }
        )
    }

    // Create Archive Dialog (ZIP / 7Z with Levels)
    showCreateArchiveDialog?.let { filesToCompress ->
        val fileObjs = filesToCompress.map { File(it.path) }
        val targetDir = File(uiState.currentPath)
        val defaultName = if (filesToCompress.size == 1) filesToCompress[0].name.substringBeforeLast('.') else "Archive"
        CreateArchiveDialog(
            selectedFiles = fileObjs,
            defaultName = defaultName,
            targetDirectory = targetDir,
            onDismiss = { showCreateArchiveDialog = null },
            onCreateArchive = { format, name, level, password ->
                if (format == com.example.filesapp.domain.archive.ArchiveFormat.SEVEN_Z) {
                    viewModel.create7zArchive(fileObjs, name, targetDir, level) { success ->
                        Toast.makeText(context, if (success) "Created '$name.7z'" else "Failed to create 7Z archive", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.createZipArchive(fileObjs, name, targetDir, level, password) { success ->
                        val msg = if (success) "Created '$name.zip'${if (password != null) " (encrypted)" else ""}"
                                  else "Failed to create ZIP archive"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                showCreateArchiveDialog = null
            }
        )
    }

    // Real Package Installer Dialog
    showApkInstallerDialog?.let { apkFile ->
        AlertDialog(
            onDismissRequest = { showApkInstallerDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = appBlue)
                    Text("Install Package", fontWeight = FontWeight.Bold, color = textPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Do you want to install '${apkFile.name}' (${formatFileSize(apkFile.size)}) onto your device?", fontSize = 12.sp, color = textPrimary)
                    Text("This launches the Android system package installer.", fontSize = 11.sp, color = textMuted)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        launchApkInstallerIntent(apkFile)
                        showApkInstallerDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Install APK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApkInstallerDialog = null }) { Text("Cancel", color = textMuted) }
            }
        )
    }

    showFileDetailsDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showFileDetailsDialog = null },
            title = { Text("File Details & Properties", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${file.name}", fontSize = 13.sp, color = textPrimary)
                    Text("Path: ${file.path}", fontSize = 11.sp, color = textMuted)
                    Text("Size: ${formatFileSize(file.size)} (${file.size} bytes)", fontSize = 13.sp, color = textPrimary)
                    Text("MIME: ${file.mimeType}", fontSize = 13.sp, color = textPrimary)
                    Text("Modified: ${formatDate(file.dateModified)}", fontSize = 13.sp, color = textPrimary)

                    if (!file.isDirectory) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                val target = file
                                showFileDetailsDialog = null
                                showChecksumDialog = target
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = appBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calculate Checksum (MD5 / SHA)", color = appBlue, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFileDetailsDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = appBlue)) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    // Duplicate Finder Full-Screen Modal Dialog
    if (showDuplicateFinderDialog) {
        DuplicateFinderDialog(
            viewModel = viewModel,
            onDismiss = { showDuplicateFinderDialog = false }
        )
    }

    // App Manager Full-Screen Modal Dialog
    if (showAppManagerDialog) {
        AppManagerDialog(
            viewModel = viewModel,
            onDismiss = { showAppManagerDialog = false }
        )
    }

    // File Checksum Dialog
    showChecksumDialog?.let { file ->
        FileChecksumDialog(
            file = file,
            viewModel = viewModel,
            onDismiss = { showChecksumDialog = null }
        )
    }

    // WiFi File Transfer Dialog
    if (showWifiTransferDialog) {
        WifiTransferDialog(
            viewModel = viewModel,
            onDismiss = { showWifiTransferDialog = false }
        )
    }

    // Image Tools (Compress / Resize / Convert) Dialog
    showImageToolsDialog?.let { file ->
        ImageToolsDialog(
            file = file,
            viewModel = viewModel,
            onDismiss = { showImageToolsDialog = null }
        )
    }

    // FTP / SMB Network Storage Dialog
    if (showNetworkStorageDialog) {
        NetworkStorageDialog(
            viewModel = viewModel,
            onDismiss = { showNetworkStorageDialog = false }
        )
    }

    // Backup & Restore Engine Dialog
    if (showBackupEngineDialog) {
        BackupEngineDialog(
            viewModel = viewModel,
            onDismiss = { showBackupEngineDialog = false }
        )
    }

    // Real-Time File Transfer Progress Dialog (Copy / Move)
    uiState.activeTransfer?.let { transfer ->
        TransferProgressDialog(
            progress = transfer,
            onCancel = {
                viewModel.cancelCurrentTransfer()
                Toast.makeText(context, "Cancelling transfer...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    showOpenWithDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showOpenWithDialog = null },
            title = { Text("Open With System App", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = { Text("Open '${file.name}' using an external application chooser?", fontSize = 13.sp, color = textPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        launchOpenWithIntent(file)
                        showOpenWithDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Open With System App", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenWithDialog = null }) { Text("Cancel", color = textMuted) }
            }
        )
    }

    showTextEditorDialog?.let { file ->
        AlertDialog(
            onDismissRequest = dismissAndWipe,
            title = { Text("Text Editor: ${file.name}", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                OutlinedTextField(
                    value = textEditorContent,
                    onValueChange = { textEditorContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            File(file.path).writeText(textEditorContent)
                            Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        dismissAndWipe()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Save File", color = Color.White)
                }
            }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                OutlinedTextField(
                    value = createFolderNameInput,
                    onValueChange = { createFolderNameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = createFolderNameInput.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createFolder(uiState.currentPath, name) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (success) {
                                    showCreateFolderDialog = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel", color = textMuted) }
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New File", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                OutlinedTextField(
                    value = createFileNameInput,
                    onValueChange = { createFileNameInput = it },
                    label = { Text("File Name (e.g. note.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = createFileNameInput.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createNewFile(uiState.currentPath, name) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (success) {
                                    showCreateFileDialog = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "File name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel", color = textMuted) }
            }
        )
    }

    // Theme Color Picker Dialog - Soft pastel tones
    if (showThemeColorDialog) {
        val softColors = listOf(
            0xFF9E6B55 to "Warm Taupe",
            0xFFE8B4B8 to "Soft Rose",
            0xFFF4C2A0 to "Soft Peach",
            0xFFD4B8E8 to "Soft Lavender",
            0xFFB8E8D4 to "Soft Mint",
            0xFFB8D4E8 to "Soft Sky",
            0xFFF4E8A0 to "Soft Lemon",
            0xFFC8E8B8 to "Soft Sage",
            0xFFE8C8D8 to "Soft Pink",
            0xFFD8C8E8 to "Soft Violet",
            0xFFB8E0E8 to "Soft Teal",
            0xFFF0D8B8 to "Soft Apricot"
        )
        AlertDialog(
            onDismissRequest = { showThemeColorDialog = false },
            title = { Text("Theme Color", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Choose a soft tone for your app",
                        fontSize = 13.sp,
                        color = textMuted
                    )
                    // Color grid
                    val columns = 4
                    val rows = (softColors.size + columns - 1) / columns
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0 until columns) {
                                val index = row * columns + col
                                if (index < softColors.size) {
                                    val (colorValue, name) = softColors[index]
                                    val isSelected = uiState.themeColor == colorValue
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                viewModel.setThemeColor(colorValue)
                                                showThemeColorDialog = false
                                                Toast.makeText(context, "Theme: $name", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorValue))
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) appBlue else Color.Gray.copy(alpha = 0.3f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            name,
                                            fontSize = 10.sp,
                                            color = textMuted,
                                            maxLines = 1
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(72.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeColorDialog = false }) {
                    Text("Close", color = appBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
