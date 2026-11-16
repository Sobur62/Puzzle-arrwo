package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassRimIce
import com.example.ui.theme.GlassShadowAmbient
import com.example.ui.theme.GlassShadowSpot
import com.example.ui.theme.GlassWhiteHigh
import com.example.ui.theme.GlassWhiteLow
import com.example.ui.theme.GlassWhiteMid
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PixelCardDark
import com.example.ui.theme.PixelPanelDark

/**
 * White Glassmorphism (frosted milky glass) surface modifier:
 * - Milky-white translucent gradient fill
 * - Diagonal specular white rim border
 * - Subtle top-edge glass sheen highlight
 * - Cool ambient drop shadow
 */
fun Modifier.glassPanel(
  darkTheme: Boolean,
  cornerRadius: Dp = 18.dp,
  elevation: Dp = 10.dp,
  highOpacity: Boolean = false,
  accentBorder: Color? = null,
): Modifier {
  val shape: Shape = RoundedCornerShape(cornerRadius)
  val fillBrush =
    if (darkTheme) {
      Brush.linearGradient(
        colors =
          listOf(
            PixelCardDark.copy(alpha = if (highOpacity) 0.94f else 0.84f),
            PixelPanelDark.copy(alpha = if (highOpacity) 0.92f else 0.82f),
          )
      )
    } else {
      Brush.linearGradient(
        colors =
          listOf(
            GlassWhiteHigh.copy(alpha = if (highOpacity) 0.95f else 0.88f),
            GlassWhiteMid.copy(alpha = if (highOpacity) 0.88f else 0.74f),
            GlassWhiteLow.copy(alpha = if (highOpacity) 0.86f else 0.72f),
          )
      )
    }

  val borderBrush =
    if (accentBorder != null) {
      Brush.linearGradient(
        colors =
          listOf(
            if (darkTheme) accentBorder else Color.White.copy(alpha = 0.96f),
            accentBorder.copy(alpha = if (darkTheme) 0.65f else 0.75f),
          )
      )
    } else if (darkTheme) {
      Brush.linearGradient(
        colors =
          listOf(
            NeonCyan.copy(alpha = 0.55f),
            NeonCyan.copy(alpha = 0.20f),
          )
      )
    } else {
      Brush.linearGradient(
        colors =
          listOf(
            GlassRimHighlight,
            Color.White.copy(alpha = 0.68f),
            GlassRimIce,
          )
      )
    }

  return this
    .shadow(
      elevation = elevation,
      shape = shape,
      ambientColor = if (darkTheme) Color.Black else GlassShadowAmbient,
      spotColor = if (darkTheme) Color.Black else GlassShadowSpot,
    )
    .clip(shape)
    .background(fillBrush)
    .drawBehind {
      if (!darkTheme) {
        val sheenHeight = (size.height * 0.38f).coerceAtMost(28.dp.toPx())
        drawRoundRect(
          brush =
            Brush.verticalGradient(
              colors = listOf(Color.White.copy(alpha = 0.52f), Color.Transparent),
              startY = 0f,
              endY = sheenHeight,
            ),
          topLeft = Offset.Zero,
          size = Size(size.width, sheenHeight),
          cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        )
      }
    }
    .border(width = 1.5.dp, brush = borderBrush, shape = shape)
}

@Composable
fun GlassInsetCard(
  darkTheme: Boolean,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 14.dp,
  contentAlignment: Alignment = Alignment.TopStart,
  content: @Composable BoxScope.() -> Unit,
) {
  val shape = RoundedCornerShape(cornerRadius)
  val bg =
    if (darkTheme) {
      PixelCardDark.copy(alpha = 0.88f)
    } else {
      Color.White.copy(alpha = 0.86f)
    }
  val border =
    if (darkTheme) {
      NeonCyan.copy(alpha = 0.28f)
    } else {
      Color(0xFFD6E3FA)
    }
  Box(
    modifier =
      modifier
        .clip(shape)
        .background(bg)
        .border(1.dp, border, shape),
    contentAlignment = contentAlignment,
    content = content,
  )
}
