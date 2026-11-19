package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CamScanAccent,
    onPrimary = Color.Black,
    primaryContainer = CamScanPrimaryDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = CamScanSecondary,
    onSecondary = Color.Black,
    tertiary = CamScanTertiary,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF24322E)
)

private val LightColorScheme = lightColorScheme(
    primary = CamScanPrimary,
    onPrimary = Color.White,
    primaryContainer = SurfaceContainerHighLight,
    onPrimaryContainer = CamScanPrimaryDark,
    secondary = CamScanSecondary,
    onSecondary = Color.White,
    tertiary = CamScanTertiary,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE2ECE8)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve brand identity
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
