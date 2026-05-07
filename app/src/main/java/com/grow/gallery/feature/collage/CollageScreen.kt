package com.grow.gallery.feature.collage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
import com.grow.gallery.core.media.MediaItem
import kotlinx.coroutines.launch

data class CollageLayout(val id: String, val label: String, val columns: Int, val rows: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    onNavigateUp: () -> Unit,
    viewModel: CollageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedLayout by remember { mutableStateOf(collageLayouts.first()) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val layouts = collageLayouts

    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.onSaveMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GalleryTopBar(
                title = "Collage Maker",
                onNavigateUp = onNavigateUp,
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = Spacing.sm),
                            strokeWidth = 2.dp,
                            color = Brand.Blue,
                        )
                    } else {
                        TextButton(
                            onClick = { scope.launch { viewModel.saveCollage(context, selectedLayout) } },
                            enabled = uiState.selectedItems.isNotEmpty(),
                        ) {
                            Text("Save", color = Brand.Blue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Collage preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(Spacing.lg)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                CollagePreview(
                    selectedItems = uiState.selectedItems,
                    layout = selectedLayout,
                )
            }

            // Layout selector
            Text(
                "Layout",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(layouts) { layout ->
                    LayoutChip(
                        layout = layout,
                        isSelected = selectedLayout.id == layout.id,
                        onClick = { selectedLayout = layout },
                    )
                }
            }

            // Photo picker
            Text(
                "Select Photos (${uiState.selectedItems.size}/${selectedLayout.columns * selectedLayout.rows})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val pickerColumns = when {
                screenWidthDp >= 840 -> 8
                screenWidthDp >= 600 -> 6
                else -> 4
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(pickerColumns),
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(uiState.availableItems, key = { it.id }) { item ->
                    val isSelected = uiState.selectedItems.contains(item)
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.small)
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                Brand.Blue,
                                MaterialTheme.shapes.small,
                            )
                            .clickable { viewModel.toggleSelection(item, selectedLayout.columns * selectedLayout.rows) },
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.thumbnailUri ?: item.uri)
                                .memoryCacheKey(MemoryCache.Key("media_${item.id}"))
                                .diskCacheKey("media_${item.id}")
                                .precision(Precision.INEXACT)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brand.Blue.copy(alpha = 0.3f)),
                            )
                            val index = uiState.selectedItems.indexOf(item) + 1
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .background(Brand.Blue, RoundedCornerShape(50))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text("$index", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollagePreview(
    selectedItems: List<MediaItem>,
    layout: CollageLayout,
) {
    val totalSlots = layout.columns * layout.rows
    Row(modifier = Modifier.fillMaxSize()) {
        repeat(layout.columns) { col ->
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                repeat(layout.rows) { row ->
                    val index = col * layout.rows + row
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val item = selectedItems.getOrNull(index)
                        if (item != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.thumbnailUri ?: item.uri)
                                    .memoryCacheKey(MemoryCache.Key("media_${item.id}"))
                                    .diskCacheKey("media_${item.id}")
                                    .precision(Precision.INEXACT)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .allowHardware(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutChip(layout: CollageLayout, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) Brand.Blue else MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.medium,
            )
            .background(if (isSelected) Brand.BlueLight else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(layout.columns) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(layout.rows) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(
                                    color = if (isSelected) Brand.Blue
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

private val collageLayouts = listOf(
    CollageLayout("1x1", "1×1", 1, 1),
    CollageLayout("1x2", "1×2", 1, 2),
    CollageLayout("2x1", "2×1", 2, 1),
    CollageLayout("2x2", "2×2", 2, 2),
    CollageLayout("2x3", "2×3", 2, 3),
    CollageLayout("3x2", "3×2", 3, 2),
    CollageLayout("3x3", "3×3", 3, 3),
)
