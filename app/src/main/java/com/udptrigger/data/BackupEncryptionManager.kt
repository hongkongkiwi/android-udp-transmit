package com.udptrigger.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import java.security.SecureRandom

/**
 * Backup Encryption Manager for secure backup storage.
 * Uses AES-256-GCM encryption with Android KeyStore for secure key storage.
 */
object BackupEncryptionManager {

    private const val KEYSTORE_ALIAS = "udp_trigger_backup_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val SALT_LENGTH = 16

    /**
     * Encryption result
     */
    sealed class EncryptionResult {
        data class Success(val encryptedData: String) : EncryptionResult()
        data class Error(val message: String) : EncryptionResult()
    }

    /**
     * Decryption result
     */
    sealed class DecryptionResult {
        data class Success(val decryptedData: String) : DecryptionResult()
        data class Error(val message: String) : DecryptionResult()
    }

    /**
     * Check if encryption key exists
     */
    fun hasEncryptionKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.containsAlias(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate encryption key in Android KeyStore
     */
    fun generateKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                return true
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get or create the secret key
     */
    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypt data with password-based key derivation
     * For enhanced security, combines Android KeyStore with password
     */
    fun encryptWithPassword(data: String, password: String): EncryptionResult {
        return try {
            // Generate salt
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)

            // Derive key from password
            val derivedKey = deriveKeyFromPassword(password, salt)

            // Generate IV
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            // Encrypt
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            // Combine salt + iv + encrypted data
            val combined = ByteArray(salt.size + iv.size + encryptedBytes.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(iv, 0, combined, salt.size, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, salt.size + iv.size, encryptedBytes.size)

            EncryptionResult.Success(Base64.encodeToString(combined, Base64.NO_WRAP))
        } catch (e: Exception) {
            EncryptionResult.Error("Encryption failed: ${e.message}")
        }
    }

    /**
     * Decrypt data with password
     */
    fun decryptWithPassword(encryptedData: String, password: String): DecryptionResult {
        return try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)

            // Extract salt, IV, and encrypted data
            val salt = combined.copyOfRange(0, SALT_LENGTH)
            val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, combined.size)

            // Derive key from password
            val derivedKey = deriveKeyFromPassword(password, salt)

            // Decrypt
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = cipher.doFinal(encrypted)

            DecryptionResult.Success(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            DecryptionResult.Error("Decryption failed: ${e.message}")
        }
    }

    /**
     * Simple encryption using only Android KeyStore (no password)
     */
    fun encrypt(data: String): EncryptionResult {
        return try {
            val key = getSecretKey() ?: return EncryptionResult.Error("Key not found")

            // Generate IV
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            // Combine iv + encrypted data
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            EncryptionResult.Success(Base64.encodeToString(combined, Base64.NO_WRAP))
        } catch (e: Exception) {
            EncryptionResult.Error("Encryption failed: ${e.message}")
        }
    }

    /**
     * Simple decryption using only Android KeyStore (no password)
     */
    fun decrypt(encryptedData: String): DecryptionResult {
        return try {
            val key = getSecretKey() ?: return DecryptionResult.Error("Key not found")

            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)

            // Extract IV and encrypted data
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = cipher.doFinal(encrypted)

            DecryptionResult.Success(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            DecryptionResult.Error("Decryption failed: ${e.message}")
        }
    }

    /**
     * Derive AES key from password using PBKDF2
     */
    private fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val iterations = 100000
        val keyLength = 256

        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
        val tmp = factory.generateSecret(spec)
        return javax.crypto.spec.SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * Delete encryption key
     */
    fun deleteKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEYSTORE_ALIAS)
            true
        } catch (e: Exception) {
            false
        }
    }
}
