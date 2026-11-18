package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.ContentWarp
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.shaders.PsychicShader
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.random.Random

/**
 * Psychic: rings pulse out from the circle while the card's content bends, as if seen through a
 * psychic wave. The bend is a filter over the content (a shader reading the drawn pixels); the
 * card's fill, border and shadow stay straight.
 */
internal object PsychicEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1100, peakMs = 480)

    override fun start(card: CardGeometry, random: Random): EffectRun =
        PsychicRun(card, ContentWarp(PsychicShader, timing, seed = random.nextFloat() * 50))
}

/** When each ring starts, ms; each lives [RING_MS]. */
private val RingStarts = floatArrayOf(0f, 130f, 260f, 390f, 520f)
private const val RING_MS = 600f
private val RingFills = listOf(typeFill("psychic"), typeFill("fairy"))

private class PsychicRun(private val card: CardGeometry, private val warp: ContentWarp) : EffectRun {
    override fun DrawScope.behind(ms: Float) {
        RingStarts.forEachIndexed { i, at ->
            val t = ms - at
            if (t < 0f || t > RING_MS) return@forEachIndexed
            val width = 7 * (1 - t / RING_MS)
            val radius = card.circleRadius + 2 + t * 0.22f
            drawCircle(PokeColors.Ink, radius, card.circleCenter, style = Stroke(width + 4))
            drawCircle(RingFills[i % 2], radius, card.circleCenter, style = Stroke(width))
        }
    }

    override val warpsContent: Boolean get() = true

    override fun contentEffect(ms: Float, density: Float): RenderEffect? = warp.effect(ms, density)
}
