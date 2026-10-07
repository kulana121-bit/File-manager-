package com.example.filesapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.filesapp.data.AndroidFileModel

/**
 * Google Files style comprehensive Storage Analyzer modal:
 * - Storage usage gauges & progress bars
 * - Category breakdown with one-tap navigation to actionable files
 * - Largest files list with preview and delete actions
 * - 3-tier duplicate files detector card
 * - Empty folders cleanup tool
 */
@Composable
fun GoogleFilesStorageAnalyzerDialog(
    viewModel: FileManagerViewModel,
    onDismiss: () -> Unit,
    onNavigateCategory: (String) -> Unit,
    onOpenFile: (AndroidFileModel) -> Unit,
    onOpenDuplicates: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isDark = uiState.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF22201D) else Color(0xFFFAF7F2)
    val textPrimary = if (isDark) Color(0xFFF2ECE4) else Color(0xFF2C2621)
    val textMuted = if (isDark) Color(0xFFA69E94) else Color(0xFF7A7064)
    val appBlue = Color(0xFF8C533E)
    val accentSecondary = Color(0xFFB57C58)
    val breakdown = uiState.storageBreakdown

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Largest Files, 2: Empty Folders

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(appBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = appBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Storage Analyzer",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "Google Files Style Deep Cleaner",
                                fontSize = 12.sp,
                                color = textMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x11000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("Overview", "Largest Files (${uiState.largestFiles.size})", "Empty Folders (${uiState.emptyFolders.size})")
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) appBlue else if (isDark) Color(0x1FFFFFFF) else Color(0x0A000000)
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabTitle,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Content Area
                when (selectedTab) {
                    0 -> {
                        // Overview Tab
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Main Storage Gauge Card
                            item {
                                if (breakdown != null) {
                                    val usedFrac = if (breakdown.totalSpaceBytes > 0)
                                        breakdown.usedSpaceBytes.toFloat() / breakdown.totalSpaceBytes
                                    else 0f
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) Color(0xFF2A2723) else Color(0xFFF3ECE1)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "${(usedFrac * 100).toInt()}% Used",
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = textPrimary
                                                    )
                                                    Text(
                                                        text = "${formatFileSize(breakdown.freeSpaceBytes)} free of ${formatFileSize(breakdown.totalSpaceBytes)}",
                                                        fontSize = 12.sp,
                                                        color = textMuted
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(appBlue.copy(alpha = 0.15f))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "${breakdown.totalFileCount} files",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = appBlue
                                                    )
                                                }
                                            }

                                            LinearProgressIndicator(
                                                progress = { usedFrac },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(5.dp)),
                                                color = appBlue,
                                                trackColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Clean Cards Row: Duplicates & Empty Folders
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Duplicates Card
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) Color(0xFF2A2723) else Color(0xFFF3ECE1)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onDismiss()
                                                onOpenDuplicates()
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = appBlue, modifier = Modifier.size(18.dp))
                                                Text("Duplicates", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                            }
                                            Text(
                                                text = if (uiState.totalDuplicateWastedBytes > 0)
                                                    "${formatFileSize(uiState.totalDuplicateWastedBytes)} wasted"
                                                else "Scan to clean",
                                                fontSize = 11.sp,
                                                color = textMuted
                                            )
                                        }
                                    }

                                    // Empty Folders Card
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) Color(0xFF2A2723) else Color(0xFFF3ECE1)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedTab = 2 }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.FolderOff, contentDescription = null, tint = accentSecondary, modifier = Modifier.size(18.dp))
                                                Text("Empty Folders", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
                                            }
                                            Text(
                                                text = "${uiState.emptyFolders.size} detected",
                                                fontSize = 11.sp,
                                                color = textMuted
                                            )
                                        }
                                    }
                                }
                            }

                            // Category Breakdown List (Actionable - Tapping Navigates!)
                            item {
                                Text(
                                    text = "Categories (Tap to view files)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = textPrimary
                                )
                            }

                            if (breakdown != null) {
                                val categories = listOf(
                                    CategoryItemData("images", "Images", breakdown.imageSizeBytes, Icons.Default.Image, Color(0xFFE57373)),
                                    CategoryItemData("videos", "Videos", breakdown.videoSizeBytes, Icons.Default.Videocam, Color(0xFF64B5F6)),
                                    CategoryItemData("audio", "Audio", breakdown.audioSizeBytes, Icons.Default.MusicNote, Color(0xFF81C784)),
                                    CategoryItemData("docs", "Documents", breakdown.docSizeBytes, Icons.Default.Description, Color(0xFFFFB74D)),
                                    CategoryItemData("apks", "Installed Apps & APKs", breakdown.apkSizeBytes, Icons.Default.Android, Color(0xFF4DB6AC)),
                                    CategoryItemData("archives", "Archives (ZIP/7Z/RAR)", breakdown.archiveSizeBytes, Icons.Default.FolderZip, Color(0xFFBA68C8)),
                                    CategoryItemData("large", "Large Files (>50MB)", uiState.largestFiles.sumOf { it.size }, Icons.Default.Warning, Color(0xFFFF8A65))
                                )

                                items(categories) { cat ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) Color(0xFF2A2723) else Color(0xFFF3ECE1)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onDismiss()
                                                onNavigateCategory(cat.id)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(cat.color.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = cat.icon,
                                                        contentDescription = null,
                                                        tint = cat.color,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Text(
                                                    text = cat.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = textPrimary
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = formatFileSize(cat.sizeBytes),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = textMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }

                    1 -> {
                        // Largest Files Tab
                        if (uiState.largestFiles.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No large files found", color = textMuted)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.largestFiles) { file ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) Color(0xFF2A2723) else Color(0xFFF3ECE1)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onDismiss()
                                                onOpenFile(file)
                                            }
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
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(appBlue.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.InsertDriveFile,
                                                        contentDescription = null,
                                                        tint = appBlue,
                                                        modifier = Modifier.size(18.dp)
                                                    )
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
                                                        text = file.path,
                                                        fontSize = 11.sp,
                                                        color = textMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Text(
                                                text = formatFileSize(file.size),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appBlue
                                            )
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }
                    }

                    2 -> {
                        // Empty Folders Tab
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (uiState.emptyFolders.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        viewModel.cleanEmptyFolders { deletedCount ->
                                            Toast.makeText(context, "Cleaned $deletedCount empty folder(s)", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Clean All ${uiState.emptyFolders.size} Empty Folders", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (uiState.emptyFolders.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No empty folders detected", color = textMuted)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.emptyFolders) { item ->
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isDark) Color(0xFF2A2723) else Color(0xFFF3ECE1)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
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
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = accentSecondary)
                                                    Column {
                                                        Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                                                        Text(item.path, fontSize = 11.sp, color = textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        }
    }
}

private data class CategoryItemData(
    val id: String,
    val title: String,
    val sizeBytes: Long,
    val icon: ImageVector,
    val color: Color
)
