package com.grow.gallery.feature.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
import com.grow.gallery.core.media.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    onOpenViewer: (Long, Boolean) -> Unit,
    onOpenSlideshow: () -> Unit,
    viewModel: MemoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Memories", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSlideshow) {
                        Icon(Icons.Default.PlayCircle, "Slideshow")
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
            uiState.memories.isEmpty() -> EmptyState(
                icon = Icons.Default.AutoAwesome,
                title = "No Memories Yet",
                description = "Take more photos to create memories.",
                modifier = Modifier.padding(paddingValues),
            )
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                    ),
                ) {
                    // On This Day section
                    if (uiState.onThisDay.isNotEmpty()) {
                        item {
                            OnThisDaySection(
                                items = uiState.onThisDay,
                                onOpenViewer = onOpenViewer,
                            )
                        }
                    }

                    // Memory cards
                    items(uiState.memories, key = { it.label }) { memory ->
                        MemoryCard(
                            memory = memory,
                            onOpenViewer = onOpenViewer,
                            onStartSlideshow = onOpenSlideshow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnThisDaySection(
    items: List<MediaItem>,
    onOpenViewer: (Long, Boolean) -> Unit,
) {
    Column {
        SectionHeader("On This Day")
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(items, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onOpenViewer(item.id, item.isVideo) },
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.uri)
                            .crossfade(true)
                            .size(240)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryGroup,
    onOpenViewer: (Long, Boolean) -> Unit,
    onStartSlideshow: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .height(240.dp),
        shape = MaterialTheme.shapes.extraLarge,
        onClick = { memory.items.firstOrNull()?.let { onOpenViewer(it.id, it.isVideo) } },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            memory.items.firstOrNull()?.let { item ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        )
                    ),
            )

            // Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.lg),
            ) {
                Text(
                    text = memory.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "${memory.items.size} photos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }

            // Play button
            IconButton(
                onClick = onStartSlideshow,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.md),
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    "Play slideshow",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
