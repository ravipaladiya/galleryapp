package com.grow.gallery.feature.premium

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.billing.PremiumPlan
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.GalleryTopBar

private const val TOS_URL = "https://example.com/terms"
private const val PRIVACY_URL = "https://example.com/privacy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onNavigateUp: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Channel-based events ensure duplicate error messages still show the snackbar
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
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
        val activity = LocalContext.current as Activity
        if (uiState.isPremium) {
            PremiumActiveContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            PremiumSubscribeContent(
                uiState = uiState,
                paddingValues = paddingValues,
                onSubscribe = { planId -> viewModel.subscribe(activity, planId) },
                onRestore = viewModel::restorePurchase,
            )
        }
    }
}

@Composable
private fun PremiumActiveContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Brand.Blue,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "You're Premium!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "All features are unlocked. Thank you for your support.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PremiumSubscribeContent(
    uiState: PremiumUiState,
    paddingValues: PaddingValues,
    onSubscribe: (String) -> Unit,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current

    // selectedPlan is set once from the best-value plan and NOT reset on subsequent re-compositions,
    // so a user's manual plan selection is preserved if the plans list re-emits.
    var selectedPlan by remember { mutableStateOf<PremiumPlan?>(null) }
    LaunchedEffect(uiState.plans) {
        if (selectedPlan == null) {
            selectedPlan = uiState.plans.firstOrNull { it.isBestValue } ?: uiState.plans.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = paddingValues.calculateBottomPadding() + Spacing.xl),
    ) {
        // Hero header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = paddingValues.calculateTopPadding())
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        )
                    )
                )
                .padding(Spacing.xxxl),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Stars,
                    contentDescription = null,
                    tint = Color.White,
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
                    color = Color.White.copy(alpha = 0.85f),
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
            premiumFeatures.forEach { (icon, label) ->
                PremiumFeatureRow(icon = icon, feature = label)
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

        // Subscribe & legal
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val canSubscribe = selectedPlan != null && !uiState.isLoading
            Button(
                onClick = { selectedPlan?.let { onSubscribe(it.id) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(
                        if (canSubscribe) Modifier.background(Brand.GoldGradient, RoundedCornerShape(12.dp))
                        else Modifier
                    )
                    .clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSubscribe) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                ),
                enabled = canSubscribe,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text(
                        "Subscribe Now",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (canSubscribe) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            TextButton(
                onClick = onRestore,
                enabled = !uiState.isRestoring,
            ) {
                if (uiState.isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text("Restore Purchase", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(Spacing.sm))

            // Play Store policy requires clickable ToS and Privacy links
            val linkColor = MaterialTheme.colorScheme.primary
            val annotated = buildAnnotatedString {
                pushStringAnnotation("URL", TOS_URL)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append("Terms of Service")
                }
                pop()
                append("  •  ")
                pushStringAnnotation("URL", PRIVACY_URL)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append("Privacy Policy")
                }
                pop()
            }
            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                ),
                onClick = { offset ->
                    annotated.getStringAnnotations("URL", offset, offset)
                        .firstOrNull()
                        ?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item))) }
                },
            )
        }
    }
}

@Composable
private fun PremiumFeatureRow(icon: ImageVector, feature: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Brand.Blue,
            modifier = Modifier.size(22.dp),
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
                Icon(Icons.Default.CheckCircle, null, tint = Brand.Blue)
            }
        }
    }
}

private val premiumFeatures = listOf(
    Icons.Outlined.Block to "No ads, forever",
    Icons.Outlined.Lock to "Secure Vault – unlimited private photos",
    Icons.Outlined.CleaningServices to "Smart Cleaner – free up space",
    Icons.Outlined.AutoAwesome to "AI Editor – enhance, remove background",
    Icons.Outlined.Map to "Map View – see photos by location",
    Icons.Outlined.VideoLibrary to "Video Trimmer",
    Icons.Outlined.GridView to "Collage Maker",
    Icons.Outlined.Storage to "Storage Manager",
    Icons.Outlined.Backup to "Backup & Sync",
    Icons.Outlined.Star to "Priority customer support",
)
