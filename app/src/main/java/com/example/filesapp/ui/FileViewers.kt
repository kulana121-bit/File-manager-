package com.example.filesapp.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.VideoView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.filesapp.data.AndroidFileModel
import com.example.filesapp.ui.theme.PrimaryAccentTaupe
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipFile
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Global File Utility Helpers
 */
fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun shareFile(context: android.content.Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifEmpty { "*/*" }
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

fun editFile(context: android.content.Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, mimeType.ifEmpty { "image/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Edit '${file.name}' with...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Edit failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 1. PREMIUM IMAGE VIEWER with Zoom, Top Controls & BitmapFactory Resolution scanner.
 */
@Composable
fun ImageViewerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(file.path))
                    .crossfade(true)
                    .build(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            )

            // One UI Style Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { shareFile(context, File(file.path), file.mimeType) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = { editFile(context, File(file.path), file.mimeType) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Information", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        val fileObj = File(file.path)
        val imageDetails = remember(file.path) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            val resolution = if (options.outWidth > 0 && options.outHeight > 0) "${options.outWidth} x ${options.outHeight}" else "Unknown"
            val sizeFormatted = formatFileSize(fileObj.length())
            val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(fileObj.lastModified()))
            Triple(resolution, sizeFormatted, dateFormatted)
        }

        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Image Information", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${file.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Resolution: ${imageDetails.first}", fontSize = 13.sp)
                    Text("File Size: ${imageDetails.second}", fontSize = 13.sp)
                    Text("Last Modified: ${imageDetails.third}", fontSize = 13.sp)
                    Text("Path: ${file.path}", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }
}

/**
 * 2. PREMIUM VIDEO PLAYER with custom controls overlay, seek progress, skip 10s, auto-hide controls.
 */
