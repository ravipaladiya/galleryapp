package com.grow.gallery.feature.premium

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.billing.PremiumPlan
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.GalleryTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onNavigateUp: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPlan by remember { mutableStateOf<PremiumPlan?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Auto-select best value plan
    LaunchedEffect(uiState.plans) {
        if (selectedPlan == null) {
            selectedPlan = uiState.plans.firstOrNull { it.isBestValue }
        }
    }

    // Navigate away if user is already premium
    LaunchedEffect(uiState.isPremium) {
        if (uiState.isPremium) onNavigateUp()
    }

    // Show messages as snackbars — each unique ID fires exactly once
    val firstMessage = uiState.userMessages.firstOrNull()
    LaunchedEffect(firstMessage?.id) {
        firstMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg.text)
            viewModel.messageShown(msg.id)
        }
    }

    Scaffold(
        topBar = {
            GalleryTopBar(
                title = "",
                onNavigateUp = onNavigateUp,
                containerColor = Color.Transparent,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = paddingValues.calculateBottomPadding() + 32.dp),
        ) {
            // Hero header — respects theme via surface color accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = paddingValues.calculateTopPadding())
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                        )
                    )
                    .padding(Spacing.xxxl),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(56.dp),
                    )
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

            // Features list
            Column(modifier = Modifier.padding(Spacing.xl)) {
                Text(
                    "What's included",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
                premiumFeatures.forEach { (icon, feature) ->
                    PremiumFeatureRow(icon, feature)
                }
            }

            // Plan selection
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

            // Subscribe + Restore
            Column(
                modifier = Modifier.padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = { selectedPlan?.let { viewModel.subscribe(it.id) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            if (selectedPlan != null) Brand.GoldGradient
                            else Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                )
                            ),
                            RoundedCornerShape(12.dp),
                        )
                        .clip(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    enabled = !uiState.isLoading && selectedPlan != null,
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(
                            if (selectedPlan != null) "Subscribe Now" else "Select a Plan",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedPlan != null) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                TextButton(
                    onClick = viewModel::restorePurchase,
                    enabled = !uiState.isRestoring,
                ) {
                    if (uiState.isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "Restore Purchase",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(Spacing.sm))

                // Play Store required: clickable Terms and Privacy links
                val annotated = buildAnnotatedString {
                    pushStringAnnotation("URL", "https://example.com/terms")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("Terms of Service")
                    }
                    pop()
                    append("  •  ")
                    pushStringAnnotation("URL", "https://example.com/privacy")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("Privacy Policy")
                    }
                    pop()
                }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable {
                        // Open first annotation found (Terms or Privacy depending on tap region)
                        val url = annotated.getStringAnnotations("URL", 0, annotated.length)
                            .firstOrNull()?.item ?: return@clickable
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
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
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Brand.Blue,
            modifier = Modifier.size(20.dp),
        )
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
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Brand.Blue)
            } else {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private val premiumFeatures = listOf(
    "ad-free" to "No ads, forever",
    "vault" to "Secure Vault - unlimited private photos",
    "cleaner" to "Smart Cleaner - free up space",
    "editor" to "AI Editor - enhance and remove background",
    "map" to "Map View - see photos by location",
    "video" to "Video Trimmer",
    "collage" to "Collage Maker",
    "storage" to "Storage Manager",
    "backup" to "Backup and Sync",
    "support" to "Priority customer support",
)
