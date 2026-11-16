package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors =
  lightColorScheme(
    primary = GlassAccentBlue,
    onPrimary = Color.White,
    secondary = GlassAccentMint,
    onSecondary = Color.White,
    tertiary = ArcadeAmber,
    background = GlassCanvasMid,
    onBackground = GlassInkPrimary,
    surface = GlassWhiteHigh,
    onSurface = GlassInkPrimary,
    surfaceVariant = GlassWhiteLow,
    onSurfaceVariant = GlassInkSecondary,
    outline = GlassRimIce,
    error = GlassRose,
    onError = Color.White,
  )

private val DarkColors =
  darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF041824),
    secondary = NeonLime,
    onSecondary = Color(0xFF041D07),
    tertiary = ArcadeGold,
    background = PixelObsidianBg,
    onBackground = TextPrimaryDark,
    surface = PixelPanelDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = PixelCardDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = NeonCyan.copy(alpha = 0.45f),
    error = NeonMagenta,
    onError = Color.White,
  )

@Composable
fun ArrowPuzzleTheme(
  darkTheme: Boolean = false,
  content: @Composable () -> Unit,
) {
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val activity = view.context as? Activity
      if (activity != null) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = ArrowPuzzleTypography,
    content = content,
  )
}
