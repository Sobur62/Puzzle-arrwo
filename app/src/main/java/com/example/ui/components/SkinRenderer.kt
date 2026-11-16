package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.game.model.ArrowEntity
import com.example.game.model.ArrowSkin
import com.example.game.model.Direction
import com.example.game.model.GridPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

object SkinRenderer {

  private val noodleColors = listOf(Color(0xFF2196F3), Color(0xFFFF8A1F), Color(0xFF8E44FD))
  private val caterpillarColors = listOf(Color(0xFF3B4FE4), Color(0xFFF97316), Color(0xFF9333EA))
  private val prismColors = listOf(Color(0xFF1D74F5), Color(0xFFFF7D1A), Color(0xFF8C3BF7))

  /**
   * Computes a shifted polyline in grid-cell coordinates (centered at cell + 0.5)
   * when an arrow slides forward by `slideCells` along its own body and out along `direction`.
   */
  fun buildTrackPoints(
    arrow: ArrowEntity,
    slideCells: Float,
    dropOffsetY: Float = 0f,
    originX: Float,
    originY: Float,
    cellSize: Float,
  ): List<Offset> {
    val cells = arrow.cells
    if (cells.isEmpty()) return emptyList()
    val segCount = (cells.size - 1).coerceAtLeast(1)

    // Extended track in cell coordinates: [c0, c1, ..., c_{k-1}, c_{k-1} + dir * extra]
    val track = ArrayList<Pair<Float, Float>>(cells.size + 2)
    for (c in cells) {
      track.add(c.x + 0.5f to c.y + 0.5f + dropOffsetY)
    }
    val tailExtend = slideCells.coerceAtLeast(0f) + 2f
    val head = cells.last()
    track.add(
      (head.x + 0.5f + arrow.direction.dx * tailExtend) to
        (head.y + 0.5f + dropOffsetY + arrow.direction.dy * tailExtend)
    )

    fun sampleAt(dist: Float): Offset {
      if (dist <= 0f) {
        val p0 = track[0]
        val p1 = track[1]
        val dx = p1.first - p0.first
        val dy = p1.second - p0.second
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val gx = p0.first + (dx / len) * dist
        val gy = p0.second + (dy / len) * dist
        return Offset(originX + gx * cellSize, originY + gy * cellSize)
      }
      var remaining = dist
      for (i in 0 until track.lastIndex) {
        val a = track[i]
        val b = track[i + 1]
        val segLen = hypot(b.first - a.first, b.second - a.second)
        if (remaining <= segLen || i == track.lastIndex - 1) {
          val frac = if (segLen > 0.0001f) (remaining / segLen).coerceAtLeast(0f) else 0f
          val gx = a.first + (b.first - a.first) * frac
          val gy = a.second + (b.second - a.second) * frac
          return Offset(originX + gx * cellSize, originY + gy * cellSize)
        }
        remaining -= segLen
      }
      val last = track.last()
      return Offset(originX + last.first * cellSize, originY + last.second * cellSize)
    }

    val result = ArrayList<Offset>(cells.size + 4)
    val startDist = slideCells
    val endDist = slideCells + segCount.toFloat()

    result.add(sampleAt(startDist))
    // Include intermediate bend vertices strictly inside (startDist, endDist)
    var cumDist = 0f
    for (i in 0 until track.lastIndex) {
      val a = track[i]
      val b = track[i + 1]
      val segLen = hypot(b.first - a.first, b.second - a.second)
      val nextCum = cumDist + segLen
      if (nextCum > startDist + 0.001f && nextCum < endDist - 0.001f) {
        result.add(Offset(originX + b.first * cellSize, originY + b.second * cellSize))
      }
      cumDist = nextCum
    }
    result.add(sampleAt(endDist))
    return result
  }

