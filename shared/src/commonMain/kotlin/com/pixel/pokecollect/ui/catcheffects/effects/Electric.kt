package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.TAU
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.inkLine
import com.pixel.pokecollect.ui.catcheffects.polar
import com.pixel.pokecollect.ui.catcheffects.starPath
import com.pixel.pokecollect.ui.catcheffects.taperPath
import com.pixel.pokecollect.ui.catcheffects.zigzag
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Electric: a bolt strikes the circle twice. The card flashes and shakes, sparks fly, arcs crawl round the circle. */
internal object ElectricEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 750, peakMs = 90)

    override fun start(card: CardGeometry, random: Random): EffectRun = ElectricRun(card, random)
}

private val Yellow: Color = typeFill("electric")

/** The second strike is smaller and shorter. */
private class Strike(val atMs: Float, val lengthMs: Float, val scale: Float, val sparks: Int)

private val Strikes = listOf(Strike(90f, 140f, 1f, sparks = 12), Strike(280f, 80f, 0.7f, sparks = 6))

/** How often the bolt redraws while it's up, so it flickers, ms. */
private const val FLICKER_MS = 45f

private class Spark(val start: Offset, val velocity: Offset, val bornMs: Float, val lifeS: Float)

internal class ElectricRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val seed = random.nextInt()

    private val sparks: List<Spark> = Strikes.flatMap { strike ->
        List(strike.sparks) {
            val angle = random.nextFloat() * TAU
            val speed = 160 + random.nextFloat() * 260
            Spark(
                start = polar(card.circleCenter, card.circleRadius, angle),
                velocity = Offset(cos(angle) * speed, sin(angle) * speed),
                bornMs = strike.atMs,
                lifeS = 0.22f + random.nextFloat() * 0.18f,
            )
        }
    }

    /**
     * The bolt and its branch for one flicker [window] of strike [strikeIndex]. Seeded, so the same
     * moment always draws the same bolt.
     */
    internal fun bolt(strikeIndex: Int, window: Int, scale: Float): Pair<List<Offset>, List<Offset>> {
        val r = Random(seed + strikeIndex * 1000 + window)
        val top = Offset(card.circleCenter.x + 30 + r.nextFloat() * 45, 0f)
        val bolt = zigzag(r, top, card.circleCenter, segments = 5, amplitude = 9 * scale)
        val fork = bolt[2]
        val branch = zigzag(r, fork, Offset(fork.x + 28 + r.nextFloat() * 14, fork.y + 14 + r.nextFloat() * 10), segments = 3, amplitude = 4f)
        return bolt to branch
    }

    override fun DrawScope.inFront(ms: Float) {
        val c = card.circleCenter
        Strikes.forEachIndexed { i, strike ->
            val t = ms - strike.atMs
            if (t < 0f || t >= strike.lengthMs) return@forEachIndexed
            val (bolt, branch) = bolt(i, (t / FLICKER_MS).toInt(), strike.scale)
            val fade = 1 - t / strike.lengthMs
            inkFill(taperPath(branch, (1.5f + 4 * fade) * strike.scale, 1f), Yellow, 2f, StrokeJoin.Miter)
            inkFill(taperPath(bolt, (9 + 3 * fade) * strike.scale, 3 * strike.scale), Yellow, 2.2f, StrokeJoin.Miter)
            if (t < 90f) {
                val burst = starPath(c, (20 + t * 0.15f) * strike.scale, (10 + t * 0.08f) * strike.scale, points = 8, rotation = 0.2f)
                inkFill(burst, Yellow, 2.5f, StrokeJoin.Miter)
            }
        }
        for (s in sparks) {
            val age = (ms - s.bornMs) / 1000f
            if (age < 0f || age > s.lifeS) continue
            val k = 1 - age / s.lifeS
            val head = s.start + s.velocity * (age - 0.8f * age * age) // slows as it flies
            inkLine(listOf(head - s.velocity * (0.06f * k), head), 1.4f + 1.2f * k, Yellow, outline = 2.6f)
        }
        // Small arcs crawling round the circle after the strike.
        if (ms >= 120f && ms < 620f) {
            val n = ((ms - 120) / 55).toInt()
            val angle = Random(seed + 5000 + n).nextFloat() * TAU
            val arc = (0 until 5).map { j -> polar(c, card.circleRadius + 4 + if (j % 2 == 1) 6 else 0, angle + j * 0.16f) }
            inkLine(arc, 2f, Yellow, outline = 3f)
        }
    }

    override fun shake(ms: Float): Offset {
        if (ms < 90f) return Offset.Zero
        val s = ms - 90
        return Offset(5 * exp(-s / 110) * sin(s * 0.11f), 0f)
    }

    override fun cardFill(ms: Float): Color? = if (ms in 90f..160f || ms in 280f..330f) Yellow else null
}
