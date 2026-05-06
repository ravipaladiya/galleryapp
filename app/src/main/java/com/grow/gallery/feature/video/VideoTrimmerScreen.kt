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

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "Trim Video",
                onNavigateUp = onNavigateUp,
                actions = {
                    TextButton(
                        onClick = {
                            // TODO: Use Media3 Transformer for actual trim export
                            // val transformer = Transformer.Builder(context).build()
                            // transformer.start(editedMediaItem, outputPath)
                            onNavigateUp()
                        },
                    ) {
                        Text("Export", fontWeight = FontWeight.SemiBold, color = Brand.Blue)
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
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                Text(
                    "Trim Range",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(Spacing.md))

                // Trim timeline visualization
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    // Trim range indicator
                    val duration = uiState.duration.coerceAtLeast(1L)
                    val startFrac = uiState.trimStart.toFloat() / duration
                    val endFrac = uiState.trimEnd.toFloat() / duration

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(endFrac - startFrac)
                            .offset(x = (startFrac * 300).dp)
                            .background(Brand.Blue.copy(alpha = 0.4f)),
                    )
                }

                Spacer(Modifier.height(Spacing.lg))

                // Start trim slider
                Text("Start: ${uiState.trimStart.toFormattedDuration()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = if (uiState.duration > 0) uiState.trimStart.toFloat() / uiState.duration else 0f,
                    onValueChange = { viewModel.setTrimStart((it * uiState.duration).toLong()) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = Brand.Blue, activeTrackColor = Brand.Blue),
                )

                // End trim slider
                Text("End: ${uiState.trimEnd.toFormattedDuration()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = if (uiState.duration > 0) uiState.trimEnd.toFloat() / uiState.duration else 1f,
                    onValueChange = { viewModel.setTrimEnd((it * uiState.duration).toLong()) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = Brand.Blue, activeTrackColor = Brand.Blue),
                )

                Spacer(Modifier.height(Spacing.xl))

                // Duration info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            (uiState.trimEnd - uiState.trimStart).toFormattedDuration(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column {
                        Text("Original", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            uiState.duration.toFormattedDuration(),
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
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${"%02d".format(s)}"
}
