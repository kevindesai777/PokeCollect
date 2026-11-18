package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.ShaderLayer
import com.pixel.pokecollect.ui.catcheffects.shaders.WaterShader
import kotlin.random.Random

/** Water: the card fills past the circle, bubbles rising, splashes as the circle fills, and drains. A shader. */
internal object WaterEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1200, peakMs = 500)

    override fun start(card: CardGeometry, random: Random): EffectRun = WaterRun(ShaderLayer(WaterShader, card, timing, random.nextFloat() * 10))
}

private class WaterRun(private val water: ShaderLayer) : EffectRun {
    override fun DrawScope.behind(ms: Float) = with(water) { draw(ms) }
}
