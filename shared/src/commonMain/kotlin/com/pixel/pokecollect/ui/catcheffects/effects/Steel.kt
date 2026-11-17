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
import com.pixel.pokecollect.ui.catcheffects.ballistic
import com.pixel.pokecollect.ui.catcheffects.circlePath
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeInOut
import com.pixel.pokecollect.ui.catcheffects.impact
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.inkLine
import com.pixel.pokecollect.ui.catcheffects.lerp
import com.pixel.pokecollect.ui.catcheffects.polyline
import com.pixel.pokecollect.ui.catcheffects.popLife
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.catcheffects.starPath
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.random.Random

/**
 * Steel: two silver shutters with interlocking teeth slam shut over the card with a clang and
 * sparks, a gleam runs across them, and they slide open on the card, caught.
 */
internal object SteelEffect : CatchEffect {
    // The circle fills behind the closed shutters, shown as they open.
    override val timing = EffectTiming(durationMs = 1150, peakMs = 380, fillMs = 780)
    override val motion = CardMotion.Beat(liftDp = 0f)

    override fun start(card: CardGeometry, random: Random): EffectRun = SteelRun(card, random)
}

/** How far each tooth reaches past the seam, dp, and how many tooth edges run down the card. */
private const val TOOTH = 10f
private const val TOOTH_ROWS = 8

private class SteelSpark(val start: Offset, val velocity: Offset, val fill: Color)

private class SteelRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val w = card.size.width
    private val h = card.size.height
    private val peak = SteelEffect.timing.peakMs.toFloat()
    private val seam = w / 2

    private val sparks = List(14) { i ->
        val side = if (i % 2 == 1) 1 else -1
        SteelSpark(
            start = Offset(seam, random.nextFloat() * h),
            velocity = Offset(side * (120 + random.nextFloat() * 260), -(60 + random.nextFloat() * 220)),
            fill = if (i % 3 == 0) typeFill("electric") else Color.White,
        )
    }

    override fun DrawScope.inFront(ms: Float) {
        // Shut by the peak (accelerating, so they slam), open from 700 ms.
        val shut = easeIn(progress(ms, 0f, peak)) * (1 - easeInOut(progress(ms, 700f, 1000f)))
        if (shut > 0.001f) {
            val left = lerp(-TOOTH - 6, seam - TOOTH / 2, shut)
            val right = lerp(w + TOOTH + 6, seam + TOOTH / 2, shut)
            val rows = (0..TOOTH_ROWS).map { i -> i * h / TOOTH_ROWS }
            plate(rows.mapIndexed { i, y -> Offset(left + if (i % 2 == 1) TOOTH else 0f, y) }, side = -1)
            plate(rows.mapIndexed { i, y -> Offset(right - if (i % 2 == 1) 0f else TOOTH, y) }, side = 1)
            if (ms > peak + 60 && ms < peak + 300) {
                // A gleam runs across the closed shutters.
                val x = lerp(-40f, w + 40, easeInOut(progress(ms, peak + 60, peak + 300)))
                drawPath(polyline(listOf(Offset(x, 0f), Offset(x + 16, 0f), Offset(x - 8, h), Offset(x - 24, h)), close = true), Color.White)
            }
        }
        // Clang: sparks fly off the seam.
        val s = (ms - peak) / 1000
        if (s in 0f..0.4f) {
            val k = 1 - s / 0.4f
            for (spark in sparks) {
                val at = ballistic(spark.start, spark.velocity, s, 900f)
                inkLine(listOf(at - spark.velocity * (0.03f * k), at), 1.6f * k + 0.6f, spark.fill, outline = 2.4f)
            }
        }
        val glint = popLife(ms - peak - 180, 300f)
        if (glint > 0f) inkFill(starPath(Offset(seam, 14f), 15 * glint, 4 * glint, points = 4, rotation = 0f), Color.White, 1.8f)
    }

    /** One shutter: its toothed [edge] faces the seam; [side] -1 extends left from it, 1 right. */
    private fun DrawScope.plate(edge: List<Offset>, side: Int) {
        val far = if (side < 0) -30f else w + 30
        val outline = polyline(listOf(Offset(far, edge.first().y)) + edge + Offset(far, edge.last().y), close = true)
        drawPath(outline, PokeColors.Silver)
        // A bright bevel just inside the teeth, then a shade line.
        val bevel = edge.map { it + Offset(side * 3f, 0f) } + edge.map { it + Offset(side * 9f, 0f) }.asReversed()
        drawPath(polyline(bevel, close = true), PokeColors.SilverLight)
        drawPath(polyline(edge.map { it + Offset(side * 15f, 0f) }), PokeColors.SilverDark, style = Stroke(2f))
        // Brushed sheen and rivets.
        val base = if (side < 0) edge.minOf { it.x } else edge.maxOf { it.x }
        for ((dx, width) in listOf(46f to 6f, 58f to 2.5f)) {
            drawLine(PokeColors.SilverLight, Offset(base + side * dx, -2f), Offset(base + side * (dx + 28), h + 2), width)
        }
        for (fy in listOf(0.22f, 0.5f, 0.78f)) inkFill(circlePath(Offset(base + side * 28f, h * fy), 3f), PokeColors.SilverLight, 1.5f)
        drawPath(outline, PokeColors.Ink, style = Stroke(2.5f, join = StrokeJoin.Miter))
    }

    override fun shake(ms: Float): Offset = impact(ms, peak, 3.5f, 70f)
}
