package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.circlePop
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.polar
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.theme.PokeColors
import kotlin.math.PI
import kotlin.random.Random

/** Normal: the plain one. The circle squashes like a rubber stamp, then stamps: action lines and a ring. */
internal object NormalEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 650, peakMs = 140)

    override fun start(card: CardGeometry, random: Random): EffectRun = NormalRun(card)
}

private const val STAMP_MS = 260f

private class NormalRun(private val card: CardGeometry) : EffectRun {
    override fun DrawScope.inFront(ms: Float) {
        val s = ms - NormalEffect.timing.peakMs
        if (s < 0f || s > STAMP_MS) return
        val k = s / STAMP_MS
        val c = card.circleCenter
        val r = card.circleRadius
        repeat(8) { i ->
            val angle = i * PI.toFloat() / 4 + 0.39f
            val inner = r + 5 + s * 0.08f
            drawLine(PokeColors.Ink, polar(c, inner, angle), polar(c, inner + 9 * (1 - k), angle), strokeWidth = 3f, cap = StrokeCap.Round)
        }
        drawCircle(PokeColors.Ink, radius = r + 2 + s * 0.1f, center = c, style = Stroke(3 * (1 - k)))
    }

    override fun circleScale(ms: Float, timing: EffectTiming): Offset {
        if (ms < timing.fillMs) {
            val k = easeOut(progress(ms, 0f, timing.peakMs.toFloat()))
            return Offset(1 + 0.12f * k, 1 - 0.16f * k)
        }
        return circlePop(ms - timing.fillMs, amount = 0.28f)
    }
}
