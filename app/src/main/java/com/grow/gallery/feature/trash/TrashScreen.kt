package com.grow.gallery.feature.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grow.gallery.core.database.TrashItem
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onNavigateUp: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showEmptyDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingDeleteIntent) {
        uiState.pendingDeleteIntent?.let { intent ->
            viewModel.onDeleteIntentConsumed()
            deleteLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
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
                title = "Recently Deleted",
                onNavigateUp = onNavigateUp,
                actions = {
                    if (uiState.items.isNotEmpty()) {
                        TextButton(onClick = { showEmptyDialog = true }) {
                            Text("Empty", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
            uiState.items.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(Spacing.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                        modifier = Modifier.size(80.dp),
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        "Recently Deleted is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.padding(paddingValues)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Items are permanently deleted after 30 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(uiState.items, key = { it.mediaId }) { trashItem ->
                            TrashMediaItem(
                                item = trashItem,
                                onRestore = { viewModel.restore(trashItem) },
                                onDelete = { viewModel.deletePermanently(trashItem) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEmptyDialog) {
        ConfirmDialog(
            title = "Empty Trash?",
            message = "All items will be permanently deleted. This cannot be undone.",
            confirmText = "Empty Trash",
            onConfirm = {
                viewModel.emptyTrash()
                showEmptyDialog = false
            },
            onDismiss = { showEmptyDialog = false },
            isDestructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashMediaItem(
    item: TrashItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val daysLeft = ((item.expiresAt - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.thumbnailPath ?: android.net.Uri.parse(item.uri))
                .crossfade(true)
                .size(320)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text("${daysLeft}d", fontSize = 10.sp, style = MaterialTheme.typography.labelSmall)
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Default.MoreVert, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Restore") },
                    leadingIcon = { Icon(Icons.Default.Restore, null) },
                    onClick = { showMenu = false; onRestore() },
                )
                DropdownMenuItem(
                    text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                )
            }
        }
    }
}