  fun DrawScope.drawArrowWithSkin(
    arrow: ArrowEntity,
    skin: ArrowSkin,
    darkTheme: Boolean,
    cellSize: Float,
    originX: Float,
    originY: Float,
    slideCells: Float = 0f,
    dropOffsetY: Float = 0f,
    isBlockedFlash: Boolean = false,
    isHinted: Boolean = false,
    hintPulse: Float = 0f,
    alpha: Float = 1f,
  ) {
    val points =
      buildTrackPoints(
        arrow = arrow,
        slideCells = slideCells,
        dropOffsetY = dropOffsetY,
        originX = originX,
        originY = originY,
        cellSize = cellSize,
      )
    if (points.size < 2) return

    val headPos = points.last()
    val preHead = points[points.size - 2]
    val angleRad = atan2(headPos.y - preHead.y, headPos.x - preHead.x)

    // Pixel-art block or hint glow highlight
    if (isHinted) {
      val glowWidth = cellSize * (0.86f + 0.24f * hintPulse)
      val glowPath = pointsToPath(points)
      drawPath(
        path = glowPath,
        color = Color(0xFF00F0FF).copy(alpha = (0.42f + 0.28f * hintPulse) * alpha),
        style = Stroke(width = glowWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
    }

    val flashOverride = if (isBlockedFlash) Color(0xFFFF2E63) else null

    when (skin) {
      ArrowSkin.GREEN_DOTS -> {
        val bodyColor = flashOverride ?: if (darkTheme) Color(0xFF39FF14) else Color(0xFF1B7A2B)
        val headColor = flashOverride ?: if (darkTheme) Color(0xFF00F0FF) else Color(0xFF2EA03F)
        val tailColor = flashOverride ?: Color(0xFFFFD700)
        drawDottedBody(points, cellSize * 0.24f, cellSize * 0.36f, bodyColor, tailColor, alpha)
        drawSharpHead(headPos, angleRad, cellSize * 0.38f, headColor, alpha)
      }
      ArrowSkin.CLASSIC_INK -> {
        val inkColor = flashOverride ?: if (darkTheme) Color(0xFF00F0FF) else Color(0xFF1A202C)
        drawPath(
          path = pointsToPath(points),
          color = inkColor.copy(alpha = alpha),
          style = Stroke(width = cellSize * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawSharpHead(headPos, angleRad, cellSize * 0.36f, inkColor, alpha)
      }
      ArrowSkin.PRISM_ARROWS -> {
        val color = flashOverride ?: prismColors[arrow.colorVariant % prismColors.size]
        drawPath(
          path = pointsToPath(points),
          color = color.copy(alpha = alpha),
          style = Stroke(width = cellSize * 0.28f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawSharpHead(headPos, angleRad, cellSize * 0.38f, color, alpha)
      }
      ArrowSkin.GOOGLY_NOODLES -> {
        val color = flashOverride ?: noodleColors[arrow.colorVariant % noodleColors.size]
        drawPath(
          path = pointsToPath(points),
          color = color.copy(alpha = alpha),
          style = Stroke(width = cellSize * 0.56f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawGooglyEyes(headPos, angleRad, cellSize, alpha)
      }
      ArrowSkin.MATCHSTICKS -> {
        val woodColor = flashOverride ?: Color(0xFFE2B165)
        val woodBorder = flashOverride ?: Color(0xFFC38938)
        val matchHeadColor = flashOverride ?: Color(0xFFE53935)
        val path = pointsToPath(points)
        drawPath(
          path = path,
          color = woodBorder.copy(alpha = alpha),
          style = Stroke(width = cellSize * 0.44f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
          path = path,
          color = woodColor.copy(alpha = alpha),
          style = Stroke(width = cellSize * 0.32f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Bulbous match head
        drawCircle(
          color = matchHeadColor.copy(alpha = alpha),
          radius = cellSize * 0.34f,
          center = headPos,
        )
        drawCircle(
          color = Color.White.copy(alpha = 0.45f * alpha),
          radius = cellSize * 0.11f,
          center = Offset(headPos.x - cellSize * 0.10f, headPos.y - cellSize * 0.10f),
        )
      }
      ArrowSkin.CATERPILLARS -> {
        val color = flashOverride ?: caterpillarColors[arrow.colorVariant % caterpillarColors.size]
        val darkShade = color.copy(red = color.red * 0.78f, green = color.green * 0.78f, blue = color.blue * 0.78f)
        drawSegmentedBeads(points, radius = cellSize * 0.28f, spacing = cellSize * 0.40f, colorA = color, colorB = darkShade, alpha = alpha)
        drawCircle(color = color.copy(alpha = alpha), radius = cellSize * 0.31f, center = headPos)
        drawGooglyEyes(headPos, angleRad, cellSize, alpha)
      }
      ArrowSkin.REINDEER -> {
        val brownA = flashOverride ?: Color(0xFF9E6234)
        val brownB = flashOverride ?: Color(0xFF7D4922)
        drawSegmentedBeads(points, radius = cellSize * 0.27f, spacing = cellSize * 0.40f, colorA = brownA, colorB = brownB, alpha = alpha)
        drawReindeerHead(headPos, angleRad, cellSize, flashOverride ?: Color(0xFF8C5228), alpha)
      }
      ArrowSkin.SANTA -> {
        val red = flashOverride ?: Color(0xFFD62D2D)
        val cream = flashOverride ?: Color(0xFFFFF4E6)
        drawSegmentedBeads(points, radius = cellSize * 0.28f, spacing = cellSize * 0.40f, colorA = red, colorB = cream, alpha = alpha)
        drawSantaHead(headPos, angleRad, cellSize, red, alpha)
      }
      ArrowSkin.PINE_TREE -> {
        val greenA = flashOverride ?: Color(0xFF1F7434)
        val greenB = flashOverride ?: Color(0xFF2D9647)
        drawSegmentedBeads(points, radius = cellSize * 0.28f, spacing = cellSize * 0.40f, colorA = greenA, colorB = greenB, alpha = alpha)
        drawSharpHead(headPos, angleRad, cellSize * 0.38f, greenB, alpha)
        drawCircle(
          color = (flashOverride ?: Color(0xFFFFD54F)).copy(alpha = alpha),
          radius = cellSize * 0.14f,
          center = headPos,
        )
      }
    }
  }

  private fun pointsToPath(points: List<Offset>): Path {
    return Path().apply {
      moveTo(points[0].x, points[0].y)
      for (i in 1 until points.size) {
        lineTo(points[i].x, points[i].y)
      }
    }
  }

  private fun DrawScope.drawDottedBody(
    points: List<Offset>,
    dotRadius: Float,
    spacing: Float,
    bodyColor: Color,
    tailColor: Color,
    alpha: Float,
  ) {
    val samples = sampleAlongPolyline(points, spacing)
    for (i in samples.indices) {
      val color = if (i == 0) tailColor else bodyColor
      drawCircle(
        color = color.copy(alpha = alpha),
        radius = if (i == 0) dotRadius * 0.85f else dotRadius,
        center = samples[i],
      )
    }
  }

  private fun DrawScope.drawSegmentedBeads(
    points: List<Offset>,
    radius: Float,
    spacing: Float,
    colorA: Color,
    colorB: Color,
    alpha: Float,
  ) {
    val samples = sampleAlongPolyline(points, spacing)
    for (i in samples.indices) {
      val c = if (i % 2 == 0) colorA else colorB
      drawCircle(color = c.copy(alpha = alpha), radius = radius, center = samples[i])
    }
  }

  private fun sampleAlongPolyline(points: List<Offset>, spacing: Float): List<Offset> {
    if (points.size < 2 || spacing <= 0.5f) return points
    val samples = ArrayList<Offset>()
    samples.add(points[0])
    var carry = 0f
    for (i in 0 until points.lastIndex) {
      val a = points[i]
      val b = points[i + 1]
      val dx = b.x - a.x
      val dy = b.y - a.y
      val len = hypot(dx, dy)
      if (len < 0.001f) continue
      var pos = spacing - carry
      while (pos <= len + 0.001f) {
        val t = (pos / len).coerceIn(0f, 1f)
        samples.add(Offset(a.x + dx * t, a.y + dy * t))
        pos += spacing
      }
      val used = pos - spacing
      carry = len - used
    }
    return samples
  }

  private fun DrawScope.drawSharpHead(
    head: Offset,
    angleRad: Float,
    size: Float,
    color: Color,
    alpha: Float,
  ) {
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    val perpX = -sinA
    val perpY = cosA

    val tip = Offset(head.x + cosA * size * 0.72f, head.y + sinA * size * 0.72f)
    val left = Offset(head.x - cosA * size * 0.45f + perpX * size * 0.72f, head.y - sinA * size * 0.45f + perpY * size * 0.72f)
    val right = Offset(head.x - cosA * size * 0.45f - perpX * size * 0.72f, head.y - sinA * size * 0.45f - perpY * size * 0.72f)

    val path =
      Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
      }
    drawPath(path = path, color = color.copy(alpha = alpha))
  }

  private fun DrawScope.drawGooglyEyes(
    head: Offset,
    angleRad: Float,
    cellSize: Float,
    alpha: Float,
  ) {
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    val perpX = -sinA
    val perpY = cosA

    val eyeOffsetSide = cellSize * 0.14f
    val eyeR = cellSize * 0.14f
    val pupilR = cellSize * 0.07f
    val lookShift = cellSize * 0.04f

    val leftEye = Offset(head.x + perpX * eyeOffsetSide, head.y + perpY * eyeOffsetSide)
    val rightEye = Offset(head.x - perpX * eyeOffsetSide, head.y - perpY * eyeOffsetSide)

    for (eye in listOf(leftEye, rightEye)) {
      drawCircle(color = Color.White.copy(alpha = alpha), radius = eyeR, center = eye)
      drawCircle(
        color = Color(0xFF1C2438).copy(alpha = alpha),
        radius = pupilR,
        center = Offset(eye.x + cosA * lookShift, eye.y + sinA * lookShift),
      )
    }
  }

  private fun DrawScope.drawReindeerHead(
    head: Offset,
    angleRad: Float,
    cellSize: Float,
    brown: Color,
    alpha: Float,
  ) {
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    val perpX = -sinA
    val perpY = cosA

    // Little antlers behind head
    val antlerColor = Color(0xFF5A3214).copy(alpha = alpha)
    for (sign in listOf(-1f, 1f)) {
      val base = Offset(head.x - cosA * cellSize * 0.10f + perpX * sign * cellSize * 0.22f, head.y - sinA * cellSize * 0.10f + perpY * sign * cellSize * 0.22f)
      val tip = Offset(base.x - cosA * cellSize * 0.26f + perpX * sign * cellSize * 0.20f, base.y - sinA * cellSize * 0.26f + perpY * sign * cellSize * 0.20f)
      drawLine(color = antlerColor, start = base, end = tip, strokeWidth = cellSize * 0.08f, cap = StrokeCap.Round)
    }

    drawCircle(color = brown.copy(alpha = alpha), radius = cellSize * 0.31f, center = head)
    drawGooglyEyes(head, angleRad, cellSize * 0.82f, alpha)
    // Red Rudolph nose
    drawCircle(
      color = Color(0xFFE53935).copy(alpha = alpha),
      radius = cellSize * 0.11f,
      center = Offset(head.x + cosA * cellSize * 0.20f, head.y + sinA * cellSize * 0.20f),
    )
  }

  private fun DrawScope.drawSantaHead(
    head: Offset,
    angleRad: Float,
    cellSize: Float,
    red: Color,
    alpha: Float,
  ) {
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    // Face
    drawCircle(color = Color(0xFFFFD8B8).copy(alpha = alpha), radius = cellSize * 0.29f, center = head)
    // White beard
    drawCircle(
      color = Color.White.copy(alpha = alpha),
      radius = cellSize * 0.22f,
      center = Offset(head.x - cosA * cellSize * 0.10f, head.y - sinA * cellSize * 0.10f),
    )
    // Red Santa hat tip
    drawSharpHead(
      head = Offset(head.x + cosA * cellSize * 0.08f, head.y + sinA * cellSize * 0.08f),
      angleRad = angleRad,
      size = cellSize * 0.34f,
      color = red,
      alpha = alpha,
    )
    drawCircle(
      color = Color.White.copy(alpha = alpha),
      radius = cellSize * 0.09f,
      center = Offset(head.x + cosA * cellSize * 0.32f, head.y + sinA * cellSize * 0.32f),
    )
  }
}

@Composable
fun SkinPreviewCanvas(
  skin: ArrowSkin,
  darkTheme: Boolean,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier.fillMaxSize()) {
    val cols = 3
    val rows = 4
    val cellSize = min(size.width / (cols + 0.6f), size.height / (rows + 0.4f))
    val originX = (size.width - cols * cellSize) / 2f
    val originY = (size.height - rows * cellSize) / 2f

    for (col in 0 until 3) {
      val dummy =
        ArrowEntity(
          id = col + 1,
          cells = listOf(GridPoint(col, 3), GridPoint(col, 2), GridPoint(col, 1), GridPoint(col, 0)),
          direction = Direction.UP,
          colorVariant = col,
        )
      with(SkinRenderer) {
        drawArrowWithSkin(
          arrow = dummy,
          skin = skin,
          darkTheme = darkTheme,
          cellSize = cellSize,
          originX = originX,
          originY = originY,
        )
      }
    }
  }
}

@Composable
fun GooglyMascotIcon(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val bodyW = w * 0.52f
    val left = (w - bodyW) / 2f
    // Coral red rounded body peeking from bottom
    drawRoundRect(
      color = Color(0xFFE75247),
      topLeft = Offset(left, h * 0.16f),
      size = Size(bodyW, h * 0.88f),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyW / 2f, bodyW / 2f),
    )
    val eyeR = w * 0.145f
    val pupilR = w * 0.055f
    val leftEye = Offset(w * 0.37f, h * 0.46f)
    val rightEye = Offset(w * 0.63f, h * 0.46f)
    drawCircle(Color.White, eyeR, leftEye)
    drawCircle(Color.White, eyeR, rightEye)
    drawCircle(Color(0xFF1A1E29), pupilR, leftEye)
    drawCircle(Color(0xFF1A1E29), pupilR, rightEye)
  }
}
