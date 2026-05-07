package com.grow.gallery.feature.video

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.grow.gallery.core.designsystem.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    mediaId: Long,
    onNavigateUp: () -> Unit,
    onOpenTrimmer: () -> Unit,
    viewModel: VideoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) { viewModel.loadVideo(mediaId) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    // Sync isPlaying state with player via listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(uiState.videoUri) {
        uiState.videoUri?.let { uri ->
            exoPlayer.setMediaItem(Media3Item.fromUri(uri))
            exoPlayer.prepare()
        }
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Pause when backgrounded, resume on foreground, release on disposal
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> { /* let user resume manually */ }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls },
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

        // Top controls
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        text = uiState.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
                    )
                    IconButton(onClick = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    }) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            "Mute",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onOpenTrimmer) {
                        Icon(Icons.Default.ContentCut, "Trim", tint = Color.White)
                    }
                }
            }
        }

        // Center play/pause controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { exoPlayer.seekBack() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Replay10, "Seek back", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
                IconButton(
                    onClick = { exoPlayer.seekForward() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Forward10, "Seek forward", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        // Bottom seek bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            VideoSeekBar(
                player = exoPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brand.ViewerControlBg)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
    }
}

@Composable
private fun VideoSeekBar(player: ExoPlayer, modifier: Modifier = Modifier) {
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0L) }

    LaunchedEffect(player) {
        while (true) {
            progress = if (player.duration > 0) {
                player.currentPosition.toFloat() / player.duration
            } else 0f
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Column(modifier = modifier) {
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                player.seekTo((newProgress * player.duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Brand.Blue,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = (progress * duration).toLong().toFormattedDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = duration.toFormattedDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
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
