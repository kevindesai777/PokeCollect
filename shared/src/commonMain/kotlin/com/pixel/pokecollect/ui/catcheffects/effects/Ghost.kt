package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.ShaderLayer
import com.pixel.pokecollect.ui.catcheffects.TAU
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.popLife
import com.pixel.pokecollect.ui.catcheffects.shaders.GhostShader
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ghost: purple wisps drift up through the card (a shader), spirit flames circle over it, and the
 * circle flickers on and off before it stays filled.
 */
internal object GhostEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1300, peakMs = 560)

    override fun start(card: CardGeometry, random: Random): EffectRun =
        GhostRun(card, ShaderLayer(GhostShader, card, timing, random.nextFloat() * 10), random)
}

private val Purple = typeFill("ghost")
private val Cream = Color(0xFFFFFDF2)

private class SpiritFlame(val angle: Float, val radius: Float, val phase: Float, val bornMs: Float)

private class GhostRun(private val card: CardGeometry, private val wisps: ShaderLayer, random: Random) : EffectRun {
    private val end = GhostEffect.timing.durationMs.toFloat()
    private val flames = List(6) { i -> SpiritFlame(i / 6f * TAU + random.nextFloat() * 0.5f, 6.5f + random.nextFloat() * 3, random.nextFloat() * 6, i * 60f) }

    override fun DrawScope.behind(ms: Float) = with(wisps) { draw(ms) }

    override fun DrawScope.inFront(ms: Float) {
        val w = card.size.width
        val h = card.size.height
        // They circle a point right of the dex circle, on a wide flat orbit across the card.
        val centreX = min(w * 0.5f, card.circleCenter.x + 140)
        for (f in flames) {
            val k = popLife(ms - f.bornMs, end - f.bornMs - 40)
            if (k <= 0f) continue
            val angle = f.angle + ms * 0.0022f
            val at = Offset(centreX + cos(angle) * w * 0.38f, h * 0.58f + sin(angle) * h * 0.26f + sin(ms / 160 + f.phase) * 4)
            val sway = sin(ms / 90 + f.phase) * 3
            val r = f.radius * k * (1 + 0.12f * sin(ms / 50 + f.phase))
            inkFill(spiritFlame(at, r, sway), Purple)
            drawCircle(Cream, r * 0.5f, at + Offset(0f, r * 0.1f))
        }
    }

    // The circle flickers like a bad bulb before it stays on.
    override fun circleFilled(ms: Float, timing: EffectTiming): Boolean {
        val s = ms - timing.fillMs
        return s >= 0f && (s >= 260f || (s / 55).toInt() % 2 == 0)
    }
}

/** A spirit flame: a round base, a tall swaying tip and two smaller ones. */
private fun spiritFlame(c: Offset, r: Float, sway: Float) = Path().apply {
    val x = c.x
    val y = c.y
    moveTo(x - r, y)
    arcTo(Rect(c, r), 180f, -180f, false) // round bottom, left to right
    quadraticTo(x + r * 1.1f, y - r * 1.1f, x + r * 0.8f + sway * 0.4f, y - r * 1.75f)
    quadraticTo(x + r * 0.45f, y - r * 1.2f, x + r * 0.3f, y - r * 1.25f)
    quadraticTo(x + r * 0.5f, y - r * 2.2f, x + sway, y - r * 3.0f)
    quadraticTo(x - r * 0.55f, y - r * 2.0f, x - r * 0.3f, y - r * 1.2f)
    quadraticTo(x - r * 0.5f, y - r * 1.15f, x - r * 0.8f + sway * 0.3f, y - r * 1.6f)
    quadraticTo(x - r * 1.15f, y - r * 1.0f, x - r, y)
    close()
}
