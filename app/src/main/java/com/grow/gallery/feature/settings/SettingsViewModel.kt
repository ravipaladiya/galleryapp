package com.grow.gallery.feature.settings

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.billing.BillingManager
import com.grow.gallery.core.common.DataStoreManager
import com.grow.gallery.core.common.openMediaManageSettings
import com.grow.gallery.core.designsystem.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val themeLabel: String = "Auto",
    val gridSize: Int = 3,
    val hideScreenshots: Boolean = false,
    val backupEnabled: Boolean = false,
    val isPremium: Boolean = false,
    val usedStorage: String = "Calculating…",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val billingManager: BillingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                dataStoreManager.appTheme,
                dataStoreManager.gridSize,
                dataStoreManager.hideScreenshots,
                billingManager.isPremium,
            ) { theme, gridSize, hideScreenshots, isPremium ->
                _uiState.update {
                    it.copy(
                        themeLabel = theme.toLabel(),
                        gridSize = gridSize,
                        hideScreenshots = hideScreenshots,
                        isPremium = isPremium,
                    )
                }
            }.collect()
        }
        viewModelScope.launch { loadStorageUsage() }
    }

    private suspend fun loadStorageUsage() {
        withContext(Dispatchers.IO) {
            runCatching {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                val used = stat.totalBytes - stat.availableBytes
                _uiState.update { it.copy(usedStorage = used.formatSize()) }
            }
        }
    }

    private fun Long.formatSize(): String = when {
        this >= 1_000_000_000L -> "%.1f GB".format(this / 1_000_000_000.0)
        this >= 1_000_000L -> "%.1f MB".format(this / 1_000_000.0)
        this >= 1_000L -> "%.0f KB".format(this / 1_000.0)
        else -> "$this B"
    }

    fun cycleTheme() {
        viewModelScope.launch {
            val current = dataStoreManager.appTheme.first()
            val next = when (current) {
                AppTheme.SYSTEM -> AppTheme.LIGHT
                AppTheme.LIGHT -> AppTheme.DARK
                AppTheme.DARK -> AppTheme.SYSTEM
            }
            dataStoreManager.setTheme(next)
        }
    }

    fun cycleGridSize() {
        viewModelScope.launch {
            val current = dataStoreManager.gridSize.first()
            dataStoreManager.setGridSize(if (current >= 4) 2 else current + 1)
        }
    }

    fun setHideScreenshots(hide: Boolean) {
        viewModelScope.launch { dataStoreManager.setHideScreenshots(hide) }
    }

    fun openMediaSettings() {
        context.openMediaManageSettings()
    }

    private fun AppTheme.toLabel() = when (this) {
        AppTheme.SYSTEM -> "Auto (System)"
        AppTheme.LIGHT -> "Always Light"
        AppTheme.DARK -> "Always Dark"
    }
}
