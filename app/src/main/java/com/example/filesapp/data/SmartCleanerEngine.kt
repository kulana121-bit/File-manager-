package com.example.filesapp.data

import android.os.Environment
import java.io.File

/**
 * Smart Cleaner engine - finds junk files like Google Files' cleaner.
 * Categories: App cache, WhatsApp junk, APKs, temp files, empty folders.
 */
data class JunkFileItem(
    val file: File,
    val size: Long,
    val category: CleanerCategory
)

enum class CleanerCategory(val title: String, val description: String) {
    APP_CACHE("App Cache", "Temporary cache files from apps"),
    WHATSAPP_JUNK("WhatsApp Junk", "Statuses, voice notes, sent media"),
    APK_FILES("APK Files", "Installer files taking up space"),
    TEMP_FILES("Temp Files", ".tmp, .log, .bak and thumbnails"),
    EMPTY_FOLDERS("Empty Folders", "Folders with nothing inside")
}

data class CleanerScanResult(
    val items: List<JunkFileItem>,
    val totalBytes: Long,
    val byCategory: Map<CleanerCategory, List<JunkFileItem>>
)

object SmartCleanerEngine {

    private val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "bak", "old", "orig")

    /**
     * Scans for junk files. Calls onProgress with (scannedCount, currentPath).
     */
    fun scanJunk(
        onProgress: (scanned: Int, currentDir: String) -> Unit = { _, _ -> }
    ): CleanerScanResult {
        val items = mutableListOf<JunkFileItem>()
        val root = Environment.getExternalStorageDirectory()
        var scanned = 0

        // 1. App cache directories
        scanAppCache(items)

        // 2. WhatsApp junk
        scanWhatsAppJunk(root, items)

        // 3. Walk storage for APKs, temp files, empty folders
        walkForJunk(root, items, 0, onProgress, intArrayOf(scanned))

        val byCategory = items.groupBy { it.category }
        val totalBytes = items.sumOf { it.size }
        return CleanerScanResult(items, totalBytes, byCategory)
    }

    private fun scanAppCache(items: MutableList<JunkFileItem>) {
        try {
            val androidData = File(Environment.getExternalStorageDirectory(), "Android/data")
            androidData.listFiles()?.forEach { appDir ->
                val cacheDir = File(appDir, "cache")
                if (cacheDir.exists() && cacheDir.isDirectory) {
                    val size = dirSize(cacheDir)
                    if (size > 0) {
                        items.add(JunkFileItem(cacheDir, size, CleanerCategory.APP_CACHE))
                    }
                }
            }
            // OBB temp files
            val obb = File(Environment.getExternalStorageDirectory(), "Android/obb")
            obb.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".tmp")) {
                    items.add(JunkFileItem(f, f.length(), CleanerCategory.APP_CACHE))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanWhatsAppJunk(root: File, items: MutableList<JunkFileItem>) {
        // Both old and new WhatsApp paths
        val waPaths = listOf(
            File(root, "WhatsApp/Media"),
            File(root, "Android/media/com.whatsapp/WhatsApp/Media")
        )
        val junkSubfolders = setOf(
            ".Statuses",
            "WhatsApp Voice Notes",
            ".Thumbs"
        )
        waPaths.forEach { mediaDir ->
            if (!mediaDir.exists()) return@forEach
            // Status files
            junkSubfolders.forEach { sub ->
                val dir = File(mediaDir, sub)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile) {
                            items.add(JunkFileItem(f, f.length(), CleanerCategory.WHATSAPP_JUNK))
                        }
                    }
                }
            }
            // Sent media (duplicates of what user sent)
            mediaDir.listFiles()?.forEach { categoryDir ->
                if (categoryDir.isDirectory) {
                    val sentDir = File(categoryDir, "Sent")
                    if (sentDir.exists()) {
                        sentDir.listFiles()?.forEach { f ->
                            if (f.isFile) {
                                items.add(JunkFileItem(f, f.length(), CleanerCategory.WHATSAPP_JUNK))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun walkForJunk(
        dir: File,
        items: MutableList<JunkFileItem>,
        depth: Int,
        onProgress: (Int, String) -> Unit,
        counter: IntArray
    ) {
        if (depth > 6) return
        val children = try {
            dir.listFiles() ?: return
        } catch (e: Exception) {
            return
        }

        // Skip system dirs
        val name = dir.name
        if (name.startsWith(".") && dir != Environment.getExternalStorageDirectory()) {
            // Still check .thumbnails
            if (name != ".thumbnails") return
        }
        if (name == "Android" && depth == 0) {
            // Handle Android/data separately (done in scanAppCache), skip deep walk
            return
        }

        var hasContent = false
        children.forEach { f ->
            counter[0]++
            if (counter[0] % 500 == 0) {
                onProgress(counter[0], dir.absolutePath)
            }
            try {
                if (f.isDirectory) {
                    hasContent = true
                    walkForJunk(f, items, depth + 1, onProgress, counter)
                } else {
                    hasContent = true
                    val ext = f.name.substringAfterLast('.', "").lowercase()
                    when {
                        ext == "apk" && f.length() > 1024 * 1024 -> {
                            // APK larger than 1MB
                            items.add(JunkFileItem(f, f.length(), CleanerCategory.APK_FILES))
                        }
                        ext in TEMP_EXTENSIONS -> {
                            items.add(JunkFileItem(f, f.length(), CleanerCategory.TEMP_FILES))
                        }
                        f.name == ".thumbdata3--1967290299" || f.name.startsWith(".thumbdata") -> {
                            items.add(JunkFileItem(f, f.length(), CleanerCategory.TEMP_FILES))
                        }
                    }
                }
            } catch (e: Exception) {
                // Skip
            }
        }

        // Empty folder check (only at depth >= 2 to avoid system dirs)
        if (!hasContent && depth >= 2 && dir.exists()) {
            items.add(JunkFileItem(dir, 0L, CleanerCategory.EMPTY_FOLDERS))
        }
    }

    private fun dirSize(dir: File): Long {
        var size = 0L
        try {
            dir.listFiles()?.forEach { f ->
                size += if (f.isDirectory) dirSize(f) else f.length()
            }
        } catch (e: Exception) {
            // Ignore
        }
        return size
    }

    /**
     * Deletes the given junk items. Returns (deletedCount, freedBytes).
     */
    fun cleanItems(items: List<JunkFileItem>): Pair<Int, Long> {
        var count = 0
        var freed = 0L
        items.forEach { item ->
            try {
                val f = item.file
                if (!f.exists()) return@forEach
                val size = if (f.isDirectory) dirSize(f) else f.length()
                val deleted = if (f.isDirectory) f.deleteRecursively() else f.delete()
                if (deleted) {
                    count++
                    freed += size
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return count to freed
    }
}
