package com.grow.gallery.feature.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
import com.grow.gallery.core.media.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    albumName: String,
    onOpenViewer: (Long, Boolean) -> Unit,
    onOpenSlideshow: (Long) -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(albumId) { viewModel.loadAlbumMedia(albumId) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
        }
    }

    if (uiState.showSortSheet) {
        SortBottomSheet(
            current = uiState.sortOrder,
            onSelect = viewModel::setSortOrder,
            onDismiss = viewModel::dismissSortSheet,
        )
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedItems.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::exitSelectionMode) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* share selected */ }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                    },
                )
            } else {
                LargeTopAppBar(
                    title = { Text(albumName) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onOpenSlideshow(albumId) },
                            enabled = uiState.items.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.PlayCircle, "Slideshow")
                        }
                        IconButton(onClick = viewModel::showSortSheet) {
                            Icon(Icons.Default.FilterList, "Sort")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
            uiState.items.isEmpty() -> EmptyState(
                icon = Icons.Default.PhotoLibrary,
                title = "No Photos",
                description = "This album is empty.",
                modifier = Modifier.padding(paddingValues),
            )
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
                    items(items = uiState.items, key = { it.id }) { item ->
                        MediaGridItem(
                            item = item,
                            isSelected = item.id in uiState.selectedItems,
                            isSelectionMode = uiState.isSelectionMode,
                            onClick = {
                                if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                                else onOpenViewer(item.id, item.isVideo)
                            },
                            onLongClick = { viewModel.enterSelectionMode(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    current: SortOrder,
    onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
            Text(
                "Sort By",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
            sortOptions.forEach { (order, label, icon) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(icon, null) },
                    trailingContent = {
                        if (current == order) Icon(Icons.Default.Check, null, tint = Brand.Blue)
                    },
                    modifier = Modifier.clickable { onSelect(order) },
                )
            }
        }
    }
}

private val sortOptions = listOf(
    Triple(SortOrder.NEWEST, "Newest First", Icons.Default.ArrowDownward),
    Triple(SortOrder.OLDEST, "Oldest First", Icons.Default.ArrowUpward),
    Triple(SortOrder.SIZE_DESC, "Largest First", Icons.Default.Storage),
    Triple(SortOrder.SIZE_ASC, "Smallest First", Icons.Default.Storage),
)
