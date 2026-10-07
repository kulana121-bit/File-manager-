package com.example.filesapp.domain.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Central registry to route file operations and storage access to the appropriate StorageProvider.
 */
class StorageProviderRegistry(private val context: Context) {

    private val localPrimaryProvider = LocalStorageProvider(
        context = context,
        location = StorageLocation.localPrimary(Environment.getExternalStorageDirectory().absolutePath)
    )

    private val appInternalProvider = LocalStorageProvider(
        context = context,
        location = StorageLocation.appInternal(context.filesDir.absolutePath)
    )

    private val mediaStoreProvider by lazy { MediaStoreProvider(context) }
    private val safProviders = mutableMapOf<String, SafStorageProvider>()
    private val removableProviders = mutableMapOf<String, RemovableStorageProvider>()

    fun getPrimaryProvider(): StorageProvider = localPrimaryProvider

    fun getAppInternalProvider(): StorageProvider = appInternalProvider

    fun getMediaStoreProvider(): StorageProvider = mediaStoreProvider

    fun getSafProvider(treeUri: Uri): StorageProvider {
        return safProviders.getOrPut(treeUri.toString()) {
            SafStorageProvider(context, treeUri)
        }
    }

    fun getRemovableProvider(volumeRoot: File): StorageProvider {
        return removableProviders.getOrPut(volumeRoot.absolutePath) {
            RemovableStorageProvider(context, volumeRoot)
        }
    }

    /**
     * Resolves the appropriate provider for an item based on its path or locationType.
     */
    fun resolveProvider(item: StorageItem): StorageProvider {
        return when (item.locationType) {
            StorageLocationType.LOCAL_PRIMARY -> localPrimaryProvider
            StorageLocationType.LOCAL_APP_INTERNAL -> appInternalProvider
            StorageLocationType.MEDIA_STORE -> mediaStoreProvider
            StorageLocationType.SAF_DOCUMENT_TREE -> {
                val uri = Uri.parse(item.uriString ?: item.path)
                getSafProvider(uri)
            }
            StorageLocationType.REMOVABLE_SDCARD -> {
                val f = File(item.path)
                getRemovableProvider(f)
            }
            else -> localPrimaryProvider
        }
    }

    /**
     * Resolves provider by string path.
     */
    fun resolveProviderForPath(path: String): StorageProvider {
        return when {
            path.startsWith("content://media") -> mediaStoreProvider
            path.startsWith("content://") -> {
                val uri = Uri.parse(path)
                getSafProvider(uri)
            }
            path.startsWith(context.filesDir.absolutePath) -> appInternalProvider
            else -> localPrimaryProvider
        }
    }
}
