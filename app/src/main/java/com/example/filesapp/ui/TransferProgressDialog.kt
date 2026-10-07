package com.example.filesapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.filesapp.domain.operations.OperationType
import com.example.filesapp.domain.operations.TransferProgress

/**
 * Production-grade transfer progress dialog showing:
 * - Percentage progress bar
 * - Transferred bytes / Total bytes
 * - Current file name
 * - Real-time throughput (e.g. 15.4 MB/s)
 * - Estimated Time Remaining (ETA)
 * - Cancel button
 */
@Composable
fun TransferProgressDialog(
    progress: TransferProgress,
    onCancel: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val surfaceColor = if (isDark) Color(0xFF22201D) else Color(0xFFFAF7F2)
    val textPrimary = if (isDark) Color(0xFFF2ECE4) else Color(0xFF2C2621)
    val textMuted = if (isDark) Color(0xFFA69E94) else Color(0xFF7A7064)
    val appBlue = Color(0xFF8C533E) // Warm terra/accent
    val accentSecondary = Color(0xFFB57C58)

    Dialog(
        onDismissRequest = { /* Non-dismissable during critical file operations */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, Color.White.copy(alpha = if (isDark) 0.1f else 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Icon & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(appBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (progress.operationType == OperationType.MOVE) Icons.Default.DriveFileMove else Icons.Default.FileCopy,
                                contentDescription = null,
                                tint = appBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (progress.operationType == OperationType.MOVE) "Moving Files..." else "Copying Files...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = textPrimary
                            )
                            if (progress.totalFiles > 1) {
                                Text(
                                    text = "File ${progress.processedFiles + 1} of ${progress.totalFiles}",
                                    fontSize = 12.sp,
                                    color = textMuted
                                )
                            }
                        }
                    }

                    // Cancel button in header
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Operation",
                            tint = textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Current file name
                Text(
                    text = progress.currentFileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (progress.isIndeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = appBlue,
                            trackColor = appBlue.copy(alpha = 0.2f)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = appBlue,
                            trackColor = appBlue.copy(alpha = 0.2f)
                        )
                    }

                    // Bytes & Percentage Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${TransferProgress.formatBytes(progress.transferredBytes)} / ${TransferProgress.formatBytes(progress.totalBytes)}",
                            fontSize = 11.sp,
                            color = textMuted
                        )
                        Text(
                            text = "${(progress.progressFraction * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = appBlue
                        )
                    }
                }

                // Speed & ETA Metrics Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0x22FFFFFF) else Color(0x0A000000))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = accentSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = progress.speedFormatted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                    }

                    if (progress.etaFormatted.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = accentSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = progress.etaFormatted,
                                fontSize = 11.sp,
                                color = textMuted
                            )
                        }
                    }
                }

                // Cancel Button Footer
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF38322B) else Color(0xFFECE4D8),
                        contentColor = textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel Transfer",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
