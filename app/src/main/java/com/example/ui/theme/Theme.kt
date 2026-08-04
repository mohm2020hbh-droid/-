package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFE53935),
    secondary = Color(0xFFFF3B30),
    tertiary = Color(0xFF8B0000),
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF161616),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFFE53935),
    secondary = Color(0xFFFF3B30),
    tertiary = Color(0xFF8B0000),
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF161616),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
