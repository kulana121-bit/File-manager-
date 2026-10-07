package com.example.filesapp.ui

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.filesapp.data.AndroidFileModel
import com.example.filesapp.data.InstalledAppInfo
import com.example.filesapp.data.TrashItemModel
import com.example.filesapp.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: FileManagerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Folders") }
    var activeBottomNav by remember { mutableStateOf("Files") }

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

    // Dedicated In-App Viewer Dialog States
    var showImageViewerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showVideoPlayerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showAudioPlayerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showPdfViewerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }
    var showHtmlViewerDialog by remember { mutableStateOf<AndroidFileModel?>(null) }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                viewModel.onGoogleSignInSuccess(account)
                Toast.makeText(context, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign in error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun startGoogleSignIn() {
        val signInClient = GoogleSignIn.getClient(context, viewModel.driveManager.getSignInOptions())
        googleSignInLauncher.launch(signInClient.signInIntent)
    }

    val filteredFiles = remember(uiState.realFiles, uiState.searchQuery, uiState.activeCategory, activeBottomNav, uiState.starredFiles) {
        uiState.realFiles.filter { file ->
            if (uiState.searchQuery.isNotEmpty() && !file.name.contains(uiState.searchQuery, ignoreCase = true)) return@filter false
            if (activeBottomNav == "Starred" && !uiState.starredFiles.contains(file.path)) return@filter false
            if (uiState.activeCategory != null) {
                when (uiState.activeCategory) {
                    "images" -> file.mimeType.startsWith("image/")
                    "docs" -> file.mimeType.contains("pdf") || file.mimeType.startsWith("text/") || file.name.endsWith(".doc") || file.name.endsWith(".docx")
                    "audio" -> file.mimeType.startsWith("audio/")
                    "apks" -> file.name.endsWith(".apk") || file.mimeType.contains("android.package-archive")
                    else -> true
                }
            } else true
        }
    }

    val currentBgColor = if (uiState.isDarkMode) Color(0xFF1C1A18) else SoftCreamBackground
    val currentCardColor = if (uiState.isDarkMode) Color(0xFF24201D) else WhiteCardSurface
    val currentTextColor = if (uiState.isDarkMode) Color(0xFFEAE3DC) else TextDarkHeadings

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown"
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun isMediaFile(file: AndroidFileModel): Boolean {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return file.mimeType.startsWith("image/") ||
               file.mimeType.startsWith("video/") ||
               ext in setOf("png", "jpg", "jpeg", "webp", "gif", "mp4", "mkv", "avi", "mov")
    }

    Scaffold(
        containerColor = currentBgColor,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = if (uiState.isDarkMode) Color(0xFF282420) else Color(0xFFEBE2D7),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Files", "Browse", "Starred", "Trash", "More").forEach { navItem ->
                        val isSelected = activeBottomNav == navItem
                        Surface(
                            onClick = {
                                activeBottomNav = navItem
                                if (navItem == "Trash") {
                                    viewModel.loadTrashItems()
                                    showTrashDialog = true
                                }
                                if (navItem == "Files") {
                                    viewModel.setCategoryFilter(null)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) (if (uiState.isDarkMode) Color(0xFFEAE3DC) else TextDarkHeadings) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (navItem) {
                                        "Files" -> Icons.Default.Folder
                                        "Browse" -> Icons.Default.GridView
                                        "Starred" -> Icons.Default.Star
                                        "Trash" -> Icons.Default.Delete
                                        else -> Icons.Default.MoreHoriz
                                    },
                                    contentDescription = navItem,
                                    tint = if (isSelected) (if (uiState.isDarkMode) TextDarkHeadings else Color.White) else TextMutedSubtitles,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = navItem,
                                    color = if (isSelected) (if (uiState.isDarkMode) TextDarkHeadings else Color.White) else TextMutedSubtitles,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Files",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = currentTextColor
                    )
                    Text(
                        text = "Your files, organized",
                        fontSize = 13.sp,
                        color = TextMutedSubtitles,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dark Mode Toggle
                    IconButton(
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(currentCardColor)
                    ) {
                        Icon(
                            imageVector = if (uiState.isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                            contentDescription = "Toggle Dark Mode",
                            tint = if (uiState.isDarkMode) Color(0xFFFFC107) else TextDarkHeadings,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8DDD0))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = TextMutedSubtitles
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Pinned Folders Bar
            if (uiState.pinnedFolders.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = PrimaryAccentTaupe, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pinned:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMutedSubtitles)
                        }
                    }
                    items(uiState.pinnedFolders.toList()) { folderPath ->
                        Surface(
                            onClick = { viewModel.setCurrentPath(folderPath) },
                            shape = RoundedCornerShape(12.dp),
                            color = currentCardColor,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SoftBorderColor))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryAccentTaupe, modifier = Modifier.size(14.dp))
                                Text(folderPath.substringAfterLast("/").ifEmpty { folderPath }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = currentTextColor)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Google Drive Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!uiState.isDriveConnected) {
                            startGoogleSignIn()
                        } else {
                            showDriveBrowserDialog = true
                        }
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = currentCardColor),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SoftBorderColor))
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0066DA)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = "Drive", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        Column {
                            Text("Google Drive", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = currentTextColor)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (uiState.isDriveConnected) Color(0xFF34A853) else Color.Gray))
                                Text(if (uiState.isDriveConnected) "Connected (${uiState.driveUserEmail})" else "Tap to Sign In", fontSize = 11.sp, color = TextMutedSubtitles)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { viewModel.toggleShowHiddenFiles() },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(if (uiState.showHiddenFiles) PrimaryAccentTaupe else Color(0xFFF5EFE8))
                        ) {
                            Icon(
                                imageVector = if (uiState.showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Hidden files",
                                tint = if (uiState.showHiddenFiles) Color.White else TextMutedSubtitles,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search real files...", fontSize = 12.sp, color = TextMutedSubtitles) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMutedSubtitles) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = currentCardColor,
                    unfocusedContainerColor = currentCardColor,
                    focusedBorderColor = PrimaryAccentTaupe,
                    unfocusedBorderColor = SoftBorderColor
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Browse Categories View
            if (activeBottomNav == "Browse") {
                Text("CATEGORIES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMutedSubtitles, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val categoriesList = listOf(
                    Triple("images", "Images", Icons.Default.Image),
                    Triple("docs", "Documents & PDFs", Icons.Default.Description),
                    Triple("audio", "Audio Files", Icons.Default.MusicNote),
                    Triple("apks", "Apps & APKs", Icons.Default.PhoneAndroid)
                )

                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(120.dp)) {
                    items(categoriesList) { (catKey, catTitle, icon) ->
                        Card(
                            modifier = Modifier.clickable {
                                viewModel.setCategoryFilter(catKey)
                                activeBottomNav = "Files"
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = currentCardColor),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SoftBorderColor))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(icon, contentDescription = null, tint = PrimaryAccentTaupe, modifier = Modifier.size(20.dp))
                                Text(catTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Segmented Folders / Recent Control
            if (activeBottomNav == "Files" && uiState.activeCategory == null) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFFEBE3D9)) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        listOf("Folders", "Recent").forEach { tab ->
                            val isSelected = selectedTab == tab
                            Surface(
                                onClick = { selectedTab = tab },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) currentCardColor else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(text = tab, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) currentTextColor else TextMutedSubtitles)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Folders Grid Section
            if (activeBottomNav == "Files" && selectedTab == "Folders" && uiState.activeCategory == null) {
                Text("FOLDERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMutedSubtitles, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.realFolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(currentCardColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No folders found", fontSize = 13.sp, color = TextMutedSubtitles)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(180.dp)
                    ) {
                        items(uiState.realFolders) { folder ->
                            Card(
                                modifier = Modifier.height(80.dp).clickable { viewModel.setCurrentPath(folder.path) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = currentCardColor),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SoftBorderColor))
                            ) {
                                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Folder, contentDescription = folder.name, tint = PrimaryAccentTaupe, modifier = Modifier.size(26.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(folder.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = currentTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.height(80.dp).clickable {
                                    if (!viewModel.isVaultPinSet()) {
                                        showPinSetupDialog = true
                                    } else {
                                        showPinUnlockDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EAE0)),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE0D2C3)))
                            ) {
                                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Lock, contentDescription = "Private Vault", tint = Color(0xFF6A503C), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Private Vault", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5C422E))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Real Files Header with Grid/List View Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.searchQuery.isNotEmpty()) "SEARCH RESULTS" else if (uiState.activeCategory != null) "CATEGORY: ${uiState.activeCategory?.uppercase()}" else "FILES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMutedSubtitles,
                    letterSpacing = 1.sp
                )

                // Grid / List View Toggle Button
                IconButton(
                    onClick = { viewModel.toggleGridView() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View Mode",
                        tint = PrimaryAccentTaupe,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(currentCardColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMutedSubtitles, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No files yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = currentTextColor)
                        Text("Scanned storage directory is empty", fontSize = 12.sp, color = TextMutedSubtitles)
                    }
                }
            } else if (uiState.isGridView) {
                // Grid View Mode
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredFiles) { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
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
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = currentCardColor),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SoftBorderColor))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (file.name.endsWith(".pdf")) Color(0xFFF8ECEB) else if (file.name.endsWith(".apk")) Color(0xFFFFF3E0) else Color(0xFFF0EBF8)),
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
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (file.name.endsWith(".pdf")) Color(0xFFE53935) else if (file.name.endsWith(".apk")) Color(0xFFE65100) else Color(0xFFA259FF)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = currentTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatFileSize(file.size), fontSize = 10.sp, color = TextMutedSubtitles)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.toggleStar(file.path) }, modifier = Modifier.size(24.dp)) {
                                            Icon(
                                                imageVector = if (uiState.starredFiles.contains(file.path)) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Star",
                                                tint = Color(0xFFFFC107),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(onClick = { showOptionsMenuDialog = file }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextMutedSubtitles, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // List View Mode
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(filteredFiles) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
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
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = currentCardColor),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SoftBorderColor))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (file.name.endsWith(".pdf")) Color(0xFFF8ECEB) else if (file.name.endsWith(".apk")) Color(0xFFFFF3E0) else Color(0xFFF0EBF8)),
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
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (file.name.endsWith(".pdf")) Color(0xFFE53935) else if (file.name.endsWith(".apk")) Color(0xFFE65100) else Color(0xFFA259FF)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = currentTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatDate(file.dateModified)} • ${formatFileSize(file.size)}", fontSize = 11.sp, color = TextMutedSubtitles)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(onClick = { viewModel.toggleStar(file.path) }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            imageVector = if (uiState.starredFiles.contains(file.path)) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Star",
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(onClick = { showOptionsMenuDialog = file }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextMutedSubtitles, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dedicated In-App Viewer Dialog Renders
    showImageViewerDialog?.let { file ->
        ImageViewerDialog(file = file, onDismiss = { showImageViewerDialog = null })
    }

    showVideoPlayerDialog?.let { file ->
        VideoPlayerDialog(file = file, onDismiss = { showVideoPlayerDialog = null })
    }

    showAudioPlayerDialog?.let { file ->
        AudioPlayerDialog(file = file, onDismiss = { showAudioPlayerDialog = null })
    }

    showPdfViewerDialog?.let { file ->
        PdfViewerDialog(file = file, onDismiss = { showPdfViewerDialog = null })
    }

    showHtmlViewerDialog?.let { file ->
        HtmlViewerDialog(file = file, onDismiss = { showHtmlViewerDialog = null })
    }

    // 1. Rename Dialog
    showRenameFileDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showRenameFileDialog = null },
            title = { Text("Rename File", fontWeight = FontWeight.Bold) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFileDialog = null }) { Text("Cancel") }
            }
        )
    }

    // 2. Move File Dialog
    showMoveFileDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showMoveFileDialog = null },
            title = { Text("Move File", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter target folder path:", fontSize = 12.sp, color = TextMutedSubtitles)
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveFileDialog = null }) { Text("Cancel") }
            }
        )
    }

    // 3. Copy File Dialog
    showCopyFileDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showCopyFileDialog = null },
            title = { Text("Copy File", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter target folder path to copy file:", fontSize = 12.sp, color = TextMutedSubtitles)
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyFileDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Options Menu Dialog with Per-File Actions
    showOptionsMenuDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showOptionsMenuDialog = null },
            title = { Text(file.name, fontWeight = FontWeight.Bold) },
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
                            Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryAccentTaupe)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("File Details & Properties", color = currentTextColor)
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
                            Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryAccentTaupe)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rename File", color = currentTextColor)
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
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = PrimaryAccentTaupe)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Move File", color = currentTextColor)
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
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PrimaryAccentTaupe)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy File", color = currentTextColor)
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
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF8C533E))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Move to Private Vault (AES-256)", color = Color(0xFF8C533E), fontWeight = FontWeight.Bold)
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
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF0066DA))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload to Google Drive", color = Color(0xFF0066DA), fontWeight = FontWeight.Bold)
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
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.starredFiles.contains(file.path)) "Unstar File" else "Star File", color = currentTextColor)
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
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete (Move to Trash)", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptionsMenuDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Trash Screen Dialog
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
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.Red)
                        Text("Trash Bin", fontWeight = FontWeight.Bold)
                    }

                    if (uiState.trashItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.emptyTrash { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Empty Trash", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    Text("Items in trash are auto-deleted after 30 days.", fontSize = 11.sp, color = TextMutedSubtitles)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.trashItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = TextMutedSubtitles, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Trash is empty", fontSize = 12.sp, color = TextMutedSubtitles)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.trashItems) { trashItem ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = currentCardColor)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(trashItem.originalName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${formatDate(trashItem.trashedAtTimestamp)} • ${formatFileSize(trashItem.size)}", fontSize = 10.sp, color = TextMutedSubtitles)
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
                                                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = PrimaryAccentTaupe)
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.deletePermanentlyFromTrash(trashItem.trashedFile) { success, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanently", tint = Color.Red)
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
                Button(onClick = { showTrashDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)) {
                    Text("Close")
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
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF0066DA))
                    Text("Google Drive Browser", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    Text("Account: ${uiState.driveUserEmail ?: "Connected"}", fontSize = 12.sp, color = TextMutedSubtitles)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.driveFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextMutedSubtitles, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("No Google Drive files found", fontSize = 12.sp, color = TextMutedSubtitles)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.driveFiles) { driveFile ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = currentCardColor)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(driveFile.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatFileSize(driveFile.size), fontSize = 10.sp, color = TextMutedSubtitles)
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.downloadDriveFileToLocal(driveFile.id, driveFile.name) { success, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color(0xFF0066DA))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDriveBrowserDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)) {
                    Text("Close")
                }
            }
        )
    }

    if (showPinSetupDialog) {
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text("Set Vault 4-Digit PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a 4-digit PIN to secure your Private Vault. Only a SHA-256 hash is saved.", fontSize = 12.sp, color = TextMutedSubtitles)
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
                ) {
                    Text("Save PIN & Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPinUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showPinUnlockDialog = false },
            title = { Text("Unlock Private Vault", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your 4-digit PIN to access encrypted files.", fontSize = 12.sp, color = TextMutedSubtitles)
                    OutlinedTextField(
                        value = pinUnlockInput,
                        onValueChange = { if (it.length <= 4) pinUnlockInput = it },
                        label = { Text("Enter PIN") },
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
                        if (viewModel.verifyVaultPin(pinUnlockInput)) {
                            showPinUnlockDialog = false
                            pinUnlockInput = ""
                            viewModel.loadVaultFiles()
                            showVaultBrowserDialog = true
                        } else {
                            Toast.makeText(context, "Incorrect Vault PIN", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinUnlockDialog = false }) { Text("Cancel") }
            }
        )
    }

    showMoveToVaultDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showMoveToVaultDialog = null },
            title = { Text("Move to Private Vault", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Encrypt '${file.name}' with AES-256-GCM and hide it from all other apps.", fontSize = 12.sp, color = TextMutedSubtitles)
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
                ) {
                    Text("Encrypt & Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveToVaultDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showVaultBrowserDialog) {
        AlertDialog(
            onDismissRequest = { showVaultBrowserDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryAccentTaupe)
                    Text("Private Vault (Encrypted)", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    Text("Files inside are AES-256-GCM encrypted. Tap any file to decrypt and restore to storage.", fontSize = 11.sp, color = TextMutedSubtitles)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.vaultFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = TextMutedSubtitles, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Private Vault is empty", fontSize = 12.sp, color = TextMutedSubtitles)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.vaultFiles) { vaultFile ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showRestoreVaultFileDialog = vaultFile },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = currentCardColor)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryAccentTaupe, modifier = Modifier.size(20.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(vaultFile.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = currentTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(formatFileSize(vaultFile.size), fontSize = 10.sp, color = TextMutedSubtitles)
                                            }
                                        }

                                        TextButton(onClick = { showRestoreVaultFileDialog = vaultFile }) {
                                            Text("Decrypt", fontSize = 11.sp, color = PrimaryAccentTaupe, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showVaultBrowserDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)) {
                    Text("Close Vault")
                }
            }
        )
    }

    showRestoreVaultFileDialog?.let { vaultFile ->
        AlertDialog(
            onDismissRequest = { showRestoreVaultFileDialog = null },
            title = { Text("Decrypt & Restore File", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Decrypt '${vaultFile.name}' and restore it to your storage folder?", fontSize = 12.sp, color = TextMutedSubtitles)
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
                ) {
                    Text("Decrypt File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreVaultFileDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showStorageAnalyzerDialog) {
        AlertDialog(
            onDismissRequest = { showStorageAnalyzerDialog = false },
            title = { Text("Storage Analyzer", fontWeight = FontWeight.Bold) },
            text = { Text("Device storage scan completed. Analyzed real files and app storage.") },
            confirmButton = {
                Button(onClick = { showStorageAnalyzerDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)) {
                    Text("OK")
                }
            }
        )
    }

    showApkInstallerDialog?.let { apkFile ->
        AlertDialog(
            onDismissRequest = { showApkInstallerDialog = null },
            title = { Text("Package Installer", fontWeight = FontWeight.Bold) },
            text = { Text("Install ${apkFile.name}? Target API 29 (Android 10).") },
            confirmButton = {
                Button(onClick = { showApkInstallerDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)) {
                    Text("Install APK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApkInstallerDialog = null }) { Text("Cancel") }
            }
        )
    }

    showFileDetailsDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showFileDetailsDialog = null },
            title = { Text("File Details & Properties", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Name: ${file.name}")
                    Text("Path: ${file.path}")
                    Text("Size: ${formatFileSize(file.size)} (${file.size} bytes)")
                    Text("Modified: ${formatDate(file.dateModified)}")
                    Text("Permissions: -rw-r--r--")
                }
            },
            confirmButton = {
                Button(onClick = { showFileDetailsDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)) {
                    Text("OK")
                }
            }
        )
    }

    showOpenWithDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showOpenWithDialog = null },
            title = { Text("Open With...", fontWeight = FontWeight.Bold) },
            text = { Text("Choose handler for ${file.name}.") },
            confirmButton = {
                Button(onClick = { showOpenWithDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)) {
                    Text("In-App Handler")
                }
            }
        )
    }

    showTextEditorDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showTextEditorDialog = null },
            title = { Text("Text & Code Editor: ${file.name}", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textEditorContent,
                    onValueChange = { textEditorContent = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            File(file.path).writeText(textEditorContent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        showTextEditorDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
                ) {
                    Text("Save File")
                }
            }
        )
    }
}
