package com.grow.gallery.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@Composable
fun AppLockScreen(onNavigateUp: () -> Unit) {
    var appLockEnabled by remember { mutableStateOf(false) }
    var biometricEnabled by remember { mutableStateOf(false) }

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
                        Switch(checked = appLockEnabled, onCheckedChange = { appLockEnabled = it })
                    },
                )
            }
            if (appLockEnabled) {
                item {
                    SettingRow(
                        title = "Use Biometric",
                        subtitle = "Fingerprint or face unlock",
                        leading = { Icon(Icons.Default.Fingerprint, null) },
                        trailing = {
                            Switch(checked = biometricEnabled, onCheckedChange = { biometricEnabled = it })
                        },
                    )
                }
                item {
                    SettingRow(
                        title = "Change PIN",
                        leading = { Icon(Icons.Default.Pin, null) },
                        trailing = { Icon(Icons.Default.ChevronRight, null) },
                        onClick = { /* Navigate to PIN setup */ },
                    )
                }
            }
        }
    }
}
