package com.grow.gallery.feature.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.grow.gallery.core.designsystem.Brand
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onNavigate: () -> Unit) {
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.85f) }
    // Survive rotation — if we've already completed, navigate immediately
    val completed = rememberSaveable { mutableStateOf(false) }

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setStatusBarColor(Brand.Blue, darkIcons = false)
        systemUiController.setNavigationBarColor(Brand.Blue, darkIcons = false)
    }

    LaunchedEffect(Unit) {
        if (completed.value) {
            onNavigate()
            return@LaunchedEffect
        }
        // Fade-in and scale-up run in parallel
        launch { alphaAnim.animateTo(1f, animationSpec = tween(450, easing = LinearEasing)) }
        scaleAnim.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        completed.value = true
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
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
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
