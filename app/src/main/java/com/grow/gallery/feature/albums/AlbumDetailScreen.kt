package com.grow.gallery.feature.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
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
    var showSortSheet by remember { mutableStateOf(false) }

    LaunchedEffect(albumId) { viewModel.loadAlbumMedia(albumId) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(albumName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenSlideshow(albumId) }) {
                        Icon(Icons.Default.PlayCircle, "Slideshow")
                    }
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(Icons.Default.FilterList, "Sort")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    items(items = uiState.items, key = { it.id }) { item ->
                        MediaGridItem(
                            item = item,
                            isSelected = false,
                            isSelectionMode = false,
                            onClick = { onOpenViewer(item.id, item.isVideo) },
                            onLongClick = {},
                        )
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            currentOrder = uiState.sortOrder,
            onSelect = { order ->
                viewModel.setSortOrder(order)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    currentOrder: SortOrder,
    onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        SortOrder.NEWEST to "Newest First",
        SortOrder.OLDEST to "Oldest First",
        SortOrder.SIZE_DESC to "Largest First",
        SortOrder.SIZE_ASC to "Smallest First",
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Sort By",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )
            options.forEach { (order, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = {
                        if (currentOrder == order) {
                            Icon(Icons.Default.Check, null, tint = Brand.Blue)
                        }
                    },
                    modifier = Modifier.clickable { onSelect(order) },
                )
            }
        }
    }
}
