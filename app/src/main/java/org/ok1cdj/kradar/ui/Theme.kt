package org.ok1cdj.kradar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val black = Color(0xFF000000)
private val white = Color(0xFFFFFFFF)

// Strictly black/white for e-ink; MMD components read MaterialTheme.colorScheme,
// so every role (including the *Container roles used by ButtonMMD) is coerced to
// black/white — otherwise the Material purple baseline leaks through.
private val EinkColors = lightColorScheme(
    primary = black,
    onPrimary = white,
    primaryContainer = white,
    onPrimaryContainer = black,
    secondary = black,
    onSecondary = white,
    secondaryContainer = white,
    onSecondaryContainer = black,
    tertiary = black,
    onTertiary = white,
    tertiaryContainer = white,
    onTertiaryContainer = black,
    background = white,
    onBackground = black,
    surface = white,
    onSurface = black,
    surfaceVariant = white,
    onSurfaceVariant = black,
    outline = black,
    outlineVariant = black,
)

@Composable
fun KRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EinkColors, content = content)
}
