package com.pixel.pokecollect.ui.catcheffects

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixel.pokecollect.ui.catcheffects.shaders.BrushShaders
import com.pixel.pokecollect.ui.catcheffects.shaders.ContentShaders
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Every effect shader compiles as AGSL, and every uniform its layer sets exists. Needs Android 13+. */
@RunWith(AndroidJUnit4::class)
class EffectShaderCompileTest {
    @Before
    fun needsRuntimeShaders() = assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)

    @Test
    fun everyPaintingShaderCompilesAsAgsl() {
        for ((type, source) in BrushShaders) {
            val shader = compile(type, source)
            shader.setFloat2("u_size", 360f, 84f)
            for (name in listOf("u_px", "u_t", "u_time", "u_sec", "u_focus", "u_tau")) shader.setFloat(name, 0.5f)
            shader.brush()
        }
    }

    @Test
    fun everyContentFilterCompilesAsAgsl() {
        for ((type, source) in ContentShaders) {
            val shader = compile(type, source)
            for (name in listOf("u_dp", "u_t", "u_sec", "u_seed")) shader.setFloat(name, 0.5f)
            shader.renderEffect() // throws if the shader has no `content` child
        }
    }

    private fun compile(type: String, source: String): EffectShader = try {
        effectShader(source)
    } catch (e: IllegalArgumentException) {
        throw AssertionError("$type doesn't compile: ${e.message}", e)
    }
}
