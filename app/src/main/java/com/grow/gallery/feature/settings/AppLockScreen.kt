package com.grow.gallery.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    onNavigateUp: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { GalleryTopBar(title = "App Lock", onNavigateUp = onNavigateUp) },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item { SectionHeader("App Lock") }
            item {
                SettingRow(
                    title = "Enable App Lock",
                    subtitle = "Require PIN to open Gallery",
                    leading = { Icon(Icons.Default.Lock, null) },
                    trailing = {
                        Switch(
                            checked = uiState.appLockEnabled,
                            onCheckedChange = viewModel::setAppLockEnabled,
                        )
                    },
                )
            }
            if (uiState.appLockEnabled) {
                item {
                    SettingRow(
                        title = "Use Biometric",
                        subtitle = "Fingerprint or face unlock",
                        leading = { Icon(Icons.Default.Fingerprint, null) },
                        trailing = {
                            Switch(
                                checked = uiState.biometricEnabled,
                                onCheckedChange = viewModel::setBiometricEnabled,
                            )
                        },
                    )
                }
                item {
                    SettingRow(
                        title = "Change PIN",
                        leading = { Icon(Icons.Default.Pin, null) },
                        trailing = { Icon(Icons.Default.ChevronRight, null) },
                        onClick = { /* Navigate to PIN setup via VaultScreen flow */ },
                    )
                }
            }
        }
    }
}
