package com.grow.gallery.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Brand {
    // Primary brand blue - #0066FF
    val Blue = Color(0xFF0066FF)
    val BlueDark = Color(0xFF0052CC)
    val BlueLight = Color(0xFFE5EEFF)
    val BlueDark200 = Color(0xFF99BBFF)
    val BlueDark300 = Color(0xFF5599FF)
    val Blue800 = Color(0xFF003D99)

    // Neutrals
    val White = Color(0xFFFFFFFF)
    val Gray50 = Color(0xFFF8F9FA)
    val Gray100 = Color(0xFFF1F3F5)
    val Gray200 = Color(0xFFE9ECEF)
    val Gray300 = Color(0xFFDEE2E6)
    val Gray400 = Color(0xFFADB5BD)
    val Gray500 = Color(0xFF6C757D)
    val Gray600 = Color(0xFF495057)
    val Gray700 = Color(0xFF343A40)
    val Gray800 = Color(0xFF212529)
    val Gray900 = Color(0xFF0F0F0F)

    // Semantic
    val Error = Color(0xFFE53935)
    val ErrorLight = Color(0xFFFF6B6B)
    val Success = Color(0xFF43A047)
    val Warning = Color(0xFFFFA000)

    // Premium gold gradient
    val GoldStart = Color(0xFFFFD700)
    val GoldEnd = Color(0xFFFF8C00)
    val GoldGradient = Brush.horizontalGradient(listOf(GoldStart, GoldEnd))
    val GoldGradientDiag = Brush.linearGradient(listOf(GoldStart, GoldEnd))

    // Dark mode surfaces
    val DarkBackground = Color(0xFF0F0F0F)
    val DarkSurface = Color(0xFF1A1A1A)
    val DarkSurfaceVariant = Color(0xFF252525)

    // Scrim
    val ScrimLight = Color(0x80000000)
    val ScrimDark = Color(0xCC000000)

    // Viewer overlay
    val ViewerOverlay = Color(0xCC000000)
    val ViewerControlBg = Color(0x80000000)

    // Vault
    val VaultBlue = Color(0xFF1A237E)
    val VaultBlueGradient = Brush.verticalGradient(
        listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
    )
}
