package com.example.filesapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.filesapp.data.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// 1. WIFI FILE TRANSFER DIALOG
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiTransferDialog(
    viewModel: FileManagerViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = uiState.isDarkMode

    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val screenBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val appBlue = Color(0xFF3B82F6)
    val appGreen = Color(0xFF10B981)
    val appRed = Color(0xFFEF4444)
    val borderCol = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Start server automatically if not running
    LaunchedEffect(Unit) {
        if (!uiState.isWifiServerRunning) {
            viewModel.startWifiServer()
        }
    }

    LaunchedEffect(uiState.wifiServerUrl, uiState.isWifiServerRunning) {
        if (uiState.isWifiServerRunning && uiState.wifiServerUrl.isNotEmpty()) {
            qrBitmap = WifiTransferManager.generateQrCodeBitmap(uiState.wifiServerUrl, 512)
        } else {
            qrBitmap = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBg),
            color = screenBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Header Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBg,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimary)
                            }
                            Column {
                                Text("Wi-Fi File Transfer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                Text("Share & download files over LAN", fontSize = 12.sp, color = textMuted)
                            }
                        }

                        // Toggle server status button
                        Button(
                            onClick = {
                                if (uiState.isWifiServerRunning) {
                                    viewModel.stopWifiServer()
                                } else {
                                    viewModel.startWifiServer()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isWifiServerRunning) appRed else appGreen
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                if (uiState.isWifiServerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (uiState.isWifiServerRunning) "Stop Server" else "Start Server",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status & QR Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Status badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (uiState.isWifiServerRunning) appGreen.copy(alpha = 0.12f) else appRed.copy(alpha = 0.12f))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.isWifiServerRunning) appGreen else appRed)
                                    )
                                    Text(
                                        if (uiState.isWifiServerRunning) "HTTP SERVER ACTIVE" else "SERVER STOPPED",
                                        color = if (uiState.isWifiServerRunning) appGreen else appRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                if (uiState.isWifiServerRunning) {
                                    // QR Code
                                    qrBitmap?.let { bmp ->
                                        Box(
                                            modifier = Modifier
                                                .size(200.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White)
                                                .border(2.dp, borderCol, RoundedCornerShape(16.dp))
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "WiFi Transfer QR Code",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    // URL Card with Copy Button
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Open URL in any web browser:", fontSize = 11.sp, color = textMuted)
                                                Text(
                                                    uiState.wifiServerUrl,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = appBlue
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", uiState.wifiServerUrl))
                                                    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp).clip(CircleShape).background(cardBg)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = appBlue, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    Text(
                                        "Ensure both devices are connected to the same Wi-Fi network. Scan the QR code or enter the URL above.",
                                        fontSize = 12.sp,
                                        color = textMuted,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )
                                } else {
                                    Text(
                                        "Tap 'Start Server' above to launch the local HTTP file server and generate your transfer URL & QR code.",
                                        fontSize = 13.sp,
                                        color = textMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Activity / Transfer Logs Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = appBlue, modifier = Modifier.size(18.dp))
                                    Text("Transfer Activity Logs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                }

                                if (uiState.wifiServerLogs.isEmpty()) {
                                    Text(
                                        "No activity yet. Upload or download files from your browser to see live transfer progress here.",
                                        fontSize = 12.sp,
                                        color = textMuted
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        uiState.wifiServerLogs.forEach { log ->
                                            Text(
                                                "• $log",
                                                fontSize = 12.sp,
                                                color = textPrimary,
                                                fontWeight = FontWeight.Medium
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

// ==========================================
// 2. IMAGE TOOLS DIALOG (Compress, Resize, Convert)
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToolsDialog(
    file: AndroidFileModel,
    viewModel: FileManagerViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = uiState.isDarkMode

    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val textPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val appBlue = Color(0xFF3B82F6)
    val borderCol = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    var selectedFormat by remember {
        mutableStateOf(
            when {
                file.name.endsWith(".png", true) -> ImageFormat.PNG
                file.name.endsWith(".webp", true) -> ImageFormat.WEBP
                else -> ImageFormat.JPEG
            }
        )
    }

    var qualitySlider by remember { mutableFloatStateOf(80f) }
    var selectedResizeMode by remember { mutableStateOf(ResizeMode.KEEP_ORIGINAL) }
    var customWidthInput by remember { mutableStateOf("") }
    var customHeightInput by remember { mutableStateOf("") }

    var isProcessing by remember { mutableStateOf(false) }
    var resultSummary by remember { mutableStateOf<ImageProcessResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.PhotoFilter, contentDescription = null, tint = appBlue, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Image Tools", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textPrimary)
                    Text(file.name, fontSize = 11.sp, color = textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info Banner: Original File
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, borderCol),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Size:", fontSize = 12.sp, color = textMuted)
                        Text(
                            "${formatFileSize(file.size)} (${file.size} bytes)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }
                }

                // 1. Convert Format
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. Convert Format", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageFormat.values().forEach { fmt ->
                            val isSelected = selectedFormat == fmt
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) appBlue else (if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedFormat = fmt }
                            ) {
                                Text(
                                    text = fmt.name,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else textPrimary
                                )
                            }
                        }
                    }
                }

                // 2. Compress Quality
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("2. Compression Quality", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        Text("${qualitySlider.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appBlue)
                    }
                    Slider(
                        value = qualitySlider,
                        onValueChange = { qualitySlider = it },
                        valueRange = 10f..100f,
                        steps = 17,
                        colors = SliderDefaults.colors(
                            thumbColor = appBlue,
                            activeTrackColor = appBlue
                        )
                    )
                }

                // 3. Resize Dimension
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("3. Resize Dimensions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            ResizeMode.KEEP_ORIGINAL to "100%",
                            ResizeMode.PERCENT_75 to "75%",
                            ResizeMode.PERCENT_50 to "50%",
                            ResizeMode.PERCENT_25 to "25%",
                            ResizeMode.CUSTOM_PIXELS to "Custom"
                        ).forEach { (mode, label) ->
                            val isSelected = selectedResizeMode == mode
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) appBlue else (if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedResizeMode = mode }
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else textPrimary
                                )
                            }
                        }
                    }

                    if (selectedResizeMode == ResizeMode.CUSTOM_PIXELS) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customWidthInput,
                                onValueChange = { customWidthInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Width px") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customHeightInput,
                                onValueChange = { customHeightInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Height px") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Success Result Preview Card
                resultSummary?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✓ Processed Successfully!", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 13.sp)
                            Text("Output: ${res.outputFile.name}", fontSize = 11.sp, color = textPrimary)
                            Text("Saved Size: ${formatFileSize(res.outputSizeBytes)} (from ${formatFileSize(res.originalSizeBytes)})", fontSize = 11.sp, color = textPrimary)
                            Text("Dimensions: ${res.outputWidth}x${res.outputHeight} px", fontSize = 11.sp, color = textMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isProcessing) return@Button
                    isProcessing = true
                    val options = ImageProcessOptions(
                        resizeMode = selectedResizeMode,
                        targetWidth = customWidthInput.toIntOrNull() ?: 0,
                        targetHeight = customHeightInput.toIntOrNull() ?: 0,
                        targetFormat = selectedFormat,
                        quality = qualitySlider.toInt()
                    )
                    viewModel.processImage(File(file.path), options) { success, result, msg ->
                        isProcessing = false
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success && result != null) {
                            resultSummary = result
                        }
                    }
                },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                shape = CircleShape
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Processing...")
                } else {
                    Text("Save As New Image", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = textMuted)
            }
        }
    )
}

