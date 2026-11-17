package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.graphics.Brush

/**
 * A runtime shader for an effect. One source string compiles on both platforms: as AGSL through
 * Android's RuntimeShader (API 33+, the reason effects need Android 13) and as SkSL through Skia's
 * RuntimeEffect on iOS. Only this wrapper differs per platform.
 *
 * The sources keep to what both languages share: `half4 main(float2 p)`, float2/float3/float4 types,
 * no derivatives (so no fwidth: outlines use one-pixel differences), and `p` in local coordinates
 * with the origin at the top left.
 */
internal interface EffectShader {
    fun setFloat(name: String, value: Float)

    fun setFloat2(name: String, x: Float, y: Float)

    /** A brush for this frame, with the uniforms as they are now. */
    fun brush(): Brush
}

/** Compiles [source]. Throws if it doesn't compile, so a bad shader fails loudly in the compile tests. */
internal expect fun effectShader(source: String): EffectShader
