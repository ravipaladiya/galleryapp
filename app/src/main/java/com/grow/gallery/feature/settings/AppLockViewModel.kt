package com.grow.gallery.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockUiState(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                dataStoreManager.appLockEnabled,
                dataStoreManager.appLockBiometric,
            ) { enabled, biometric ->
                _uiState.update { it.copy(appLockEnabled = enabled, biometricEnabled = biometric) }
            }.collect()
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setAppLockEnabled(enabled)
            if (!enabled) dataStoreManager.setAppLockBiometric(false)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.setAppLockBiometric(enabled) }
    }
}
