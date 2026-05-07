package com.grow.gallery.feature.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
import com.grow.gallery.core.media.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    onOpenViewer: (Long, Boolean) -> Unit,
    onOpenSlideshow: (mediaIds: List<Long>) -> Unit,
    viewModel: MemoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
            snapshotFlow { uiState.isLoading }.filter { !it }.first()
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Memories", fontWeight = FontWeight.Bold) },
                actions = {
                    val allIds = (uiState.onThisDay + uiState.memories.flatMap { it.items }).map { it.id }
                    IconButton(
                        onClick = { onOpenSlideshow(allIds) },
                        enabled = allIds.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.PlayCircle, "Slideshow")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            when {
                uiState.isLoading && !isRefreshing -> LoadingScreen(Modifier.fillMaxSize())
                uiState.memories.isEmpty() && uiState.onThisDay.isEmpty() && !isRefreshing -> EmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = "No Memories Yet",
                    description = "Take more photos to create memories. Your best moments will appear here.",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            bottom = paddingValues.calculateBottomPadding() + 16.dp,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (uiState.onThisDay.isNotEmpty()) {
                            item {
                                OnThisDaySection(
                                    items = uiState.onThisDay,
                                    onOpenViewer = onOpenViewer,
                                )
                            }
                        }

                        items(
                            uiState.memories,
                            key = { "${it.year}-${it.monthIndex}" },
                        ) { memory ->
                            MemoryCard(
                                memory = memory,
                                onOpenViewer = onOpenViewer,
                                onStartSlideshow = { onOpenSlideshow(memory.items.map { it.id }) },
                            )
                        }
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
        SectionHeader(
            title = "On This Day",
            count = items.size,
        )
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
                            // 480px: correct for xxxhdpi (4×) 120dp containers, sharp on foldables
                            .size(480)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (item.isVideo) {
                        Icon(
                            Icons.Default.PlayCircle,
                            null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp),
                        )
                    }
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
                        // Bounded decode size to avoid thrashing the bitmap pool on long lists
                        .size(Size(800, 480))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        )
                    ),
            )

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
                    text = "${memory.items.size} photo${if (memory.items.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }

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
