package com.grow.gallery.feature.slideshow

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grow.gallery.core.designsystem.*
import kotlinx.coroutines.delay

@Composable
fun SlideshowScreen(
    albumId: Long,
    onNavigateUp: () -> Unit,
    viewModel: SlideshowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(albumId) { viewModel.loadSlideshow(albumId) }

    LaunchedEffect(isPlaying, currentIndex, uiState.items.size) {
        if (isPlaying && uiState.items.isNotEmpty()) {
            delay(uiState.intervalMs)
            currentIndex = (currentIndex + 1) % uiState.items.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls },
    ) {
        if (uiState.items.isNotEmpty()) {
            val item = uiState.items[currentIndex]

            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    fadeIn(tween(800)) togetherWith fadeOut(tween(800))
                },
                label = "slideshow",
            ) { index ->
                val slideItem = uiState.items.getOrNull(index)
                if (slideItem != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(slideItem.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
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
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                    Text(
                        "${currentIndex + 1} / ${uiState.items.size}",
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brand.ViewerControlBg)
                    .navigationBarsPadding()
                    .padding(vertical = Spacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        currentIndex = (currentIndex - 1 + uiState.items.size) % uiState.items.size
                    }) {
                        Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(Spacing.xl))
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.xl))
                    IconButton(onClick = {
                        currentIndex = (currentIndex + 1) % uiState.items.size
                    }) {
                        Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
