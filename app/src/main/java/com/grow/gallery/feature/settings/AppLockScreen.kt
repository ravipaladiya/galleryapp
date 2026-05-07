package com.grow.gallery.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
        }
    }

    // Change PIN flow dialog
    if (uiState.changePinStep != ChangePinStep.NONE) {
        ChangePinDialog(
            step = uiState.changePinStep,
            pinError = uiState.pinError,
            onOldPin = viewModel::submitOldPin,
            onNewPin = viewModel::submitNewPin,
            onConfirm = viewModel::confirmNewPin,
            onDismiss = viewModel::cancelChangePin,
        )
    }

    // No-PIN alert when enabling lock
    if (uiState.pinSetupRequired) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPinSetupRequired,
            icon = { Icon(Icons.Default.Lock, null, tint = Brand.Blue) },
            title = { Text("Set a PIN First") },
            text = { Text("You need to set a PIN before enabling App Lock. Would you like to set one now?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissPinSetupRequired()
                    viewModel.startChangePin()
                }) { Text("Set PIN") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPinSetupRequired) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = { GalleryTopBar(title = "App Lock", onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    subtitle = if (!uiState.hasPinSet) "Set a PIN first to enable" else "Require PIN to open Gallery",
                    leading = { Icon(Icons.Default.Lock, null) },
                    trailing = {
                        Switch(
                            checked = uiState.appLockEnabled,
                            onCheckedChange = viewModel::setAppLockEnabled,
                        )
                    },
                )
            }
            if (uiState.appLockEnabled || uiState.hasPinSet) {
                if (uiState.isBiometricAvailable && uiState.appLockEnabled) {
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
                item {
                    SettingRow(
                        title = if (uiState.hasPinSet) "Change PIN" else "Set PIN",
                        subtitle = if (!uiState.hasPinSet) "Required to enable App Lock" else null,
                        leading = { Icon(Icons.Default.Pin, null) },
                        trailing = { Icon(Icons.Default.ChevronRight, null) },
                        onClick = viewModel::startChangePin,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangePinDialog(
    step: ChangePinStep,
    pinError: String?,
    onOldPin: (String) -> Unit,
    onNewPin: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember(step) { mutableStateOf("") }
    val title = when (step) {
        ChangePinStep.ENTER_OLD -> "Enter Current PIN"
        ChangePinStep.ENTER_NEW -> "Enter New PIN"
        ChangePinStep.CONFIRM_NEW -> "Confirm New PIN"
        ChangePinStep.NONE -> ""
    }
    val subtitle = when (step) {
        ChangePinStep.ENTER_OLD -> "Enter your current 4-digit PIN"
        ChangePinStep.ENTER_NEW -> "Choose a new 4-digit PIN"
        ChangePinStep.CONFIRM_NEW -> "Re-enter your new PIN to confirm"
        ChangePinStep.NONE -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.lg))
                // PIN dots
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    if (i < pin.length) Brand.Blue else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                        )
                    }
                }
                pinError?.let { err ->
                    Spacer(Modifier.height(Spacing.sm))
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(Spacing.lg))
                // PIN pad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "⌫"),
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                            row.forEach { key ->
                                if (key.isEmpty()) {
                                    Spacer(Modifier.size(52.dp))
                                } else {
                                    OutlinedIconButton(
                                        onClick = {
                                            if (key == "⌫") {
                                                if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            } else if (pin.length < 4) {
                                                pin += key
                                                if (pin.length == 4) {
                                                    val captured = pin
                                                    when (step) {
                                                        ChangePinStep.ENTER_OLD -> onOldPin(captured)
                                                        ChangePinStep.ENTER_NEW -> onNewPin(captured)
                                                        ChangePinStep.CONFIRM_NEW -> onConfirm(captured)
                                                        ChangePinStep.NONE -> {}
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(52.dp),
                                    ) {
                                        if (key == "⌫") {
                                            Icon(Icons.Default.Backspace, "Delete", modifier = Modifier.size(18.dp))
                                        } else {
                                            Text(key, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
