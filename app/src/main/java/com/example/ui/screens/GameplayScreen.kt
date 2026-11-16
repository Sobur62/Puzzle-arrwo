package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.ArrowSkin
import com.example.game.model.GameMode
import com.example.game.viewmodel.BoardSessionState
import com.example.ui.components.OutlinedGameText
import com.example.ui.components.SkinRenderer
import com.example.ui.components.TiledArrowBackground
import com.example.ui.components.glassPanel
import com.example.ui.theme.ArcadeAmber
import com.example.ui.theme.ArcadeGold
import com.example.ui.theme.DarkBoard
import com.example.ui.theme.GlassAccentBlue
import com.example.ui.theme.GlassAccentCyan
import com.example.ui.theme.GlassGoldWarm
import com.example.ui.theme.GlassInkPrimary
import com.example.ui.theme.GlassInkSecondary
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassRimIce
import com.example.ui.theme.HeartEmpty
import com.example.ui.theme.HeartRed
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PeriwinkleTrack
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Pixel Arrow gameplay screen with White Glassmorphism frosted board, HUD, and controls
@Composable
fun GameplayScreen(
  boardState: BoardSessionState,
  skin: ArrowSkin,
  hintsRemaining: Long,
  soundActive: Boolean,
  darkTheme: Boolean,
  onArrowTapped: (Int) -> Unit,
  onTriggerHint: () -> Unit,
  onOpenSettings: () -> Unit,
  onToggleQuickSound: () -> Unit,
  onBackHome: () -> Unit,
) {
  BackHandler { onBackHome() }

  val inkPrimary = if (darkTheme) Color.White else GlassInkPrimary

  var zoom by remember(boardState.mode, boardState.levelNumber, boardState.isBonusLevel) {
    mutableFloatStateOf(1f)
  }
  var panOffset by remember(boardState.mode, boardState.levelNumber, boardState.isBonusLevel) {
    mutableStateOf(Offset.Zero)
  }
  var canvasSize by remember { mutableStateOf(IntSize.Zero) }

  var frameClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var lastDropVersion by remember(boardState.mode, boardState.levelNumber) { mutableIntStateOf(0) }
  var dropAnimStartMs by remember(boardState.mode, boardState.levelNumber) { mutableLongStateOf(0L) }

  LaunchedEffect(boardState.gravityDropVersion) {
    if (boardState.gravityDropVersion > lastDropVersion) {
      lastDropVersion = boardState.gravityDropVersion
      dropAnimStartMs = System.currentTimeMillis()
    }
  }

  // Smoothly pan board toward the hinted arrow when Hint is triggered
  LaunchedEffect(boardState.focusPointRequest) {
    val req = boardState.focusPointRequest ?: return@LaunchedEffect
    if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
    val pt = req.second
    val targetZoom = maxOf(zoom, if (boardState.cols >= 18) 2.2f else 1.35f).coerceIn(1f, 4f)
    val baseCell =
      min(
        (canvasSize.width * 0.90f) / boardState.cols,
        (canvasSize.height * 0.92f) / boardState.rows,
      )
    val scaledCell = baseCell * targetZoom
    val arrowOffsetFromCenterX = (pt.x + 0.5f - boardState.cols / 2f) * scaledCell
    val arrowOffsetFromCenterY = (pt.y + 0.5f - boardState.rows / 2f) * scaledCell
    zoom = targetZoom
    val maxPanX = (canvasSize.width * (targetZoom - 1f)) * 0.55f + 40f
    val maxPanY = (canvasSize.height * (targetZoom - 1f)) * 0.55f + 40f
    panOffset =
      Offset(
        x = (-arrowOffsetFromCenterX).coerceIn(-maxPanX, maxPanX),
        y = (-arrowOffsetFromCenterY).coerceIn(-maxPanY, maxPanY),
      )
  }

  val hasActiveAnimations =
    boardState.exitingArrows.isNotEmpty() ||
      boardState.blockedFeedback != null ||
      boardState.hintArrowId != null ||
      (System.currentTimeMillis() - dropAnimStartMs < 350L)

  LaunchedEffect(hasActiveAnimations, boardState.arrows.size) {
    if (!hasActiveAnimations) return@LaunchedEffect
    while (true) {
      withFrameNanos {
        frameClockMs = System.currentTimeMillis()
      }
    }
  }

  val latestBoard by rememberUpdatedState(boardState)
  val latestZoom by rememberUpdatedState(zoom)
  val latestPan by rememberUpdatedState(panOffset)

  Box(modifier = Modifier.fillMaxSize()) {
    TiledArrowBackground(darkTheme = darkTheme)

    Column(modifier = Modifier.fillMaxSize()) {
      // Top White Glassmorphism HUD bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .glassPanel(darkTheme = darkTheme, cornerRadius = 14.dp, elevation = 8.dp)
            .clickable(onClick = onOpenSettings)
            .testTag("game_settings_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = inkPrimary,
            modifier = Modifier.size(24.dp),
          )
        }

        // Level number and hearts glass pill
        Column(
          modifier = Modifier
            .glassPanel(
              darkTheme = darkTheme,
              cornerRadius = 16.dp,
              elevation = 8.dp,
              highOpacity = true,
              accentBorder = if (boardState.isBonusLevel) ArcadeGold else null,
            )
            .padding(horizontal = 18.dp, vertical = 7.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          val headerLabel =
            when {
              boardState.isBonusLevel -> "BONUS LVL ${boardState.levelNumber}"
              boardState.mode == GameMode.CLASSIC -> "LEVEL ${boardState.levelNumber}"
              else -> "FALL ${boardState.levelNumber} · ${boardState.fallScore}"
            }
          if (darkTheme) {
            OutlinedGameText(
              text = headerLabel,
              fontSize = 15.sp,
              fillColor = if (boardState.isBonusLevel) ArcadeGold else Color.White,
              outlineColor = Color(0xFF0A142E),
              outlineWidth = 5f,
            )
          } else {
            Text(
              text = headerLabel,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = if (boardState.isBonusLevel) GlassGoldWarm else GlassInkPrimary,
            )
          }
          Spacer(modifier = Modifier.height(3.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..3) {
              Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = if (i <= boardState.hearts) "Heart remaining" else "Heart lost",
                tint = if (i <= boardState.hearts) HeartRed else HeartEmpty,
                modifier = Modifier.size(19.dp),
              )
            }
          }
        }

        Row(
          modifier = Modifier
            .glassPanel(darkTheme = darkTheme, cornerRadius = 14.dp, elevation = 8.dp)
            .padding(horizontal = 10.dp, vertical = 11.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = if (soundActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = "Mute Toggle",
            tint = inkPrimary,
            modifier = Modifier
              .size(24.dp)
              .clickable(onClick = onToggleQuickSound)
              .testTag("game_sound_button"),
          )
          Icon(
            imageVector = Icons.Default.CenterFocusStrong,
            contentDescription = "Reset Zoom",
            tint = if (darkTheme) NeonCyan else GlassAccentBlue,
            modifier = Modifier
              .size(24.dp)
              .clickable {
                zoom = 1f
                panOffset = Offset.Zero
              }
              .testTag("game_recenter_button"),
          )
        }
      }

      // Elevated White Glassmorphism puzzle board stage
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 4.dp)
          .then(
            if (darkTheme) {
              Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkBoard)
                .border(1.5.dp, NeonCyan.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
            } else {
              Modifier.glassPanel(
                darkTheme = false,
                cornerRadius = 24.dp,
                elevation = 12.dp,
                highOpacity = true,
              )
            }
          )
          .clipToBounds()
          .onSizeChanged { canvasSize = it }
          .pointerInput(boardState.mode, boardState.levelNumber, boardState.isBonusLevel) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              val downPos = down.position
              val downTime = System.currentTimeMillis()
              var movedPastSlop = false
              var maxPointers = 1

              while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount > maxPointers) maxPointers = pressedCount

                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (pressedCount >= 2 || (zoomChange != 1f && maxPointers >= 2)) {
                  movedPastSlop = true
                  val newZoom = (latestZoom * zoomChange).coerceIn(1f, 4f)
                  val maxPanX = (size.width * (newZoom - 1f)) * 0.55f + 40f
                  val maxPanY = (size.height * (newZoom - 1f)) * 0.55f + 40f
                  zoom = newZoom
                  panOffset =
                    Offset(
                      x = (latestPan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                      y = (latestPan.y + panChange.y).coerceIn(-maxPanY, maxPanY),
                    )
                  event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (pressedCount == 1) {
                  val change = event.changes.first()
                  val dist = hypot(change.position.x - downPos.x, change.position.y - downPos.y)
                  if (dist > touchSlop) movedPastSlop = true
                  if (movedPastSlop) {
                    val maxPanX = (size.width * (latestZoom - 1f)) * 0.55f + 40f
                    val maxPanY = (size.height * (latestZoom - 1f)) * 0.55f + 40f
                    panOffset =
                      Offset(
                        x = (latestPan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                        y = (latestPan.y + panChange.y).coerceIn(-maxPanY, maxPanY),
                      )
                    if (change.positionChanged()) change.consume()
                  }
                }

                if (event.changes.none { it.pressed }) {
                  if (!movedPastSlop && maxPointers == 1 && System.currentTimeMillis() - downTime < 400L) {
                    val b = latestBoard
                    val baseCell =
                      min(
                        (size.width * 0.90f) / b.cols,
                        (size.height * 0.92f) / b.rows,
                      )
                    val cellSize = baseCell * latestZoom
                    val boardW = b.cols * cellSize
                    val boardH = b.rows * cellSize
                    val originX = (size.width - boardW) / 2f + latestPan.x
                    val originY = (size.height - boardH) / 2f + latestPan.y

                    val gx = (downPos.x - originX) / cellSize
                    val gy = (downPos.y - originY) / cellSize

                    val hitArrow =
                      b.arrows
                        .mapNotNull { arrow ->
                          val minDist =
                            arrow.cells.minOf { cell ->
                              hypot(gx - (cell.x + 0.5f), gy - (cell.y + 0.5f))
                            }
                          if (minDist <= 0.85f) arrow to minDist else null
                        }
                        .minByOrNull { it.second }
                        ?.first

                    if (hitArrow != null) {
                      onArrowTapped(hitArrow.id)
                    }
                  }
                  break
                }
              }
            }
          }
          .testTag("puzzle_board_canvas")
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val baseCell =
            min(
              (size.width * 0.90f) / boardState.cols,
              (size.height * 0.92f) / boardState.rows,
            )
          val cellSize = baseCell * zoom
          val boardW = boardState.cols * cellSize
          val boardH = boardState.rows * cellSize
          val originX = (size.width - boardW) / 2f + panOffset.x
          val originY = (size.height - boardH) / 2f + panOffset.y

          // Pixel grid anchor dots
          if (zoom >= 1.4f || boardState.cols <= 14) {
            val dotColor = if (darkTheme) Color(0x2200F0FF) else Color(0x226885C4)
            for (r in 0 until boardState.rows) {
              for (c in 0 until boardState.cols) {
                drawCircle(
                  color = dotColor,
                  radius = (cellSize * 0.048f).coerceAtLeast(1.2f),
                  center = Offset(originX + (c + 0.5f) * cellSize, originY + (r + 0.5f) * cellSize),
                )
              }
            }
          }

          val dropElapsed = (frameClockMs - dropAnimStartMs).coerceAtLeast(0L)
          val dropT = (dropElapsed / 240f).coerceIn(0f, 1f)
          val dropEase = 1f - (1f - dropT) * (1f - dropT)

          val blocked = boardState.blockedFeedback
          val blockedElapsed = if (blocked != null) frameClockMs - blocked.startedAtMs else Long.MAX_VALUE
          val isBlockedActive = blocked != null && blockedElapsed in 0L..420L

          val hintPulse = ((sin(frameClockMs / 180.0) + 1.0) * 0.5).toFloat()

          for (arrow in boardState.arrows) {
            val deltaRows = boardState.gravityDeltas[arrow.id] ?: 0
            val dropOffset =
              if (deltaRows > 0 && dropT < 1f) {
                -deltaRows * (1f - dropEase)
              } else {
                0f
              }

            var slideCells = 0f
            var isFlash = false
            if (isBlockedActive && blocked.arrowId == arrow.id) {
              val bt = (blockedElapsed / 420f).coerceIn(0f, 1f)
              val bump = sin(bt * PI).toFloat()
              slideCells = bump * 0.44f
              isFlash = bt < 0.72f
            }

            with(SkinRenderer) {
              drawArrowWithSkin(
                arrow = arrow,
                skin = skin,
                darkTheme = darkTheme,
                cellSize = cellSize,
                originX = originX,
                originY = originY,
                slideCells = slideCells,
                dropOffsetY = dropOffset,
                isBlockedFlash = isFlash,
                isHinted = boardState.hintArrowId == arrow.id,
                hintPulse = hintPulse,
              )
            }
          }

          val maxExitExtra = maxOf(boardState.cols, boardState.rows) + 4
          for (anim in boardState.exitingArrows) {
            val elapsed = (frameClockMs - anim.startedAtMs).coerceAtLeast(0L)
            val rawT = (elapsed / anim.durationMs.toFloat()).coerceIn(0f, 1f)
            if (rawT >= 1f) continue
            val eased = rawT * rawT * (1.2f + 0.3f * rawT)
            val slide = eased * (anim.arrow.cells.size + maxExitExtra)
            val alpha = (1f - (rawT - 0.75f).coerceAtLeast(0f) * 4f).coerceIn(0f, 1f)

            with(SkinRenderer) {
              drawArrowWithSkin(
                arrow = anim.arrow,
                skin = skin,
                darkTheme = darkTheme,
                cellSize = cellSize,
                originX = originX,
                originY = originY,
                slideCells = slide,
                alpha = alpha,
              )
            }
          }

          // Blocked arrow sightline and impact spark
          if (blocked != null && blockedElapsed in 0L..420L) {
            val tapped = boardState.arrows.firstOrNull { it.id == blocked.arrowId }
            if (tapped != null) {
              val headCenter =
                Offset(
                  originX + (tapped.head.x + 0.5f) * cellSize,
                  originY + (tapped.head.y + 0.5f) * cellSize,
                )
              val blockCenter =
                Offset(
                  originX + (blocked.blockPoint.x + 0.5f) * cellSize,
                  originY + (blocked.blockPoint.y + 0.5f) * cellSize,
                )
              val fade = (1f - blockedElapsed / 420f).coerceIn(0f, 1f)
              drawLine(
                color = HeartRed.copy(alpha = 0.7f * fade),
                start = headCenter,
                end = blockCenter,
                strokeWidth = (cellSize * 0.12f).coerceAtLeast(2f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
              )
              drawCircle(
                color = HeartRed.copy(alpha = 0.85f * fade),
                radius = cellSize * (0.35f + 0.25f * (1f - fade)),
                center = blockCenter,
              )
            }
          }
        }

        // Remaining arrows counter badge (frosted glass)
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
            .glassPanel(darkTheme = darkTheme, cornerRadius = 10.dp, elevation = 4.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Text(
            text = "${boardState.arrows.size} left",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (darkTheme) Color.White else GlassInkSecondary,
          )
        }
      }

      // Bottom zoom slider and Hint button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        ZoomSliderBar(
          zoom = zoom,
          darkTheme = darkTheme,
          onZoomChange = { newZoom ->
            zoom = newZoom.coerceIn(1f, 4f)
            if (zoom <= 1.01f) panOffset = Offset.Zero
          },
          modifier = Modifier
            .weight(1f)
            .glassPanel(darkTheme = darkTheme, cornerRadius = 18.dp, elevation = 8.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Hint button (triggers Rewarded Ad for a free hint when charges are empty)
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .glassPanel(darkTheme = darkTheme, cornerRadius = 18.dp, elevation = 8.dp)
            .clickable(onClick = onTriggerHint)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("game_hint_button"),
        ) {
          Box(contentAlignment = Alignment.TopEnd) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(
                  Brush.verticalGradient(listOf(if (darkTheme) NeonCyan else GlassAccentCyan, GlassAccentBlue))
                )
                .border(1.5.dp, Color.White, CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = "Hint",
                tint = Color.White,
                modifier = Modifier.size(23.dp),
              )
            }
            Box(
              modifier = Modifier
                .clip(CircleShape)
                .background(if (hintsRemaining > 0) Color(0xFFFF2E63) else ArcadeAmber)
                .border(1.dp, Color.White, CircleShape)
                .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
              Text(
                text = if (hintsRemaining > 0) "$hintsRemaining" else "AD",
                color = if (hintsRemaining > 0) Color.White else Color(0xFF1F1100),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
              )
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "HINT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = inkPrimary,
          )
        }
      }
    }
  }
}