// ==========================================
// 3. FTP/SMB NETWORK STORAGE BROWSER DIALOG
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkStorageDialog(
    viewModel: FileManagerViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = uiState.isDarkMode

    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val screenBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val appBlue = Color(0xFF3B82F6)
    val borderCol = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    var showAddServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadSavedNetworkServers()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBg),
            color = screenBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBg,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimary)
                            }
                            Column {
                                Text(
                                    uiState.activeRemoteServer?.name ?: "Network Storage",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    if (uiState.activeRemoteServer != null) "Remote: ${uiState.currentRemotePath}" else "FTP & SMB Locations",
                                    fontSize = 12.sp,
                                    color = textMuted
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.activeRemoteServer != null) {
                                Button(
                                    onClick = { viewModel.disconnectRemoteServer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Disconnect", fontSize = 12.sp, color = Color.White)
                                }
                            } else {
                                Button(
                                    onClick = { showAddServerDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Server", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // If currently connected to a server -> Show Remote File Browser
                if (uiState.activeRemoteServer != null) {
                    val activeServer = uiState.activeRemoteServer!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Breadcrumb & Actions
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderCol)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (uiState.currentRemotePath != "/" && uiState.currentRemotePath.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val parent = uiState.currentRemotePath.substringBeforeLast("/").ifEmpty { "/" }
                                                viewModel.navigateRemoteFolder(parent)
                                            },
                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Parent Directory", tint = textPrimary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Text(
                                        uiState.currentRemotePath,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.navigateRemoteFolder(uiState.currentRemotePath) },
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = appBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (uiState.isConnectingRemote) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(color = appBlue)
                                    Text("Loading remote directory...", fontSize = 12.sp, color = textMuted)
                                }
                            }
                        } else if (uiState.currentRemoteFiles.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Remote folder is empty", fontSize = 13.sp, color = textMuted)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.currentRemoteFiles, key = { it.path }) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = cardBg,
                                        border = BorderStroke(1.dp, borderCol),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (item.isDirectory) {
                                                    viewModel.navigateRemoteFolder(item.path)
                                                }
                                            }
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
                                                        .clip(CircleShape)
                                                        .background(if (item.isDirectory) appBlue.copy(alpha = 0.12f) else (if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        if (item.isDirectory) Icons.Default.Folder else Icons.Outlined.InsertDriveFile,
                                                        contentDescription = null,
                                                        tint = if (item.isDirectory) appBlue else textMuted,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Column {
                                                    Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(
                                                        if (item.isDirectory) "Directory" else formatFileSize(item.size),
                                                        fontSize = 11.sp,
                                                        color = textMuted
                                                    )
                                                }
                                            }

                                            if (!item.isDirectory) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.downloadRemoteFile(item) { success, msg ->
                                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.1f))
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = "Download", tint = appBlue, modifier = Modifier.size(18.dp))
                                                }
                                            } else {
                                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Show Saved Servers List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "Configured Servers",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }

                        if (uiState.remoteStatusMessage != null) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        uiState.remoteStatusMessage!!,
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        if (uiState.savedNetworkServers.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = cardBg,
                                    border = BorderStroke(1.dp, borderCol),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(56.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Outlined.Dns, contentDescription = null, tint = appBlue, modifier = Modifier.size(28.dp))
                                        }
                                        Text("No Network Servers Added", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimary)
                                        Text(
                                            "Add an FTP or SMB server to browse remote directories and seamlessly upload/download files.",
                                            fontSize = 12.sp,
                                            color = textMuted,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(
                                            onClick = { showAddServerDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                                            shape = CircleShape
                                        ) {
                                            Text("Add New Server", color = Color.White)
                                        }
                                    }
                                }
                            }
                        } else {
                            items(uiState.savedNetworkServers, key = { it.id }) { server ->
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = cardBg,
                                    border = BorderStroke(1.dp, borderCol),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(44.dp).clip(CircleShape).background(appBlue.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (server.protocol == ServerProtocol.FTP) Icons.Default.Dns else Icons.Default.Storage,
                                                    contentDescription = null,
                                                    tint = appBlue,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }

                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(server.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
                                                    ) {
                                                        Text(server.protocol.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = appBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                }
                                                Text("${server.username}@${server.host}:${server.port}", fontSize = 12.sp, color = textMuted)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Button(
                                                onClick = { viewModel.connectToNetworkServer(server) },
                                                colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                                                shape = CircleShape,
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text("Connect", fontSize = 12.sp, color = Color.White)
                                            }

                                            IconButton(
                                                onClick = { viewModel.deleteNetworkServer(server.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
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

    if (showAddServerDialog) {
        var serverName by remember { mutableStateOf("") }
        var protocol by remember { mutableStateOf(ServerProtocol.FTP) }
        var host by remember { mutableStateOf("") }
        var portStr by remember { mutableStateOf("21") }
        var username by remember { mutableStateOf("anonymous") }
        var password by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddServerDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Add Network Server", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("Display Name (e.g. My Home NAS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Protocol toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ServerProtocol.values().forEach { proto ->
                            val isSel = protocol == proto
                            Surface(
                                shape = CircleShape,
                                color = if (isSel) appBlue else (if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)),
                                modifier = Modifier.weight(1f).clickable {
                                    protocol = proto
                                    if (proto == ServerProtocol.FTP && portStr == "445") portStr = "21"
                                    if (proto == ServerProtocol.SMB && portStr == "21") portStr = "445"
                                }
                            ) {
                                Text(
                                    proto.name,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else textPrimary
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host / IP Address") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = portStr,
                            onValueChange = { portStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (host.isNotBlank()) {
                            val config = NetworkServerConfig(
                                name = serverName.ifBlank { host },
                                protocol = protocol,
                                host = host.trim(),
                                port = portStr.toIntOrNull() ?: if (protocol == ServerProtocol.FTP) 21 else 445,
                                username = username.ifBlank { "anonymous" },
                                password = password,
                                initialPath = "/"
                            )
                            viewModel.saveNetworkServer(config)
                            showAddServerDialog = false
                        } else {
                            Toast.makeText(context, "Host cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appBlue),
                    shape = CircleShape
                ) {
                    Text("Save Server", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddServerDialog = false }) {
                    Text("Cancel", color = textMuted)
                }
            }
        )
    }
}
