package com.grow.gallery.feature.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.billing.PremiumPlan
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.GalleryTopBar

@Composable
fun PremiumScreen(
    onNavigateUp: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPlan by remember { mutableStateOf<PremiumPlan?>(uiState.plans.firstOrNull { it.isBestValue }) }

    LaunchedEffect(uiState.plans) {
        if (selectedPlan == null) selectedPlan = uiState.plans.firstOrNull { it.isBestValue }
    }

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "",
                onNavigateUp = onNavigateUp,
                containerColor = Color.Transparent,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                // Hero header with gold gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = paddingValues.calculateTopPadding())
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1A1A2E),
                                    Color(0xFF16213E),
                                )
                            )
                        )
                        .padding(Spacing.xxxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✨", fontSize = 56.sp)
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            "Gallery Premium",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "Unlock all features. One app, endless possibilities.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Features list
            item {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    Text(
                        "What's included",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                    premiumFeatures.forEach { feature ->
                        PremiumFeatureRow(feature.first, feature.second)
                    }
                }
            }

            // Plan selection
            item {
                Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                    Text(
                        "Choose your plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                    uiState.plans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            isSelected = selectedPlan?.id == plan.id,
                            onClick = { selectedPlan = plan },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                }
            }

            // Subscribe button
            item {
                Column(
                    modifier = Modifier.padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = { selectedPlan?.let { viewModel.subscribe(it.id) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Brand.GoldGradient, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        enabled = !uiState.isLoading,
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text(
                                "Subscribe Now",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    TextButton(onClick = viewModel::restorePurchase) {
                        Text("Restore Purchase", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Terms of Service • Privacy Policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumFeatureRow(icon: String, feature: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(Spacing.md))
        Text(feature, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlanCard(
    plan: PremiumPlan,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Brand.Blue else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp),
            )
            .background(
                if (isSelected) Brand.BlueLight else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plan.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (plan.isBestValue) {
                        Spacer(Modifier.width(Spacing.sm))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Brand.GoldStart,
                        ) {
                            Text(
                                "Best Value",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Text(
                    plan.price,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = Brand.Blue)
            }
        }
    }
}

private val premiumFeatures = listOf(
    "🚫" to "No ads, forever",
    "🔐" to "Secure Vault - unlimited private photos",
    "🧹" to "Smart Cleaner - free up space",
    "🤖" to "AI Editor - enhance, remove background",
    "🗺️" to "Map View - see photos by location",
    "🎬" to "Video Trimmer",
    "🖼️" to "Collage Maker",
    "📊" to "Storage Manager",
    "☁️" to "Backup & Sync",
    "⭐" to "Priority customer support",
)
