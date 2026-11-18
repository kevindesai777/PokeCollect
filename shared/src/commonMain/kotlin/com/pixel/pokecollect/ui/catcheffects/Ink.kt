package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pixel.pokecollect.ui.theme.PokeColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
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

internal fun easeInOut(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun degrees(radians: Float) = radians * 180f / PI.toFloat()

/** A particle's size over its life: pops in over the first quarter, holds, then shrinks away. 0 outside it. */
internal fun popLife(age: Float, length: Float): Float {
    if (age < 0f || age > length) return 0f
    val rise = 0.25f * length
    return if (age < rise) easeOutBack(age / rise) else 1 - easeIn((age - rise) / (length - rise))
}

/** Where something thrown from [start] at [velocity] (dp/s) is after [ageS] seconds of [gravity] (dp/s²). */
internal fun ballistic(start: Offset, velocity: Offset, ageS: Float, gravity: Float) =
    Offset(start.x + velocity.x * ageS, start.y + velocity.y * ageS + 0.5f * gravity * ageS * ageS)

/** A knock at [atMs] that dies away over about [decayMs]: mostly up and down, a little sideways, dp. */
internal fun impact(ms: Float, atMs: Float, amplitude: Float, decayMs: Float): Offset {
    val s = ms - atMs
    if (s < 0f) return Offset.Zero
    val k = amplitude * exp(-s / decayMs)
    return Offset(k * 0.35f * sin(s * 0.15f), k * sin(s * 0.12f))
}

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

internal fun circlePath(center: Offset, radius: Float) = Path().apply { addOval(Rect(center, maxOf(radius, 0.01f))) }

internal fun heartPath(center: Offset, size: Float) = Path().apply {
    val x = center.x
    val y = center.y
    moveTo(x, y + size * 0.9f)
    cubicTo(x - size * 1.25f, y + size * 0.05f, x - size * 0.85f, y - size * 1.05f, x, y - size * 0.35f)
    cubicTo(x + size * 0.85f, y - size * 1.05f, x + size * 1.25f, y + size * 0.05f, x, y + size * 0.9f)
    close()
}

/** A leaf or feather growing from [base] toward [angle]: pointed at both ends, with a midrib. */
internal fun DrawScope.leaf(base: Offset, length: Float, angle: Float, fill: Color, widthRatio: Float = 0.4f) {
    if (length <= 0.5f) return
    val dir = Offset(cos(angle), sin(angle))
    val tip = base + dir * length
    val mid = base + dir * (length / 2)
    val side = Offset(-dir.y, dir.x) * (length * widthRatio)
    val path = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(mid.x + side.x, mid.y + side.y, tip.x, tip.y)
        quadraticTo(mid.x - side.x, mid.y - side.y, base.x, base.y)
        close()
    }
    inkFill(path, fill, 1.8f)
    drawLine(PokeColors.Ink, base, base + dir * (length * 0.72f), strokeWidth = 1.2f)
}

/** An irregular stone or shard: one corner per value in [corners] (0..1, how far out it reaches). */
internal fun rockPath(center: Offset, size: Float, corners: FloatArray, rotation: Float): Path =
    polyline(corners.mapIndexed { i, v -> polar(center, size * (0.7f + 0.35f * v), rotation + i * TAU / corners.size) }, close = true)

/** A thin blade-shaped slash from [from] toward [to], [k] of the way there. */
internal fun DrawScope.slash(from: Offset, to: Offset, k: Float, width: Float) {
    if (k <= 0f || width <= 0.2f) return
    val end = from + (to - from) * k
    val mid = (from + end) / 2f
    val length = (end - from).getDistance().takeIf { it > 0f } ?: 1f
    val n = Offset(-(end.y - from.y) / length * width, (end.x - from.x) / length * width)
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(mid.x + n.x, mid.y + n.y, end.x, end.y)
        quadraticTo(mid.x - n.x, mid.y - n.y, from.x, from.y)
        close()
    }
    inkFill(path, Color.White, 1.6f)
}

/**
 * Overlapping circles outlined as one shape (clouds, dust): every circle is outlined first, then
 * every circle filled, so only the outside edge keeps its ink.
 */
internal fun DrawScope.puffs(circles: List<Pair<Offset, Float>>, fill: Color, outline: Float = 4.5f) {
    for ((center, radius) in circles) if (radius > 0.1f) drawCircle(PokeColors.Ink, radius, center, style = Stroke(outline))
    for ((center, radius) in circles) if (radius > 0.1f) drawCircle(fill, radius, center)
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
