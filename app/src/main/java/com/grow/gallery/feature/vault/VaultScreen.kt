package com.grow.gallery.feature.vault

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    when {
        !uiState.isSetupDone -> VaultSetupScreen(viewModel = viewModel)
        !uiState.isUnlocked -> VaultLockedScreen(
            viewModel = viewModel,
            onNavigateUp = onNavigateUp,
        )
        else -> VaultUnlockedScreen(
            viewModel = viewModel,
            onNavigateUp = onNavigateUp,
        )
    }
}

@Composable
private fun VaultSetupScreen(viewModel: VaultViewModel) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) } // 0 = enter, 1 = confirm
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
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
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
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                                    scope.launch {
                                        viewModel.setupPin(pin)
                                    }
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
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }

            Spacer(Modifier.height(Spacing.xl))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(
                "Secure Vault",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Enter your PIN to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
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
                            // Always reset the visible PIN; error state is driven by uiState.error
                            pin = ""
                        }
                    }
                },
                onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            )
            if (uiState.biometricEnabled) {
                Spacer(Modifier.height(Spacing.xl))
                TextButton(onClick = {
                    val activity = context as? FragmentActivity ?: return@TextButton
                    val executor = ContextCompat.getMainExecutor(activity)
                    val token = viewModel.prepareBiometricChallenge()
                    val callback = object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            viewModel.unlockWithBiometric(token)
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                viewModel.onBiometricError(errString.toString())
                            }
                        }
                        override fun onAuthenticationFailed() {
                            // Handled automatically by the BiometricPrompt UI; no extra action needed
                        }
                    }
                    BiometricPrompt(activity, executor, callback).authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock Vault")
                            .setSubtitle("Use biometric to access your secure vault")
                            .setNegativeButtonText("Use PIN")
                            .build()
                    )
                }) {
                    Icon(Icons.Default.Fingerprint, null, tint = Color.White)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Use Biometric", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultUnlockedScreen(viewModel: VaultViewModel, onNavigateUp: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedItem by remember { mutableStateOf<VaultItem?>(null) }

    // Auto-lock when the app goes to background (#G-V3)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.lock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMediaToVault(uris)
        }
    }

    // Launch system delete dialog to remove originals from MediaStore after import
    val deleteOriginalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingDeleteIntent) {
        uiState.pendingDeleteIntent?.let { pendingIntent ->
            viewModel.onDeleteIntentConsumed()
            deleteOriginalLauncher.launch(
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = Spacing.sm),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        IconButton(onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }) {
                            Icon(Icons.Default.Add, "Add photos to vault", tint = Color.White)
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        if (uiState.vaultItems.isEmpty()) {
            EmptyState(
                icon = Icons.Default.LockOpen,
                title = "Vault is Empty",
                description = "Tap + to add private photos and videos.\nThey will be encrypted and hidden here.",
                modifier = Modifier.padding(paddingValues),
                action = "Add Photos" to {
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
            )
        } else {
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
                    VaultGridItem(
                        item = item,
                        onClick = { selectedItem = item },
                    )
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
private fun VaultGridItem(item: VaultItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Brand.VaultBlue.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.sm),
        ) {
            Icon(
                if (item.mimeType.startsWith("video/")) Icons.Default.VideoFile else Icons.Default.Image,
                contentDescription = null,
                tint = Brand.VaultBlue,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.displayName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Tap overlay
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            onClick = onClick,
        ) {}
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )
            ListItem(
                headlineContent = { Text("Remove from Vault", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.error)
                },
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
private fun PinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
) {
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
