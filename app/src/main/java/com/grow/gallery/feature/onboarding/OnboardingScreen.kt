package com.grow.gallery.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grow.gallery.core.designsystem.Brand
import com.grow.gallery.core.designsystem.ShapeButtonRound
import com.grow.gallery.core.designsystem.Spacing
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val features: List<String>,
    val gradientStart: Color,
    val gradientEnd: Color,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.PhotoLibrary,
        title = "Smart Gallery",
        description = "Your photos, beautifully organized.",
        features = listOf("Auto date grouping", "Fast photo search", "Videos & photos in one place"),
        gradientStart = Color(0xFF0052CC),
        gradientEnd = Color(0xFF0099FF),
    ),
    OnboardingPage(
        icon = Icons.Default.AutoAwesome,
        title = "AI-Powered Tools",
        description = "Edit and enhance with one tap.",
        features = listOf("AI photo editor", "Crop, adjust & filter", "Batch operations"),
        gradientStart = Color(0xFF5B21B6),
        gradientEnd = Color(0xFF8B5CF6),
    ),
    OnboardingPage(
        icon = Icons.Default.Lock,
        title = "Secure & Private",
        description = "Your memories stay protected.",
        features = listOf("Encrypted vault", "PIN & biometric lock", "Secure trash bin"),
        gradientStart = Color(0xFF065F46),
        gradientEnd = Color(0xFF10B981),
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            OnboardingPageContent(page = pages[page])
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = Spacing.xxxl),
            ) {
                pages.indices.forEach { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == pagerState.currentPage) 28.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "indicator",
                    )
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            ),
                    )
                }
            }

            if (pagerState.currentPage < pages.size - 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            scope.launch { viewModel.completeOnboarding() }
                            onFinish()
                        },
                    ) {
                        Text(
                            "Skip",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        shape = ShapeButtonRound,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = pages[pagerState.currentPage].gradientStart,
                        ),
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp),
                    ) {
                        Text("Next", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        scope.launch { viewModel.completeOnboarding() }
                        onFinish()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = ShapeButtonRound,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = pages.last().gradientStart,
                    ),
                ) {
                    Text(
                        "Get Started",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(page.gradientStart, page.gradientEnd))
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = Spacing.xxxl)
                .padding(bottom = 200.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Icon in a frosted circle
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }

            Spacer(Modifier.height(Spacing.xxxl))

            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
            )

            Spacer(Modifier.height(Spacing.xxxl))

            // Feature chips
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                page.features.forEach { feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
