package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect

/**
 * A runtime shader for an effect. One source string compiles on both platforms: as AGSL through
 * Android's RuntimeShader (API 33+, the reason effects need Android 13) and as SkSL through Skia's
 * RuntimeEffect on iOS. Only this wrapper differs per platform.
 *
 * The sources keep to what both languages share: `half4 main(float2 p)`, float2/float3/float4 types,
 * no derivatives (so no fwidth: outlines use one-pixel differences), and `p` in local coordinates
 * with the origin at the top left.
 *
 * A shader is used one of two ways. Most paint the card ([brush]). One that declares
 * `uniform shader content` filters what's already drawn instead ([renderEffect]): it reads the
 * drawn pixels with `content.eval(p)`, which is how psychic bends the card.
 */
internal interface EffectShader {
    fun setFloat(name: String, value: Float)

    fun setFloat2(name: String, x: Float, y: Float)

    /** A brush for this frame, with the uniforms as they are now. */
    fun brush(): Brush

    /**
     * A filter for a graphics layer, with the uniforms as they are now. The layer's drawing is the
     * shader's `content`, and `p` is in the layer's pixels.
     */
    fun renderEffect(): RenderEffect
}

/** The child shader a filtering shader reads the drawn content from. */
internal const val CONTENT_SHADER = "content"

/** Compiles [source]. Throws if it doesn't compile, so a bad shader fails loudly in the compile tests. */
internal expect fun effectShader(source: String): EffectShader
