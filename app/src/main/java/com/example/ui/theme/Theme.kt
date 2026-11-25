package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TabTubeDarkScheme = darkColorScheme(
    primary = CrimsonPlay,
    onPrimary = Color.White,
    primaryContainer = CrimsonPlayContainer,
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = PhosphorCyan,
    onSecondary = Color(0xFF002026),
    secondaryContainer = PhosphorCyanContainer,
    onSecondaryContainer = Color(0xFFB2EBF2),
    tertiary = TelemetryAmber,
    onTertiary = Color(0xFF261900),
    tertiaryContainer = TelemetryAmberContainer,
    onTertiaryContainer = Color(0xFFFFE082),
    background = CinemaCanvas,
    onBackground = TextPrimary,
    surface = CinemaSurface,
    onSurface = TextPrimary,
    surfaceVariant = CinemaSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = CinemaSurfaceElevated,
    surfaceContainerHighest = CinemaSurfaceHighlight,
    outline = OutlineSubtle,
    outlineVariant = Color(0xFF1F2734)
)

private val TabTubeLightScheme = lightColorScheme(
    primary = CrimsonPlay,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = CrimsonPlayDark,
    secondary = Color(0xFF007C91),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF4FA),
    onSecondaryContainer = Color(0xFF002026),
    tertiary = Color(0xFF9A6700),
    onTertiary = Color.White,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFD0D8E4)
)

val TabTubeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TabTubeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) TabTubeDarkScheme else TabTubeLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TabTubeShapes,
        content = content
    )
}