@Composable
private fun ZoomSliderBar(
  zoom: Float,
  darkTheme: Boolean,
  onZoomChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val fraction = ((zoom - 1f) / 3f).coerceIn(0f, 1f)
  var trackWidthPx by remember { mutableIntStateOf(1) }
  val iconTint = if (darkTheme) Color(0xFF768BD8) else GlassAccentBlue

  Row(
    modifier = modifier.height(50.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clickable { onZoomChange(zoom - 0.5f) }
        .testTag("zoom_out_button"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "Zoom Out",
        tint = iconTint,
        modifier = Modifier
          .size(32.dp)
          .rotate(180f),
      )
      Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }

    Box(
      modifier = Modifier
        .weight(1f)
        .height(34.dp)
        .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
        .pointerInput(Unit) {
          detectTapGestures { offset ->
            val f = (offset.x / trackWidthPx.toFloat()).coerceIn(0f, 1f)
            onZoomChange(1f + f * 3f)
          }
        },
      contentAlignment = Alignment.CenterStart,
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(14.dp)
          .clip(RoundedCornerShape(50))
          .background(if (darkTheme) PeriwinkleTrack.copy(alpha = 0.55f) else Color(0xFFD6E2FA))
          .border(1.dp, if (darkTheme) Color(0x44FFFFFF) else GlassRimHighlight, RoundedCornerShape(50))
      )
      val thumbSizePx = 28.dp
      val maxOffsetPx = (trackWidthPx - 80).coerceAtLeast(1)
      Box(
        modifier = Modifier
          .offset { IntOffset((fraction * maxOffsetPx).roundToInt(), 0) }
          .size(thumbSizePx)
          .shadow(4.dp, RoundedCornerShape(9.dp))
          .clip(RoundedCornerShape(9.dp))
          .background(Color.White)
          .border(1.5.dp, GlassRimIce, RoundedCornerShape(9.dp))
      )
    }

    Box(
      modifier = Modifier
        .size(44.dp)
        .clickable { onZoomChange(zoom + 0.5f) }
        .testTag("zoom_in_button"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "Zoom In",
        tint = iconTint,
        modifier = Modifier.size(32.dp),
      )
      Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
  }
}
