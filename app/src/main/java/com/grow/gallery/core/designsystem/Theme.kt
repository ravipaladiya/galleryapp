package com.grow.gallery.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LightColorScheme = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Brand.White,
    primaryContainer = Brand.BlueLight,
    onPrimaryContainer = Brand.BlueDark,
    secondary = Brand.Gray600,
    onSecondary = Brand.White,
    secondaryContainer = Brand.Gray100,
    onSecondaryContainer = Brand.Gray900,
    tertiary = Brand.GoldStart,
    surface = Brand.White,
    onSurface = Brand.Gray900,
    surfaceVariant = Brand.Gray100,
    onSurfaceVariant = Brand.Gray600,
    outline = Brand.Gray300,
    outlineVariant = Brand.Gray200,
    background = Brand.Gray50,
    onBackground = Brand.Gray900,
    error = Brand.Error,
    onError = Brand.White,
    scrim = Brand.ScrimLight,
)

val DarkColorScheme = darkColorScheme(
    primary = Brand.BlueDark300,
    onPrimary = Brand.DarkBackground,
    primaryContainer = Brand.Blue800,
    onPrimaryContainer = Brand.BlueDark200,
    secondary = Brand.Gray400,
    onSecondary = Brand.DarkBackground,
    secondaryContainer = Brand.Gray800,
    onSecondaryContainer = Brand.Gray200,
    tertiary = Brand.GoldStart,
    surface = Brand.DarkSurface,
    onSurface = Brand.Gray100,
    surfaceVariant = Brand.DarkSurfaceVariant,
    onSurfaceVariant = Brand.Gray400,
    outline = Brand.Gray700,
    outlineVariant = Brand.Gray800,
    background = Brand.DarkBackground,
    onBackground = Brand.Gray100,
    error = Brand.ErrorLight,
    onError = Brand.DarkBackground,
    scrim = Brand.ScrimDark,
)

enum class AppTheme { SYSTEM, LIGHT, DARK }

val LocalAppTheme = staticCompositionLocalOf { AppTheme.SYSTEM }

@Composable
fun GalleryTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> systemDark
    }

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GalleryTypography,
            shapes = GalleryShapes,
            content = content,
        )
    }
}
