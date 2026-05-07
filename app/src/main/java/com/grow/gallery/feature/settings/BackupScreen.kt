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
        topBar = { GalleryTopBar(title = "Local Export", onNavigateUp = onNavigateUp) },
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
                            Icons.Default.FolderOpen,
                            null,
                            tint = Brand.Blue,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            "Local Export",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Export copies of your photos to an external folder on this device. No cloud or account required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        Button(
                            onClick = { viewModel.setBackupEnabled(!uiState.backupEnabled) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (uiState.backupEnabled) "Disable Auto-Export" else "Enable Auto-Export")
                        }
                    }
                }
            }

            item { SectionHeader("Settings") }
            item {
                SettingRow(
                    title = "Export on Wi-Fi Only",
                    subtitle = "Only export when connected to Wi-Fi",
                    leading = { Icon(Icons.Default.Wifi, null) },
                    trailing = {
                        Switch(
                            checked = uiState.wifiOnly,
                            onCheckedChange = viewModel::setWifiOnly,
                        )
                    },
                )
            }
        }
    }
}
