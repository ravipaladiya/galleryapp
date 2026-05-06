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
                vaultManager.isBiometricEnabled,
            ) { appLock, biometric ->
                _uiState.update { it.copy(appLockEnabled = appLock, biometricEnabled = biometric) }
            }.collect()
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setAppLockEnabled(enabled)
            if (!enabled) vaultManager.enableBiometric(false)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { vaultManager.enableBiometric(enabled) }
    }
}
