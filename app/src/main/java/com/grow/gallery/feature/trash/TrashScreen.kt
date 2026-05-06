package com.grow.gallery.feature.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var selectedItem by remember { mutableStateOf<TrashItem?>(null) }
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
            uiState.items.isEmpty() -> EmptyState(
                icon = Icons.Default.Delete,
                title = "Recently Deleted is Empty",
                description = "Deleted photos and videos appear here for 30 days before being permanently removed.",
                modifier = Modifier.padding(paddingValues),
            )
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
                                onClick = { selectedItem = trashItem },
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

    selectedItem?.let { item ->
        TrashItemSheet(
            item = item,
            onRestore = {
                viewModel.restore(item)
                selectedItem = null
            },
            onDelete = {
                viewModel.deletePermanently(item)
                selectedItem = null
            },
            onDismiss = { selectedItem = null },
        )
    }
}

@Composable
private fun TrashMediaItem(item: TrashItem, onClick: () -> Unit) {
    val daysLeft = ((item.expiresAt - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashItemSheet(
    item: TrashItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
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
                headlineContent = { Text("Restore") },
                leadingContent = { Icon(Icons.Default.RestoreFromTrash, null) },
                modifier = Modifier.clickable { onRestore() },
            )
            ListItem(
                headlineContent = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable { onDelete() },
            )
        }
    }
}
