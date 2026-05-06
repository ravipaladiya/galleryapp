package com.grow.gallery.feature.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
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
        gradientStart = Brand.Blue,
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

    // Collect the navigate event emitted by the ViewModel after DataStore write completes.
    // This ensures the flag is persisted before navigation removes this composable.
    LaunchedEffect(Unit) {
        viewModel.navigateToHome.collect { onFinish() }
    }

    // Back on page > 0 → go to previous page instead of exiting the app
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(
                page = pagerState.currentPage - 1,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val isCurrentPage = page == pagerState.currentPage
            OnboardingPageContent(
                page = pages[page],
                modifier = if (!isCurrentPage) {
                    // Hide off-screen pages from the accessibility tree
                    Modifier.semantics { invisibleToUser() }
                } else Modifier,
            )
        }

        // Bottom controls — clear of the gesture nav bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xxxl),
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
                        onClick = { viewModel.completeOnboarding() },
                    ) {
                        Text(
                            "Skip",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Button(
                        onClick = {
                            if (!pagerState.isScrollInProgress) {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        page = pagerState.currentPage + 1,
                                        animationSpec = tween(400, easing = FastOutSlowInEasing),
                                    )
                                }
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
                    onClick = { viewModel.completeOnboarding() },
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
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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
