package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OldTubeLightScheme = lightColorScheme(
    primary = ClassicTubeRed,
    onPrimary = Color.White,
    primaryContainer = ClassicTubeRedSoft,
    onPrimaryContainer = ClassicTubeRedDark,
    secondary = Color(0xFF167AC6), // Classic 2013 YouTube Link/Subscribe Blue
    onSecondary = Color.White,
    secondaryContainer = HoloCyanContainerLight,
    onSecondaryContainer = Color(0xFF0A3B5C),
    tertiary = TelemetryAmber,
    onTertiary = Color.White,
    tertiaryContainer = TelemetryAmberContainer,
    onTertiaryContainer = Color(0xFF4D3200),
    background = ClassicCanvasLight,
    onBackground = ClassicTextPrimaryLight,
    surface = ClassicSurfaceLight,
    onSurface = ClassicTextPrimaryLight,
    surfaceVariant = ClassicSurfaceAltLight,
    onSurfaceVariant = ClassicTextSecondaryLight,
    surfaceContainerHigh = ClassicSurfaceAltLight,
    surfaceContainerHighest = Color(0xFFE6E6E6),
    outline = ClassicBorderLight,
    outlineVariant = ClassicBorderLight
)

private val OldTubeDarkScheme = darkColorScheme(
    primary = ClassicTubeRedBright,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1517),
    onPrimaryContainer = Color(0xFFFFD6D6),
    secondary = HoloCyan,
    onSecondary = Color(0xFF002330),
    secondaryContainer = HoloCyanContainerDark,
    onSecondaryContainer = Color(0xFFC2EEFF),
    tertiary = TelemetryAmber,
    onTertiary = Color(0xFF261700),
    tertiaryContainer = Color(0xFF3E2B05),
    onTertiaryContainer = Color(0xFFFFE29A),
    background = ClassicCanvasDark,
    onBackground = ClassicTextPrimaryDark,
    surface = ClassicSurfaceDark,
    onSurface = ClassicTextPrimaryDark,
    surfaceVariant = ClassicSurfaceAltDark,
    onSurfaceVariant = ClassicTextSecondaryDark,
    surfaceContainerHigh = ClassicSurfaceAltDark,
    surfaceContainerHighest = Color(0xFF343434),
    outline = ClassicBorderDark,
    outlineVariant = ClassicBorderDark
)

// Crisp 2013-era flatter, sharper shapes (low overdraw, nostalgic geometry)
val OldTubeShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun OldTubeTheme(
    classicLightTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (classicLightTheme) OldTubeLightScheme else OldTubeDarkScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = OldTubeShapes,
        content = content
    )
}
