package com.grow.gallery.feature.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onNavigateUp: () -> Unit,
    onOpenCleaner: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { GalleryTopBar(title = "Storage Manager", onNavigateUp = onNavigateUp) },
    ) { paddingValues ->
        if (uiState.isLoading) {
            LoadingScreen(Modifier.padding(paddingValues))
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Device Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(Spacing.xl))
                        StorageProgressBar(
                            usedBytes = uiState.usedBytes,
                            totalBytes = uiState.totalBytes,
                        )
                        Spacer(Modifier.height(Spacing.xl))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StorageStat("Used", uiState.usedBytes.formatSize(), Brand.Blue)
                            StorageStat("Free", uiState.freeBytes.formatSize(), Color(0xFF43A047))
                            StorageStat("Total", uiState.totalBytes.formatSize(), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { SectionHeader("By Type") }
            item {
                StorageTypeRow(
                    icon = Icons.Default.Photo,
                    label = "Photos",
                    size = uiState.photosBytes.formatSize(),
                    fraction = if (uiState.totalBytes > 0) uiState.photosBytes.toFloat() / uiState.totalBytes else 0f,
                    color = Brand.Blue,
                )
            }
            item {
                StorageTypeRow(
                    icon = Icons.Default.VideoFile,
                    label = "Videos",
                    size = uiState.videosBytes.formatSize(),
                    fraction = if (uiState.totalBytes > 0) uiState.videosBytes.toFloat() / uiState.totalBytes else 0f,
                    color = Color(0xFF7C3AED),
                )
            }
            item {
                val other = (uiState.usedBytes - uiState.photosBytes - uiState.videosBytes).coerceAtLeast(0L)
                StorageTypeRow(
                    icon = Icons.Default.FolderOpen,
                    label = "Other Files",
                    size = other.formatSize(),
                    fraction = if (uiState.totalBytes > 0) other.toFloat() / uiState.totalBytes else 0f,
                    color = Color(0xFFD97706),
                )
            }

            item { SectionHeader("Quick Actions") }
            item {
                SettingRow(
                    title = "Smart Cleaner",
                    subtitle = "Find and remove junk files",
                    leading = { Icon(Icons.Default.CleaningServices, null) },
                    trailing = { Icon(Icons.Default.ChevronRight, null) },
                    onClick = onOpenCleaner,
                )
            }
            item {
                SettingRow(
                    title = "Recently Deleted",
                    subtitle = "Manage deleted items",
                    leading = { Icon(Icons.Default.Delete, null) },
                    trailing = { Icon(Icons.Default.ChevronRight, null) },
                    onClick = onOpenTrash,
                )
            }
        }
    }
}

@Composable
private fun StorageProgressBar(usedBytes: Long, totalBytes: Long) {
    val fraction = (usedBytes.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
    val color = when {
        fraction > 0.9f -> MaterialTheme.colorScheme.error
        fraction > 0.7f -> Color(0xFFD97706)
        else -> Brand.Blue
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "${(fraction * 100).toInt()}% used",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StorageTypeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: String,
    fraction: Float,
    color: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(size, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

private fun Long.formatSize(): String = when {
    this >= 1_000_000_000L -> "%.1f GB".format(this / 1_000_000_000.0)
    this >= 1_000_000L -> "%.1f MB".format(this / 1_000_000.0)
    this >= 1_000L -> "%.0f KB".format(this / 1_000.0)
    else -> "$this B"
}
