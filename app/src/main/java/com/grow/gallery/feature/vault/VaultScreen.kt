package com.grow.gallery.feature.vault

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.database.VaultItem
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateUp: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingDeleteIntent) {
        uiState.pendingDeleteIntent?.let { pendingIntent ->
            viewModel.onDeleteIntentConsumed()
            deleteLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.onSnackbarShown()
        }
    }

    when {
        uiState.isLoading -> LoadingScreen()
        !uiState.isSetupDone -> VaultSetupScreen(viewModel = viewModel)
        !uiState.isUnlocked -> VaultLockedScreen(
            viewModel = viewModel,
            onNavigateUp = onNavigateUp,
        )
        else -> VaultUnlockedScreen(
            viewModel = viewModel,
            onNavigateUp = onNavigateUp,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
private fun VaultSetupScreen(viewModel: VaultViewModel) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.VaultBlueGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xxxl),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = if (step == 0) "Create Vault PIN" else "Confirm PIN",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = if (step == 0) "Choose a 4-digit PIN to secure your vault"
                else "Re-enter your PIN to confirm",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxxl))
            PinDots(count = if (step == 0) pin.length else confirmPin.length)
            error?.let { err ->
                Spacer(Modifier.height(Spacing.md))
                Text(err, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.xxxl))
            PinPad(
                onDigit = { digit ->
                    if (step == 0) {
                        if (pin.length < 4) {
                            pin += digit
                            if (pin.length == 4) step = 1
                        }
                    } else {
                        if (confirmPin.length < 4) {
                            confirmPin += digit
                            if (confirmPin.length == 4) {
                                if (pin == confirmPin) {
                                    scope.launch { viewModel.setupPin(pin) }
                                } else {
                                    error = "PINs do not match"
                                    confirmPin = ""
                                }
                            }
                        }
                    }
                },
                onDelete = {
                    if (step == 0) { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                    else { if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1) }
                },
            )
        }
    }
}

@Composable
private fun VaultLockedScreen(viewModel: VaultViewModel, onNavigateUp: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) pin = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.VaultBlueGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xxxl),
        ) {
            IconButton(onClick = onNavigateUp, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
            Spacer(Modifier.height(Spacing.xl))
            Box(
                modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(Spacing.xl))
            Text("Secure Vault", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text("Enter your PIN to unlock", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(Spacing.xxxl))
            PinDots(count = pin.length)
            uiState.error?.let { err ->
                Spacer(Modifier.height(Spacing.md))
                Text(err, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(Spacing.xxxl))
            PinPad(
                onDigit = { digit ->
                    if (pin.length < 4) {
                        pin += digit
                        if (pin.length == 4) {
                            viewModel.unlock(pin)
                            if (!viewModel.uiState.value.isUnlocked) pin = ""
                        }
                    }
                },
                onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            )
            if (uiState.biometricEnabled && uiState.isBiometricAvailable) {
                Spacer(Modifier.height(Spacing.xl))
                TextButton(onClick = { launchBiometricPrompt(context, viewModel) }) {
                    Icon(Icons.Default.Fingerprint, null, tint = Color.White)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Use Biometric", color = Color.White)
                }
            }
        }
    }
}

private fun launchBiometricPrompt(context: Context, viewModel: VaultViewModel) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            viewModel.unlockWithBiometric()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
        override fun onAuthenticationFailed() {}
    }
    val biometricPrompt = BiometricPrompt(activity, executor, callback)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Vault")
        .setSubtitle("Use biometric to access your secure vault")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    biometricPrompt.authenticate(promptInfo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultUnlockedScreen(
    viewModel: VaultViewModel,
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedItem by remember { mutableStateOf<VaultItem?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMediaToVault(uris)
        }
    }

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "Secure Vault",
                onNavigateUp = {
                    viewModel.lock()
                    onNavigateUp()
                },
                containerColor = Brand.VaultBlue,
                contentColor = Color.White,
                actions = {
                    IconButton(onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }) {
                        Icon(Icons.Default.Add, "Add to Vault", tint = Color.White)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when {
            uiState.isImporting -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Brand.VaultBlue)
                        Spacer(Modifier.height(Spacing.md))
                        Text("Moving to vault…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            uiState.vaultItems.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.LockOpen,
                    title = "Vault is Empty",
                    description = "Tap + to add private photos and videos.",
                    modifier = Modifier.padding(paddingValues),
                    action = "Add Media" to {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(uiState.vaultItems, key = { it.mediaId }) { item ->
                        VaultMediaItem(item = item, onClick = { selectedItem = item })
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        VaultItemSheet(
            item = item,
            onRemove = {
                viewModel.removeFromVault(item)
                selectedItem = null
            },
            onDismiss = { selectedItem = null },
        )
    }
}

@Composable
private fun VaultMediaItem(item: VaultItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Lock,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(32.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultItemSheet(
    item: VaultItem,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )
            ListItem(
                headlineContent = { Text("Remove from Vault", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.error)
                },
                supportingContent = { Text("File will be deleted permanently") },
                modifier = Modifier.clickable { onRemove() },
            )
        }
    }
}

@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        if (i < count) Color.White else Color.White.copy(alpha = 0.3f),
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PinPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxxl)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Box(modifier = Modifier.size(64.dp))
                    } else {
                        OutlinedButton(
                            onClick = { if (key == "⌫") onDelete() else onDigit(key) },
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        ) {
                            if (key == "⌫") {
                                Icon(Icons.Default.Backspace, "Delete", tint = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text(key, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
