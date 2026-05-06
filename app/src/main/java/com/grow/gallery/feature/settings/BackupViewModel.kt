package com.grow.gallery.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val backupEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                dataStoreManager.backupEnabled,
                dataStoreManager.backupWifiOnly,
            ) { enabled, wifiOnly ->
                _uiState.update { it.copy(backupEnabled = enabled, wifiOnly = wifiOnly) }
            }.collect()
        }
    }

    fun setBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.setBackupEnabled(enabled) }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { dataStoreManager.setBackupWifiOnly(wifiOnly) }
    }
}
