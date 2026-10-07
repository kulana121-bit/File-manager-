package com.example.filesapp.domain.storage

/**
 * Represents the storage tier or backend provider for a file or directory.
 */
enum class StorageLocationType {
    LOCAL_PRIMARY,      // Primary internal/shared storage (/sdcard or /storage/emulated/0)
    LOCAL_APP_INTERNAL, // App private internal storage (context.filesDir)
    REMOVABLE_SDCARD,   // Secondary SD card or USB OTG storage volume
    SAF_DOCUMENT_TREE,  // Storage Access Framework tree URI (content://...)
    MEDIA_STORE,        // Android MediaStore content provider (content://media/...)
    GOOGLE_DRIVE,       // Google Drive Cloud Storage
    FTP_NETWORK,        // FTP / FTPS Remote Server
    SMB_NETWORK         // SMB / Samba Windows Network Share
}

/**
 * Storage location descriptor with human-readable identifier and metadata.
 */
data class StorageLocation(
    val type: StorageLocationType,
    val rootPath: String,
    val displayName: String,
    val isRemovable: Boolean = false,
    val isReadOnly: Boolean = false,
    val isCloud: Boolean = false,
    val isNetwork: Boolean = false
) {
    companion object {
        fun localPrimary(rootPath: String): StorageLocation = StorageLocation(
            type = StorageLocationType.LOCAL_PRIMARY,
            rootPath = rootPath,
            displayName = "Internal Storage",
            isRemovable = false,
            isReadOnly = false
        )

        fun appInternal(rootPath: String): StorageLocation = StorageLocation(
            type = StorageLocationType.LOCAL_APP_INTERNAL,
            rootPath = rootPath,
            displayName = "App Storage",
            isRemovable = false,
            isReadOnly = false
        )

        fun removableSdCard(rootPath: String, name: String = "SD Card"): StorageLocation = StorageLocation(
            type = StorageLocationType.REMOVABLE_SDCARD,
            rootPath = rootPath,
            displayName = name,
            isRemovable = true,
            isReadOnly = false
        )

        fun googleDrive(accountEmail: String): StorageLocation = StorageLocation(
            type = StorageLocationType.GOOGLE_DRIVE,
            rootPath = "drive://root",
            displayName = "Google Drive ($accountEmail)",
            isCloud = true
        )

        fun ftpServer(host: String, port: Int): StorageLocation = StorageLocation(
            type = StorageLocationType.FTP_NETWORK,
            rootPath = "ftp://$host:$port/",
            displayName = "FTP ($host)",
            isNetwork = true
        )

        fun smbServer(host: String, share: String): StorageLocation = StorageLocation(
            type = StorageLocationType.SMB_NETWORK,
            rootPath = "smb://$host/$share/",
            displayName = "SMB ($host)",
            isNetwork = true
        )
    }
}