@Composable
fun VideoPlayerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    val activity = context as? android.app.Activity
    var isLandscapeMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Controls Auto-Hide timer
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Polling track progress
    LaunchedEffect(videoViewRef, isPlaying) {
        while (isPlaying) {
            videoViewRef?.let {
                currentPosition = it.currentPosition
                duration = it.duration
            }
            delay(250)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { controlsVisible = !controlsVisible },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(file.path)
                        setOnPreparedListener { mp ->
                            videoViewRef = this
                            duration = mp.duration
                            mp.isLooping = false
                            start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            currentPosition = duration
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Elegant Custom Controls
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = file.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    // Play/Skip Center controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                videoViewRef?.let {
                                    val target = (it.currentPosition - 10000).coerceAtLeast(0)
                                    it.seekTo(target)
                                    currentPosition = target
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(20.dp))
                                Text("-10s", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = {
                                videoViewRef?.let {
                                    if (it.isPlaying) {
                                        it.pause()
                                        isPlaying = false
                                    } else {
                                        it.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.White, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                videoViewRef?.let {
                                    val target = (it.currentPosition + 10000).coerceAtMost(duration)
                                    it.seekTo(target)
                                    currentPosition = target
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(20.dp))
                                Text("+10s", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Bottom Control Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatVideoTime(currentPosition), color = Color.White, fontSize = 12.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(formatVideoTime(duration), color = Color.White, fontSize = 12.sp)
                                IconButton(
                                    onClick = {
                                        activity?.let {
                                            if (isLandscapeMode) {
                                                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                isLandscapeMode = false
                                            } else {
                                                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                isLandscapeMode = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLandscapeMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Toggle Fullscreen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { fraction ->
                                videoViewRef?.let {
                                    val target = (fraction * duration).toInt()
                                    it.seekTo(target)
                                    currentPosition = target
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF007AFF)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

fun formatVideoTime(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

/**
 * 3. PREMIUM AUDIO PLAYER with interactive cover art design and controls.
 */
@Composable
fun AudioPlayerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var currentPosition by remember { mutableIntStateOf(0) }

    val mediaPlayer = remember {
        MediaPlayer().apply {
            try {
                setDataSource(file.path)
                prepare()
                duration = this.duration
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer.currentPosition
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Now Playing",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF007AFF).copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = file.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatVideoTime(currentPosition), fontSize = 11.sp, color = Color.Gray)
                    Text(formatVideoTime(duration), fontSize = 11.sp, color = Color.Gray)
                }

                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { fraction ->
                        val target = (fraction * duration).toInt()
                        mediaPlayer.seekTo(target)
                        currentPosition = target
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF007AFF),
                        activeTrackColor = Color(0xFF007AFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    IconButton(
                        onClick = {
                            val target = (currentPosition - 10000).coerceAtLeast(0)
                            mediaPlayer.seekTo(target)
                            currentPosition = target
                        }
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color(0xFF007AFF), modifier = Modifier.size(28.dp))
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF007AFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = (currentPosition + 10000).coerceAtMost(duration)
                            mediaPlayer.seekTo(target)
                            currentPosition = target
                        }
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", tint = Color(0xFF007AFF), modifier = Modifier.size(28.dp))
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * 4. PREMIUM PDF VIEWER with high-performance Dispatchers.IO page rendering.
 */
@Composable
fun PdfViewerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(false) }

    LaunchedEffect(currentPageIndex, file.path) {
        isRendering = true
        currentBitmap = null
        withContext(Dispatchers.IO) {
            try {
                val fileObj = File(file.path)
                if (fileObj.exists()) {
                    ParcelFileDescriptor.open(fileObj, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { pdfRenderer ->
                            totalPages = pdfRenderer.pageCount.coerceAtLeast(1)
                            val safePageIndex = currentPageIndex.coerceIn(0, totalPages - 1)
                            pdfRenderer.openPage(safePageIndex).use { page ->
                                // upscale to 2.0x for crisp reading experience on high density displays
                                val scaleFactor = 2
                                val bitmap = Bitmap.createBitmap(page.width * scaleFactor, page.height * scaleFactor, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                currentBitmap = bitmap
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRendering = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // One UI top bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F4F7),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1C1E))
                    }

                    Text(
                        text = file.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    IconButton(onClick = { shareFile(context, File(file.path), file.mimeType) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = Color(0xFF007AFF))
                    }
                }
            }

            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            LaunchedEffect(currentPageIndex) {
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFE5E5EA))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isRendering || currentBitmap == null) {
                    CircularProgressIndicator(color = Color(0xFF007AFF))
                } else {
                    currentBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPageIndex + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .padding(12.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Page Indicator Navigation
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F4F7),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page", tint = if (currentPageIndex > 0) Color(0xFF007AFF) else Color.Gray)
                    }

                    Text(
                        text = "Page ${currentPageIndex + 1} of $totalPages",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E)
                    )

                    IconButton(
                        onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                        enabled = currentPageIndex < totalPages - 1
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Page", tint = if (currentPageIndex < totalPages - 1) Color(0xFF007AFF) else Color.Gray)
                    }
                }
            }
        }
    }
}

/**
 * 5. PREMIUM HTML VIEWER with external browser redirect option.
 */
@Composable
fun HtmlViewerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F4F7),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1C1E))
                    }

                    Text(
                        text = file.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { shareFile(context, File(file.path), file.mimeType) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF007AFF))
                        }
                        IconButton(onClick = { openHtmlInBrowser(context, File(file.path)) }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser", tint = Color(0xFF007AFF))
                        }
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        loadUrl("file://${file.path}")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

fun openHtmlInBrowser(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open in Browser...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open in browser: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 6. PREMIUM CSV TABLE VIEWER with high-contrast columns grids.
 */
@Composable
fun CsvViewerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    val fileObj = File(file.path)
    val csvData = remember(file.path) {
        try {
            if (fileObj.exists()) {
                fileObj.readLines().map { line ->
                    line.split(",").map { it.trim().removeSurrounding("\"") }
                }
            } else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F4F7),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1C1E))
                    }

                    Text(
                        text = file.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (csvData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Could not parse CSV or file is empty", color = Color.Gray)
                }
            } else {
                val horizontalScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                        .padding(16.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(csvData) { index, row ->
                            val isHeader = index == 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isHeader) Color(0xFFE5E5EA)
                                        else if (index % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .padding(vertical = 10.dp)
                            ) {
                                row.forEach { cell ->
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = cell,
                                            fontSize = 13.sp,
                                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isHeader) Color.Black else MaterialTheme.colorScheme.onBackground,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7. PREMIUM MARKDOWN VIEWER with robust visual styling cards.
 */
@Composable
fun MarkdownViewerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    val fileObj = File(file.path)
    val mdLines = remember(file.path) {
        try {
            if (fileObj.exists()) fileObj.readLines() else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F4F7),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1C1E))
                    }

                    Text(
                        text = file.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (mdLines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Empty Markdown file", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mdLines) { line ->
                        val trimmed = line.trim()
                        when {
                            trimmed.startsWith("# ") -> {
                                Text(
                                    text = trimmed.removePrefix("# "),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            trimmed.startsWith("## ") -> {
                                Text(
                                    text = trimmed.removePrefix("## "),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            trimmed.startsWith("### ") -> {
                                Text(
                                    text = trimmed.removePrefix("### "),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                Row(
                                    modifier = Modifier.padding(start = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("•", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
                                    Text(
                                        text = parseMdSpans(trimmed.substring(2)),
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            trimmed.startsWith("> ") -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(26.dp)
                                            .background(Color(0xFF007AFF))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = parseMdSpans(trimmed.removePrefix("> ")),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            trimmed.isBlank() -> {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            else -> {
                                Text(
                                    text = parseMdSpans(trimmed),
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseMdSpans(text: String): String {
    return text.replace("**", "").replace("*", "").replace("`", "")
}

/**
 * 8. PREMIUM EPUB E-BOOK READER with crisp formatting.
 */
@Composable
fun EpubReaderDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    val fileObj = File(file.path)
    var epubChapters by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file.path) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                if (fileObj.exists()) {
                    val chapters = mutableListOf<Pair<String, String>>()
                    ZipFile(fileObj).use { zipFile ->
                        val entries = zipFile.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name.lowercase()
                            if (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) {
                                zipFile.getInputStream(entry).use { isStream ->
                                    BufferedReader(InputStreamReader(isStream)).use { reader ->
                                        val sb = StringBuilder()
                                        var line: String?
                                        while (reader.readLine().also { line = it } != null) {
                                            sb.append(line).append("\n")
                                        }
                                        val rawText = sb.toString()
                                            .replace(Regex("<head>[\\s\\S]*?</head>"), "")
                                            .replace(Regex("<[^>]*>"), "")
                                            .replace("&nbsp;", " ")
                                            .replace("&amp;", "&")
                                            .replace("&lt;", "<")
                                            .replace("&gt;", ">")
                                            .replace(Regex("\\n{3,}"), "\n\n")
                                            .trim()

                                        val title = name.substringAfterLast("/").substringBeforeLast(".")
                                            .replace("_", " ").capitalize(Locale.ROOT)

                                        if (rawText.isNotBlank()) {
                                            chapters.add(Pair(title, rawText))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    chapters.sortBy { it.first }
                    epubChapters = chapters
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F4F7),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1C1E))
                    }

                    Text(
                        text = file.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF007AFF))
                }
            } else if (epubChapters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Could not read EPUB content or e-book is empty.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                }
            } else {
                val chapter = epubChapters[currentChapterIndex]
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Chapter: ${chapter.first}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF007AFF),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = chapter.second,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentChapterIndex > 0) currentChapterIndex-- },
                            enabled = currentChapterIndex > 0
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = if (currentChapterIndex > 0) Color(0xFF007AFF) else Color.Gray)
                        }

                        Text(
                            text = "${currentChapterIndex + 1} of ${epubChapters.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        IconButton(
                            onClick = { if (currentChapterIndex < epubChapters.size - 1) currentChapterIndex++ },
                            enabled = currentChapterIndex < epubChapters.size - 1
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = if (currentChapterIndex < epubChapters.size - 1) Color(0xFF007AFF) else Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 9. PREMIUM ARCHIVE TREE BROWSER (without extracting)
 */
data class ArchiveItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val entry: java.util.zip.ZipEntry?
)

@Composable
fun ArchiveBrowserDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit,
    onExtractAll: () -> Unit
) {
    val context = LocalContext.current
    var currentPrefix by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<java.util.zip.ZipEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Active inner preview file details
    var activePreviewFile by remember { mutableStateOf<AndroidFileModel?>(null) }

    val zipFileRef = remember {
        try {
            ZipFile(File(file.path))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    LaunchedEffect(file.path) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                zipFileRef?.let { zip ->
                    entries = zip.entries().asSequence().toList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                zipFileRef?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Dynamic filtering for virtual tree directories list
    val currentItems = remember(entries, currentPrefix) {
        val items = mutableSetOf<ArchiveItem>()
        entries.forEach { entry ->
            val name = entry.name
            if (name.startsWith(currentPrefix)) {
                val relative = name.removePrefix(currentPrefix)
                if (relative.isNotEmpty()) {
                    val parts = relative.split('/')
                    val firstPart = parts[0]
                    if (parts.size > 1 || entry.isDirectory) {
                        items.add(
                            ArchiveItem(
                                name = firstPart,
                                path = currentPrefix + firstPart + "/",
                                isDirectory = true,
                                size = 0L,
                                entry = null
                            )
                        )
                    } else {
                        items.add(
                            ArchiveItem(
                                name = firstPart,
                                path = name,
                                isDirectory = false,
                                size = entry.size,
                                entry = entry
                            )
                        )
                    }
                }
            }
        }
        items.toList().sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // One UI styling header bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F4F7),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                if (currentPrefix.isNotEmpty()) {
                                    val trimmed = currentPrefix.removeSuffix("/")
                                    val index = trimmed.lastIndexOf('/')
                                    currentPrefix = if (index >= 0) trimmed.substring(0, index + 1) else ""
                                } else {
                                    onDismiss()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C1C1E))
                        }

                        Column {
                            Text(
                                text = file.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1C1E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (currentPrefix.isEmpty()) "Archive Root" else "Root / ${currentPrefix.removeSuffix("/")}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = onExtractAll,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Extract All", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF007AFF))
                }
            } else {
                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (currentItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This directory folder is empty", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(currentItems) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (item.isDirectory) {
                                                currentPrefix = item.path
                                            } else {
                                                zipFileRef?.let { zip ->
                                                    item.entry?.let { zipEntry ->
                                                        try {
                                                            val previewDir = File(context.cacheDir, "zip_previews")
                                                            if (!previewDir.exists()) previewDir.mkdirs()
                                                            val tempFile = File(previewDir, zipEntry.name.substringAfterLast("/"))
                                                            tempFile.deleteOnExit()

                                                            zip.getInputStream(zipEntry).use { input ->
                                                                tempFile.outputStream().use { output ->
                                                                    input.copyTo(output)
                                                                }
                                                            }

                                                            activePreviewFile = AndroidFileModel(
                                                                id = tempFile.absolutePath,
                                                                name = tempFile.name,
                                                                path = tempFile.absolutePath,
                                                                size = tempFile.length(),
                                                                mimeType = getMimeTypeForZipFile(tempFile),
                                                                dateModified = tempFile.lastModified(),
                                                                isDirectory = false
                                                            )
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                                contentDescription = null,
                                                tint = Color(0xFF007AFF),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!item.isDirectory) {
                                                    Text(formatFileSize(item.size), fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                        }

                                        if (!item.isDirectory) {
                                            IconButton(
                                                onClick = {
                                                    zipFileRef?.let { zip ->
                                                        item.entry?.let { zipEntry ->
                                                            try {
                                                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                                                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                                                val dest = File(downloadsDir, item.name)
                                                                zip.getInputStream(zipEntry).use { input ->
                                                                    dest.outputStream().use { output ->
                                                                        input.copyTo(output)
                                                                    }
                                                                }
                                                                Toast.makeText(context, "Extracted '${item.name}' to Downloads", Toast.LENGTH_SHORT).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Extraction failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = "Extract Single File", tint = Color(0xFF007AFF))
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

    // Inner Active Preview container inside archive dialog
    activePreviewFile?.let { tempPreviewFile ->
        val onPreviewDismiss: () -> Unit = {
            activePreviewFile = null
            try {
                File(tempPreviewFile.path).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val ext = tempPreviewFile.name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "png", "jpg", "jpeg", "webp", "gif" -> ImageViewerDialog(file = tempPreviewFile, onDismiss = onPreviewDismiss)
            "mp4", "mkv" -> VideoPlayerDialog(file = tempPreviewFile, onDismiss = onPreviewDismiss)
            "mp3", "wav", "m4a" -> AudioPlayerDialog(file = tempPreviewFile, onDismiss = onPreviewDismiss)
            "pdf" -> PdfViewerDialog(file = tempPreviewFile, onDismiss = onPreviewDismiss)
            "html", "htm" -> HtmlViewerDialog(file = tempPreviewFile, onDismiss = onPreviewDismiss)
            "csv" -> CsvViewerDialog(file = tempPreviewFile, onDismiss = onPreviewDismiss)
            "md" -> MarkdownViewerDialog(file = tempPreviewFile, onDismiss = onPreviewDismiss)
            "epub" -> EpubReaderDialog(file = tempPreviewFile, onDismiss = onPreviewDismiss)
            else -> {
                AlertDialog(
                    onDismissRequest = onPreviewDismiss,
                    title = { Text(tempPreviewFile.name, fontWeight = FontWeight.Bold) },
                    text = {
                        val textContent = remember(tempPreviewFile.path) {
                            try {
                                val f = File(tempPreviewFile.path)
                                if (f.length() < 1000000L) f.readText() else "File is too large to preview directly."
                            } catch (e: Exception) {
                                "Could not read text content: ${e.message}"
                            }
                        }
                        Box(modifier = Modifier.height(200.dp).verticalScroll(rememberScrollState())) {
                            Text(textContent, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    },
                    confirmButton = {
                        Button(onClick = onPreviewDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))) {
                            Text("OK", color = Color.White)
                        }
                    }
                )
            }
        }
    }
}

fun getMimeTypeForZipFile(file: File): String {
    return when (file.extension.lowercase()) {
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
        "zip" -> "application/zip"
        "html", "htm" -> "text/html"
        else -> "text/plain"
    }
}
