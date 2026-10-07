package com.example.filesapp.data

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
 * Native Android AES-256-GCM Encryption Manager for Private Vault.
 * Uses PBKDF2WithHmacSHA256 for key derivation from user PIN.
 * Uses SHA-256 for PIN verification hash in SharedPreferences (never stores raw PIN).
 * Uses streaming CipherInputStream/CipherOutputStream to prevent OOM errors on large files.
 */
class PrivateVaultManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("private_vault_prefs", Context.MODE_PRIVATE)

    private val vaultDirectory: File by lazy {
        File(context.filesDir, "private_vault_encrypted").apply {
            if (!exists()) mkdirs()
        }
    }

    fun isPinSet(): Boolean {
        return prefs.contains("pin_hash")
    }

    fun savePin(pin: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        val hexString = hash.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("pin_hash", hexString).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString("pin_hash", null) ?: return false
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        val hexString = hash.joinToString("") { "%02x".format(it) }
        return storedHash == hexString
    }

    private fun deriveKey(pin: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin, salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts a real file using streaming CipherOutputStream (OOM safe) and moves it into vault.
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
            fos.write(salt) // Write 16-byte salt
            fos.write(iv)   // Write 12-byte IV

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

        sourceFile.delete()
        return destFile
    }

    /**
     * Decrypts a vault file using streaming CipherInputStream (OOM safe) and restores it to targetDir.
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

    fun listVaultFiles(): List<File> {
        return vaultDirectory.listFiles()?.toList() ?: emptyList()
    }
}
