package com.example.filesapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImageProcessOptions(
    val resizeMode: ResizeMode = ResizeMode.KEEP_ORIGINAL,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
    val targetFormat: ImageFormat = ImageFormat.JPEG,
    val quality: Int = 85 // 1 to 100
)

enum class ResizeMode {
    KEEP_ORIGINAL,
    PERCENT_75,
    PERCENT_50,
    PERCENT_25,
    CUSTOM_PIXELS
}

enum class ImageFormat(val extension: String, val compressFormat: Bitmap.CompressFormat) {
    JPEG("jpg", Bitmap.CompressFormat.JPEG),
    PNG("png", Bitmap.CompressFormat.PNG),
    WEBP("webp", if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
    })
}

data class ImageProcessResult(
    val outputFile: File,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int
)

class ImageToolsManager(private val context: Context) {

    suspend fun getImageDimensions(file: File): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        Pair(options.outWidth, options.outHeight)
    }

    suspend fun processImage(
        inputFile: File,
        options: ImageProcessOptions,
        outputDirectory: File? = null
    ): Result<ImageProcessResult> = withContext(Dispatchers.IO) {
        try {
            val originalSize = inputFile.length()
            val (origW, origH) = getImageDimensions(inputFile)
            if (origW <= 0 || origH <= 0) {
                return@withContext Result.failure(Exception("Invalid or unsupported image file"))
            }

            // Calculate scaled dimensions
            var destW = origW
            var destH = origH

            when (options.resizeMode) {
                ResizeMode.PERCENT_75 -> {
                    destW = (origW * 0.75).toInt().coerceAtLeast(1)
                    destH = (origH * 0.75).toInt().coerceAtLeast(1)
                }
                ResizeMode.PERCENT_50 -> {
                    destW = (origW * 0.50).toInt().coerceAtLeast(1)
                    destH = (origH * 0.50).toInt().coerceAtLeast(1)
                }
                ResizeMode.PERCENT_25 -> {
                    destW = (origW * 0.25).toInt().coerceAtLeast(1)
                    destH = (origH * 0.25).toInt().coerceAtLeast(1)
                }
                ResizeMode.CUSTOM_PIXELS -> {
                    if (options.targetWidth > 0 && options.targetHeight > 0) {
                        destW = options.targetWidth
                        destH = options.targetHeight
                    } else if (options.targetWidth > 0) {
                        destW = options.targetWidth
                        destH = ((origH.toDouble() / origW) * destW).toInt().coerceAtLeast(1)
                    } else if (options.targetHeight > 0) {
                        destH = options.targetHeight
                        destW = ((origW.toDouble() / origH) * destH).toInt().coerceAtLeast(1)
                    }
                }
                ResizeMode.KEEP_ORIGINAL -> {
                    // unchanged
                }
            }

            // Calculate inSampleSize for efficient decoding of large images
            val decodeOptions = BitmapFactory.Options().apply {
                var sampleSize = 1
                if (origH > destH || origW > destW) {
                    val halfHeight = origH / 2
                    val halfWidth = origW / 2
                    while ((halfHeight / sampleSize) >= destH && (halfWidth / sampleSize) >= destW) {
                        sampleSize *= 2
                    }
                }
                inSampleSize = sampleSize
            }

            val decodedBitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)
                ?: return@withContext Result.failure(Exception("Failed to decode image"))

            val scaledBitmap = if (decodedBitmap.width != destW || decodedBitmap.height != destH) {
                Bitmap.createScaledBitmap(decodedBitmap, destW, destH, true)
            } else {
                decodedBitmap
            }

            // Determine output directory & unique filename
            val targetDir = outputDirectory ?: inputFile.parentFile ?: context.filesDir
            val baseName = inputFile.nameWithoutExtension
            val ext = options.targetFormat.extension
            val timestamp = System.currentTimeMillis() % 100000
            var finalOutputFile = File(targetDir, "${baseName}_edit_$timestamp.$ext")
            var counter = 1
            while (finalOutputFile.exists()) {
                finalOutputFile = File(targetDir, "${baseName}_edit_${timestamp}_$counter.$ext")
                counter++
            }

            FileOutputStream(finalOutputFile).use { out ->
                scaledBitmap.compress(options.targetFormat.compressFormat, options.quality, out)
                out.flush()
            }

            if (scaledBitmap != decodedBitmap) {
                scaledBitmap.recycle()
            }
            decodedBitmap.recycle()

            val result = ImageProcessResult(
                outputFile = finalOutputFile,
                originalSizeBytes = originalSize,
                outputSizeBytes = finalOutputFile.length(),
                originalWidth = origW,
                originalHeight = origH,
                outputWidth = destW,
                outputHeight = destH
            )
            Result.success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
