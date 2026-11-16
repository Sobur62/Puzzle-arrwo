package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ArrowPuzzleLogo
import com.example.ui.components.TiledArrowBackground
import com.example.ui.components.glassPanel
import com.example.ui.theme.GlassAccentBlue
import com.example.ui.theme.GlassAccentCyan
import com.example.ui.theme.GlassAccentMint
import com.example.ui.theme.GlassInkPrimary
import com.example.ui.theme.GlassRose
import com.example.ui.theme.PlayGreenBottom
import com.example.ui.theme.PlayGreenTop
import kotlinx.coroutines.delay

/** White Glassmorphism splash loading screen; transitions straight into the game with no login screen. */
@Composable
fun SplashLoadingScreen(
  onFinished: () -> Unit,
) {
  val progress = remember { Animatable(0.12f) }

  LaunchedEffect(Unit) {
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
    )
    delay(120L)
    onFinished()
  }

  Box(modifier = Modifier.fillMaxSize()) {
    TiledArrowBackground(darkTheme = false)

    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.weight(0.28f))
      ArrowPuzzleLogo()
      Spacer(modifier = Modifier.weight(0.32f))

      Text(
        text = "LOADING GRID...",
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp, letterSpacing = 1.2.sp),
        color = GlassAccentBlue,
      )
      Spacer(modifier = Modifier.height(10.dp))

      Box(
        modifier = Modifier
          .widthIn(max = 310.dp)
          .fillMaxWidth(0.76f)
          .height(22.dp)
          .glassPanel(darkTheme = false, cornerRadius = 11.dp, elevation = 6.dp)
          .padding(3.5.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(progress.value.coerceIn(0.05f, 1f))
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(PlayGreenTop, PlayGreenBottom)))
        )
      }

      Spacer(modifier = Modifier.weight(0.25f))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp)
          .glassPanel(darkTheme = false, cornerRadius = 18.dp, elevation = 8.dp)
          .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MazeMiniIcon(modifier = Modifier.size(34.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(
          text = "Pixel Arrow",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
          color = GlassInkPrimary,
        )
      }
    }
  }
}

@Composable
private fun MazeMiniIcon(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.10f
    drawLine(GlassAccentMint, Offset(w * 0.15f, h * 0.25f), Offset(w * 0.55f, h * 0.25f), stroke, StrokeCap.Square)
    drawLine(GlassAccentMint, Offset(w * 0.55f, h * 0.25f), Offset(w * 0.55f, h * 0.65f), stroke, StrokeCap.Square)
    drawLine(GlassAccentCyan, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.20f, h * 0.85f), stroke, StrokeCap.Square)
    drawLine(GlassAccentCyan, Offset(w * 0.20f, h * 0.85f), Offset(w * 0.80f, h * 0.85f), stroke, StrokeCap.Square)
    drawLine(GlassRose, Offset(w * 0.80f, h * 0.18f), Offset(w * 0.80f, h * 0.62f), stroke, StrokeCap.Square)
    drawLine(Color(0xFFF59E0B), Offset(w * 0.40f, h * 0.45f), Offset(w * 0.78f, h * 0.45f), stroke, StrokeCap.Square)
  }
}
