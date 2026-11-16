package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ArcadeAmber
import com.example.ui.theme.ArcadeGold
import com.example.ui.theme.DarkBg
import com.example.ui.theme.LavenderBgLight
import com.example.ui.theme.GlassCanvasBottom
import com.example.ui.theme.GlassCanvasMid
import com.example.ui.theme.GlassCanvasTop
import com.example.ui.theme.GlassInkPrimary
import com.example.ui.theme.GlassOrbCyan
import com.example.ui.theme.GlassOrbMint
import com.example.ui.theme.GlassOrbPeriwinkle
import com.example.ui.theme.GlassOrbViolet
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassRimIce
import com.example.ui.theme.GlassWhiteHigh
import com.example.ui.theme.GlassWhiteMid
import com.example.ui.theme.LogoBlueBottom
import com.example.ui.theme.LogoBlueTop
import com.example.ui.theme.LogoGoldBottom
import com.example.ui.theme.LogoGoldTop
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PlayGreenBottom
import com.example.ui.theme.PlayGreenLip
import com.example.ui.theme.PlayGreenTop
import com.example.ui.theme.SilkscreenFontFamily

// White Glassmorphism pastel canvas and Dark Neon pixel-grid background
@Composable
fun TiledArrowBackground(
  darkTheme: Boolean,
  modifier: Modifier = Modifier,
) {
  val bgBrush =
    if (darkTheme) {
      Brush.verticalGradient(listOf(DarkBg, Color(0xFF0E1529), DarkBg))
    } else {
      Brush.linearGradient(listOf(GlassCanvasTop, GlassCanvasMid, GlassCanvasBottom))
    }
  val gridColor = if (darkTheme) Color(0xFF141D35) else Color(0x4DFFFFFF)
  val gridSubColor = if (darkTheme) Color.Transparent else Color(0x167E97CC)
  val arrowTint = if (darkTheme) Color(0xFF1C294A) else Color(0x1D6886C4)

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .background(bgBrush)
  ) {
    // Soft ambient color orbs behind white glass to enhance frosted depth
    if (!darkTheme) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(GlassOrbCyan, Color.Transparent),
          center = Offset(size.width * 0.14f, size.height * 0.16f),
          radius = size.minDimension * 0.62f,
        ),
        center = Offset(size.width * 0.14f, size.height * 0.16f),
        radius = size.minDimension * 0.62f,
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(GlassOrbViolet, Color.Transparent),
          center = Offset(size.width * 0.88f, size.height * 0.28f),
          radius = size.minDimension * 0.56f,
        ),
        center = Offset(size.width * 0.88f, size.height * 0.28f),
        radius = size.minDimension * 0.56f,
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(GlassOrbPeriwinkle, Color.Transparent),
          center = Offset(size.width * 0.24f, size.height * 0.78f),
          radius = size.minDimension * 0.64f,
        ),
        center = Offset(size.width * 0.24f, size.height * 0.78f),
        radius = size.minDimension * 0.64f,
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(GlassOrbMint, Color.Transparent),
          center = Offset(size.width * 0.84f, size.height * 0.84f),
          radius = size.minDimension * 0.50f,
        ),
        center = Offset(size.width * 0.84f, size.height * 0.84f),
        radius = size.minDimension * 0.50f,
      )
    }

    // Pixel-art background grid lines
    val gridStep = 36.dp.toPx()
    var gx = 0f
    while (gx <= size.width) {
      drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1.dp.toPx())
      if (!darkTheme) {
        drawLine(gridSubColor, Offset(gx + 1.dp.toPx(), 0f), Offset(gx + 1.dp.toPx(), size.height), strokeWidth = 0.75.dp.toPx())
      }
      gx += gridStep
    }
    var gy = 0f
    while (gy <= size.height) {
      drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
      if (!darkTheme) {
        drawLine(gridSubColor, Offset(0f, gy + 1.dp.toPx()), Offset(size.width, gy + 1.dp.toPx()), strokeWidth = 0.75.dp.toPx())
      }
      gy += gridStep
    }

    val stepX = 112.dp.toPx()
    val stepY = 112.dp.toPx()
    val arrowLen = 40.dp.toPx()
    val cols = (size.width / stepX).toInt() + 2
    val rows = (size.height / stepY).toInt() + 2

    for (r in -1..rows) {
      for (c in -1..cols) {
        val cx = c * stepX + (if (r % 2 == 0) 0f else stepX * 0.5f)
        val cy = r * stepY
        val angle = if ((r + c) % 2 == 0) -45f else -135f
        rotate(degrees = angle, pivot = Offset(cx, cy)) {
          val shaftPath =
            Path().apply {
              moveTo(cx - arrowLen * 0.45f, cy)
              lineTo(cx + arrowLen * 0.18f, cy)
            }
          drawPath(
            path = shaftPath,
            color = arrowTint,
            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Butt),
          )
          val headPath =
            Path().apply {
              moveTo(cx + arrowLen * 0.55f, cy)
              lineTo(cx + arrowLen * 0.08f, cy - 15.dp.toPx())
              lineTo(cx + arrowLen * 0.08f, cy + 15.dp.toPx())
              close()
            }
          drawPath(path = headPath, color = arrowTint)
        }
      }
    }
  }
}

