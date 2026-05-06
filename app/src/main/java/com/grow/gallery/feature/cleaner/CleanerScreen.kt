package com.grow.gallery.feature.cleaner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(
    onNavigateUp: () -> Unit,
    viewModel: CleanerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "Smart Cleaner",
                onNavigateUp = onNavigateUp,
            )
        },
    ) { paddingValues ->
        when (uiState.phase) {
            CleanerPhase.IDLE -> CleanerIdle(
                onStartScan = viewModel::startScan,
                modifier = Modifier.padding(paddingValues),
            )
            CleanerPhase.SCANNING -> CleanerScanning(modifier = Modifier.padding(paddingValues))
            CleanerPhase.RESULTS -> CleanerResults(
                uiState = uiState,
                onClean = viewModel::cleanSelected,
                onCleanAll = viewModel::cleanAll,
                modifier = Modifier.padding(paddingValues),
            )
            CleanerPhase.DONE -> CleanerDone(
                freedSpace = uiState.freedSpace,
                deletedCount = uiState.deletedCount,
                onDone = onNavigateUp,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun CleanerIdle(onStartScan: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Brand.BlueLight, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.CleaningServices,
                contentDescription = null,
                tint = Brand.Blue,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            "Smart Cleaner",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Free up space by removing duplicate and similar photos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxxl))

        // Feature list
        listOf(
            Icons.Default.CopyAll to "Duplicate Photos",
            Icons.Default.PhotoLibrary to "Similar Photos",
            Icons.Default.Screenshot to "Screenshots",
            Icons.Default.BlurOn to "Blurry Photos",
        ).forEach { (icon, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = Brand.Blue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.md))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(Spacing.xxxl))
        Button(
            onClick = onStartScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.Search, null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Start Scanning", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun CleanerScanning(modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition(label = "rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "rotation",
    )
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = null,
            tint = Brand.Blue,
            modifier = Modifier.size(80.dp).rotate(rotation),
        )
        Spacer(Modifier.height(Spacing.xl))
        Text("Scanning your gallery…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.sm))
        Text("This may take a moment.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xl))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f), color = Brand.Blue)
    }
}

@Composable
private fun CleanerResults(
    uiState: CleanerUiState,
    onClean: () -> Unit,
    onCleanAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Summary header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Brand.BlueLight,
        ) {
            Column(modifier = Modifier.padding(Spacing.xl)) {
                Text(
                    "Scan Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Brand.Blue,
                )
                Text(
                    "${uiState.categories.sumOf { it.itemCount }} items • ${uiState.totalReclaimable} can be freed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BlueDark,
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            items(uiState.categories, key = { it.name }) { category ->
                CleanerCategoryCard(
                    category = category,
                    onToggle = { /* toggle category selection */ },
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onCleanAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Clean All — Free ${uiState.totalReclaimable}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CleanerCategoryCard(
    category: CleanerCategory,
    onToggle: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it; onToggle(it) },
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${category.itemCount} items • ${category.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Expand",
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanerDone(
    freedSpace: String,
    deletedCount: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFF43A047).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF43A047), modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text("Cleaning Complete!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.sm))
        Text("$deletedCount items deleted", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$freedSpace freed", style = MaterialTheme.typography.headlineSmall, color = Brand.Blue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.xxxl))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Done", fontWeight = FontWeight.SemiBold) }
    }
}
