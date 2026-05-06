package com.grow.gallery.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateUp: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { GalleryTopBar(title = "Backup & Sync", onNavigateUp = onNavigateUp) },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            if (uiState.backupEnabled) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            null,
                            tint = if (uiState.backupEnabled) Brand.Blue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            if (uiState.backupEnabled) "Backup On" else "Backup Off",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (uiState.backupEnabled) "Your photos are being backed up"
                            else "Enable backup to protect your photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        Button(
                            onClick = { viewModel.setBackupEnabled(!uiState.backupEnabled) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (uiState.backupEnabled) "Disable Backup" else "Enable Backup")
                        }
                    }
                }
            }

            item { SectionHeader("Settings") }
            item {
                SettingRow(
                    title = "Wi-Fi Only",
                    subtitle = "Only backup when connected to Wi-Fi",
                    leading = { Icon(Icons.Default.Wifi, null) },
                    trailing = {
                        Switch(
                            checked = uiState.wifiOnly,
                            onCheckedChange = viewModel::setWifiOnly,
                        )
                    },
                )
            }
            item {
                SettingRow(
                    title = "Account",
                    subtitle = "Not signed in",
                    leading = { Icon(Icons.Default.AccountCircle, null) },
                    trailing = { Icon(Icons.Default.ChevronRight, null) },
                    onClick = { },
                )
            }
        }
    }
}
