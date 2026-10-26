package com.garp.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palet Vercel/Linear flat dark — selaras frontend (styles.css)
val Bg = Color(0xFF0A0A0B)
val Surface = Color(0xFF101012)
val CardBg = Color(0xFF141417)
val Border = Color(0xFF232327)
val BorderSoft = Color(0xFF1B1B1F)
val TextPrimary = Color(0xFFEDEDEF)
val TextMuted = Color(0xFF8F8F94)
val TextFaint = Color(0xFF5C5C62)
val Accent = Color(0xFF5E6AD2)      // indigo Linear
val AccentGreen = Color(0xFF3FB68B) // status selesai
val AccentYellow = Color(0xFFD29922)
val AccentRed = Color(0xFFE5484D)
val AccentBlue = Color(0xFF4C8DFF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    secondary = AccentBlue,
    background = Bg,
    surface = Surface,
    surfaceVariant = CardBg,
    error = AccentRed,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    outline = Border,
)

private val GarpTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.5.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.8.sp),
)

@Composable
fun GarpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = GarpTypography, content = content)
}
