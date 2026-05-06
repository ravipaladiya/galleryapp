package com.grow.gallery.feature.albums

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
import com.grow.gallery.core.media.Album

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onOpenAlbum: (Long, String) -> Unit,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Albums", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, "Create Album")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
            uiState.albums.isEmpty() -> EmptyState(
                icon = Icons.Default.GridView,
                title = "No Albums",
                description = "Create an album to organize your photos.",
                modifier = Modifier.padding(paddingValues),
            )
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                        start = Spacing.lg,
                        end = Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // Recents / All photos first
                    item(span = { GridItemSpan(2) }) {
                        SectionHeader(title = "Media")
                    }
                    items(
                        items = uiState.albums.filter { it.isSystemAlbum },
                        key = { it.id },
                    ) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onOpenAlbum(album.id, album.name) },
                        )
                    }

                    if (uiState.albums.any { !it.isSystemAlbum }) {
                        item(span = { GridItemSpan(2) }) {
                            SectionHeader(title = "My Albums")
                        }
                        items(
                            items = uiState.albums.filter { !it.isSystemAlbum },
                            key = { "custom_${it.id}" },
                        ) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onOpenAlbum(album.id, album.name) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAlbumDialog(
            onConfirm = { name ->
                viewModel.createAlbum(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
fun CreateAlbumDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Album") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Album name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
