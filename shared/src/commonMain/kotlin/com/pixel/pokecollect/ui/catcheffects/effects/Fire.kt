package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.ShaderLayer
import com.pixel.pokecollect.ui.catcheffects.shaders.FireShader
import kotlin.random.Random

/** Fire: flowing flame tongues rise from the bottom edge, tallest round the circle, with embers. A shader. */
internal object FireEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1100, peakMs = 440)

    override fun start(card: CardGeometry, random: Random): EffectRun = FireRun(ShaderLayer(FireShader, card, timing, random.nextFloat() * 10))
}

private class FireRun(private val flames: ShaderLayer) : EffectRun {
    override fun DrawScope.behind(ms: Float) = with(flames) { draw(ms) }
}
