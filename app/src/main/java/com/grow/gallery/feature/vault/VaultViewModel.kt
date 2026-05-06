package com.grow.gallery.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.database.VaultDao
import com.grow.gallery.core.database.VaultItem
import com.grow.gallery.core.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultManager: VaultManager,
    private val vaultDao: VaultDao,
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

    fun biometricUnlock() {
        viewModelScope.launch {
            val items = vaultDao.getAllVaultItems()
            _uiState.update { it.copy(isUnlocked = true, vaultItems = items, error = null) }
        }
    }

    fun lock() {
        _uiState.update { it.copy(isUnlocked = false, vaultItems = emptyList(), error = null) }
    }
}
