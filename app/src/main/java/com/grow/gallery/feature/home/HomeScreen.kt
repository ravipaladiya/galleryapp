package com.grow.gallery.feature.home

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.*
import com.grow.gallery.core.common.*
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
import com.grow.gallery.core.media.*
import com.grow.gallery.core.permissions.MediaPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onOpenViewer: (Long, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenCleaner: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showSortFilter by remember { mutableStateOf(false) }

    // System delete confirmation dialog launcher (Android R+)
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    // Launch the system delete intent when the ViewModel sets it
    LaunchedEffect(uiState.pendingDeleteIntent) {
        uiState.pendingDeleteIntent?.let { pendingIntent ->
            viewModel.onDeleteIntentConsumed()
            deleteLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        }
    }

    val permissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions) { _ ->
        viewModel.onPermissionResult()
    }

    LaunchedEffect(multiplePermissionsState.allPermissionsGranted) {
        if (!multiplePermissionsState.allPermissionsGranted &&
            !multiplePermissionsState.shouldShowRationale
        ) {
            multiplePermissionsState.launchMultiplePermissionRequest()
        } else {
            viewModel.onPermissionResult()
        }
    }

    LaunchedEffect(uiState.shareUris) {
        uiState.shareUris?.let { uris ->
            if (uris.isNotEmpty()) {
                val mimeType = if (uris.size == 1) {
                    uiState.mediaGroups.flatMap { it.items }
                        .firstOrNull { it.uri == uris.first() }?.mimeType ?: "*/*"
                } else "*/*"
                context.shareMultipleMedia(uris, mimeType)
                viewModel.onShareHandled()
                viewModel.exitSelectionMode()
            }
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.onSnackbarShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AnimatedVisibility(visible = !uiState.isSelectionMode) {
                    LargeTopAppBar(
                        title = { Text("Photos", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { showSortFilter = true }) {
                                Icon(Icons.Default.FilterList, "Sort & Filter")
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, "Settings")
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
                AnimatedVisibility(visible = uiState.isSelectionMode) {
                    SelectionTopBar(
                        count = uiState.selectedItems.size,
                        onClose = viewModel::exitSelectionMode,
                        onSelectAll = viewModel::selectAll,
                    )
                }
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        ) { paddingValues ->
            when {
                uiState.permissionState == MediaPermissionState.DENIED ||
                        uiState.permissionState == MediaPermissionState.NOT_ASKED -> {
                    PermissionEmptyState(
                        title = "Access Your Photos",
                        description = "Gallery needs permission to show your photos and videos.",
                        actionText = "Grant Permission",
                        onAction = { multiplePermissionsState.launchMultiplePermissionRequest() },
                        modifier = Modifier.padding(paddingValues),
                    )
                }
                uiState.isLoading -> {
                    LoadingScreen(modifier = Modifier.padding(paddingValues))
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(Spacing.lg))
                        Button(onClick = viewModel::loadMedia) { Text("Retry") }
                    }
                }
                uiState.mediaGroups.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.PhotoLibrary,
                        title = "No Photos Yet",
                        description = "Photos and videos from your device will appear here.",
                        modifier = Modifier.padding(paddingValues),
                    )
                }
                else -> {
                    MediaTimeline(
                        groups = uiState.mediaGroups,
                        selectedItems = uiState.selectedItems,
                        isSelectionMode = uiState.isSelectionMode,
                        onItemClick = { item ->
                            if (uiState.isSelectionMode) viewModel.toggleSelection(item.id)
                            else onOpenViewer(item.id, item.isVideo)
                        },
                        onItemLongClick = { item -> viewModel.enterSelectionMode(item.id) },
                        contentPadding = paddingValues,
                        selectionMode = uiState.isSelectionMode,
                        gridSize = uiState.gridSize,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isSelectionMode,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SelectionActionBar(
                count = uiState.selectedItems.size,
                onShare = viewModel::prepareShare,
                onDelete = viewModel::requestDeleteSelected,
                onFavorite = viewModel::favoriteSelected,
            )
        }
    }

    if (uiState.showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete ${uiState.selectedItems.size} item(s)?",
            message = "Selected photos and videos will be moved to the system trash.",
            confirmText = "Delete",
            onConfirm = viewModel::confirmDeleteSelected,
            onDismiss = viewModel::cancelDelete,
            isDestructive = true,
        )
    }

    if (showSortFilter) {
        SortFilterSheet(
            currentSort = uiState.sortOrder,
            currentFilter = uiState.filter,
            onSortSelected = viewModel::setSortOrder,
            onFilterSelected = viewModel::setFilter,
            onDismiss = { showSortFilter = false },
        )
    }
}

@Composable
private fun MediaTimeline(
    groups: List<MediaGroup>,
    selectedItems: Set<Long>,
    isSelectionMode: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    contentPadding: PaddingValues,
    selectionMode: Boolean = false,
    gridSize: Int = 3,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridSize.coerceIn(2, 5)),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + if (selectionMode) 80.dp else 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        groups.forEach { group ->
            item(
                key = "header_${group.label}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                SectionHeader(
                    title = group.label,
                    count = group.items.size,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(items = group.items, key = { it.id }) { item ->
                MediaGridItem(
                    item = item,
                    isSelected = selectedItems.contains(item.id),
                    isSelectionMode = isSelectionMode,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close selection")
            }
        },
        actions = {
            TextButton(onClick = onSelectAll) { Text("Select All") }
        },
    )
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionAction(Icons.Default.Share, "Share", onShare)
                SelectionAction(Icons.Default.FavoriteBorder, "Favorite", onFavorite)
                SelectionAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = Spacing.sm),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortFilterSheet(
    currentSort: SortOrder,
    currentFilter: MediaFilter,
    onSortSelected: (SortOrder) -> Unit,
    onFilterSelected: (MediaFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSort by remember { mutableStateOf(currentSort) }
    var selectedFilter by remember { mutableStateOf(currentFilter) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Sort & Filter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )

            Text(
                "Sort by",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )
            listOf(
                SortOrder.NEWEST to "Newest First",
                SortOrder.OLDEST to "Oldest First",
                SortOrder.SIZE_DESC to "Size: Large First",
                SortOrder.SIZE_ASC to "Size: Small First",
            ).forEach { (sort, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = {
                        if (selectedSort == sort) Icon(Icons.Default.Check, null, tint = Brand.Blue)
                    },
                    modifier = Modifier.clickable { selectedSort = sort },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))

            Text(
                "Filter by",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )
            listOf(
                MediaFilter.ALL to "All",
                MediaFilter.PHOTOS to "Photos",
                MediaFilter.VIDEOS to "Videos",
                MediaFilter.FAVORITES to "Favorites",
            ).forEach { (filter, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = {
                        if (selectedFilter == filter) Icon(Icons.Default.Check, null, tint = Brand.Blue)
                    },
                    modifier = Modifier.clickable { selectedFilter = filter },
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(
                    onClick = {
                        selectedSort = SortOrder.NEWEST
                        selectedFilter = MediaFilter.ALL
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset") }
                Button(
                    onClick = {
                        onSortSelected(selectedSort)
                        onFilterSelected(selectedFilter)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Apply") }
            }
        }
    }
}
