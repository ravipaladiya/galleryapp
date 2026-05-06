package com.grow.gallery.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.BuildConfig
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAppLock: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GalleryTopBar(title = "Settings", onNavigateUp = onNavigateUp)
        },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
        ) {
            // Premium nudge
            if (!uiState.isPremium) {
                item {
                    PremiumCard(
                        title = "Unlock Premium",
                        description = "Vault, AI Editor, Cleaner, and more.",
                        onClick = onOpenPremium,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
            }

            // General
            item { SectionHeader("General") }
            item {
                SettingRow(
                    title = "Dark Mode",
                    subtitle = uiState.themeLabel,
                    leading = { Icon(Icons.Default.DarkMode, null) },
                    onClick = viewModel::cycleTheme,
                )
            }
            item {
                SettingRow(
                    title = "Grid Size",
                    subtitle = "${uiState.gridSize} columns",
                    leading = { Icon(Icons.Default.GridView, null) },
                    onClick = viewModel::cycleGridSize,
                )
            }

            // Privacy
            item { SectionHeader("Privacy") }
            item {
                SettingRow(
                    title = "Secure Vault",
                    subtitle = "Protect private photos",
                    leading = { Icon(Icons.Default.Lock, null) },
                    trailing = { Icon(Icons.Default.ChevronRight, null) },
                    onClick = onOpenVault,
                )
            }
            item {
                SettingRow(
                    title = "App Lock",
                    subtitle = "PIN or biometric lock",
                    leading = { Icon(Icons.Default.Fingerprint, null) },
                    trailing = { Icon(Icons.Default.ChevronRight, null) },
                    onClick = onOpenAppLock,
                )
            }
            item {
                SettingRow(
                    title = "Hide Screenshots",
                    subtitle = "Don't show in Albums",
                    leading = { Icon(Icons.Default.Screenshot, null) },
                    trailing = {
                        Switch(
                            checked = uiState.hideScreenshots,
                            onCheckedChange = viewModel::setHideScreenshots,
                        )
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                item {
                    SettingRow(
                        title = "Manage Selected Photos",
                        subtitle = "Update photo access",
                        leading = { Icon(Icons.Default.PhotoLibrary, null) },
                        trailing = { Icon(Icons.Default.ChevronRight, null) },
                        onClick = viewModel::openMediaSettings,
                    )
                }
            }

            // Storage
            item { SectionHeader("Storage") }
            item {
                SettingRow(
                    title = "Storage Manager",
                    subtitle = "${uiState.usedStorage} used",
                    leading = { Icon(Icons.Default.Storage, null) },
                    trailing = { Icon(Icons.Default.ChevronRight, null) },
                    onClick = onOpenStorage,
                )
            }
            item {
                SettingRow(
                    title = "Backup & Sync",
                    subtitle = if (uiState.backupEnabled) "On" else "Off",
                    leading = { Icon(Icons.Default.Backup, null) },
                    trailing = { Icon(Icons.Default.ChevronRight, null) },
                    onClick = onOpenBackup,
                )
            }

            // About
            item { SectionHeader("About") }
            item {
                SettingRow(
                    title = "Version",
                    subtitle = BuildConfig.VERSION_NAME,
                    leading = { Icon(Icons.Default.Info, null) },
                )
            }
            item {
                SettingRow(
                    title = "Rate App",
                    leading = { Icon(Icons.Default.Star, null) },
                    trailing = { Icon(Icons.Default.OpenInNew, null) },
                    onClick = viewModel::rateApp,
                )
            }
            item {
                SettingRow(
                    title = "Send Feedback",
                    leading = { Icon(Icons.Default.Feedback, null) },
                    trailing = { Icon(Icons.Default.OpenInNew, null) },
                    onClick = viewModel::sendFeedback,
                )
            }
        }
    }
}
