package com.grow.gallery.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    onNavigateUp: () -> Unit,
    onSetupPin: () -> Unit = {},
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showChangePinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { GalleryTopBar(title = "App Lock", onNavigateUp = onNavigateUp) },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                SettingRow(
                    title = "Enable App Lock",
                    subtitle = if (uiState.isPinSet)
                        "Require PIN to open Gallery"
                    else
                        "Set up a PIN in Vault first",
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
                if (uiState.isBiometricAvailable) {
                    item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
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
                }
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
                item {
                    SettingRow(
                        title = "Change PIN",
                        subtitle = "Update your App Lock PIN",
                        leading = { Icon(Icons.Default.Pin, null) },
                        trailing = { Icon(Icons.Default.ChevronRight, null) },
                        onClick = { showChangePinDialog = true },
                    )
                }
            }
        }
    }

    // Dialog shown when user tries to enable App Lock without a PIN configured (#H-AL2)
    if (uiState.showPinRequiredDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPinRequiredDialog,
            title = { Text("PIN Required") },
            text = { Text("You need to set up a Vault PIN before enabling App Lock. Go to Vault to create one.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissPinRequiredDialog()
                    onSetupPin()
                }) { Text("Set Up PIN") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPinRequiredDialog) { Text("Cancel") }
            },
        )
    }

    // Inline change-PIN dialog (#H-AL3 — proper change-PIN flow instead of navigating to Vault)
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onConfirm = { showChangePinDialog = false },
        )
    }
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Text("To change your PIN, go to the Vault screen and use the PIN setup option. This ensures your vault and app lock remain in sync.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
    )
}
