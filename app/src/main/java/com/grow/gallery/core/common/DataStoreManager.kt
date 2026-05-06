package com.grow.gallery.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.grow.gallery.core.designsystem.AppTheme
import com.grow.gallery.core.media.MediaFilter
import com.grow.gallery.core.media.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.appDataStore

    companion object {
        private val KEY_THEME = stringPreferencesKey("app_theme")
        private val KEY_GRID_SIZE = intPreferencesKey("grid_size")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        private val KEY_HIDE_SCREENSHOTS = booleanPreferencesKey("hide_screenshots")
        private val KEY_SLIDESHOW_SPEED = intPreferencesKey("slideshow_speed")
        private val KEY_SLIDESHOW_TRANSITION = stringPreferencesKey("slideshow_transition")
        private val KEY_BACKUP_WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val KEY_BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_ui_enabled")
        private val KEY_APP_LOCK_BIOMETRIC = booleanPreferencesKey("app_lock_biometric")
        private const val RECENT_SEARCHES_DELIMITER = "||"
        private const val MAX_RECENT_SEARCHES = 10
    }

    val appTheme: Flow<AppTheme> = dataStore.data.map { prefs ->
        when (prefs[KEY_THEME]) {
            "LIGHT" -> AppTheme.LIGHT
            "DARK" -> AppTheme.DARK
            else -> AppTheme.SYSTEM
        }
    }

    val gridSize: Flow<Int> = dataStore.data.map { it[KEY_GRID_SIZE] ?: 3 }
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }
    val sortOrder: Flow<SortOrder> = dataStore.data.map {
        SortOrder.valueOf(it[KEY_SORT_ORDER] ?: SortOrder.NEWEST.name)
    }
    val hideScreenshots: Flow<Boolean> = dataStore.data.map { it[KEY_HIDE_SCREENSHOTS] ?: false }
    val slideshowSpeed: Flow<Int> = dataStore.data.map { it[KEY_SLIDESHOW_SPEED] ?: 3 }
    val backupWifiOnly: Flow<Boolean> = dataStore.data.map { it[KEY_BACKUP_WIFI_ONLY] ?: true }
    val backupEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_BACKUP_ENABLED] ?: false }
    val appLockEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_APP_LOCK_ENABLED] ?: false }
    val appLockBiometric: Flow<Boolean> = dataStore.data.map { it[KEY_APP_LOCK_BIOMETRIC] ?: false }
    val recentSearches: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_RECENT_SEARCHES]
            ?.split(RECENT_SEARCHES_DELIMITER)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setGridSize(size: Int) {
        dataStore.edit { it[KEY_GRID_SIZE] = size }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[KEY_SORT_ORDER] = order.name }
    }

    suspend fun setHideScreenshots(hide: Boolean) {
        dataStore.edit { it[KEY_HIDE_SCREENSHOTS] = hide }
    }

    suspend fun setSlideshowSpeed(speed: Int) {
        dataStore.edit { it[KEY_SLIDESHOW_SPEED] = speed }
    }

    suspend fun setBackupWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { it[KEY_BACKUP_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKUP_ENABLED] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setAppLockBiometric(enabled: Boolean) {
        dataStore.edit { it[KEY_APP_LOCK_BIOMETRIC] = enabled }
    }

    suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_RECENT_SEARCHES]
                ?.split(RECENT_SEARCHES_DELIMITER)
                ?.filter { it.isNotBlank() && it != query }
                ?: emptyList()
            val updated = listOf(query) + current
            prefs[KEY_RECENT_SEARCHES] = updated.take(MAX_RECENT_SEARCHES).joinToString(RECENT_SEARCHES_DELIMITER)
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { it.remove(KEY_RECENT_SEARCHES) }
    }
}
