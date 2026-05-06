package com.grow.gallery.feature.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grow.gallery.core.designsystem.Brand
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigate: () -> Unit) {
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(600))
        scaleAnim.animateTo(1f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f))
        delay(800)
        onNavigate()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand.Blue),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value),
        ) {
            // Camera icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "📷",
                    fontSize = 40.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Gallery",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Smart Photo Manager",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}
