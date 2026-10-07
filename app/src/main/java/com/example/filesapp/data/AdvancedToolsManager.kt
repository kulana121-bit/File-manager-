package com.example.filesapp.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class DuplicateFileGroup(
    val hash: String,
    val fileSize: Long,
    val files: List<File>
) {
    val wastedSizeBytes: Long
        get() = if (files.size > 1) (files.size - 1) * fileSize else 0L
}

data class DuplicateScanProgress(
    val isScanning: Boolean,
    val scannedFiles: Int,
    val totalFiles: Int,
    val currentFileName: String,
    val duplicateGroups: List<DuplicateFileGroup> = emptyList(),
    val totalWastedBytes: Long = 0L
)

data class FileChecksumResult(
    val fileName: String,
    val fileSize: Long,
    val md5: String,
    val sha1: String,
    val sha256: String
)

data class InstalledAppDetails(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    val apkPath: String,
    val isSystemApp: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val targetSdkVersion: Int = 0,
    val minSdkVersion: Int = 0,
    val permissions: List<String> = emptyList(),
    val certificateSha256: String = "",
    val isSplitApk: Boolean = false,
    val splitApkCount: Int = 0,
    val splitPaths: List<String> = emptyList()
)

object AdvancedToolsManager {

    /**
     * Computes MD5, SHA-1, and SHA-256 in a single streaming pass through the file
     * on Dispatchers.IO to prevent freezing/ANRs on large files.
     */
    suspend fun calculateChecksums(
        file: File,
        onProgress: (Float) -> Unit = {}
    ): FileChecksumResult = withContext(Dispatchers.IO) {
        val md5Digest = MessageDigest.getInstance("MD5")
        val sha1Digest = MessageDigest.getInstance("SHA-1")
        val sha256Digest = MessageDigest.getInstance("SHA-256")

        val totalLength = file.length().coerceAtLeast(1L)
        var readTotal = 0L

        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                md5Digest.update(buffer, 0, bytesRead)
                sha1Digest.update(buffer, 0, bytesRead)
                sha256Digest.update(buffer, 0, bytesRead)
                readTotal += bytesRead
                onProgress((readTotal.toFloat() / totalLength).coerceIn(0f, 1f))
            }
        }

        fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }

        FileChecksumResult(
            fileName = file.name,
            fileSize = file.length(),
            md5 = bytesToHex(md5Digest.digest()),
            sha1 = bytesToHex(sha1Digest.digest()),
            sha256 = bytesToHex(sha256Digest.digest())
        )
    }

    /**
     * Fast SHA-256 or prefix+full hash computation for duplicate scanning.
     */
    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val sb = StringBuilder()
        for (b in digest.digest()) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * Scans storage for duplicates by content hash.
     * Uses 2-stage optimization:
     * 1. Group files with identical file size (> 0 B)
     * 2. Hash only files whose size is shared with other files, saving massive I/O.
     */
    fun scanDuplicatesFlow(rootDirectory: File): Flow<DuplicateScanProgress> = flow {
        val allFiles = mutableListOf<File>()

        fun collectFiles(dir: File) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    collectFiles(f)
                } else if (f.isFile && f.length() > 0) {
                    allFiles.add(f)
                }
            }
        }

        emit(DuplicateScanProgress(isScanning = true, scannedFiles = 0, totalFiles = 0, currentFileName = "Indexing storage..."))
        collectFiles(rootDirectory)

        val totalFiles = allFiles.size
        // Stage 1: Group by size
        val sizeBuckets = allFiles.groupBy { it.length() }.filter { it.value.size > 1 }
        val candidateFiles = sizeBuckets.values.flatten()
        val candidateTotal = candidateFiles.size

        val hashBuckets = mutableMapOf<String, MutableList<File>>()
        var processed = 0

        for (file in candidateFiles) {
            processed++
            emit(
                DuplicateScanProgress(
                    isScanning = true,
                    scannedFiles = processed,
                    totalFiles = candidateTotal.coerceAtLeast(1),
                    currentFileName = file.name
                )
            )

            try {
                val hash = computeFileSha256(file)
                val key = "${file.length()}_$hash"
                hashBuckets.getOrPut(key) { mutableListOf() }.add(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val duplicateGroups = hashBuckets.values
            .filter { it.size > 1 }
            .map { list ->
                val f = list.first()
                DuplicateFileGroup(
                    hash = f.name,
                    fileSize = f.length(),
                    files = list.sortedBy { it.lastModified() }
                )
            }
            .sortedByDescending { it.wastedSizeBytes }

        val totalWasted = duplicateGroups.sumOf { it.wastedSizeBytes }

        emit(
            DuplicateScanProgress(
                isScanning = false,
                scannedFiles = candidateTotal,
                totalFiles = candidateTotal,
                currentFileName = "Complete",
                duplicateGroups = duplicateGroups,
                totalWastedBytes = totalWasted
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Lists all installed apps on the device with icons, version, package name,
     * permissions, certificate SHA-256, target SDK, and split APK information.
     */
    suspend fun getInstalledApps(context: Context): List<InstalledAppDetails> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

        val packages = pm.getInstalledPackages(flags)
        val appList = mutableListOf<InstalledAppDetails>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val appName = pm.getApplicationLabel(appInfo).toString()
            val apkPath = appInfo.sourceDir ?: ""
            val baseFile = File(apkPath)

            // Split APK detection & total size calculation
            val splitDirs = appInfo.splitSourceDirs ?: emptyArray()
            val isSplit = splitDirs.isNotEmpty()
            val splitPathsList = splitDirs.toList()
            var totalSize = if (baseFile.exists()) baseFile.length() else 0L
            for (split in splitDirs) {
                val sf = File(split)
                if (sf.exists()) totalSize += sf.length()
            }

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }

            val targetSdk = appInfo.targetSdkVersion
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0

            val permissionsList = pkg.requestedPermissions?.toList() ?: emptyList()

            // Certificate SHA-256 fingerprint extraction
            val certSha256 = getCertificateSha256(pkg)

            appList.add(
                InstalledAppDetails(
                    name = if (appName.isBlank()) pkg.packageName else appName,
                    packageName = pkg.packageName,
                    versionName = pkg.versionName ?: "1.0",
                    versionCode = vCode,
                    sizeBytes = totalSize,
                    apkPath = apkPath,
                    isSystemApp = isSystem,
                    firstInstallTime = pkg.firstInstallTime,
                    lastUpdateTime = pkg.lastUpdateTime,
                    targetSdkVersion = targetSdk,
                    minSdkVersion = minSdk,
                    permissions = permissionsList,
                    certificateSha256 = certSha256,
                    isSplitApk = isSplit,
                    splitApkCount = splitDirs.size,
                    splitPaths = splitPathsList
                )
            )
        }

        // Sort: user apps first, then alphabetically
        appList.sortedWith(compareBy({ it.isSystemApp }, { it.name.lowercase() }))
    }

    private fun getCertificateSha256(packageInfo: PackageInfo): String {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val certBytes = signatures[0].toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(certBytes)
                val sb = StringBuilder()
                for (i in digest.indices) {
                    if (i > 0) sb.append(":")
                    sb.append(String.format("%02X", digest[i]))
                }
                sb.toString()
            } else {
                "Not available"
            }
        } catch (e: Exception) {
            "Not available"
        }
    }
}
