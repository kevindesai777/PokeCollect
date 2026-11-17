package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pixel.pokecollect.ui.theme.PokeColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Timing helpers. Every effect is a function of the ms since the tap, built from these.

internal const val TAU = (2 * PI).toFloat()

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** How far [ms] is through the window [from]..[to], from 0 to 1. */
internal fun progress(ms: Float, from: Float, to: Float) = ((ms - from) / (to - from)).coerceIn(0f, 1f)

internal fun easeOut(x: Float) = 1 - (1 - x).pow(3)

internal fun easeIn(x: Float) = x * x * x

/** Overshoots a little past 1, then settles: a spring. */
internal fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1
    return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
}

/** The point [radius] from [center] at [angle] (radians, clockwise from the right). */
internal fun polar(center: Offset, radius: Float, angle: Float) = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)

/** The circle's pop just after it fills: a swell of [amount] and back, over [POP_MS]. */
internal fun circlePop(sinceFill: Float, amount: Float): Offset {
    if (sinceFill <= 0f || sinceFill >= POP_MS) return Offset(1f, 1f)
    val s = 1 + amount * sin(PI.toFloat() * sinceFill / POP_MS)
    return Offset(s, s)
}

// Drawing helpers. The flat look: every shape is filled, then outlined in black ink.

internal fun polyline(points: List<Offset>, close: Boolean = false) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    if (close) close()
}

internal fun DrawScope.inkFill(path: Path, fill: Color, lineWidth: Float = 2f, join: StrokeJoin = StrokeJoin.Round) {
    drawPath(path, fill)
    drawPath(path, PokeColors.Ink, style = Stroke(lineWidth, miter = 4f, join = join))
}

/** A line drawn twice: a wider ink stroke, then [color] on top, so it reads as a coloured line with an ink outline. */
internal fun DrawScope.inkLine(points: List<Offset>, width: Float, color: Color?, outline: Float = 4f) {
    if (points.size < 2) return
    val path = polyline(points)
    drawPath(path, PokeColors.Ink, style = Stroke(width + outline, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (color != null) drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A star or comic burst: [points] tips at [outer], valleys at [inner]. */
internal fun starPath(center: Offset, outer: Float, inner: Float, points: Int, rotation: Float): Path =
    polyline(
        (0 until points * 2).map { i ->
            polar(center, if (i % 2 == 0) outer else inner, rotation + i * PI.toFloat() / points - PI.toFloat() / 2)
        },
        close = true,
    )

/** A band along [points] that narrows from [startWidth] to [endWidth]: lightning, ribbons. */
internal fun taperPath(points: List<Offset>, startWidth: Float, endWidth: Float): Path {
    val left = ArrayList<Offset>(points.size)
    val right = ArrayList<Offset>(points.size)
    for (i in points.indices) {
        val before = points[maxOf(0, i - 1)]
        val after = points[minOf(points.lastIndex, i + 1)]
        val dx = after.x - before.x
        val dy = after.y - before.y
        val length = hypot(dx, dy).takeIf { it > 0f } ?: 1f
        val half = lerp(startWidth, endWidth, i / points.lastIndex.toFloat()) / 2
        val normal = Offset(-dy / length * half, dx / length * half)
        left += points[i] + normal
        right += points[i] - normal
    }
    return polyline(left + right.asReversed(), close = true)
}

/**
 * A zigzag from [from] to [to] in [segments] steps, each inner corner pushed alternately to one
 * side and the other by up to [amplitude]. The ends stay put.
 */
internal fun zigzag(random: Random, from: Offset, to: Offset, segments: Int, amplitude: Float): List<Offset> {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy).takeIf { it > 0f } ?: 1f
    val nx = -dy / length
    val ny = dx / length
    return (0..segments).map { i ->
        val t = i / segments.toFloat()
        val push = if (i == 0 || i == segments) 0f else (if (i % 2 == 1) 1 else -1) * amplitude * (0.6f + 0.8f * random.nextFloat())
        Offset(from.x + dx * t + nx * push, from.y + dy * t + ny * push)
    }
}
