package com.grow.gallery.feature.map

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.GalleryTopBar
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewScreen(
    onNavigateUp: () -> Unit,
    onOpenViewer: (Long, Boolean) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(false) }

    // Request ACCESS_MEDIA_LOCATION so EXIF coordinates are available on Q+.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            hasLocationPermission = true
        }
    }

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "Map View",
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Brand.Blue)
                            Spacer(Modifier.height(Spacing.md))
                            Text(
                                "Reading photo locations…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                uiState.geoItems.isEmpty() && !uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(Spacing.xxxl),
                        ) {
                            Icon(
                                Icons.Default.LocationOff,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(Modifier.height(Spacing.lg))
                            Text(
                                "No photos with location data",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "Enable location in your camera app to see photos on the map.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    OsmMap(
                        geoItems = uiState.geoItems,
                        context = context,
                        onMarkerClick = { viewModel.selectItem(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Photo count badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.md),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            "${uiState.geoItems.size} photos",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // Selected photo preview card
            AnimatedVisibility(
                visible = uiState.selectedItem != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                uiState.selectedItem?.let { geo ->
                    SelectedPhotoCard(
                        geo = geo,
                        onOpen = { onOpenViewer(geo.item.id, geo.item.mimeType.startsWith("video/")) },
                        onDismiss = { viewModel.selectItem(null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OsmMap(
    geoItems: List<GeoMediaItem>,
    context: Context,
    onMarkerClick: (GeoMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = ctx.packageName

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(5.0)

                // Centre the map on the first photo's location
                geoItems.firstOrNull()?.let { first ->
                    controller.setCenter(GeoPoint(first.latitude, first.longitude))
                }

                geoItems.forEach { geo ->
                    val marker = Marker(this).apply {
                        position = GeoPoint(geo.latitude, geo.longitude)
                        title = geo.item.displayName
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onMarkerClick(geo)
                            true
                        }
                    }
                    overlays.add(marker)
                }

                // Zoom to fit all markers if there are multiple
                if (geoItems.size > 1) {
                    val lats = geoItems.map { it.latitude }
                    val lngs = geoItems.map { it.longitude }
                    val north = lats.max()
                    val south = lats.min()
                    val east = lngs.max()
                    val west = lngs.min()
                    val center = GeoPoint((north + south) / 2, (east + west) / 2)
                    controller.setCenter(center)
                }
            }
        },
        update = { mapView ->
            mapView.invalidate()
        },
        onRelease = { mapView -> mapView.onDetach() },
        modifier = modifier,
    )
}

@Composable
private fun SelectedPhotoCard(
    geo: GeoMediaItem,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable { onOpen() }
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(geo.item.uri)
                    .crossfade(true)
                    .size(80)
                    .build(),
                contentDescription = geo.item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    geo.item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "%.4f, %.4f".format(geo.latitude, geo.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
