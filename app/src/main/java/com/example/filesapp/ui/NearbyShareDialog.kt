package com.example.filesapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Nearby Share dialog - Google Files parity.
 * Send files to nearby devices without internet.
 */
@Composable
fun NearbyShareDialog(
    viewModel: FileManagerViewModel,
    fileToShare: File?,
    onDismiss: () -> Unit
) {
    val nearbyDevices by viewModel.nearbyDevices.collectAsState()
    val nearbyLogs by viewModel.nearbyLogs.collectAsState()
    val nearbyConnected by viewModel.nearbyConnected.collectAsState()

    var isAdvertising by remember { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf(android.os.Build.MODEL ?: "Files User") }

    val appBlue = Color(0xFF8C533E)

    // Cleanup on dismiss
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopNearby()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(appBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                    }
                    Text("Nearby Share", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (fileToShare != null) {
                    Text(
                        "Sharing: ${fileToShare.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = appBlue
                    )
                }

                // Mode buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isAdvertising) {
                                viewModel.stopNearby()
                                isAdvertising = false
                            } else {
                                viewModel.startNearbyAdvertising(deviceName)
                                isAdvertising = true
                                isDiscovering = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAdvertising) Color.Gray else appBlue
                        )
                    ) {
                        Text(if (isAdvertising) "Stop" else "Receive", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (isDiscovering) {
                                viewModel.stopNearby()
                                isDiscovering = false
                            } else {
                                viewModel.startNearbyDiscovery()
                                isDiscovering = true
                                isAdvertising = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDiscovering) Color.Gray else appBlue
                        )
                    ) {
                        Text(if (isDiscovering) "Stop" else "Send", fontSize = 12.sp)
                    }
                }

                // Discovered devices
                if (nearbyDevices.isNotEmpty()) {
                    Text("Nearby devices:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(nearbyDevices) { (id, name) ->
                            val isConnected = nearbyConnected.containsKey(id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x0F000000))
                                    .clickable {
                                        if (!isConnected) {
                                            viewModel.connectNearby(id, deviceName)
                                        } else if (fileToShare != null) {
                                            viewModel.sendFileNearby(id, fileToShare)
                                        }
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (isConnected) "Connected - tap to send"
                                        else "Tap to connect",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (isConnected && fileToShare != null) {
                                    Icon(Icons.Default.Send, contentDescription = "Send", tint = appBlue)
                                }
                            }
                        }
                    }
                } else if (isDiscovering || isAdvertising) {
                    Text(
                        "Looking for devices... Make sure the other device has Nearby Share open.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Logs
                if (nearbyLogs.isNotEmpty()) {
                    Text("Status:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 100.dp)
                    ) {
                        items(nearbyLogs.takeLast(5)) { log ->
                            Text(log, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = { }
    )
}
