package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * One effect shader painted over the whole card. It compiles on its first frame rather than in
 * start(), which the shared tests call on the JVM, where there are no runtime shaders.
 *
 * Drawn inside the player's dp scaling, so the shader's coordinates come out in dp.
 */
internal class ShaderLayer(
    private val source: String,
    private val card: CardGeometry,
    private val timing: EffectTiming,
    /** Offsets the shader's clock so two catches of the same type don't look identical. */
    private val seed: Float,
) {
    private val shader by lazy { effectShader(source) }

    fun DrawScope.draw(ms: Float) {
        shader.setFloat2("u_size", card.size.width, card.size.height)
        shader.setFloat("u_px", 1f / density)
        shader.setFloat("u_t", (ms / timing.durationMs).coerceIn(0f, 1f))
        shader.setFloat("u_time", ms / 1000 + seed)
        shader.setFloat("u_sec", ms / 1000)
        shader.setFloat("u_focus", card.circleCenter.x)
        shader.setFloat("u_tau", (ms - timing.fillMs) / 1000)
        drawRect(shader.brush(), size = card.size)
    }
}
