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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Modifier.bounceClick() = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                }
            )
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
    var showDriveBrowserDialog by remember { mutableStateOf(false) }
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

    // Dynamic Style Colors (One UI / iOS style with perfect contrast)
    val isDark = uiState.isDarkMode
    val appBlue = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF) // Premium iOS Blue
    val appGreen = if (isDark) Color(0xFF30D158) else Color(0xFF34C759)
    val appRed = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    val appAmber = if (isDark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    val appIndigo = if (isDark) Color(0xFF5E5CE6) else Color(0xFF5856D6)

    val bgColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF2F4F7) // Soft clean premium backing
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF) // High elevation iOS cards
    val textPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val textMuted = if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)
    val separatorColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    val previewVaultFile: (AndroidFileModel) -> Unit = { vaultFile ->
        if (cachedVaultPin.isNotBlank()) {
            val decryptedFile = viewModel.decryptVaultFileToCache(File(vaultFile.path), cachedVaultPin)
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
                        textEditorContent = decryptedFile.readText()
                        showTextEditorDialog = tempModel
                    }
                    "zip" -> {
                        showExtractZipDialog = tempModel
                    }
                    else -> {
                        showOpenFallbackDialog = tempModel
                    }
                }
            } else {
                Toast.makeText(context, "Failed to decrypt preview: Incorrect PIN or file error", Toast.LENGTH_SHORT).show()
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
            "zip" -> showExtractZipDialog = file
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cardColor,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = "New File",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = "New File")
                            }
                        }

                        // New Folder button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cardColor,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = "New Folder",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = appBlue,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Create Options"
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Elegant, elevated One UI / iOS hybrid bottom nav bar with large, comfortable touch targets (48dp+)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable {
                                    activeBottomNav = navKey
                                    if (navKey == "Drive" && uiState.isDriveConnected) {
                                        showDriveBrowserDialog = true
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) appBlue else textMuted,
                                modifier = Modifier.size(if (isSelected) 24.dp else 22.dp)
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = activeBottomNav,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                    fadeOut(animationSpec = tween(90))
                },
                modifier = Modifier.fillMaxSize(),
                label = "navTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    "Dashboard" -> {
                        // Samsung One UI Style Large Header & Dashboard
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                // Huge One UI low-reach top banner
                                Spacer(modifier = Modifier.height(48.dp))
                                Column {
                                    Text(
                                        text = "My Device",
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "Everything in one clean place",
                                        fontSize = 14.sp,
                                        color = textMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // 1. Beautiful circular/linear arc Storage Card
                            item {
                                val breakdown = uiState.storageBreakdown
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Storage, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                                                Text("Internal Storage", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                            if (breakdown != null) {
                                                Text(
                                                    text = "${((breakdown.usedSpaceBytes.toFloat() / breakdown.totalSpaceBytes) * 100).toInt()}% Used",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = appBlue
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (breakdown != null) {
                                            val usedFraction = breakdown.usedSpaceBytes.toFloat() / breakdown.totalSpaceBytes
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(12.dp)
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
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${formatFileSize(breakdown.usedSpaceBytes)} of ${formatFileSize(breakdown.totalSpaceBytes)} used",
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
                                            CircularProgressIndicator(color = appBlue, modifier = Modifier.align(Alignment.CenterHorizontally))
                                        }
                                    }
                                }
                            }

                            // 2. High-contrast iOS-style Grid Categories Card
                            item {
                                Text(
                                    text = "CATEGORIES",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textMuted,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val categories = listOf(
                                    Triple("images", "Images", Icons.Outlined.Image),
                                    Triple("docs", "Documents", Icons.Outlined.Description),
                                    Triple("audio", "Audio", Icons.Outlined.MusicNote),
                                    Triple("apks", "APKs", Icons.Outlined.PhoneAndroid),
                                    Triple("archives", "Archives", Icons.Outlined.FolderZip)
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        categories.chunked(3).forEach { rowList ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                rowList.forEach { (catKey, catTitle, icon) ->
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
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
                                                                .clip(RoundedCornerShape(14.dp))
                                                                .background(
                                                                    when (catKey) {
                                                                        "images" -> appBlue.copy(alpha = 0.12f)
                                                                        "docs" -> appIndigo.copy(alpha = 0.12f)
                                                                        "audio" -> appGreen.copy(alpha = 0.12f)
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
                                                                    "docs" -> appIndigo
                                                                    "audio" -> appGreen
                                                                    "apks" -> appAmber
                                                                    else -> appRed
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(catTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                    }
                                                }
                                                // Pad row if not full
                                                if (rowList.size < 3) {
                                                    Spacer(modifier = Modifier.weight(3f - rowList.size))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Quick Actions Panel
                            item {
                                Text(
                                    text = "QUICK TOOLS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textMuted,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Analyzer Card
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                viewModel.loadStorageBreakdown()
                                                showStorageAnalyzerDialog = true
                                            },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Outlined.PieChart, contentDescription = null, tint = appBlue)
                                            Text("Analyzer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                        }
                                    }

                                    // Trash Card
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                viewModel.loadTrashItems()
                                                showTrashDialog = true
                                            },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = appRed)
                                            Text("Trash Bin", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                        }
                                    }
                                }
                            }

                            // 4. Recent Files List
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RECENT FILES",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textMuted,
                                        letterSpacing = 1.2.sp
                                    )
                                    TextButton(onClick = { activeBottomNav = "Files" }) {
                                        Text("See All", fontSize = 12.sp, color = appBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                val recents = uiState.realFiles.take(4)
                                if (recents.isEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No recent files found", fontSize = 13.sp, color = textMuted, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            recents.forEachIndexed { index, file ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
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
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    // Thumbnail Box with Coil or high-quality dynamic colors
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                if (file.name.endsWith(".pdf")) Color(0xFFFDE8E8)
                                                                else if (file.name.endsWith(".apk")) Color(0xFFFFF3E0)
                                                                else Color(0xFFE8F0FE)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isMediaFile(file)) {
                                                            AsyncImage(
                                                                model = File(file.path),
                                                                contentDescription = file.name,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
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
                                                            fontSize = 13.sp,
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
                                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 10.dp), color = separatorColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(20.dp))
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
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                viewModel.setCategoryFilter(null)
                                                activeBottomNav = "Dashboard"
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(cardColor)
                                        ) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = appBlue)
                                        }
                                        Column {
                                            Text(
                                                text = uiState.activeCategory?.capitalize(Locale.ROOT) ?: "Category",
                                                fontSize = 34.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = textPrimary,
                                                letterSpacing = (-0.5).sp
                                            )
                                            Text(
                                                text = "Recursive category view",
                                                fontSize = 14.sp,
                                                color = textMuted
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
                                                fontSize = 34.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = textPrimary,
                                                letterSpacing = (-0.5).sp
                                            )
                                            Text(
                                                text = "Manage your internal storage",
                                                fontSize = 14.sp,
                                                color = textMuted
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.toggleGridView() },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(cardColor)
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                                contentDescription = "Toggle Grid/List",
                                                tint = appBlue
                                            )
                                        }
                                    }
                                }
                            }

                            // Sleek horizontal scrollable categories chip bar under big bold header
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
                                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, separatorColor)

                                        Surface(
                                            onClick = { viewModel.setCategoryFilter(catKey) },
                                            shape = RoundedCornerShape(16.dp),
                                            color = chipBg,
                                            contentColor = chipContentColor,
                                            border = borderStroke,
                                            shadowElevation = if (isSelected) 4.dp else 1.dp,
                                            modifier = Modifier.bounceClick().animateContentSize()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                            // Directory Path Bar
                            if (uiState.activeCategory == null) {
                                item {
                                    val isAtRoot = uiState.currentPath == "/" || uiState.currentPath == rootPath
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor)
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
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = appBlue)
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }

                                            Icon(Icons.Default.Folder, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))

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

                            // Folders Selection Header & Tab Panel
                            item {
                                if (uiState.activeCategory == null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                                    ) {
                                        Row(modifier = Modifier.padding(4.dp)) {
                                            listOf("Folders", "Recent").forEach { tab ->
                                                val isSelected = selectedTab == tab
                                                Surface(
                                                    onClick = { selectedTab = tab },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) cardColor else Color.Transparent,
                                                    shadowElevation = if (isSelected) 1.dp else 0.dp
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

                            // Subfolders Grid List
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
                                        Spacer(modifier = Modifier.height(4.dp))

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.heightIn(max = 240.dp)
                                        ) {
                                            items(uiState.realFolders) { folder ->
                                                Card(
                                                    modifier = Modifier
                                                        .height(84.dp)
                                                        .shadow(2.dp, RoundedCornerShape(20.dp))
                                                        .bounceClick()
                                                        .clickable { viewModel.setCurrentPath(folder.path) },
                                                    shape = RoundedCornerShape(20.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(
                                                                Brush.verticalGradient(
                                                                    colors = if (isDark) listOf(cardColor, cardColor.copy(alpha = 0.85f))
                                                                             else listOf(Color.White, Color(0xFFF9FAFC))
                                                                )
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Icon(Icons.Default.Folder, contentDescription = folder.name, tint = appBlue, modifier = Modifier.size(28.dp))
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = folder.name,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = textPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.padding(horizontal = 6.dp)
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
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = textMuted, modifier = Modifier.size(48.dp))
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text("This directory is empty", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                        }
                                    }
                                }
                            } else if (uiState.isGridView) {
                                item {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.heightIn(max = 800.dp)
                                    ) {
                                        items(filteredFiles) { file ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(144.dp)
                                                    .clickable { onFileClick(file) },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = CardDefaults.cardColors(containerColor = cardColor)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(72.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(
                                                                if (file.name.endsWith(".pdf")) Color(0xFFFDE8E8)
                                                                else if (file.name.endsWith(".apk")) Color(0xFFFFF3E0)
                                                                else Color(0xFFE8F0FE)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isMediaFile(file)) {
                                                            AsyncImage(
                                                                model = File(file.path),
                                                                contentDescription = file.name,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
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
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = textPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(formatFileSize(file.size), fontSize = 10.sp, color = textMuted)
                                                        }

                                                        IconButton(
                                                            onClick = { showOptionsMenuDialog = file },
                                                            modifier = Modifier.size(24.dp)
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
                                            .shadow(2.dp, RoundedCornerShape(20.dp))
                                            .bounceClick()
                                            .clickable { onFileClick(file) },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = if (isDark) listOf(cardColor, cardColor.copy(alpha = 0.85f))
                                                                 else listOf(Color.White, Color(0xFFF9FAFC))
                                                    )
                                                )
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (file.name.endsWith(".pdf")) Color(0xFFFDE8E8)
                                                        else if (file.name.endsWith(".apk")) Color(0xFFFFF3E0)
                                                        else Color(0xFFE8F0FE)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isMediaFile(file)) {
                                                    AsyncImage(
                                                        model = File(file.path),
                                                        contentDescription = file.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
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
                                                    fontSize = 13.sp,
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
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.MoreVert, contentDescription = null, tint = textMuted)
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
                                // PIN Setup Screen inside tab
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = appBlue, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Set Vault 4-Digit PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Enter a 4-digit PIN to secure your Private Vault.", fontSize = 14.sp, color = textMuted, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    OutlinedTextField(
                                        value = pinSetupInput,
                                        onValueChange = { if (it.length <= 4) pinSetupInput = it },
                                        label = { Text("Enter PIN") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = pinSetupConfirm,
                                        onValueChange = { if (it.length <= 4) pinSetupConfirm = it },
                                        label = { Text("Confirm PIN") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
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
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        Text("Save PIN & Unlock", color = Color.White)
                                    }
                                }
                            } else if (cachedVaultPin.isBlank()) {
                                // PIN Unlock Screen inside tab
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = appBlue, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Unlock Private Vault", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Enter your 4-digit PIN to access encrypted files.", fontSize = 14.sp, color = textMuted, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
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
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    )
                                }
                            } else {
                                // Unlocked Full Screen Vault Screen
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(bgColor)
                                ) {
                                    // Elegant One UI style top bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = appBlue, modifier = Modifier.size(24.dp))
                                            Text(
                                                text = "Private Vault",
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = textPrimary
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
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(cardColor)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close Vault", tint = textMuted)
                                        }
                                    }

                                    Text(
                                        text = "AES-256-GCM encrypted files. Tapping any file previews it directly in-app.",
                                        fontSize = 12.sp,
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
                                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = textMuted, modifier = Modifier.size(64.dp))
                                                Spacer(modifier = Modifier.height(12.dp))
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
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(uiState.vaultFiles) { vaultFile ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { previewVaultFile(vaultFile) },
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(containerColor = cardColor)
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
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(40.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(appBlue.copy(alpha = 0.12f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(Icons.Default.Key, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
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
                                                                    text = formatFileSize(vaultFile.size),
                                                                    fontSize = 11.sp,
                                                                    color = textMuted
                                                                )
                                                            }
                                                        }

                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            TextButton(
                                                                onClick = { showRestoreVaultFileDialog = vaultFile }
                                                            ) {
                                                                Text("Restore", fontSize = 12.sp, color = appBlue, fontWeight = FontWeight.Bold)
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
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "Cloud files integration",
                                        fontSize = 14.sp,
                                        color = textMuted
                                    )
                                }

                                if (uiState.isDriveConnected) {
                                    TextButton(
                                        onClick = {
                                            viewModel.signOutGoogleDrive {
                                                Toast.makeText(context, "Google Drive disconnected", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text("Disconnect", color = appRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))

                            if (!uiState.isDriveConnected) {
                                // Signed Out State with Premium styling
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = null,
                                            tint = appBlue,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Connect Google Drive",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "View and download your Drive files in place, or backup local files directly to the secure cloud.",
                                            fontSize = 13.sp,
                                            color = textMuted,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))

                                        Button(
                                            onClick = { startGoogleSignIn() },
                                            colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Text(
                                                            text = "G",
                                                            fontSize = 12.sp,
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
                                                fontSize = 11.sp,
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
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CloudOff,
                                            contentDescription = null,
                                            tint = appRed,
                                            modifier = Modifier.size(48.dp)
                                        )
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
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Retry Connection", color = Color.White)
                                        }
                                    }
                                }
                            } else {
                                // Connected File Browser directly in the tab!
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
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
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = appBlue, modifier = Modifier.size(16.dp))
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
                                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = textMuted, modifier = Modifier.size(48.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
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
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = CardDefaults.cardColors(containerColor = cardColor)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(40.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(appBlue.copy(alpha = 0.12f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (driveFile.isDirectory) Icons.Default.Folder else Icons.Default.Cloud,
                                                                    contentDescription = null,
                                                                    tint = appBlue,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = driveFile.name,
                                                                    fontSize = 13.sp,
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
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = "Download to local Downloads folder", tint = appBlue)
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
                        // Samsung style premium system settings list
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp)
                        ) {
                            Spacer(modifier = Modifier.height(48.dp))
                            Text(
                                text = "Settings",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Personalize files manager",
                                fontSize = 14.sp,
                                color = textMuted
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = cardColor)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Dark Mode Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleDarkMode() }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(
                                                imageVector = if (isDark) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay,
                                                contentDescription = null,
                                                tint = appBlue
                                            )
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

                                    // Storage Analyzer Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.loadStorageBreakdown()
                                                showStorageAnalyzerDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(Icons.Outlined.PieChart, contentDescription = null, tint = appIndigo)
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
                                            .clickable {
                                                viewModel.loadTrashItems()
                                                showTrashDialog = true
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = appRed)
                                            Column {
                                                Text("Trash Bin Manager", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                Text("Restore soft-deleted files", fontSize = 11.sp, color = textMuted)
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

    // In-App Viewers Integration
    showImageViewerDialog?.let { file ->
        ImageViewerDialog(file = file, onDismiss = dismissAndWipe)
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
            title = { Text(file.name, fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

    // Trash Screen Dialog (iOS Styled Sheet)
    if (showTrashDialog) {
        AlertDialog(
            onDismissRequest = { showTrashDialog = false },
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
                            onClick = {
                                viewModel.emptyTrash { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Empty Trash", color = appRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    Text("Items in trash are auto-deleted after 30 days.", fontSize = 11.sp, color = textMuted)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.trashItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = textMuted, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Trash is empty", fontSize = 12.sp, color = textMuted)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.trashItems) { trashItem ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(trashItem.originalName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${formatDate(trashItem.trashedAtTimestamp)} • ${formatFileSize(trashItem.size)}", fontSize = 10.sp, color = textMuted)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.restoreFromTrash(trashItem.trashedFile) { success, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = appBlue)
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.deletePermanentlyFromTrash(trashItem.trashedFile) { success, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanently", tint = appRed)
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
                Button(onClick = { showTrashDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = appBlue)) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // Google Drive Browser Dialog
    if (showDriveBrowserDialog) {
        AlertDialog(
            onDismissRequest = { showDriveBrowserDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = appBlue)
                    Text("Google Drive Browser", fontWeight = FontWeight.Bold, color = textPrimary)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    Text("Account: ${uiState.driveUserEmail ?: "Connected"}", fontSize = 12.sp, color = textMuted)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (uiState.driveFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = textMuted, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("No Google Drive files found", fontSize = 12.sp, color = textMuted)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.driveFiles) { driveFile ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardColor)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(driveFile.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatFileSize(driveFile.size), fontSize = 10.sp, color = textMuted)
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.downloadDriveFileToLocal(driveFile.id, driveFile.name) { success, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Download", tint = appBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDriveBrowserDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = appBlue)) {
                    Text("Close", color = Color.White)
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

    // Real Computed Storage Analyzer Dialog (Premium One UI / iOS Card format)
    if (showStorageAnalyzerDialog) {
        val breakdown = uiState.storageBreakdown
        AlertDialog(
            onDismissRequest = { showStorageAnalyzerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PieChart, contentDescription = null, tint = appBlue)
                    Text("Storage Breakdown", fontWeight = FontWeight.Bold, color = textPrimary)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (breakdown != null) {
                        val usedFraction = if (breakdown.totalSpaceBytes > 0) breakdown.usedSpaceBytes.toFloat() / breakdown.totalSpaceBytes else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Storage Used", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                Text("${formatFileSize(breakdown.usedSpaceBytes)} / ${formatFileSize(breakdown.totalSpaceBytes)}", fontSize = 12.sp, color = textMuted)
                            }
                            LinearProgressIndicator(
                                progress = { usedFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(CircleShape),
                                color = appBlue,
                                trackColor = separatorColor
                            )
                            Text("${formatFileSize(breakdown.freeSpaceBytes)} free • ${breakdown.totalFileCount} files indexed", fontSize = 11.sp, color = textMuted)
                        }

                        HorizontalDivider(color = separatorColor)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple("Images", breakdown.imageSizeBytes, Icons.Default.Image),
                                Triple("Videos", breakdown.videoSizeBytes, Icons.Default.Movie),
                                Triple("Audio", breakdown.audioSizeBytes, Icons.Default.MusicNote),
                                Triple("Documents", breakdown.docSizeBytes, Icons.Default.Description),
                                Triple("APKs & Apps", breakdown.apkSizeBytes, Icons.Default.PhoneAndroid),
                                Triple("Archives", breakdown.archiveSizeBytes, Icons.Default.FolderZip),
                                Triple("Other Files", breakdown.otherSizeBytes, Icons.Default.InsertDriveFile)
                            ).forEach { (catName, size, icon) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(icon, contentDescription = catName, tint = appBlue, modifier = Modifier.size(16.dp))
                                        Text(catName, fontSize = 13.sp, color = textPrimary)
                                    }
                                    Text(formatFileSize(size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = appBlue)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showStorageAnalyzerDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = appBlue)) {
                    Text("OK", color = Color.White)
                }
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Name: ${file.name}", fontSize = 13.sp, color = textPrimary)
                    Text("Path: ${file.path}", fontSize = 11.sp, color = textMuted)
                    Text("Size: ${formatFileSize(file.size)} (${file.size} bytes)", fontSize = 13.sp, color = textPrimary)
                    Text("MIME: ${file.mimeType}", fontSize = 13.sp, color = textPrimary)
                    Text("Modified: ${formatDate(file.dateModified)}", fontSize = 13.sp, color = textPrimary)
                }
            },
            confirmButton = {
                Button(onClick = { showFileDetailsDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = appBlue)) {
                    Text("OK", color = Color.White)
                }
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
}
