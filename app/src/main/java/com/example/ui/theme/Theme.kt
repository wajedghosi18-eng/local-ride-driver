package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
  primary = EmeraldPrimary,
  onPrimary = Color.White,
  primaryContainer = EmeraldContainer,
  onPrimaryContainer = OnEmeraldContainer,
  secondary = AmberCaptain,
  onSecondary = Color(0xFF1F1600),
  secondaryContainer = AmberContainer,
  onSecondaryContainer = OnAmberContainer,
  background = LightBackground,
  onBackground = LightOnSurface,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightOnSurfaceVariant,
  outline = LightOutline,
  error = OfflineRed,
  onError = Color.White,
  errorContainer = OfflineRedSurface,
  onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
  primary = DarkEmeraldPrimary,
  onPrimary = Color(0xFF042214),
  primaryContainer = DarkEmeraldContainer,
  onPrimaryContainer = EmeraldContainer,
  secondary = AmberCaptain,
  onSecondary = Color(0xFF281800),
  secondaryContainer = Color(0xFF4B3000),
  onSecondaryContainer = AmberContainer,
  background = DarkBackground,
  onBackground = DarkOnSurface,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  outline = DarkOutline,
  error = Color(0xFFF87171),
  onError = Color(0xFF450A0A),
  errorContainer = Color(0xFF7F1D1D),
  onErrorContainer = OfflineRedSurface,
)

val AppShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LocalRideDriverTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColors
    else -> LightColors
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
