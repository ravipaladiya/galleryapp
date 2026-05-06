package com.grow.gallery.feature.storage

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@Composable
fun StorageScreen(onNavigateUp: () -> Unit) {
    val stat = remember { StatFs(Environment.getExternalStorageDirectory().path) }
    val totalBytes = remember { stat.totalBytes }
    val freeBytes = remember { stat.availableBytes }
    val usedBytes = remember { totalBytes - freeBytes }

    Scaffold(
        topBar = { GalleryTopBar(title = "Storage Manager", onNavigateUp = onNavigateUp) },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                // Storage donut chart card
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
                            usedBytes = usedBytes,
                            totalBytes = totalBytes,
                        )
                        Spacer(Modifier.height(Spacing.xl))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StorageStat("Used", usedBytes.formatSize(), Brand.Blue)
                            StorageStat("Free", freeBytes.formatSize(), Color(0xFF43A047))
                            StorageStat("Total", totalBytes.formatSize(), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { SectionHeader("By Type") }
            item {
                StorageTypeRow(
                    icon = Icons.Default.Photo,
                    label = "Photos",
                    size = "Calculating…",
                    color = Brand.Blue,
                )
            }
            item {
                StorageTypeRow(
                    icon = Icons.Default.VideoFile,
                    label = "Videos",
                    size = "Calculating…",
                    color = Color(0xFF7C3AED),
                )
            }
            item {
                StorageTypeRow(
                    icon = Icons.Default.FolderOpen,
                    label = "Other Files",
                    size = "Calculating…",
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
                    onClick = { /* Navigate to cleaner */ },
                )
            }
            item {
                SettingRow(
                    title = "Empty Trash",
                    subtitle = "Permanently delete trashed items",
                    leading = { Icon(Icons.Default.Delete, null) },
                    trailing = { Icon(Icons.Default.ChevronRight, null) },
                    onClick = { /* Navigate to trash */ },
                )
            }
        }
    }
}

@Composable
private fun StorageProgressBar(usedBytes: Long, totalBytes: Long) {
    val fraction = (usedBytes.toFloat() / totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f)
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brand.Blue),
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
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
}

private fun Long.formatSize(): String = when {
    this >= 1_000_000_000L -> "%.1f GB".format(this / 1_000_000_000.0)
    this >= 1_000_000L -> "%.1f MB".format(this / 1_000_000.0)
    this >= 1_000L -> "%.0f KB".format(this / 1_000.0)
    else -> "$this B"
}
