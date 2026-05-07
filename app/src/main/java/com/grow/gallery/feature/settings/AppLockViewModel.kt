package com.grow.gallery.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.DataStoreManager
import com.grow.gallery.core.security.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val isPinSet: Boolean = false,
    /** True when user tried to enable App Lock without a PIN configured. */
    val showPinRequiredDialog: Boolean = false,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val vaultManager: VaultManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isBiometricAvailable = vaultManager.isBiometricAvailable()) }
        viewModelScope.launch {
            combine(
                dataStoreManager.appLockEnabled,
                dataStoreManager.appLockBiometric,
                vaultManager.hasPinSet,
            ) { appLock, biometric, pinSet ->
                _uiState.update {
                    it.copy(
                        appLockEnabled = appLock,
                        biometricEnabled = biometric,
                        isPinSet = pinSet,
                    )
                }
            }.catch { /* DataStore errors should not crash the settings screen */ }.collect()
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !_uiState.value.isPinSet) {
                // Guard: a PIN must be set up before App Lock can be enabled (#H-AL2)
                _uiState.update { it.copy(showPinRequiredDialog = true) }
                return@launch
            }
            dataStoreManager.setAppLockEnabled(enabled)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.setAppLockBiometric(enabled) }
    }

    fun dismissPinRequiredDialog() {
        _uiState.update { it.copy(showPinRequiredDialog = false) }
    }
}
