package com.pixel.pokecollect.ui.catcheffects

import com.pixel.pokecollect.ui.catcheffects.shaders.AllShaders
import kotlin.test.Test
import kotlin.test.fail

/** Every effect shader compiles as SkSL in Skia, and every uniform the shader layer sets exists. */
class EffectShaderCompileTest {
    @Test
    fun everyEffectShaderCompilesAsSksl() {
        for ((type, source) in AllShaders) {
            val shader = try {
                effectShader(source)
            } catch (e: RuntimeException) {
                fail("$type doesn't compile: ${e.message}")
            }
            shader.setFloat2("u_size", 360f, 84f)
            for (name in listOf("u_px", "u_t", "u_time", "u_sec", "u_focus", "u_tau")) shader.setFloat(name, 0.5f)
            shader.brush()
        }
    }
}
