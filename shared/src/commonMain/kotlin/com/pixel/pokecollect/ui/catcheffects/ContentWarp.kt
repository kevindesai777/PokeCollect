package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.graphics.RenderEffect

/**
 * A filtering shader over the card's content (the circle, name, chips and box), for effects that
 * bend the card itself rather than draw on it. Like [ShaderLayer], it compiles on its first frame,
 * so start() still runs on the JVM.
 *
 * The filter works on the layer's pixels, not dp: the shader gets the density as `u_dp` and
 * converts.
 */
internal class ContentWarp(
    private val source: String,
    private val timing: EffectTiming,
    /** Moves the shader's noise so two catches of the same type don't bend the same way. */
    private val seed: Float,
) {
    private val shader by lazy { effectShader(source) }

    /** The filter for [ms] since the tap, or null outside the effect, when nothing bends. */
    fun effect(ms: Float, density: Float): RenderEffect? {
        if (ms <= 0f || ms >= timing.durationMs) return null
        shader.setFloat("u_dp", density)
        shader.setFloat("u_t", ms / timing.durationMs)
        shader.setFloat("u_sec", ms / 1000)
        shader.setFloat("u_seed", seed)
        return shader.renderEffect()
    }
}
