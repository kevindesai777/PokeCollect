package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun effectShader(source: String): EffectShader = SkslShader(source)

/**
 * SkSL through Skia, which Compose draws with on iOS. A Skia shader is immutable, so each frame
 * makes a new one from the builder with that frame's uniforms.
 */
private class SkslShader(source: String) : EffectShader {
    private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))

    override fun setFloat(name: String, value: Float) = builder.uniform(name, value)

    override fun setFloat2(name: String, x: Float, y: Float) = builder.uniform(name, x, y)

    override fun brush(): Brush = ShaderBrush(builder.makeShader().asComposeShader())
}
