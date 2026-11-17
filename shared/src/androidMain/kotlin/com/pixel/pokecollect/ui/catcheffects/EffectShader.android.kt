package com.pixel.pokecollect.ui.catcheffects

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect

// Effects only play where catchEffectsSupported is true: API 33 and newer.
@SuppressLint("NewApi")
internal actual fun effectShader(source: String): EffectShader {
    check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { "Runtime shaders need Android 13" }
    return AgslShader(source)
}

/**
 * AGSL through RuntimeShader. One shader object is reused: setting a uniform marks it changed, and
 * the next draw picks up a fresh native shader, so one brush serves every frame.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslShader(source: String) : EffectShader {
    private val shader = RuntimeShader(source)
    private val brush = ShaderBrush(shader)

    override fun setFloat(name: String, value: Float) = shader.setFloatUniform(name, value)

    override fun setFloat2(name: String, x: Float, y: Float) = shader.setFloatUniform(name, x, y)

    override fun brush(): Brush = brush

    // A RenderEffect copies the shader's uniforms when it's made, so each frame makes its own.
    override fun renderEffect(): RenderEffect =
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, CONTENT_SHADER).asComposeRenderEffect()
}
