package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.TAU
import com.pixel.pokecollect.ui.catcheffects.heartPath
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.polar
import com.pixel.pokecollect.ui.catcheffects.popLife
import com.pixel.pokecollect.ui.catcheffects.starPath
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Fairy: a spiral of sparkles round the circle while pink hearts float up across the card; a burst of both as it fills. */
internal object FairyEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1150, peakMs = 450)

    override fun start(card: CardGeometry, random: Random): EffectRun = FairyRun(card, random)
}

private val Pink = typeFill("fairy")
private val HotPink = typeFill("psychic")
private val SparkleFills = listOf(Pink, Color.White, typeFill("electric"))

private class Heart(val bornMs: Float, val x: Float, val phase: Float, val size: Float, val fill: Color)

private class FairyRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val spiralStart = random.nextFloat() * TAU
    private val hearts = List(8) { i ->
        Heart(
            bornMs = 40 + i * 60f,
            x = card.circleCenter.x + 26 + random.nextFloat() * min(card.size.width * 0.62f, 260f),
            phase = random.nextFloat() * 6,
            size = 8.5f + random.nextFloat() * 4,
            fill = if (i % 2 == 1) Pink else HotPink,
        )
    }

    override fun DrawScope.inFront(ms: Float) {
        val c = card.circleCenter
        for (heart in hearts) {
            val t = ms - heart.bornMs
            val k = popLife(t, 720f)
            if (k <= 0f) continue
            val at = Offset(heart.x + sin(t * 0.01f + heart.phase) * 6, card.size.height - 8 - t * 0.075f)
            val size = heart.size * k
            inkFill(heartPath(at, size), heart.fill, 1.8f)
            drawCircle(Color.White, size * 0.18f, Offset(at.x - size * 0.45f, at.y - size * 0.35f))
        }
        // Sparkles pop one after another along a spiral out from the circle.
        for (i in 0 until 14) {
            val k = popLife(ms - i * 32, 420f)
            if (k <= 0f) continue
            val angle = spiralStart + i * 0.85f
            val radius = card.circleRadius + 8 + i * 5.5f
            val size = (10 + (i % 3) * 3) * k
            val at = Offset(c.x + cos(angle) * radius, c.y + sin(angle) * radius * 0.6f)
            inkFill(starPath(at, size, size * 0.32f, points = 4, rotation = ms / 600 + i), SparkleFills[i % 3], 1.6f)
        }
        val s = ms - FairyEffect.timing.peakMs
        if (s in 0f..420f) {
            val k = 1 - s / 420
            for (i in 0 until 10) {
                val at = polar(c, card.circleRadius + s * 0.2f, i / 10f * TAU + 0.2f)
                if (i % 2 == 1) {
                    inkFill(heartPath(at, 7 * k), HotPink, 1.5f)
                } else {
                    inkFill(starPath(at, 7 * k, 2.4f * k, points = 4, rotation = 0f), SparkleFills[(i / 2) % 3], 1.4f)
                }
            }
        }
    }
}
