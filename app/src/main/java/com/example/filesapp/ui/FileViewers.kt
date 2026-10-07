package com.example.filesapp.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

/**
 * 1. Image Viewer with Coil AsyncImage and Gesture Pinch-Zoom / Pan.
 */
@Composable
fun ImageViewerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
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

            // Top Header Bar
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
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
        }
    }
}

/**
 * 2. Video Player using VideoView wrapped in AndroidView with MediaController controls.
 */
@Composable
fun VideoPlayerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
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
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        val mediaController = MediaController(context)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)
                        setVideoPath(file.path)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/**
 * 3. Audio Player using MediaPlayer with Play/Pause state and Auto-Release.
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = PrimaryAccentTaupe)
                Text(file.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = PrimaryAccentTaupe.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PrimaryAccentTaupe, modifier = Modifier.size(40.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        .clip(CircleShape)
                        .background(PrimaryAccentTaupe)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccentTaupe)
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * 4. PDF Viewer using Android PdfRenderer rendering Bitmap pages.
 */
@Composable
fun PdfViewerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(currentPageIndex, file.path) {
        try {
            val fileObj = File(file.path)
            if (fileObj.exists()) {
                val pfd = ParcelFileDescriptor.open(fileObj, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(pfd)
                totalPages = pdfRenderer.pageCount.coerceAtLeast(1)

                val safePageIndex = currentPageIndex.coerceIn(0, totalPages - 1)
                val page = pdfRenderer.openPage(safePageIndex)

                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                currentBitmap = bitmap

                page.close()
                pdfRenderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            // PDF Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF7F3ED),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C2825),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF2C2825))
                    }
                }
            }

            // Page Render Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFEFEFEF)),
                contentAlignment = Alignment.Center
            ) {
                currentBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: CircularProgressIndicator(color = PrimaryAccentTaupe)
            }

            // Bottom Navigation Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF7F3ED),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                    }

                    Text(
                        text = "Page ${currentPageIndex + 1} of $totalPages",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C2825)
                    )

                    IconButton(
                        onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                        enabled = currentPageIndex < totalPages - 1
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                    }
                }
            }
        }
    }
}

/**
 * 5. HTML Viewer using WebView in AndroidView.
 */
@Composable
fun HtmlViewerDialog(
    file: AndroidFileModel,
    onDismiss: () -> Unit
) {
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
                color = Color(0xFFF7F3ED),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C2825),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF2C2825))
                    }
                }
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
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
