package com.example.filesapp.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production-Grade Private Vault 2.0 Encryption Manager.
 * - AES-256-GCM with unique 16-byte cryptographically secure salts and 12-byte IVs per file.
 * - Strong Key Derivation: PBKDF2WithHmacSHA256 with 125,000 iterations.
 * - Salted PBKDF2 PIN verification (never stores raw PIN or unsalted SHA-256).
 * - Failed-attempt throttling (lockout for 30s after 5 consecutive failures).
 * - Secure temporary file decryption to private internal cache with zero-overwrite wiping on close.
 * - Zero decrypted plaintext ever leaked to public external storage.
 */
class PrivateVaultManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("private_vault_prefs_v2", Context.MODE_PRIVATE)

    private val vaultDirectory: File by lazy {
        File(context.filesDir, "private_vault_encrypted_v2").apply {
            if (!exists()) mkdirs()
        }
    }

    private val tempPreviewDirectory: File by lazy {
        File(context.cacheDir, "vault_secure_temp").apply {
            if (!exists()) mkdirs()
        }
    }

    companion object {
        private const val PBKDF2_ITERATIONS = 125_000
        private const val KEY_LENGTH_BITS = 256
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L
    }

    fun isPinSet(): Boolean {
        return prefs.contains("pin_pbkdf2_hash") && prefs.contains("pin_salt")
    }

    /**
     * Stores a salted PBKDF2 (125k iterations) hash of the user's PIN.
     */
    fun savePin(pin: String) {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val hash = hashPinWithPbkdf2(pin.toCharArray(), salt)
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_pbkdf2_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("failed_attempts", 0)
            .putLong("lockout_until", 0L)
            .apply()
    }

    /**
     * Checks if vault is currently in a throttled lockout state.
     * Returns remaining lockout seconds (0 if not locked out).
     */
    fun getLockoutRemainingSeconds(): Int {
        val lockoutUntil = prefs.getLong("lockout_until", 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            ((lockoutUntil - now) / 1000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
    }

    /**
     * Verifies PIN against the stored salted PBKDF2 hash with attempt throttling.
     */
    fun verifyPin(pin: String): Boolean {
        if (getLockoutRemainingSeconds() > 0) {
            return false
        }

        val saltB64 = prefs.getString("pin_salt", null) ?: return false
        val hashB64 = prefs.getString("pin_pbkdf2_hash", null) ?: return false

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(hashB64, Base64.NO_WRAP)
        val computedHash = hashPinWithPbkdf2(pin.toCharArray(), salt)

        // Constant-time byte array comparison to prevent timing side-channel attacks
        val isValid = MessageDigest.isEqual(expectedHash, computedHash)

        if (isValid) {
            // Reset failure counter on success
            prefs.edit()
                .putInt("failed_attempts", 0)
                .putLong("lockout_until", 0L)
                .apply()
        } else {
            val failedCount = prefs.getInt("failed_attempts", 0) + 1
            val editor = prefs.edit().putInt("failed_attempts", failedCount)
            if (failedCount >= MAX_FAILED_ATTEMPTS) {
                editor.putLong("lockout_until", System.currentTimeMillis() + LOCKOUT_DURATION_MS)
            }
            editor.apply()
        }

        return isValid
    }

    private fun hashPinWithPbkdf2(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun deriveKey(pin: CharArray, salt: ByteArray): SecretKeySpec {
        val keyBytes = hashPinWithPbkdf2(pin, salt)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts a real file using streaming AES-256-GCM (OOM safe) with unique salt/IV into vault.
     */
    fun encryptAndMoveToVault(sourceFile: File, pin: String): File {
        if (!sourceFile.exists()) throw IllegalArgumentException("Source file does not exist")

        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

        val secretKey = deriveKey(pin.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val destFile = File(vaultDirectory, "${sourceFile.name}.enc")

        FileOutputStream(destFile).use { fos ->
            fos.write(salt) // 16-byte unique salt
            fos.write(iv)   // 12-byte unique IV

            CipherOutputStream(fos, cipher).use { cos ->
                FileInputStream(sourceFile).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        cos.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        // Secure zero-overwrite original before deletion
        secureWipeFile(sourceFile)
        return destFile
    }

    /**
     * Decrypts a vault file using streaming AES-256-GCM and restores it to target directory.
     */
    fun decryptAndRestoreFile(encryptedFile: File, pin: String, targetDir: File): File {
        if (!encryptedFile.exists()) throw IllegalArgumentException("Encrypted file missing")

        val restoredFileName = encryptedFile.name.removeSuffix(".enc")
        if (!targetDir.exists()) targetDir.mkdirs()
        val restoredFile = File(targetDir, restoredFileName)

        FileInputStream(encryptedFile).use { fis ->
            val salt = ByteArray(16)
            val iv = ByteArray(12)

            if (fis.read(salt) != 16 || fis.read(iv) != 12) {
                throw IllegalStateException("Corrupted header in encrypted file")
            }

            val secretKey = deriveKey(pin.toCharArray(), salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(restoredFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (cis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        encryptedFile.delete()
        return restoredFile
    }

    /**
     * Decrypts a vault file strictly to a temporary file in the app's internal cache directory
     * WITHOUT deleting the encrypted file.
     */
    fun decryptToTempCacheFile(encryptedFile: File, pin: String): File {
        if (!encryptedFile.exists()) throw IllegalArgumentException("Encrypted file missing")

        val restoredFileName = encryptedFile.name.removeSuffix(".enc")
        val tempFile = File(tempPreviewDirectory, restoredFileName)

        FileInputStream(encryptedFile).use { fis ->
            val salt = ByteArray(16)
            val iv = ByteArray(12)

            if (fis.read(salt) != 16 || fis.read(iv) != 12) {
                throw IllegalStateException("Corrupted header in encrypted file")
            }

            val secretKey = deriveKey(pin.toCharArray(), salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(tempFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (cis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        return tempFile
    }

    /**
     * Securely zero-wipes and deletes all temporary preview files created from vault.
     */
    fun wipeAllTempPreviews() {
        try {
            val files = tempPreviewDirectory.listFiles() ?: return
            for (f in files) {
                secureWipeFile(f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Overwrites file contents with zeros before deleting to prevent forensic data recovery.
     */
    fun secureWipeFile(file: File) {
        if (!file.exists()) return
        try {
            if (file.isFile && file.length() > 0) {
                val len = file.length()
                RandomAccessFile(file, "rws").use { raf ->
                    val zeros = ByteArray(8192)
                    var remaining = len
                    while (remaining > 0) {
                        val toWrite = remaining.coerceAtMost(zeros.size.toLong()).toInt()
                        raf.write(zeros, 0, toWrite)
                        remaining -= toWrite
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            file.delete()
        }
    }

    fun listVaultFiles(): List<File> {
        return vaultDirectory.listFiles()?.toList() ?: emptyList()
    }
}
