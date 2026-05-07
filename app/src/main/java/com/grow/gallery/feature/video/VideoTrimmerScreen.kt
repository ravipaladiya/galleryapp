package com.grow.gallery.feature.video

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.GalleryTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimmerScreen(
    mediaId: Long,
    onNavigateUp: () -> Unit,
    viewModel: VideoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(mediaId) { viewModel.loadVideo(mediaId) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }

    LaunchedEffect(uiState.videoUri) {
        uiState.videoUri?.let {
            exoPlayer.setMediaItem(Media3Item.fromUri(it))
            exoPlayer.prepare()
        }
    }

    // Track which handle is actively being dragged so only its position drives seek.
    var activeDragHandle by remember { mutableStateOf<String?>(null) } // "start" | "end" | null

    LaunchedEffect(uiState.trimStart) {
        if ((activeDragHandle == "start" || activeDragHandle == null) && exoPlayer.duration > 0)
            exoPlayer.seekTo(uiState.trimStart)
    }
    LaunchedEffect(uiState.trimEnd) {
        if ((activeDragHandle == "end" || activeDragHandle == null) && exoPlayer.duration > 0)
            exoPlayer.seekTo(uiState.trimEnd)
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    // Export success/error dialogs
    if (uiState.exportSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.onExportDismissed(); onNavigateUp() },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = Brand.Blue) },
            title = { Text("Exported") },
            text = { Text("Trimmed video saved to Movies/GalleryApp.") },
            confirmButton = {
                Button(onClick = { viewModel.onExportDismissed(); onNavigateUp() }) {
                    Text("Done")
                }
            },
        )
    }

    if (uiState.exportError != null) {
        AlertDialog(
            onDismissRequest = viewModel::onExportDismissed,
            icon = { Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Export Failed") },
            text = { Text(uiState.exportError!!) },
            confirmButton = {
                Button(onClick = viewModel::onExportDismissed) { Text("OK") }
            },
        )
    }

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "Trim Video",
                onNavigateUp = onNavigateUp,
                actions = {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = Spacing.sm),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = viewModel::exportTrimmedVideo,
                            enabled = uiState.duration > 0 &&
                                    (uiState.trimEnd - uiState.trimStart) >= 1000L,
                        ) {
                            Text("Export", fontWeight = FontWeight.SemiBold, color = Brand.Blue)
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Video preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    onRelease = { playerView -> playerView.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                // Export progress bar
                if (uiState.isExporting) {
                    Text(
                        "Exporting… ${(uiState.exportProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    LinearProgressIndicator(
                        progress = { uiState.exportProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Brand.Blue,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }

                Text(
                    "Trim Range",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(Spacing.md))

                // Trim timeline bar
                val duration = uiState.duration.coerceAtLeast(1L)
                val startFrac = uiState.trimStart.toFloat() / duration
                val endFrac = uiState.trimEnd.toFloat() / duration

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (startFrac > 0f) {
                            Spacer(
                                Modifier
                                    .fillMaxHeight()
                                    .weight(startFrac)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight((endFrac - startFrac).coerceAtLeast(0.01f))
                                .background(Brand.Blue.copy(alpha = 0.35f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(Brand.Blue),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(Brand.Blue),
                            )
                        }
                        if (endFrac < 1f) {
                            Spacer(
                                Modifier
                                    .fillMaxHeight()
                                    .weight((1f - endFrac).coerceAtLeast(0.01f))
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                // Unified RangeSlider: handles cannot cross each other.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Start: ${uiState.trimStart.toFormattedDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "End: ${uiState.trimEnd.toFormattedDuration()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val startFraction = if (duration > 0) uiState.trimStart.toFloat() / duration else 0f
                val endFraction = if (duration > 0) uiState.trimEnd.toFloat() / duration else 1f
                RangeSlider(
                    value = startFraction..endFraction,
                    onValueChange = { range ->
                        val newStart = (range.start * duration).toLong()
                        val newEnd = (range.endInclusive * duration).toLong()
                        // Determine which handle moved to update only that seek target.
                        if (newStart != uiState.trimStart) {
                            activeDragHandle = "start"
                            viewModel.setTrimStart(newStart)
                        } else if (newEnd != uiState.trimEnd) {
                            activeDragHandle = "end"
                            viewModel.setTrimEnd(newEnd)
                        }
                    },
                    onValueChangeFinished = { activeDragHandle = null },
                    valueRange = 0f..1f,
                    steps = 0,
                    colors = SliderDefaults.colors(thumbColor = Brand.Blue, activeTrackColor = Brand.Blue),
                    enabled = !uiState.isExporting,
                )

                Spacer(Modifier.height(Spacing.xl))

                // Duration info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "Selection",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            (uiState.trimEnd - uiState.trimStart).toFormattedDuration(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            duration.toFormattedDuration(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun Long.toFormattedDuration(): String {
    val totalSec = this / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
