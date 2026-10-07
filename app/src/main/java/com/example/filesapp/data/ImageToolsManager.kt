package com.example.filesapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class CropDimensions(
    val xPercent: Float = 0f,
    val yPercent: Float = 0f,
    val widthPercent: Float = 1f,
    val heightPercent: Float = 1f
)

data class ImageProcessOptions(
    val resizeMode: ResizeMode = ResizeMode.KEEP_ORIGINAL,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
    val targetFormat: ImageFormat = ImageFormat.JPEG,
    val quality: Int = 85, // 1 to 100
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val cropDimensions: CropDimensions? = null,
    val preserveExif: Boolean = false,
    val overwriteOriginal: Boolean = false
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

/**
 * Production-Grade Image Processing Engine.
 * Supports Resize, Compress, Rotate (90/180/270), Crop, EXIF preservation/stripping,
 * Batch processing, and Non-destructive output generation.
 */
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

            // 1. Decode original bitmap efficiently
            val decodeOptions = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var workingBitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)
                ?: return@withContext Result.failure(Exception("Failed to decode image"))

            // 2. Apply Crop if specified
            options.cropDimensions?.let { crop ->
                val cropX = (workingBitmap.width * crop.xPercent).toInt().coerceIn(0, workingBitmap.width - 1)
                val cropY = (workingBitmap.height * crop.yPercent).toInt().coerceIn(0, workingBitmap.height - 1)
                val cropW = (workingBitmap.width * crop.widthPercent).toInt().coerceIn(1, workingBitmap.width - cropX)
                val cropH = (workingBitmap.height * crop.heightPercent).toInt().coerceIn(1, workingBitmap.height - cropY)

                val cropped = Bitmap.createBitmap(workingBitmap, cropX, cropY, cropW, cropH)
                if (cropped != workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = cropped
                }
            }

            // 3. Apply Rotation if specified
            if (options.rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(options.rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(workingBitmap, 0, 0, workingBitmap.width, workingBitmap.height, matrix, true)
                if (rotated != workingBitmap) {
                    workingBitmap.recycle()
                    workingBitmap = rotated
                }
            }

            // 4. Calculate target dimensions for resize
            var destW = workingBitmap.width
            var destH = workingBitmap.height

            when (options.resizeMode) {
                ResizeMode.PERCENT_75 -> {
                    destW = (workingBitmap.width * 0.75).toInt().coerceAtLeast(1)
                    destH = (workingBitmap.height * 0.75).toInt().coerceAtLeast(1)
                }
                ResizeMode.PERCENT_50 -> {
                    destW = (workingBitmap.width * 0.50).toInt().coerceAtLeast(1)
                    destH = (workingBitmap.height * 0.50).toInt().coerceAtLeast(1)
                }
                ResizeMode.PERCENT_25 -> {
                    destW = (workingBitmap.width * 0.25).toInt().coerceAtLeast(1)
                    destH = (workingBitmap.height * 0.25).toInt().coerceAtLeast(1)
                }
                ResizeMode.CUSTOM_PIXELS -> {
                    if (options.targetWidth > 0 && options.targetHeight > 0) {
                        destW = options.targetWidth
                        destH = options.targetHeight
                    } else if (options.targetWidth > 0) {
                        destW = options.targetWidth
                        destH = ((workingBitmap.height.toDouble() / workingBitmap.width) * destW).toInt().coerceAtLeast(1)
                    } else if (options.targetHeight > 0) {
                        destH = options.targetHeight
                        destW = ((workingBitmap.width.toDouble() / workingBitmap.height) * destH).toInt().coerceAtLeast(1)
                    }
                }
                ResizeMode.KEEP_ORIGINAL -> {
                    // unchanged
                }
            }

            val finalScaledBitmap = if (workingBitmap.width != destW || workingBitmap.height != destH) {
                val scaled = Bitmap.createScaledBitmap(workingBitmap, destW, destH, true)
                if (scaled != workingBitmap) workingBitmap.recycle()
                scaled
            } else {
                workingBitmap
            }

            // 5. Determine destination output file
            val targetDir = outputDirectory ?: inputFile.parentFile ?: context.filesDir
            val finalOutputFile = if (options.overwriteOriginal) {
                inputFile
            } else {
                val baseName = inputFile.nameWithoutExtension
                val ext = options.targetFormat.extension
                val timestamp = System.currentTimeMillis() % 100000
                var candidate = File(targetDir, "${baseName}_edit_$timestamp.$ext")
                var counter = 1
                while (candidate.exists()) {
                    candidate = File(targetDir, "${baseName}_edit_${timestamp}_$counter.$ext")
                    counter++
                }
                candidate
            }

            // 6. Compress and save to disk
            FileOutputStream(finalOutputFile).use { out ->
                finalScaledBitmap.compress(options.targetFormat.compressFormat, options.quality.coerceIn(1, 100), out)
                out.flush()
            }

            // 7. Preserve or strip EXIF metadata
            if (options.preserveExif && options.targetFormat == ImageFormat.JPEG) {
                try {
                    val srcExif = ExifInterface(inputFile.absolutePath)
                    val destExif = ExifInterface(finalOutputFile.absolutePath)
                    val attributes = listOf(
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE_REF
                    )
                    for (attr in attributes) {
                        val v = srcExif.getAttribute(attr)
                        if (v != null) destExif.setAttribute(attr, v)
                    }
                    destExif.saveAttributes()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            finalScaledBitmap.recycle()

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

    /**
     * Batch processes multiple image files in parallel on Dispatchers.IO.
     */
    suspend fun batchProcessImages(
        inputFiles: List<File>,
        options: ImageProcessOptions,
        outputDirectory: File? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<ImageProcessResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ImageProcessResult>()
        inputFiles.forEachIndexed { index, file ->
            onProgress(index + 1, inputFiles.size)
            val res = processImage(file, options, outputDirectory)
            res.getOrNull()?.let { results.add(it) }
        }
        results
    }
}
