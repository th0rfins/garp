package com.garp.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palet gelap Haulix-style, konsisten dengan frontend
val Bg = Color(0xFF0B0E14)
val Surface = Color(0xFF12161F)
val CardBg = Color(0xFF161B26)
val Border = Color(0xFF232A38)
val TextPrimary = Color(0xFFE7EAF0)
val TextMuted = Color(0xFF8B93A7)
val AccentGreen = Color(0xFF2ED573)
val AccentYellow = Color(0xFFFFB020)
val AccentRed = Color(0xFFFF5A5A)
val AccentBlue = Color(0xFF4C8DFF)

private val DarkColors = darkColorScheme(
    primary = AccentGreen,
    secondary = AccentBlue,
    background = Bg,
    surface = Surface,
    surfaceVariant = CardBg,
    error = AccentRed,
    onPrimary = Color(0xFF06130B),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    outline = Border,
)

private val GarpTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun GarpTheme(content: @Composable () -> Unit) {
    // Selalu dark (Haulix style)
    MaterialTheme(
        colorScheme = DarkColors,
        typography = GarpTypography,
        content = content
    )
}
