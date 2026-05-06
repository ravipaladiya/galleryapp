package com.grow.gallery.feature.viewer

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grow.gallery.core.common.setAsWallpaper
import com.grow.gallery.core.common.shareMedia
import com.grow.gallery.core.common.toFormattedDateTime
import com.grow.gallery.core.common.toFormattedSize
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.ConfirmDialog
import com.grow.gallery.core.media.MediaItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    mediaId: Long,
    isVideo: Boolean,
    onNavigateUp: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenVideoPlayer: () -> Unit,
    onOpenTrimmer: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showSetAsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) { viewModel.loadMedia(mediaId, isVideo) }

    // System delete confirmation launcher (Android R+)
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val empty = viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
        if (empty) onNavigateUp()
    }

    LaunchedEffect(uiState.pendingDeleteIntent) {
        uiState.pendingDeleteIntent?.let { pendingIntent ->
            viewModel.onDeleteIntentConsumed()
            deleteLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        }
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.currentIndex,
        pageCount = { uiState.items.size.coerceAtLeast(1) },
    )

    LaunchedEffect(pagerState.settledPage) {
        viewModel.setCurrentIndex(pagerState.settledPage)
    }

    LaunchedEffect(uiState.isLoading, uiState.currentIndex) {
        if (!uiState.isLoading && pagerState.currentPage != uiState.currentIndex) {
            pagerState.scrollToPage(uiState.currentIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val item = uiState.items.getOrNull(page)
                if (item != null) {
                    if (item.isVideo) {
                        VideoThumbnailPage(
                            item = item,
                            onTap = { showControls = !showControls },
                            onPlayClick = onOpenVideoPlayer,
                        )
                    } else {
                        ZoomableImage(
                            item = item,
                            pageIndex = page,
                            onTap = { showControls = !showControls },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brand.ViewerControlBg),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                        uiState.currentItem?.let { item ->
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                fontWeight = FontWeight.Medium,
                            )
                            if (uiState.totalCount > 1) {
                                Text(
                                    text = "${uiState.currentIndex + 1} / ${uiState.totalCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                    Row {
                        IconButton(onClick = {
                            uiState.currentItem?.let { viewModel.toggleFavorite(it) }
                        }) {
                            Icon(
                                imageVector = if (uiState.currentItem?.isFavorite == true)
                                    Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (uiState.currentItem?.isFavorite == true)
                                    Brand.GoldStart else Color.White,
                            )
                        }
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(Icons.Default.Info, "Info", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Bottom action bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brand.ViewerControlBg)
                    .navigationBarsPadding()
                    .padding(vertical = Spacing.md),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ViewerAction(Icons.Default.Share, "Share") {
                        uiState.currentItem?.let { context.shareMedia(it.uri, it.mimeType) }
                    }
                    if (uiState.currentItem?.isVideo != true) {
                        ViewerAction(Icons.Default.Edit, "Edit") { onOpenEditor() }
                    }
                    ViewerAction(Icons.Default.Delete, "Delete") { showDeleteDialog = true }
                    ViewerAction(Icons.Default.MoreVert, "More") { showSetAsSheet = true }
                    if (uiState.currentItem?.isVideo == true) {
                        ViewerAction(Icons.Default.ContentCut, "Trim") { onOpenTrimmer() }
                        ViewerAction(Icons.Default.PlayCircle, "Play") { onOpenVideoPlayer() }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete?",
            message = "This will be moved to the system trash.",
            confirmText = "Delete",
            onConfirm = {
                val item = uiState.currentItem
                showDeleteDialog = false
                if (item != null) {
                    viewModel.deleteItem(item)
                    // For pre-R: item removed immediately; check if list is empty
                    if (uiState.items.size <= 1) onNavigateUp()
                }
            },
            onDismiss = { showDeleteDialog = false },
            isDestructive = true,
        )
    }

    if (showInfoSheet) {
        uiState.currentItem?.let { item ->
            PhotoInfoSheet(item = item, onDismiss = { showInfoSheet = false })
        }
    }

    if (showSetAsSheet) {
        SetAsSheet(
            onSetAsWallpaper = {
                uiState.currentItem?.let { context.setAsWallpaper(it.uri) }
                showSetAsSheet = false
            },
            onDismiss = { showSetAsSheet = false },
        )
    }
}

@Composable
private fun VideoThumbnailPage(
    item: MediaItem,
    onTap: () -> Unit,
    onPlayClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = onPlayClick, modifier = Modifier.size(80.dp)) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

/**
 * Zoomable image with pinch-to-zoom and double-tap zoom.
 * Zoom state is keyed to [pageIndex] so navigating to a new page always resets to 1×.
 */
@Composable
private fun ZoomableImage(
    item: MediaItem,
    pageIndex: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(pageIndex) { mutableStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        val newOffset = if (newScale > 1f) offset + offsetChange else Offset.Zero
        scale = newScale
        offset = newOffset
    }

    Box(
        modifier = modifier
            .transformable(transformableState)
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoInfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Photo Info",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )
            InfoRow("File Name", item.displayName)
            InfoRow("Size", item.formattedSize)
            if (item.resolution.isNotEmpty()) InfoRow("Resolution", item.resolution)
            InfoRow(
                "Date",
                (item.dateTaken?.times(1000) ?: item.dateAdded * 1000L).toFormattedDateTime(),
            )
            InfoRow("Type", item.mimeType)
            item.duration?.let { InfoRow("Duration", it.toFormattedDuration()) }
            if (item.bucketName.isNotEmpty()) InfoRow("Album", item.bucketName)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun Long.toFormattedDuration(): String {
    val totalSec = this / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${"%02d".format(s)}"
}

private fun Long.toFormattedDateTime(): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(this))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetAsSheet(
    onSetAsWallpaper: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Set as…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )
            ListItem(
                headlineContent = { Text("Wallpaper") },
                leadingContent = { Icon(Icons.Default.Wallpaper, null) },
                modifier = Modifier.clickable { onSetAsWallpaper() },
            )
            ListItem(
                headlineContent = { Text("Contact Photo") },
                leadingContent = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.clickable { onDismiss() },
            )
        }
    }
}
