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
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTool by remember { mutableStateOf<EditorTool?>(null) }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) { viewModel.loadMedia(mediaId) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
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
                        TextButton(onClick = viewModel::resetAdjustments) {
                            Text("Reset")
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
            // Image preview
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
                    )
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
            onDismissRequest = { },
            title = { Text("Save Failed") },
            text = { Text(err) },
            confirmButton = {
                Button(onClick = { }) { Text("OK") }
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
                    Text(
                        "✨ Premium feature — Upgrade to unlock",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.GoldEnd,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            } else {
                when (tool.id) {
                    "adjust" -> AdjustOptions(uiState = uiState, viewModel = viewModel)
                    "crop" -> CropOptions()
                    "filter" -> FilterOptions()
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
private fun CropOptions() {
    val ratios = listOf("Free", "1:1", "4:3", "16:9", "3:2", "9:16")
    var selected by remember { mutableStateOf("Free") }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ratios.forEach { ratio ->
            FilterChip(
                selected = selected == ratio,
                onClick = { selected = ratio },
                label = { Text(ratio, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun FilterOptions() {
    val filters = listOf("None", "Vivid", "Warm", "Cool", "B&W", "Fade", "Chrome")
    var selected by remember { mutableStateOf("None") }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(filters) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { selected = filter },
                label = { Text(filter, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}
