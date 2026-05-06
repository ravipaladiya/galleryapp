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
                _uiState.update { it.copy(error = "Incorrect PIN. Try again.") }
            }
        }
    }

    fun unlockWithBiometric() {
        viewModelScope.launch {
            val items = vaultDao.getAllVaultItems()
            _uiState.update { it.copy(isUnlocked = true, vaultItems = items, error = null) }
        }
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

            for (uri in uris) {
                try {
                    val displayName = getDisplayName(uri) ?: "media_${System.currentTimeMillis()}"
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

                    val vaultFileName = vaultManager.addToVault(uri, displayName)
                    if (vaultFileName != null) {
                        vaultDao.insertVaultItem(
                            VaultItem(
                                mediaId = uri.lastPathSegment?.toLongOrNull() ?: System.currentTimeMillis(),
                                encryptedUri = vaultFileName,
                                displayName = displayName,
                                mimeType = mimeType,
                            )
                        )
                        importedUris.add(uri)
                        successCount++
                    }
                } catch (_: Exception) {}
            }

            // Request deletion of originals from MediaStore
            if (importedUris.isNotEmpty()) {
                requestDeleteOriginals(importedUris)
            }

            val items = vaultDao.getAllVaultItems()
            _uiState.update {
                it.copy(
                    isImporting = false,
                    vaultItems = items,
                    snackbarMessage = if (successCount > 0) "$successCount photo(s) moved to vault" else "Import failed",
                )
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
            } catch (_: Exception) {}
        } else {
            uris.forEach {
                try { context.contentResolver.delete(it, null, null) } catch (_: Exception) {}
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
            vaultManager.deleteVaultFile(item.encryptedUri)
            vaultDao.deleteById(item.mediaId)
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
