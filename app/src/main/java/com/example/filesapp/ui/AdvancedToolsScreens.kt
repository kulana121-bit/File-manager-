package com.example.filesapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.example.filesapp.data.AndroidFileModel
import com.example.filesapp.data.DuplicateFileGroup
import com.example.filesapp.data.FileChecksumResult
import com.example.filesapp.data.InstalledAppDetails
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1. DUPLICATE FINDER SCREEN (Full Screen Dialog)
 * Scans storage for duplicates by content hash, groups them, shows wasted space total,
 * lets user select and delete duplicates with confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderDialog(
    viewModel: FileManagerViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = uiState.isDarkMode

    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFF2F4F7)
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardSubtle = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F4F7)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
    val textMuted = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
    val appBlue = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val appRed = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)

    val progress = uiState.duplicateScanProgress
    val groups = uiState.duplicateGroups
    val isScanning = progress?.isScanning == true

    // Selected files map: Key = file path, Value = File
    val selectedFilesToDelete = remember { mutableStateMapOf<String, File>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (groups.isEmpty() && progress == null) {
            viewModel.scanDuplicateFiles()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = bgColor,
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardColor,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(cardSubtle)
                                    .bounceClick()
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimary, modifier = Modifier.size(20.dp))
                            }

                            Column {
                                Text(
                                    text = "Duplicate Finder",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary
                                )
                                Text(
                                    text = if (isScanning) "Scanning storage by hash..." else "${groups.size} duplicate groups found",
                                    fontSize = 12.sp,
                                    color = textMuted
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                selectedFilesToDelete.clear()
                                viewModel.scanDuplicateFiles()
                            },
                            enabled = !isScanning,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardSubtle)
                                .bounceClick()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = appBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            bottomBar = {
                if (selectedFilesToDelete.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = cardColor,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${selectedFilesToDelete.size} selected",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                val freedBytes = selectedFilesToDelete.values.sumOf { it.length() }
                                Text(
                                    text = "Free up ${formatFileSize(freedBytes)}",
                                    fontSize = 12.sp,
                                    color = appRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = appRed),
                                shape = CircleShape,
                                modifier = Modifier.bounceClick()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Delete Selected", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Summary Wasted Space Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (uiState.totalDuplicateWastedBytes > 0) appRed.copy(alpha = 0.12f) else appBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.totalDuplicateWastedBytes > 0) Icons.Default.DeleteSweep else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (uiState.totalDuplicateWastedBytes > 0) appRed else appBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wasted Storage", fontSize = 12.sp, color = textMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = formatFileSize(uiState.totalDuplicateWastedBytes),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.totalDuplicateWastedBytes > 0) appRed else textPrimary
                            )
                            Text(
                                text = if (isScanning) "Hashing candidate files..." else "In ${groups.size} duplicate sets",
                                fontSize = 11.sp,
                                color = textMuted
                            )
                        }

                        if (groups.isNotEmpty() && !isScanning) {
                            Surface(
                                onClick = {
                                    // Auto-select all duplicates except the first original file in each group
                                    selectedFilesToDelete.clear()
                                    groups.forEach { group ->
                                        group.files.drop(1).forEach { f ->
                                            selectedFilesToDelete[f.absolutePath] = f
                                        }
                                    }
                                },
                                shape = CircleShape,
                                color = appBlue.copy(alpha = 0.12f),
                                modifier = Modifier.bounceClick()
                            ) {
                                Text(
                                    text = "Select Extra",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appBlue,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isScanning) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val scanned = progress?.scannedFiles ?: 0
                            val total = progress?.totalFiles ?: 1
                            val fraction = if (total > 0) scanned.toFloat() / total else 0f

                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = appBlue,
                                trackColor = cardSubtle
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Hashing file $scanned of $total", fontSize = 12.sp, color = textMuted)
                                Text("${(fraction * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appBlue)
                            }

                            Text(
                                text = progress?.currentFileName ?: "Scanning...",
                                fontSize = 11.sp,
                                color = textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(cardSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, tint = appBlue, modifier = Modifier.size(36.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("No Duplicates Found", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Your internal storage is clean and optimized.", fontSize = 13.sp, color = textMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(groups) { group ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = group.files.firstOrNull()?.name ?: "Duplicate File",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${formatFileSize(group.fileSize)} each • ${group.files.size} identical copies",
                                                fontSize = 11.sp,
                                                color = textMuted
                                            )
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = appRed.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "+${formatFileSize(group.wastedSizeBytes)} wasted",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appRed,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = cardSubtle)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    group.files.forEachIndexed { idx, file ->
                                        val isSelected = selectedFilesToDelete.containsKey(file.absolutePath)
                                        val isFirstOriginal = idx == 0

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    if (isSelected) {
                                                        selectedFilesToDelete.remove(file.absolutePath)
                                                    } else {
                                                        selectedFilesToDelete[file.absolutePath] = file
                                                    }
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        selectedFilesToDelete[file.absolutePath] = file
                                                    } else {
                                                        selectedFilesToDelete.remove(file.absolutePath)
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = appRed)
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = file.name,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isFirstOriginal) FontWeight.Bold else FontWeight.Medium,
                                                        color = textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isFirstOriginal) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = appBlue.copy(alpha = 0.12f)
                                                        ) {
                                                            Text(
                                                                text = "Original",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = appBlue,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = file.parent ?: "",
                                                    fontSize = 10.sp,
                                                    color = textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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

    // Confirmation Dialog for Deleting Duplicates
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(appRed.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = appRed, modifier = Modifier.size(20.dp))
                    }
                    Text("Delete Duplicate Files?", fontWeight = FontWeight.Bold, color = textPrimary)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete ${selectedFilesToDelete.size} selected duplicate file(s)? This will free up storage immediately.",
                    fontSize = 13.sp,
                    color = textPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val files = selectedFilesToDelete.values.toList()
                        showDeleteConfirmDialog = false
                        viewModel.deleteSelectedDuplicates(files) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            selectedFilesToDelete.clear()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appRed),
                    shape = CircleShape
                ) {
                    Text("Delete Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }
}

/**
 * 2. FILE CHECKSUM CALCULATOR DIALOG
 * Computes and displays MD5, SHA-1, SHA-256 with copy buttons.
 */
@Composable
fun FileChecksumDialog(
    file: AndroidFileModel,
    viewModel: FileManagerViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = uiState.isDarkMode

    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardSubtle = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F4F7)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
    val textMuted = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
    val appBlue = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)

    val isComputing = uiState.isComputingChecksum
    val progress = uiState.checksumProgress
    val result = uiState.currentChecksumResult

    LaunchedEffect(file.path) {
        viewModel.calculateFileChecksum(File(file.path))
    }

    fun copyToClipboard(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.clearChecksumResult()
            onDismiss()
        },
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(appBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("File Checksum", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textPrimary)
                    Text(file.name, fontSize = 12.sp, color = textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isComputing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 3.5.dp,
                            color = appBlue
                        )
                        Text(
                            text = "Hashing file... ${(progress * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Text(
                            text = formatFileSize(file.size),
                            fontSize = 11.sp,
                            color = textMuted
                        )
                    }
                } else if (result != null) {
                    listOf(
                        Triple("MD5", result.md5, "128-bit hash"),
                        Triple("SHA-1", result.sha1, "160-bit hash"),
                        Triple("SHA-256", result.sha256, "256-bit strong cryptographic hash")
                    ).forEach { (algo, hashValue, subtitle) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = cardSubtle)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(algo, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appBlue)
                                        Text(subtitle, fontSize = 10.sp, color = textMuted)
                                    }

                                    IconButton(
                                        onClick = { copyToClipboard(algo, hashValue) },
                                        modifier = Modifier.size(32.dp).clip(CircleShape).bounceClick()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy $algo", tint = appBlue, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                SelectionContainer {
                                    Text(
                                        text = hashValue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textPrimary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Could not compute checksum for this file", color = textMuted)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.clearChecksumResult()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                shape = CircleShape
            ) {
                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * 3. APP MANAGER SCREEN (Full Screen Dialog)
 * Lists all installed apps (icon, name, package, size, version)
 * with search/filter, details sheet, APK export/backup, uninstall, and share APK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerDialog(
    viewModel: FileManagerViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = uiState.isDarkMode

    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFF2F4F7)
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val cardSubtle = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F4F7)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
    val textMuted = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
    val appBlue = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val appRed = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "User", "System"
    var selectedAppForDetails by remember { mutableStateOf<InstalledAppDetails?>(null) }

    LaunchedEffect(Unit) {
        if (uiState.installedApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    val filteredApps = remember(uiState.installedApps, searchQuery, selectedFilter) {
        uiState.installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "User" -> !app.isSystemApp
                "System" -> app.isSystemApp
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = bgColor,
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardColor,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(cardSubtle)
                                        .bounceClick()
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimary, modifier = Modifier.size(20.dp))
                                }

                                Column {
                                    Text(
                                        text = "App Manager",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "${filteredApps.size} apps available",
                                        fontSize = 12.sp,
                                        color = textMuted
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.loadInstalledApps() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(cardSubtle)
                                    .bounceClick()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = appBlue, modifier = Modifier.size(20.dp))
                            }
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search installed apps...", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = textMuted, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = textMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = cardSubtle,
                                unfocusedContainerColor = cardSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        )

                        // Filter Chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "User", "System").forEach { filter ->
                                val isSelected = selectedFilter == filter
                                Surface(
                                    onClick = { selectedFilter = filter },
                                    shape = CircleShape,
                                    color = if (isSelected) appBlue else cardSubtle,
                                    modifier = Modifier.bounceClick()
                                ) {
                                    Text(
                                        text = filter,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else textPrimary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
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
                if (uiState.isLoadingApps) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = appBlue)
                            Text("Loading installed applications...", fontSize = 13.sp, color = textMuted)
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching apps found", color = textMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredApps) { app ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick()
                                    .clickable { selectedAppForDetails = app },
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = cardColor),
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
                                        // App Icon
                                        val pm = context.packageManager
                                        val iconBitmap = remember(app.packageName) {
                                            try {
                                                pm.getApplicationIcon(app.packageName).toBitmap()
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }

                                        if (iconBitmap != null) {
                                            Image(
                                                bitmap = iconBitmap.asImageBitmap(),
                                                contentDescription = app.name,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(appBlue.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = appBlue, modifier = Modifier.size(24.dp))
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = app.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (app.isSystemApp) {
                                                    Surface(shape = CircleShape, color = cardSubtle) {
                                                        Text("SYS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "${app.packageName} • v${app.versionName}",
                                                fontSize = 11.sp,
                                                color = textMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatFileSize(app.sizeBytes),
                                                fontSize = 10.sp,
                                                color = appBlue,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(
                                            onClick = {
                                                viewModel.backupAppDetailsApk(app) { success, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(cardSubtle).bounceClick()
                                        ) {
                                            Icon(Icons.Default.SaveAlt, contentDescription = "Backup APK", tint = appBlue, modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = { selectedAppForDetails = app },
                                            modifier = Modifier.size(36.dp).clip(CircleShape).bounceClick()
                                        ) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted, modifier = Modifier.size(20.dp))
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

    // App Details Bottom Sheet / Dialog
    selectedAppForDetails?.let { app ->
        AlertDialog(
            onDismissRequest = { selectedAppForDetails = null },
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pm = context.packageManager
                    val iconBitmap = remember(app.packageName) {
                        try {
                            pm.getApplicationIcon(app.packageName).toBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap.asImageBitmap(),
                            contentDescription = app.name,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Column {
                        Text(app.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = textPrimary)
                        Text(app.packageName, fontSize = 11.sp, color = textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Version: ${app.versionName} (${app.versionCode})", fontSize = 12.sp, color = textPrimary)
                    Text("APK Size: ${formatFileSize(app.sizeBytes)}", fontSize = 12.sp, color = textPrimary)
                    Text("Source Path: ${app.apkPath}", fontSize = 11.sp, color = textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (app.firstInstallTime > 0) {
                        val installDate = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(app.firstInstallTime))
                        Text("Installed: $installDate", fontSize = 11.sp, color = textMuted)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Backup APK
                        Button(
                            onClick = {
                                viewModel.backupAppDetailsApk(app) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).bounceClick()
                        ) {
                            Text("Backup APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share APK
                        Button(
                            onClick = {
                                try {
                                    val apkFile = File(app.apkPath)
                                    if (apkFile.exists()) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/vnd.android.package-archive"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share APK '${app.name}' via..."))
                                    } else {
                                        Toast.makeText(context, "APK file not accessible", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cardSubtle),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).bounceClick()
                        ) {
                            Text("Share APK", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Uninstall Button (If not system app)
                    if (!app.isSystemApp) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Intent.ACTION_DELETE).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = appRed.copy(alpha = 0.12f)),
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().bounceClick()
                        ) {
                            Text("Uninstall App", color = appRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAppForDetails = null }) {
                    Text("Close", color = textMuted, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
