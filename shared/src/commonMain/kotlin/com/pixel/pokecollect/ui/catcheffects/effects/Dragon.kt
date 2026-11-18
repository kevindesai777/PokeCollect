package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CardMotion
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.circlePath
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.lerp
import com.pixel.pokecollect.ui.catcheffects.polyline
import com.pixel.pokecollect.ui.catcheffects.popLife
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.catcheffects.starPath
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dragon: rare, strong, elusive. Scales sweep over the card (it is the dragon's hide, spines along
 * the top), a great golden eye opens in the circle, a roar shakes the card, the eye looks round,
 * blinks shut, and the dragon slips away, a few scales catching the light.
 */
internal object DragonEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1200, peakMs = 780)
    override val motion = CardMotion.Beat(liftDp = 0f)

    override fun start(card: CardGeometry, random: Random): EffectRun = DragonRun(card, random)
}

private val Scale = typeFill("dragon")
private val LitScale = typeFill("ghost")
private val Spine = typeFill("fighting")
private val Iris = typeFill("electric")
private val EyeRim = typeFill("fire")

/** When the eye has opened and roars, ms. */
private const val ROAR = 440f

/** Scale size: width and row spacing, dp. */
private const val SCALE_W = 16f
private const val SCALE_H = 9f

private class Glint(val at: Offset, val delayMs: Float)

private class DragonRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val w = card.size.width
    private val h = card.size.height
    private val peak = DragonEffect.timing.peakMs.toFloat()

    /** A few scales picked to be the lighter colour, by column and row. */
    private val lit: Set<Int> = List(40) { random.nextInt(30) * 100 + random.nextInt(12) }.toSet()

    private val glints = List(5) {
        val c = card.circleCenter
        Glint(Offset(c.x + 50 + random.nextFloat() * (w - c.x - 70), 10 + random.nextFloat() * (h - 20)), random.nextFloat() * 120)
    }

    // The hide never moves, only its edge does, so its scales and spines are made once per catch:
    // every scale outlined in one path, the light ones filled in another. Made and drawn as ~260
    // separate paths every frame, they cost a Pixel 8 about 3 ms more GPU time per frame.
    private val scales = Path()
    private val litScales = Path()
    private val spines = Path()

    init {
        var row = 0
        while (row * SCALE_H < h + SCALE_H) {
            var col = -1
            while (col * SCALE_W < w + SCALE_W) {
                val x = col * SCALE_W + if (row % 2 == 1) SCALE_W / 2 else 0f
                val y = row * SCALE_H
                val bounds = Rect(x - SCALE_W / 2, y - SCALE_W / 2, x + SCALE_W / 2, y + SCALE_W / 2)
                scales.addArc(bounds, 0f, 180f)
                if ((col + 1) * 100 + row in lit) litScales.addArc(bounds, 0f, 180f)
                col++
            }
            row++
        }
        var x = 14f
        while (x < w) {
            spines.addPath(polyline(listOf(Offset(x - 9, 9f), Offset(x + 2, -9f), Offset(x + 9, 9f)), close = true))
            x += 30
        }
    }

    override fun DrawScope.behind(ms: Float) {
        // The hide sweeps in from the right, and out to the right after the eye closes.
        val inEdge = lerp(w + 40, -40f, easeOut(progress(ms, 0f, 300f)))
        val outEdge = lerp(-40f, w + 40, easeIn(progress(ms, peak, peak + 360)))
        val edge = maxOf(inEdge, outEdge)
        if (edge > w + 30) return
        val region = polyline(listOf(Offset(edge + 16, -20f), Offset(w + 30, -20f), Offset(w + 30, h + 12), Offset(edge - 16, h + 12)), close = true)
        clipPath(region) {
            drawRect(Scale, Offset(-10f, 0f), Size(w + 20, h + 10))
            drawPath(litScales, LitScale)
            drawPath(scales, PokeColors.Ink, style = Stroke(1.5f))
            inkFill(spines, Spine, 1.8f)
        }
        drawLine(PokeColors.Ink, Offset(edge + 16, -20f), Offset(edge - 16, h + 12), 2.5f)
    }

    override fun DrawScope.inFront(ms: Float) {
        val c = card.circleCenter
        val r = card.circleRadius
        // The roar: two shock rings.
        val s = ms - ROAR
        if (s in 0f..420f) {
            for (delay in listOf(0f, 90f)) {
                val t = s - delay
                if (t < 0f) continue
                drawCircle(PokeColors.Ink, r + 16 + t * 0.45f, c, style = Stroke(5 * (1 - t / 420)))
            }
        }
        val open = when {
            ms < 280f -> 0f
            ms < ROAR -> easeOut(progress(ms, 280f, ROAR))
            ms < peak - 80 -> 1f
            ms < peak -> 1 - easeIn(progress(ms, peak - 80, peak))
            else -> 0f
        }
        if (open > 0.02f) eye(ms, open)
        // A few scales catch the light as it leaves.
        val sinceClose = ms - peak
        if (sinceClose in 0f..360f) {
            for (g in glints) {
                val k = popLife(sinceClose - g.delayMs, 220f)
                if (k > 0f) inkFill(starPath(g.at, 9 * k, 2.6f * k, points = 4, rotation = 0f), Color.White, 1.5f)
            }
        }
    }

    /** The eye, an almond as wide as the circle and more; [open] 0..1 is how far the lids are apart. */
    private fun DrawScope.eye(ms: Float, open: Float) {
        val c = card.circleCenter
        val r = card.circleRadius
        val halfWidth = r + 15
        val halfHeight = r * 0.62f * open
        val lens = Path().apply {
            moveTo(c.x - halfWidth, c.y)
            quadraticTo(c.x, c.y - 2 * halfHeight, c.x + halfWidth, c.y)
            quadraticTo(c.x, c.y + 2 * halfHeight, c.x - halfWidth, c.y)
            close()
        }
        drawPath(lens, EyeRim)
        clipPath(lens) {
            val look = if (ms > ROAR + 20) 7 * sin((ms - ROAR - 20) / 110) else 0f
            val pupil = Offset(c.x + look, c.y)
            inkFill(circlePath(pupil, r * 0.78f), Iris, 1.5f)
            // The slit narrows at the roar, then widens again as it looks round.
            val slit = if (ms < ROAR) 6.5f else lerp(2.2f, 5f, progress(ms, ROAR + 150, DragonEffect.timing.peakMs - 80f))
            drawOval(PokeColors.Ink, Offset(pupil.x - slit, pupil.y - r * 0.72f), Size(slit * 2, r * 1.44f))
            drawCircle(Color.White, 2.6f, Offset(pupil.x - 7, pupil.y - 6))
        }
        drawPath(lens, PokeColors.Ink, style = Stroke(3f))
        if (open > 0.3f) {
            val brow = Path().apply {
                moveTo(c.x - halfWidth - 4, c.y - 2)
                quadraticTo(c.x - 6, c.y - 2 * halfHeight - 14, c.x + halfWidth + 8, c.y - 10)
            }
            drawPath(brow, PokeColors.Ink, style = Stroke(4.5f, cap = StrokeCap.Round))
        }
    }

    // The roar shakes the card.
    override fun shake(ms: Float): Offset {
        val s = ms - ROAR
        if (s < 0f || s > 500f) return Offset.Zero
        val k = exp(-s / 150)
        return Offset(2.6f * k * sin(s * 0.55f), 1.6f * k * sin(s * 0.41f))
    }
}
