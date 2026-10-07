package com.example.filesapp.domain.search

import com.example.filesapp.data.AndroidFileModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Filter category for search engine.
 */
enum class SearchCategoryFilter(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Docs"),
    APKS("APKs"),
    ARCHIVES("Archives"),
    LARGE_FILES("Large (>50MB)"),
    RECENT("Recent (7d)")
}

/**
 * Filter criteria for rich query matching.
 */
data class SearchQuery(
    val queryText: String = "",
    val category: SearchCategoryFilter = SearchCategoryFilter.ALL,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val dateModifiedMin: Long? = null,
    val dateModifiedMax: Long? = null,
    val showHidden: Boolean = false
)

/**
 * High-performance search engine with in-memory caching and index synchronization.
 * Prevents redundant filesystem traversal on every keystroke.
 */
class SearchEngine {

    private val indexedFilesCache = mutableListOf<AndroidFileModel>()
    private val cacheLock = Any()

    /**
     * Updates the full in-memory index from scanned files.
     */
    fun updateIndex(files: List<AndroidFileModel>) {
        synchronized(cacheLock) {
            indexedFilesCache.clear()
            indexedFilesCache.addAll(files)
        }
    }

    /**
     * Incremental index addition.
     */
    fun addToIndex(file: AndroidFileModel) {
        synchronized(cacheLock) {
            indexedFilesCache.removeAll { it.path == file.path }
            indexedFilesCache.add(file)
        }
    }

    /**
     * Incremental index removal.
     */
    fun removeFromIndex(path: String) {
        synchronized(cacheLock) {
            indexedFilesCache.removeAll { it.path == path }
        }
    }

    /**
     * Synchronous in-memory search executed on background thread.
     */
    suspend fun search(query: SearchQuery): List<AndroidFileModel> = withContext(Dispatchers.Default) {
        val snapshot: List<AndroidFileModel>
        synchronized(cacheLock) {
            snapshot = indexedFilesCache.toList()
        }

        if (snapshot.isEmpty() && query.queryText.isBlank() && query.category == SearchCategoryFilter.ALL) {
            return@withContext emptyList()
        }

        val text = query.queryText.trim().lowercase()
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)

        snapshot.filter { item ->
            // 1. Hidden file filter
            if (!query.showHidden && item.isHidden) return@filter false

            // 2. Text query matching (name or extension)
            val matchesText = text.isEmpty() ||
                    item.name.lowercase().contains(text) ||
                    item.path.lowercase().contains(text)
            if (!matchesText) return@filter false

            // 3. Category matching
            val mime = item.mimeType.lowercase()
            val ext = item.name.substringAfterLast('.', "").lowercase()

            val matchesCategory = when (query.category) {
                SearchCategoryFilter.ALL -> true
                SearchCategoryFilter.IMAGES -> mime.startsWith("image/") || ext in listOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp")
                SearchCategoryFilter.VIDEOS -> mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv")
                SearchCategoryFilter.AUDIO -> mime.startsWith("audio/") || ext in listOf("mp3", "wav", "m4a", "flac", "aac", "ogg")
                SearchCategoryFilter.DOCUMENTS -> mime.contains("pdf") || mime.startsWith("text/") || ext in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "json", "xml", "csv", "kt", "java")
                SearchCategoryFilter.APKS -> ext == "apk" || mime.contains("package-archive")
                SearchCategoryFilter.ARCHIVES -> ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz") || mime.contains("zip") || mime.contains("compressed")
                SearchCategoryFilter.LARGE_FILES -> item.size >= 50L * 1024 * 1024 // > 50MB
                SearchCategoryFilter.RECENT -> item.dateModified >= sevenDaysAgo
            }
            if (!matchesCategory) return@filter false

            // 4. Size constraints
            if (query.minSizeBytes != null && item.size < query.minSizeBytes) return@filter false
            if (query.maxSizeBytes != null && item.size > query.maxSizeBytes) return@filter false

            // 5. Date constraints
            if (query.dateModifiedMin != null && item.dateModified < query.dateModifiedMin) return@filter false
            if (query.dateModifiedMax != null && item.dateModified > query.dateModifiedMax) return@filter false

            true
        }.sortedWith(compareByDescending<AndroidFileModel> { it.dateModified }.thenBy { it.name.lowercase() })
    }

    /**
     * Builds a debounced search flow for reactive UI queries.
     */
    fun searchFlow(queryFlow: Flow<SearchQuery>): Flow<List<AndroidFileModel>> {
        return queryFlow
            .debounce(150)
            .map { query -> search(query) }
            .flowOn(Dispatchers.Default)
    }
}
