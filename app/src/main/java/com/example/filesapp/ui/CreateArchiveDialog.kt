package com.example.filesapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filesapp.domain.archive.ArchiveFormat
import com.example.filesapp.domain.archive.CompressionLevel
import java.io.File

/**
 * ZArchiver style archive creation dialog supporting ZIP and 7Z formats
 * with Store (0), Normal (5), and Maximum (9) compression levels.
 */
@Composable
fun CreateArchiveDialog(
    selectedFiles: List<File>,
    defaultName: String = "Archive",
    targetDirectory: File,
    onDismiss: () -> Unit,
    onCreateArchive: (format: ArchiveFormat, name: String, level: CompressionLevel, password: String?) -> Unit
) {
    var archiveName by remember { mutableStateOf(defaultName) }
    var selectedFormat by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var selectedLevel by remember { mutableStateOf(CompressionLevel.NORMAL) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val appBlue = Color(0xFF8C533E)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(appBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                }
                Text("Create Archive", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Name Input
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text("Archive Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Format Selector (ZIP vs 7Z)
                Text("Archive Format:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z).forEach { fmt ->
                        val isSel = selectedFormat == fmt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) appBlue else Color(0x0F000000))
                                .clickable { selectedFormat = fmt }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (fmt == ArchiveFormat.ZIP) "ZIP (.zip)" else "7-Zip (.7z)",
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else Color.Unspecified
                            )
                        }
                    }
                }

                // Compression Level Selector
                Text("Compression Level:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompressionLevel.values().forEach { level ->
                        val isSel = selectedLevel == level
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedLevel = level }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = { selectedLevel = level },
                                colors = RadioButtonDefaults.colors(selectedColor = appBlue)
                            )
                            Text(level.title, fontSize = 12.sp)
                        }
                    }
                }

                Text(
                    text = "${selectedFiles.size} item(s) selected for compression",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                // Password (optional, ZIP only - ZArchiver parity)
                if (selectedFormat == ArchiveFormat.ZIP) {
                    Text("Password (optional):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Leave empty for no password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "Show", fontSize = 11.sp)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (archiveName.isNotBlank()) {
                        val pwd = password.takeIf { it.isNotBlank() }
                        onCreateArchive(selectedFormat, archiveName.trim(), selectedLevel, pwd)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = appBlue)
            ) {
                Text("Compress", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
