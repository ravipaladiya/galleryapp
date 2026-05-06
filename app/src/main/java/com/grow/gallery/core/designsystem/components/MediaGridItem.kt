package com.grow.gallery.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grow.gallery.core.designsystem.Brand
import com.grow.gallery.core.designsystem.ShapeMediaItem
import com.grow.gallery.core.media.MediaItem

@Composable
fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.92f else 1f,
        animationSpec = tween(150),
        label = "itemScale",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(ShapeMediaItem)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, Brand.Blue, ShapeMediaItem)
                else Modifier
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.thumbnailUri ?: item.uri)
                .crossfade(true)
                .size(320)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Dark overlay for selected state
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brand.ScrimLight),
            )
        }

        // Video indicator
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            // Video duration
            item.duration?.let { duration ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), ShapeMediaItem)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = duration.formatDuration(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Favorite indicator
        if (item.isFavorite && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .padding(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = Brand.GoldStart,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Selection checkbox
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = scaleIn(tween(150)),
            exit = scaleOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Brand.Blue,
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.White, CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.White.copy(alpha = 0.7f), CircleShape)
                        .border(1.5.dp, Color.White, CircleShape),
                )
            }
        }
    }
}

private fun Long.formatDuration(): String {
    val totalSec = this / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