// Outlined pixel-arcade text
@Composable
fun OutlinedGameText(
  text: String,
  fontSize: TextUnit,
  fillColor: Color = Color.White,
  outlineColor: Color = Color(0xFF0A1229),
  outlineWidth: Float = 6f,
  modifier: Modifier = Modifier,
) {
  val baseStyle =
    TextStyle(
      fontFamily = SilkscreenFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = fontSize,
    )
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Text(
      text = text,
      style =
        baseStyle.copy(
          color = outlineColor,
          drawStyle = Stroke(width = outlineWidth, join = StrokeJoin.Miter),
        ),
      maxLines = 1,
    )
    Text(
      text = text,
      style = baseStyle.copy(color = fillColor),
      maxLines = 1,
    )
  }
}

// "PIXEL ARROW" pixel-art neon logo
@Composable
fun ArrowPuzzleLogo(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(contentAlignment = Alignment.Center) {
      // Right-pointing neon cyan pixel arrow banner
      Canvas(
        modifier = Modifier
          .size(width = 280.dp, height = 62.dp)
          .offset(x = 14.dp, y = 4.dp)
      ) {
        val y = size.height * 0.56f
        val tipX = size.width - 6.dp.toPx()
        val shaftEnd = tipX - 24.dp.toPx()
        val arrowPath =
          Path().apply {
            moveTo(20.dp.toPx(), y - 8.dp.toPx())
            lineTo(shaftEnd, y - 8.dp.toPx())
            lineTo(shaftEnd, y - 18.dp.toPx())
            lineTo(tipX, y)
            lineTo(shaftEnd, y + 18.dp.toPx())
            lineTo(shaftEnd, y + 8.dp.toPx())
            lineTo(20.dp.toPx(), y + 8.dp.toPx())
            close()
          }
        drawPath(
          path = arrowPath,
          color = Color(0xFF041428),
          style = Stroke(width = 10.dp.toPx(), join = StrokeJoin.Miter),
        )
        drawPath(
          path = arrowPath,
          brush = Brush.verticalGradient(listOf(LogoBlueTop, LogoBlueBottom)),
        )
      }

      val pixelStyle =
        TextStyle(
          fontFamily = SilkscreenFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 44.sp,
          letterSpacing = 2.sp,
        )
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = "PIXEL",
          style =
            pixelStyle.copy(
              color = Color(0x5500F0FF),
              drawStyle = Stroke(width = 20f, join = StrokeJoin.Miter),
            ),
          modifier = Modifier.offset(y = 3.dp),
        )
        Text(
          text = "PIXEL",
          style =
            pixelStyle.copy(
              color = Color(0xFF051326),
              drawStyle = Stroke(width = 14f, join = StrokeJoin.Miter),
            ),
        )
        Text(
          text = "PIXEL",
          style =
            pixelStyle.copy(
              brush = Brush.verticalGradient(listOf(NeonCyan, LogoBlueBottom)),
            ),
        )
      }
    }

    Box(
      modifier = Modifier.offset(y = (-6).dp),
      contentAlignment = Alignment.Center,
    ) {
      // Top-left pointing golden pixel zigzag arrow
      Canvas(
        modifier = Modifier
          .size(width = 290.dp, height = 62.dp)
          .offset(x = (-16).dp, y = 2.dp)
      ) {
        val zigPath =
          Path().apply {
            moveTo(64.dp.toPx(), size.height * 0.44f)
            lineTo(46.dp.toPx(), size.height * 0.70f)
            lineTo(34.dp.toPx(), size.height * 0.42f)
            lineTo(16.dp.toPx(), size.height * 0.22f)
          }
        drawPath(
          path = zigPath,
          color = Color(0xFF1F1200),
          style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
        )
        drawPath(
          path = zigPath,
          brush = Brush.verticalGradient(listOf(LogoGoldTop, LogoGoldBottom)),
          style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter),
        )
        val head =
          Path().apply {
            moveTo(2.dp.toPx(), size.height * 0.12f)
            lineTo(30.dp.toPx(), size.height * 0.14f)
            lineTo(14.dp.toPx(), size.height * 0.42f)
            close()
          }
        drawPath(
          path = head,
          color = Color(0xFF1F1200),
          style = Stroke(width = 7.dp.toPx(), join = StrokeJoin.Miter),
        )
        drawPath(
          path = head,
          brush = Brush.verticalGradient(listOf(ArcadeGold, ArcadeAmber)),
        )
      }

      val arrowStyle =
        TextStyle(
          fontFamily = SilkscreenFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 42.sp,
          letterSpacing = 1.5.sp,
        )
      Box(
        modifier = Modifier.padding(start = 28.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "ARROW",
          style =
            arrowStyle.copy(
              color = Color(0x44FFD700),
              drawStyle = Stroke(width = 20f, join = StrokeJoin.Miter),
            ),
          modifier = Modifier.offset(y = 3.dp),
        )
        Text(
          text = "ARROW",
          style =
            arrowStyle.copy(
              color = Color(0xFF281600),
              drawStyle = Stroke(width = 14f, join = StrokeJoin.Miter),
            ),
        )
        Text(
          text = "ARROW",
          style =
            arrowStyle.copy(
              brush = Brush.verticalGradient(listOf(LogoGoldTop, LogoGoldBottom)),
            ),
        )
      }
    }
  }
}

