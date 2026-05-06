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
import com.grow.gallery.core.designsystem.components.GalleryTopBar

data class EditorTool(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isPremium: Boolean = false,
)

@Composable
fun EditorScreen(
    mediaId: Long,
    onNavigateUp: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTool by remember { mutableStateOf<EditorTool?>(null) }

    LaunchedEffect(mediaId) { viewModel.loadMedia(mediaId) }

    val tools = remember {
        listOf(
            EditorTool("crop", "Crop", Icons.Default.Crop),
            EditorTool("adjust", "Adjust", Icons.Default.Tune),
            EditorTool("filter", "Filter", Icons.Default.AutoFixHigh),
            EditorTool("enhance", "Enhance", Icons.Default.AutoAwesome, isPremium = true),
            EditorTool("remove_bg", "Remove BG", Icons.Default.ContentCut, isPremium = true),
            EditorTool("colorize", "Colorize", Icons.Default.Palette, isPremium = true),
            EditorTool("upscale", "Upscale", Icons.Default.ZoomIn, isPremium = true),
        )
    }

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "AI Editor",
                onNavigateUp = onNavigateUp,
                actions = {
                    TextButton(
                        onClick = { /* TODO: Save */ onNavigateUp() },
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold, color = Brand.Blue)
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
                    CircularProgressIndicator(color = Brand.Blue)
                }
            }

            // Tool options based on selection
            selectedTool?.let { tool ->
                EditorToolOptions(tool = tool, onClose = { selectedTool = null })
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
            .background(
                if (isSelected) Brand.BlueLight else MaterialTheme.colorScheme.surfaceVariant
            )
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
            Text(
                text = "✨",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun EditorToolOptions(tool: EditorTool, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
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
                        "✨ Premium feature - Upgrade to unlock",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.GoldEnd,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            } else {
                when (tool.id) {
                    "adjust" -> AdjustOptions()
                    "crop" -> CropOptions()
                    else -> {
                        Text(
                            "${tool.label} options",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustOptions() {
    val adjustments = listOf("Brightness", "Contrast", "Saturation", "Sharpness")
    adjustments.forEach { adj ->
        var value by remember { mutableStateOf(0f) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(adj, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Brand.Blue, activeTrackColor = Brand.Blue),
            )
        }
    }
}

@Composable
private fun CropOptions() {
    val ratios = listOf("Free", "1:1", "4:3", "16:9", "3:2")
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
