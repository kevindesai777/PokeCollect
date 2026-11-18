package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.TAU
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeInOut
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.leaf
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.catcheffects.slash
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Grass: Razor Leaf. Sharp leaves blow in from the right, whirl round the circle, cut an X across it, and blow away. */
internal object GrassEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1250, peakMs = 560)

    override fun start(card: CardGeometry, random: Random): EffectRun = GrassRun(card, random)
}

private val Green = typeFill("grass")
private val YellowGreen = typeFill("bug")

/** How fast the whirl turns, radians per second. */
private const val WHIRL = 7f

private class RazorLeaf(
    val bornMs: Float,
    val start: Offset,
    val wave: Float,
    val phase: Float,
    val orbitAngle: Float,
    val orbitExtra: Float,
    val rotation: Float,
    val spin: Float,
    val size: Float,
    val fill: Color,
)

private class GrassRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val peak = GrassEffect.timing.peakMs.toFloat()
    private val end = GrassEffect.timing.durationMs.toFloat()

    private val leaves = List(24) { i ->
        RazorLeaf(
            bornMs = i * 18f,
            start = Offset(card.size.width + 20 + random.nextFloat() * 60, 8 + random.nextFloat() * (card.size.height - 16)),
            wave = 6 + random.nextFloat() * 10,
            phase = random.nextFloat() * 6,
            orbitAngle = random.nextFloat() * TAU,
            orbitExtra = 6 + random.nextFloat() * 30,
            rotation = random.nextFloat() * 6,
            spin = (if (random.nextBoolean()) -1 else 1) * (8 + random.nextFloat() * 6),
            size = 19 + random.nextFloat() * 6,
            fill = if (i % 3 == 0) YellowGreen else Green,
        )
    }

    /** A leaf's place in the whirl round the circle at [ms]: an ellipse, wider than tall. */
    private fun orbit(leaf: RazorLeaf, ms: Float): Offset {
        val angle = leaf.orbitAngle + WHIRL * ms / 1000
        val radius = card.circleRadius + 8 + leaf.orbitExtra
        return Offset(card.circleCenter.x + cos(angle) * radius * 1.15f, card.circleCenter.y + sin(angle) * radius * 0.7f)
    }

    override fun DrawScope.inFront(ms: Float) {
        val shrink = 1 - easeIn(progress(ms, end - 220, end))
        for (razor in leaves) {
            if (ms < razor.bornMs) continue
            val at = if (ms < peak) {
                // Blown in on a wavy line, drawn into the whirl.
                val k = easeInOut(progress(ms, razor.bornMs, peak))
                val drift = Offset(razor.start.x - (ms - razor.bornMs) * 0.25f, razor.start.y + sin(ms * 0.012f + razor.phase) * razor.wave)
                drift + (orbit(razor, ms) - drift) * k
            } else {
                // The whirl flies apart and a gust carries the leaves off to the right.
                val s = (ms - peak) / 1000
                val angle = razor.orbitAngle + WHIRL * peak / 1000
                orbit(razor, peak) + Offset(cos(angle) * 240 * s + 560 * s * s, sin(angle) * 160 * s - 60 * s)
            }
            val length = razor.size * shrink
            val angle = razor.rotation + razor.spin * ms / 1000
            leaf(at - Offset(cos(angle), sin(angle)) * (length / 2), length, angle, razor.fill, widthRatio = 0.32f)
        }
        // The razor cut: an X of white slashes across the circle.
        val s = ms - peak
        if (s in 0f..280f) {
            val thin = 1 - easeIn(progress(s, 100f, 280f))
            val c = card.circleCenter
            slash(c + Offset(-42f, -26f), c + Offset(42f, 26f), easeOut((s / 70).coerceIn(0f, 1f)), 4.5f * thin)
            slash(c + Offset(-42f, 22f), c + Offset(42f, -22f), easeOut(((s - 40) / 70).coerceIn(0f, 1f)), 4.5f * thin)
        }
    }
}