// 3D pixel-arcade button (supports green Play as well as rewarded/gold/cyan accents)
@Composable
fun GlossyGreenButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  height: Dp = 62.dp,
  fontSize: TextUnit = 20.sp,
  topColor: Color = PlayGreenTop,
  bottomColor: Color = PlayGreenBottom,
  lipColor: Color = PlayGreenLip,
  outlineColor: Color = Color(0xFF0A3D15),
) {
  val shape = RoundedCornerShape(10.dp)
  val innerShape = RoundedCornerShape(8.dp)
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .height(height)
      .shadow(elevation = 8.dp, shape = shape)
      .clip(shape)
      .background(lipColor)
      .padding(bottom = 5.dp)
      .clip(innerShape)
      .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
      .border(2.dp, Color(0x99FFFFFF), innerShape)
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(color = Color.White),
        onClick = onClick,
      ),
    contentAlignment = Alignment.Center,
  ) {
    OutlinedGameText(
      text = text,
      fontSize = fontSize,
      fillColor = Color.White,
      outlineColor = outlineColor,
      outlineWidth = 5f,
    )
  }
}

// White glass / Dark neon status and level pill badge
@Composable
fun PillBadge(
  text: String,
  modifier: Modifier = Modifier,
  darkTheme: Boolean = false,
  backgroundColor: Color? = null,
  borderColor: Color = NeonCyan.copy(alpha = 0.45f),
) {
  val baseModifier =
    if (backgroundColor != null) {
      modifier
        .clip(RoundedCornerShape(12.dp))
        .background(backgroundColor)
        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
    } else {
      modifier.glassPanel(
        darkTheme = darkTheme,
        cornerRadius = 12.dp,
        elevation = 6.dp,
        highOpacity = true,
        accentBorder = if (darkTheme) borderColor else null,
      )
    }

  Box(
    modifier = baseModifier.padding(horizontal = 14.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
      color = if (darkTheme || backgroundColor != null) Color.White else GlassInkPrimary,
    )
  }
}
