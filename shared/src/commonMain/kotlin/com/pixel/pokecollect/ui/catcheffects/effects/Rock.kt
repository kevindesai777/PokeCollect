package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CardMotion
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.TAU
import com.pixel.pokecollect.ui.catcheffects.ballistic
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.impact
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.polar
import com.pixel.pokecollect.ui.catcheffects.polyline
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.catcheffects.rockPath
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Rock: a rockfall. Every landing jolts the card; the last, biggest rock lands on the circle and cracks the card. */
internal object RockEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1350, peakMs = 600)

    // No lift first: the card just takes the hits (Kevin, 2026-11-16).
    override val motion = CardMotion.Beat(liftDp = 0f)

    override fun start(card: CardGeometry, random: Random): EffectRun = RockRun(card, random)
}

/** Gravity for falling rocks, dp/s². */
private const val GRAVITY = 1800f

private val Stone = typeFill("rock")
private val Fills = listOf(Stone, Stone, typeFill("ground"), typeFill("dark"))

/** A rock that falls from above the card, bounces once on its bottom edge and rests there. */
private class Pebble(val x: Float, val bornMs: Float, val size: Float, val corners: FloatArray, val rotation: Float, val fill: Color, cardHeight: Float) {
    val startY = -size * 2
    val floor = cardHeight - size * 0.75f
    val fallS = sqrt(2 * (floor - startY) / GRAVITY)
    val landMs = bornMs + fallS * 1000

    fun y(ageS: Float): Float {
        if (ageS < fallS) return startY + 0.5f * GRAVITY * ageS * ageS
        val t = ageS - fallS
        val bounce = 0.28f * GRAVITY * fallS
        return min(floor, floor - (bounce * t - 0.5f * GRAVITY * t * t))
    }
}

private class RockRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val end = RockEffect.timing.durationMs.toFloat()
    private val peak = RockEffect.timing.peakMs.toFloat()

    private val pebbles = List(16) { i ->
        val size = 4 + random.nextFloat() * 5.5f
        Pebble(
            x = 10 + random.nextFloat() * (card.size.width - 20),
            bornMs = random.nextFloat() * 640,
            size = size,
            corners = FloatArray(7) { random.nextFloat() },
            rotation = random.nextFloat() * 6,
            fill = Fills[i % Fills.size],
            cardHeight = card.size.height,
        )
    }

    // The big one: timed to land on top of the circle at the peak.
    private val bigCorners = FloatArray(8) { random.nextFloat() }
    private val bigBornMs = peak - sqrt(2 * (card.circleCenter.y - card.circleRadius - 8 + 30) / GRAVITY) * 1000

    private val cracks: List<List<Offset>> = List(5) { i ->
        var angle = i / 5f * TAU + random.nextFloat() * 0.6f
        var point = polar(card.circleCenter, card.circleRadius, angle)
        val points = mutableListOf(point)
        repeat(5 + random.nextInt(4)) {
            angle += (random.nextFloat() - 0.5f)
            point = polar(point, 9 + random.nextFloat() * 7, angle)
            points += point
        }
        points
    }

    override fun DrawScope.behind(ms: Float) {
        val s = ms - peak
        if (s < 0f) return
        val grow = easeOut(progress(s, 0f, 130f)) * (1 - easeIn(progress(ms, end - 260, end)))
        for (crack in cracks) {
            val n = maxOf(1, (grow * (crack.size - 1)).toInt())
            drawPath(polyline(crack.take(n + 1)), PokeColors.Ink, style = Stroke(2.5f, join = StrokeJoin.Miter))
        }
    }

    override fun DrawScope.inFront(ms: Float) {
        val fade = 1 - easeIn(progress(ms, end - 260, end))
        for (p in pebbles) {
            val age = (ms - p.bornMs) / 1000
            if (age < 0f) continue
            val y = p.y(age)
            inkFill(rockPath(Offset(p.x, y), p.size * fade, p.corners, p.rotation + min(age, p.fallS) * 4), p.fill)
        }
        val bigAge = (ms - bigBornMs) / 1000
        if (ms < peak && bigAge > 0f) {
            inkFill(rockPath(Offset(card.circleCenter.x, -30 + 0.5f * GRAVITY * bigAge * bigAge), 11f, bigCorners, bigAge * 3), Stone, 2.2f)
        }
        // It breaks on the circle.
        val s = (ms - peak) / 1000
        if (s in 0f..0.6f) {
            val top = Offset(card.circleCenter.x, card.circleCenter.y - card.circleRadius)
            repeat(5) { i ->
                val angle = -PI.toFloat() / 2 + (i - 2) * 0.55f
                val at = ballistic(top, Offset(cos(angle) * 180, sin(angle) * 260), s, 1400f)
                inkFill(rockPath(at, 4.5f * (1 - s / 0.6f), bigCorners.copyOf(6), i + s * 6), Stone, 1.8f)
            }
        }
    }

    override fun shake(ms: Float): Offset {
        var total = impact(ms, peak, 4.5f, 90f)
        for (p in pebbles) total += impact(ms, p.landMs, p.size * 0.3f, 55f)
        return total
    }
}
