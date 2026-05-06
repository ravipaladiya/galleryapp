package com.grow.gallery.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val emoji: String,
    val title: String,
    val description: String,
    val backgroundColor: Color,
)

private val pages = listOf(
    OnboardingPage(
        emoji = "🖼️",
        title = "Smart Gallery",
        description = "AI-powered gallery that understands your photos and memories.",
        backgroundColor = Color(0xFF0066FF),
    ),
    OnboardingPage(
        emoji = "📁",
        title = "Organize Effortlessly",
        description = "Auto-albums, face groups, and location-based collections.",
        backgroundColor = Color(0xFF7C3AED),
    ),
    OnboardingPage(
        emoji = "🔒",
        title = "Secure & Private",
        description = "Lock your private photos in an encrypted vault with PIN or biometric.",
        backgroundColor = Color(0xFF0F766E),
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

        // Controls overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = Spacing.xl, end = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = Spacing.xxxl),
            ) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (i == pagerState.currentPage) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.4f)
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
                            text = "Skip",
                            color = Color.White.copy(alpha = 0.8f),
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
                            contentColor = Brand.Blue,
                        ),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(
                            text = "Next",
                            fontWeight = FontWeight.SemiBold,
                        )
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
                        contentColor = Brand.Blue,
                    ),
                ) {
                    Text(
                        text = "Get Started",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        // Skip button at top
        if (pagerState.currentPage < pages.size - 1) {
            TextButton(
                onClick = {
                    scope.launch { viewModel.completeOnboarding() }
                    onFinish()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = Spacing.lg),
            ) {
                Text(text = "Skip", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(page.backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 160.dp),
        ) {
            Text(
                text = page.emoji,
                fontSize = 80.sp,
            )
            Spacer(Modifier.height(Spacing.xxxl))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
            )
        }
    }
}
