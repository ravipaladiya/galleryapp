package com.grow.gallery.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.ConfirmDialog
import com.grow.gallery.core.designsystem.components.GalleryTopBar

data class EditorTool(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isPremium: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    mediaId: Long,
    onNavigateUp: () -> Unit,
    onOpenPremium: () -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTool by remember { mutableStateOf<EditorTool?>(null) }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) { viewModel.loadMedia(mediaId) }

    // Use a one-shot state key to avoid race on isSaved flip-back
    LaunchedEffect(uiState.savedUri) {
        if (uiState.isSaved && uiState.savedUri != null) {
            showSaveSuccessDialog = true
            viewModel.onSavedHandled()
        }
    }

    val tools = remember {
        listOf(
            EditorTool("adjust", "Adjust", Icons.Default.Tune),
            EditorTool("filter", "Filter", Icons.Default.AutoFixHigh),
            EditorTool("crop", "Crop", Icons.Default.Crop),
            EditorTool("enhance", "Enhance", Icons.Default.AutoAwesome, isPremium = true),
            EditorTool("remove_bg", "Remove BG", Icons.Default.ContentCut, isPremium = true),
            EditorTool("colorize", "Colorize", Icons.Default.Palette, isPremium = true),
            EditorTool("upscale", "Upscale", Icons.Default.ZoomIn, isPremium = true),
        )
    }

    // Build live preview ColorFilter from current adjustments + filter preset
    val previewColorFilter = remember(uiState.brightness, uiState.contrast, uiState.saturation, uiState.filterName) {
        val hasWork = uiState.brightness != 0f || uiState.contrast != 0f ||
                uiState.saturation != 0f || uiState.filterName != "None"
        if (hasWork) {
            val matrix = viewModel.buildColorMatrix(uiState)
            val composeMatrix = matrix.array
            ColorFilter.colorMatrix(ColorMatrix(composeMatrix))
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "Photo Editor",
                onNavigateUp = onNavigateUp,
                actions = {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = Spacing.sm),
                            strokeWidth = 2.dp,
                            color = Brand.Blue,
                        )
                    } else {
                        val hasAdjustments = viewModel.hasAdjustments()
                        TextButton(
                            onClick = viewModel::resetAdjustments,
                            enabled = hasAdjustments,
                        ) {
                            Text(
                                "Reset",
                                color = if (hasAdjustments) Brand.Blue
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                        TextButton(onClick = viewModel::saveImage) {
                            Text("Save", fontWeight = FontWeight.SemiBold, color = Brand.Blue)
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
            // Image preview with live color adjustments applied
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                uiState.imageUri?.let { uri ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = previewColorFilter,
                    )
                    // Crop ratio overlay indicator
                    if (uiState.cropRatio != "Free") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.sm)
                                .background(Brand.Blue.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                .padding(horizontal = Spacing.sm, vertical = 2.dp),
                        ) {
                            Text(
                                uiState.cropRatio,
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                    // Sharpness indicator (can't show in real-time with ColorFilter)
                    if (uiState.sharpness > 0.01f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(Spacing.sm)
                                .background(Brand.Blue.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                .padding(horizontal = Spacing.sm, vertical = 2.dp),
                        ) {
                            Text(
                                "Sharpen: ${"%.1f".format(uiState.sharpness)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
                if (uiState.isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Brand.Blue)
                            Spacer(Modifier.height(Spacing.sm))
                            Text("Saving…", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Tool options based on selection
            selectedTool?.let { tool ->
                EditorToolOptions(
                    tool = tool,
                    uiState = uiState,
                    viewModel = viewModel,
                    onClose = { selectedTool = null },
                    onOpenPremium = onOpenPremium,
                )
            }

            // Tool list
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(tools) { tool ->
                    EditorToolChip(
                        tool = tool,
                        isSelected = selectedTool?.id == tool.id,
                        onClick = { selectedTool = if (selectedTool?.id == tool.id) null else tool },
                    )
                }
            }
        }
    }

    if (showSaveSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSaveSuccessDialog = false; onNavigateUp() },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = Brand.Blue) },
            title = { Text("Saved") },
            text = { Text("Edited photo saved to Pictures/GalleryApp.") },
            confirmButton = {
                Button(onClick = { showSaveSuccessDialog = false; onNavigateUp() }) {
                    Text("Done")
                }
            },
        )
    }

    uiState.error?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Save Failed") },
            text = { Text(err) },
            confirmButton = {
                Button(onClick = viewModel::clearError) { Text("OK") }
            },
        )
    }
}

@Composable
private fun EditorToolChip(
    tool: EditorTool,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Brand.BlueLight else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) Brand.Blue else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = tool.label,
            tint = if (isSelected) Brand.Blue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Brand.Blue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tool.isPremium) {
            Text("✨", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EditorToolOptions(
    tool: EditorTool,
    uiState: EditorUiState,
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onOpenPremium: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tool.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
                }
            }
            if (tool.isPremium) {
                Spacer(Modifier.height(Spacing.sm))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = Brand.GoldStart.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "✨ Premium feature",
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.GoldEnd,
                        )
                        TextButton(
                            onClick = onOpenPremium,
                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 2.dp),
                        ) {
                            Text(
                                "Upgrade",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Brand.GoldEnd,
                            )
                        }
                    }
                }
            } else {
                when (tool.id) {
                    "adjust" -> AdjustOptions(uiState = uiState, viewModel = viewModel)
                    "crop" -> CropOptions(selectedRatio = uiState.cropRatio, onSelect = viewModel::setCropRatio)
                    "filter" -> FilterOptions(selectedFilter = uiState.filterName, onSelect = viewModel::setFilter)
                    else -> Text(
                        "${tool.label} options",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustOptions(uiState: EditorUiState, viewModel: EditorViewModel) {
    val adjustments = listOf(
        "Brightness" to (uiState.brightness to viewModel::setBrightness),
        "Contrast" to (uiState.contrast to viewModel::setContrast),
        "Saturation" to (uiState.saturation to viewModel::setSaturation),
        "Sharpness" to (uiState.sharpness to viewModel::setSharpness),
    )
    adjustments.forEach { (label, valueSetter) ->
        val (value, setter) = valueSetter
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = value,
                onValueChange = setter,
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Brand.Blue, activeTrackColor = Brand.Blue),
            )
            Text(
                "%.1f".format(value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
        }
    }
}

@Composable
private fun CropOptions(selectedRatio: String, onSelect: (String) -> Unit) {
    val ratios = listOf("Free", "1:1", "4:3", "16:9", "3:2", "9:16")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(ratios) { ratio ->
            FilterChip(
                selected = selectedRatio == ratio,
                onClick = { onSelect(ratio) },
                label = { Text(ratio, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun FilterOptions(selectedFilter: String, onSelect: (String) -> Unit) {
    val filters = listOf("None", "Vivid", "Warm", "Cool", "B&W", "Fade", "Chrome")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(filters) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}
