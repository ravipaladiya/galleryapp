package com.grow.gallery.core.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_prefs")

@Singleton
class VaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.vaultDataStore

    companion object {
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_VAULT_ENABLED = booleanPreferencesKey("vault_enabled")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private const val KEYSTORE_ALIAS = "gallery_vault_key"
        private const val VAULT_DIR = "vault"
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_SIZE = 128
    }

    val isVaultEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_VAULT_ENABLED] ?: false }
    val isBiometricEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_BIOMETRIC_ENABLED] ?: false }
    val isAppLockEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_APP_LOCK_ENABLED] ?: false }
    val hasPinSet: Flow<Boolean> = dataStore.data.map { it[KEY_PIN_HASH] != null }

    suspend fun setupPin(pin: String): Boolean {
        return try {
            val hash = hashPin(pin)
            dataStore.edit { prefs ->
                prefs[KEY_PIN_HASH] = hash
                prefs[KEY_VAULT_ENABLED] = true
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val storedHash = dataStore.data.first()[KEY_PIN_HASH] ?: return false
        return hashPin(pin) == storedHash
    }

    suspend fun enableBiometric(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun enableAppLock(enabled: Boolean) {
        dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Copies a media file from [sourceUri] into the app's private vault directory,
     * encrypted with AES-GCM using the Android Keystore key.
     * Returns the vault file path (relative to vault dir) or null on failure.
     */
    suspend fun addToVault(sourceUri: Uri, originalName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val vaultDir = File(context.filesDir, VAULT_DIR).also { it.mkdirs() }
                val vaultFileName = "${System.currentTimeMillis()}_${sanitize(originalName)}.enc"
                val vaultFile = File(vaultDir, vaultFileName)

                val secretKey = getOrCreateSecretKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv

                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    vaultFile.outputStream().use { fileOut ->
                        fileOut.write(iv)
                        CipherOutputStream(fileOut, cipher).use { cipherOut ->
                            input.copyTo(cipherOut)
                        }
                    }
                }
                vaultFileName
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Opens an encrypted vault file for reading (returns an InputStream).
     * Caller is responsible for closing the stream.
     */
    fun openVaultFile(vaultFileName: String): java.io.InputStream? {
        return try {
            val vaultFile = File(File(context.filesDir, VAULT_DIR), vaultFileName)
            if (!vaultFile.exists()) return null
            val secretKey = getOrCreateSecretKey()
            val fileIn = vaultFile.inputStream()
            val iv = ByteArray(GCM_IV_SIZE).also { fileIn.read(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_SIZE, iv))
            CipherInputStream(fileIn, cipher)
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes an encrypted vault file from internal storage. */
    fun deleteVaultFile(vaultFileName: String) {
        File(File(context.filesDir, VAULT_DIR), vaultFileName).delete()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)

    private fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
