package com.example.filesapp.domain.storage

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage Provider for Removable Storage Volumes (Secondary SD Card, USB OTG).
 */
class RemovableStorageProvider(
    private val context: Context,
    val rootDir: File,
    override val location: StorageLocation = StorageLocation.removableSdCard(
        rootPath = rootDir.absolutePath,
        name = rootDir.name.ifEmpty { "Removable Storage" }
    )
) : StorageProvider by LocalStorageProvider(
    context = context,
    location = StorageLocation.removableSdCard(rootDir.absolutePath, rootDir.name.ifEmpty { "Removable Storage" })
) {
    companion object {
        /**
         * Discovers all currently mounted external/removable storage volumes.
         */
        fun getRemovableVolumes(context: Context): List<File> {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            val removable = mutableListOf<File>()
            for (dir in externalDirs) {
                if (dir != null) {
                    // Extract root path of volume (e.g. /storage/XXXX-XXXX)
                    val path = dir.absolutePath
                    val storageIdx = path.indexOf("/storage/")
                    if (storageIdx >= 0) {
                        val sub = path.substring(storageIdx)
                        val parts = sub.split("/").filter { it.isNotEmpty() }
                        if (parts.size >= 2 && parts[1] != "emulated" && parts[1] != "self") {
                            val volumeRoot = File("/storage/${parts[1]}")
                            if (volumeRoot.exists() && !removable.contains(volumeRoot)) {
                                removable.add(volumeRoot)
                            }
                        }
                    }
                }
            }
            return removable
        }
    }
}
