package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.circlePath
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.lerp
import com.pixel.pokecollect.ui.catcheffects.polar
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Poison: purple sludge rises along the bottom; bubbles swell out of it and pop. */
internal object PoisonEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1150, peakMs = 480)

    override fun start(card: CardGeometry, random: Random): EffectRun = PoisonRun(card, random)
}

private val Purple = typeFill("poison")

private class Bubble(val bornMs: Float, val x: Float, val lifeMs: Float, val maxRadius: Float, val speed: Float)

private class PoisonRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val bubbles = List(30) {
        Bubble(
            bornMs = random.nextFloat() * 700,
            x = 8 + random.nextFloat() * (card.size.width - 16),
            lifeMs = 280 + random.nextFloat() * 340,
            maxRadius = 3.5f + random.nextFloat() * 7,
            speed = 40 + random.nextFloat() * 80,
        )
    }

    override fun DrawScope.behind(ms: Float) {
        val w = card.size.width
        val h = card.size.height
        val level = h * 0.3f * easeOut(progress(ms, 0f, 350f)) * (1 - easeIn(progress(ms, 800f, 1150f)))
        fun surface(x: Float) = h - level + 3 * sin(x * 0.06f + ms * 0.008f) + 2 * sin(x * 0.13f - ms * 0.011f)

        if (level > 0.5f) {
            val sludge = Path().apply {
                moveTo(-10f, h + 10)
                var x = -10f
                while (x <= w + 10) {
                    lineTo(x, surface(x))
                    x += 6
                }
                lineTo(w + 10, h + 10)
                close()
            }
            inkFill(sludge, Purple)
        }
        for (b in bubbles) {
            val t = ms - b.bornMs
            if (t < 0f) continue
            val y0 = surface(b.x) + 2
            if (t < b.lifeMs) {
                val y = y0 - t * b.speed / 1000
                val r = lerp(2f, b.maxRadius, easeOut(t / b.lifeMs))
                inkFill(circlePath(Offset(b.x, y), r), Purple, 1.8f)
                drawCircle(Color.White, r * 0.28f, Offset(b.x - r * 0.35f, y - r * 0.35f))
            } else if (t < b.lifeMs + 110) {
                // Popped: a ring of short ink rays.
                val center = Offset(b.x, y0 - b.lifeMs * b.speed / 1000)
                val k = (t - b.lifeMs) / 110
                val inner = b.maxRadius * (1 + 0.6f * k)
                repeat(6) { i ->
                    val angle = i * PI.toFloat() / 3 + 0.3f
                    drawLine(PokeColors.Ink, polar(center, inner, angle), polar(center, inner + 5 * (1 - k), angle), 1.8f, StrokeCap.Round)
                }
            }
        }
    }
}
