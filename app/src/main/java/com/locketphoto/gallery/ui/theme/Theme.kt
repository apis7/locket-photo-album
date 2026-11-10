package com.locketphoto.gallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFE2B65C)

private val Dark = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF231A06),
    background = Color(0xFF0F0E12),
    onBackground = Color(0xFFEDE9F0),
    surface = Color(0xFF0F0E12),
    onSurface = Color(0xFFEDE9F0),
    surfaceVariant = Color(0xFF1C1A21),
    onSurfaceVariant = Color(0xFFB6B0BE),
    surfaceContainer = Color(0xFF19171E),
    surfaceContainerHigh = Color(0xFF232029),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A6418),
    onPrimary = Color.White,
    background = Color(0xFFFBF9F6),
    onBackground = Color(0xFF1B1A1E),
    surface = Color(0xFFFBF9F6),
    onSurface = Color(0xFF1B1A1E),
    surfaceVariant = Color(0xFFEDE8E1),
    onSurfaceVariant = Color(0xFF5D5866),
    surfaceContainer = Color(0xFFF3EFEA),
    surfaceContainerHigh = Color(0xFFEAE5DE),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.sp),
)

@Composable
fun LocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        content = content,
    )
}
