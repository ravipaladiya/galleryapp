package com.grow.gallery.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

// Note: Map view requires Google Maps SDK or OSM integration.
// This screen provides the UI shell; actual map rendering requires
// adding the Maps SDK dependency and API key configuration.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewScreen(
    onNavigateUp: () -> Unit,
    onOpenViewer: (Long, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "Map View",
                onNavigateUp = onNavigateUp,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Map placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(Spacing.xxxl),
                ) {
                    Icon(
                        Icons.Default.Map,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                        modifier = Modifier.size(80.dp),
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    Text(
                        "Map View",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Photos with location data will appear on the map.\n\n" +
                                "To enable: Add Google Maps SDK and API key in build config.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    // TODO: Replace with actual Google Maps Compose or OSMDroid
                    // GoogleMap(
                    //     modifier = Modifier.fillMaxSize(),
                    //     cameraPositionState = cameraPositionState,
                    // ) {
                    //     mediaWithLocation.forEach { item ->
                    //         Marker(state = MarkerState(LatLng(item.latitude!!, item.longitude!!)))
                    //     }
                    // }
                }
            }
        }
    }
}
