package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CardMotion
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.TAU
import com.pixel.pokecollect.ui.catcheffects.degrees
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeInOut
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.easeOutBack
import com.pixel.pokecollect.ui.catcheffects.lerp
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.catcheffects.starPath
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dark: night falls round the circle with a smoky edge. A crescent moon and stars come out,
 * Umbreon-style yellow rings turn round the circle and pulse outward, and a pair of red eyes
 * watches from the dark and blinks. Then the night lifts.
 */
internal object DarkEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1350, peakMs = 740)
    override val motion = CardMotion.Beat(liftDp = 0f)

    override fun start(card: CardGeometry, random: Random): EffectRun = DarkRun(card, random)
}

private val Glow = typeFill("electric")
private val Moon = Color(0xFFFFFDF2) // the light theme's cream
private val Smoke = Color(0xFF3B3832) // between ink and the dark surface: the smoky rim of the night

private class Star(val at: Offset, val phase: Float)

private class DarkRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val w = card.size.width
    private val h = card.size.height
    private val peak = DarkEffect.timing.peakMs.toFloat()
    private val c = card.circleCenter

    /** The hole round the circle at its tightest, and big enough to clear the whole card. */
    private val tight = card.circleRadius + 6
    private val open = hypot(max(c.x, w - c.x), h) + 40

    private val stars = List(10) { Star(Offset(c.x + 70 + random.nextFloat() * (w - c.x - 90), 6 + random.nextFloat() * (h - 12)), random.nextFloat() * 6) }

    private fun holeRadius(ms: Float) = when {
        ms < 360f -> lerp(open, tight, easeInOut(progress(ms, 0f, 360f)))
        ms < peak + 80 -> tight
        else -> lerp(tight, open, easeIn(progress(ms, peak + 80, peak + 560)))
    }

    override fun DrawScope.inFront(ms: Float) {
        val radius = holeRadius(ms)
        if (radius >= open - 1) return
        val settled = radius <= tight + 0.5f
        // The hole's edge billows like smoke while it moves, and barely stirs once it's settled.
        fun edge(angle: Float, extra: Float) = radius + extra +
            if (settled) 1.2f * sin(7 * angle + ms * 0.02f) else 7 * sin(5 * angle + ms * 0.012f) + 4 * sin(9 * angle - ms * 0.02f)

        fun night(extra: Float) = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, w, h))
            for (i in 0..72) {
                val angle = i / 72f * TAU
                val rr = edge(angle, extra)
                val x = c.x + cos(angle) * rr
                val y = c.y + sin(angle) * rr
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(night(0f), Smoke)
        drawPath(night(9f), PokeColors.Ink)

        val dark = 1 - ((radius - tight) / (open * 0.55f)).coerceIn(0f, 1f)
        for (star in stars) {
            val s = (1.6f + 1.4f * sin(ms * 0.012f + star.phase)) * dark
            if (s > 0.2f) drawPath(starPath(star.at, s * 1.6f, s * 0.5f, points = 4, rotation = 0f), Moon)
        }
        if (dark > 0.05f) {
            val moon = Offset(w - 32, 22 + (1 - dark) * 30)
            drawCircle(Moon, 11f, moon)
            drawCircle(PokeColors.Ink, 9.5f, moon + Offset(5f, -4f))
        }

        // Rings round the circle, turning like a spell, then pulses going out.
        val rings = easeOutBack(progress(ms, 300f, 460f)) * (1 - easeIn(progress(ms, peak + 40, peak + 240)))
        if (rings > 0.01f) {
            arcs(tight + 13 * rings, count = 4, sweep = 0.8f, turn = ms * 0.004f, width = 3.4f * rings)
            arcs(tight + 23 * rings, count = 7, sweep = 0.32f, turn = -ms * 0.003f, width = 2.4f * rings)
        }
        for (at in listOf(480f, 620f)) {
            val t = ms - at
            if (t in 0f..420f) drawCircle(Glow, tight + 26 + t * 0.32f, c, style = Stroke(3 * (1 - t / 420)))
        }

        // Red eyes in the dark, on the right. They open, blink once, and close before the light returns.
        var lids = if (ms > 380f && ms < peak - 30) easeOut(progress(ms, 380f, 440f)) else 0f
        if (ms > 560f && ms < 620f) lids *= abs(cos((ms - 560) / 60 * PI.toFloat()))
        if (lids > 0.05f) {
            for (side in listOf(-1f, 1f)) {
                translate(w - 92 + side * 14, c.y - 2) {
                    rotate(degrees(side * 0.3f), Offset.Zero) {
                        val almond = Path().apply {
                            moveTo(-10f, 0f)
                            quadraticTo(0f, -11 * lids, 10f, 0f)
                            quadraticTo(0f, 11 * lids, -10f, 0f)
                            close()
                        }
                        drawPath(almond, PokeColors.Error)
                        drawOval(PokeColors.Ink, Offset(-side * 1.5f - 1.6f, -4.4f * lids), Size(3.2f, 8.8f * lids))
                    }
                }
            }
        }
    }

    private fun DrawScope.arcs(radius: Float, count: Int, sweep: Float, turn: Float, width: Float) {
        repeat(count) { i ->
            drawArc(
                Glow,
                startAngle = degrees(turn + i * TAU / count),
                sweepAngle = degrees(sweep),
                useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width),
            )
        }
    }
}
