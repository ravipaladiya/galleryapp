package com.grow.gallery.feature.albums

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
        }
    }

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
                scrollBehavior = scrollBehavior,
            )
        },
        // nestedScroll on Scaffold only — LazyVerticalGrid inside forwards deltas automatically
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
            uiState.albums.isEmpty() -> EmptyState(
                icon = Icons.Default.GridView,
                title = "No Albums",
                description = "Photos and videos from your device will appear as albums here.",
                modifier = Modifier.padding(paddingValues),
                action = "Create Album" to { showCreateDialog = true },
            )
            else -> {
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val columns = when {
                    screenWidthDp >= 840 -> 4
                    screenWidthDp >= 600 -> 3
                    else -> 2
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                        start = Spacing.lg,
                        end = Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    val systemAlbums = uiState.albums.filter { it.isSystemAlbum }
                    val customAlbums = uiState.albums.filter { !it.isSystemAlbum }

                    if (systemAlbums.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = "Media", count = systemAlbums.size)
                        }
                        items(items = systemAlbums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onOpenAlbum(album.id, album.name) },
                            )
                        }
                    }

                    if (customAlbums.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = "My Albums", count = customAlbums.size)
                        }
                        items(items = customAlbums, key = { "custom_${it.id}" }) { album ->
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
                onValueChange = { if (it.length <= 64) name = it },
                label = { Text("Album name") },
                singleLine = true,
                supportingText = { Text("${name.length}/64") },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
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
