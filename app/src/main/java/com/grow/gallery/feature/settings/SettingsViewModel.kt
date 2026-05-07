package com.grow.gallery.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.BuildConfig
import com.grow.gallery.core.billing.BillingManager
import com.grow.gallery.core.common.DataStoreManager
import com.grow.gallery.core.common.openMediaManageSettings
import com.grow.gallery.core.common.toFormattedSize
import com.grow.gallery.core.designsystem.AppTheme
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val mediaRepository: MediaRepository,
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
                dataStoreManager.backupEnabled,
            ) { theme, gridSize, hideScreenshots, isPremium, backupEnabled ->
                _uiState.update {
                    it.copy(
                        themeLabel = theme.toLabel(),
                        gridSize = gridSize,
                        hideScreenshots = hideScreenshots,
                        isPremium = isPremium,
                        backupEnabled = backupEnabled,
                    )
                }
            }.collect()
        }
        computeUsedStorage()
    }

    private fun computeUsedStorage() {
        viewModelScope.launch {
            try {
                val storage = mediaRepository.computeStorageByType()
                val totalBytes = storage.photoBytes + storage.videoBytes
                _uiState.update { it.copy(usedStorage = totalBytes.toFormattedSize()) }
            } catch (_: Exception) {
                _uiState.update { it.copy(usedStorage = "Unknown") }
            }
        }
    }

    fun cycleTheme() {
        viewModelScope.launch {
            dataStoreManager.dataStore.edit { prefs ->
                val currentName = prefs[DataStoreManager.KEY_THEME_PUBLIC] ?: AppTheme.SYSTEM.name
                val current = runCatching { AppTheme.valueOf(currentName) }.getOrDefault(AppTheme.SYSTEM)
                val next = when (current) {
                    AppTheme.SYSTEM -> AppTheme.LIGHT
                    AppTheme.LIGHT -> AppTheme.DARK
                    AppTheme.DARK -> AppTheme.SYSTEM
                }
                prefs[DataStoreManager.KEY_THEME_PUBLIC] = next.name
            }
        }
    }

    fun cycleGridSize() {
        viewModelScope.launch {
            dataStoreManager.dataStore.edit { prefs ->
                val current = prefs[DataStoreManager.KEY_GRID_SIZE_PUBLIC] ?: 3
                prefs[DataStoreManager.KEY_GRID_SIZE_PUBLIC] = if (current >= 4) 2 else current + 1
            }
        }
    }

    fun setHideScreenshots(hide: Boolean) {
        viewModelScope.launch { dataStoreManager.setHideScreenshots(hide) }
    }

    fun openMediaSettings() {
        context.openMediaManageSettings()
    }

    fun rateApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@galleryapp.dev"))
            putExtra(Intent.EXTRA_SUBJECT, "Gallery App Feedback")
            putExtra(
                Intent.EXTRA_TEXT,
                "Android version: ${android.os.Build.VERSION.RELEASE}\nApp version: ${BuildConfig.VERSION_NAME}\n\n",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Send Feedback").also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) { }
    }

    private fun AppTheme.toLabel() = when (this) {
        AppTheme.SYSTEM -> "Auto (System)"
        AppTheme.LIGHT -> "Always Light"
        AppTheme.DARK -> "Always Dark"
    }
}
