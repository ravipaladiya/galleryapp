package com.grow.gallery.feature.vault

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.database.VaultDao
import com.grow.gallery.core.database.VaultItem
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaRepository
import com.grow.gallery.core.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

data class VaultUiState(
    val isSetupDone: Boolean = false,
    val isUnlocked: Boolean = false,
    val biometricEnabled: Boolean = false,
    val vaultItems: List<VaultItem> = emptyList(),
    val error: String? = null,
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val pendingDeleteIntent: PendingIntent? = null,
    val pendingDeleteUris: List<Uri> = emptyList(),
    val snackbarMessage: String? = null,
    val isBiometricAvailable: Boolean = false,
    /** Incremented on wrong PIN so the screen can reset its local pin field. */
    val pinResetSignal: Int = 0,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val vaultDao: VaultDao,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val secureRandom = SecureRandom()

    init {
        viewModelScope.launch {
            combine(
                vaultManager.hasPinSet,
                vaultManager.isBiometricEnabled,
            ) { hasPins, biometric ->
                _uiState.update {
                    it.copy(
                        isSetupDone = hasPins,
                        biometricEnabled = biometric,
                        isBiometricAvailable = vaultManager.isBiometricAvailable(),
                        isLoading = false,
                    )
                }
            }.collect()
        }
    }

    suspend fun setupPin(pin: String) {
        val success = vaultManager.setupPin(pin)
        if (success) {
            _uiState.update { it.copy(isSetupDone = true, isUnlocked = true) }
        }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            val correct = vaultManager.verifyPin(pin)
            if (correct) {
                val items = vaultDao.getAllVaultItems()
                _uiState.update { it.copy(isUnlocked = true, vaultItems = items, error = null) }
            } else {
                _uiState.update { state ->
                    state.copy(
                        error = "Incorrect PIN. Try again.",
                        pinResetSignal = state.pinResetSignal + 1,
                    )
                }
            }
        }
    }

    // Challenge token prevents programmatic bypasses; generated via SecureRandom (#H-V1)
    @Volatile private var biometricToken: Long = 0L

    fun prepareBiometricChallenge(): Long {
        var token: Long
        do { token = secureRandom.nextLong() } while (token == 0L)
        biometricToken = token
        return biometricToken
    }

    fun unlockWithBiometric(token: Long) {
        if (token == 0L || token != biometricToken) return
        biometricToken = 0L
        viewModelScope.launch {
            try {
                val items = vaultDao.getAllVaultItems()
                _uiState.update { it.copy(isUnlocked = true, vaultItems = items, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load vault. Please try again.") }
            }
        }
    }

    fun onBiometricError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun lock() {
        _uiState.update { it.copy(isUnlocked = false, vaultItems = emptyList(), error = null) }
    }

    fun addMediaToVault(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val importedUris = mutableListOf<Uri>()
            var successCount = 0
            var failCount = 0

            for ((index, uri) in uris.withIndex()) {
                try {
                    val displayName = getDisplayName(uri) ?: "media_${System.currentTimeMillis()}"
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

                    val vaultFileName = vaultManager.addToVault(uri, displayName)
                    if (vaultFileName != null) {
                        // Extract numeric ID; use SecureRandom fallback to prevent collisions (#H-V2)
                        val mediaId = uri.lastPathSegment
                            ?.substringAfterLast(":")
                            ?.toLongOrNull()
                            ?: run {
                                var id: Long
                                do { id = secureRandom.nextLong() } while (id <= 0)
                                id
                            }
                        vaultDao.insertVaultItem(
                            VaultItem(
                                mediaId = mediaId,
                                encryptedUri = vaultFileName,
                                displayName = displayName,
                                mimeType = mimeType,
                            )
                        )
                        importedUris.add(uri)
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (_: Exception) {
                    failCount++
                }
            }

            if (importedUris.isNotEmpty()) {
                requestDeleteOriginals(importedUris)
            }

            val items = vaultDao.getAllVaultItems()
            val message = when {
                failCount == 0 -> "$successCount photo(s) moved to vault"
                successCount > 0 -> "$successCount of ${uris.size} photo(s) moved to vault ($failCount failed)"
                else -> "Import failed"
            }
            _uiState.update {
                it.copy(isImporting = false, vaultItems = items, snackbarMessage = message)
            }
        }
    }

    private fun requestDeleteOriginals(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                _uiState.update {
                    it.copy(pendingDeleteIntent = pendingIntent, pendingDeleteUris = uris)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Photos added to vault but originals could not be deleted") }
            }
        } else {
            var deleteErrors = 0
            uris.forEach {
                try { context.contentResolver.delete(it, null, null) } catch (_: Exception) { deleteErrors++ }
            }
            if (deleteErrors > 0) {
                _uiState.update { it.copy(snackbarMessage = "Photos added to vault ($deleteErrors originals could not be deleted)") }
            }
        }
    }

    fun onDeleteIntentConsumed() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    fun onDeleteResult(confirmed: Boolean) {
        _uiState.update { it.copy(pendingDeleteUris = emptyList()) }
        if (!confirmed) {
            _uiState.update { it.copy(snackbarMessage = "Photos added to vault (originals kept)") }
        }
    }

    fun removeFromVault(item: VaultItem) {
        viewModelScope.launch {
            // Delete DB row first — if process dies after this but before file delete,
            // the item is gone from UI. Orphaned encrypted file is acceptable; ghost DB
            // entry is not (#H-V3).
            vaultDao.deleteById(item.mediaId)
            vaultManager.deleteVaultFile(item.encryptedUri)
            val items = vaultDao.getAllVaultItems()
            _uiState.update { it.copy(vaultItems = items, snackbarMessage = "${item.displayName} removed from vault") }
        }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
    }
}
