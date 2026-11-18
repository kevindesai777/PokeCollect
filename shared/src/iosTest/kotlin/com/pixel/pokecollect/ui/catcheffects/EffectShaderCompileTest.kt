package com.pixel.pokecollect.ui.catcheffects

import com.pixel.pokecollect.ui.catcheffects.shaders.BrushShaders
import com.pixel.pokecollect.ui.catcheffects.shaders.ContentShaders
import kotlin.test.Test
import kotlin.test.fail

/** Every effect shader compiles as SkSL in Skia, and every uniform its layer sets exists. */
class EffectShaderCompileTest {
    @Test
    fun everyPaintingShaderCompilesAsSksl() {
        for ((type, source) in BrushShaders) {
            val shader = compile(type, source)
            shader.setFloat2("u_size", 360f, 84f)
            for (name in listOf("u_px", "u_t", "u_time", "u_sec", "u_focus", "u_tau")) shader.setFloat(name, 0.5f)
            shader.brush()
        }
    }

    @Test
    fun everyContentFilterCompilesAsSksl() {
        for ((type, source) in ContentShaders) {
            val shader = compile(type, source)
            for (name in listOf("u_dp", "u_t", "u_sec", "u_seed")) shader.setFloat(name, 0.5f)
            shader.renderEffect()
        }
    }

    private fun compile(type: String, source: String): EffectShader = try {
        effectShader(source)
    } catch (e: RuntimeException) {
        fail("$type doesn't compile: ${e.message}")
    }
}
