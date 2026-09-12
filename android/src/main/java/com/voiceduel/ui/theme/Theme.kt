package com.voiceduel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val DuelPurple = Color(0xFF6C4BF6)
val DuelPurpleDark = Color(0xFF3B2A8C)
val DuelAmber = Color(0xFFFFB020)
val DuelTeal = Color(0xFF19C3B2)
val DuelRed = Color(0xFFE5484D)
val DuelInk = Color(0xFF14121F)
val DuelSurface = Color(0xFF1E1B2E)

private val DarkColors = darkColorScheme(
    primary = DuelPurple,
    onPrimary = Color.White,
    primaryContainer = DuelPurpleDark,
    onPrimaryContainer = Color.White,
    secondary = DuelAmber,
    onSecondary = DuelInk,
    tertiary = DuelTeal,
    onTertiary = DuelInk,
    background = DuelInk,
    onBackground = Color(0xFFEDEAF7),
    surface = DuelSurface,
    onSurface = Color(0xFFEDEAF7),
    surfaceVariant = Color(0xFF2A2640),
    onSurfaceVariant = Color(0xFFBFB8D9),
    error = DuelRed,
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = DuelPurple,
    onPrimary = Color.White,
    secondary = DuelAmber,
    tertiary = DuelTeal,
    error = DuelRed,
)

/**
 * The app's theme.
 *
 * Every string in the app is Arabic, so the layout direction is pinned to RTL
 * rather than following the device locale.
 */
@Composable
fun VoiceDuelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DuelTypography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
