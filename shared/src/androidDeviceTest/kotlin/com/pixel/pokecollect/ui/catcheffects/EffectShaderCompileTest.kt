package com.pixel.pokecollect.ui.catcheffects

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixel.pokecollect.ui.catcheffects.shaders.AllShaders
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Every effect shader compiles as AGSL, and every uniform the shader layer sets exists. Needs Android 13+. */
@RunWith(AndroidJUnit4::class)
class EffectShaderCompileTest {
    @Test
    fun everyEffectShaderCompilesAsAgsl() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        for ((type, source) in AllShaders) {
            val shader = try {
                effectShader(source)
            } catch (e: IllegalArgumentException) {
                throw AssertionError("$type doesn't compile: ${e.message}", e)
            }
            shader.setUniformsLikeTheLayer()
            shader.brush()
        }
    }
}

private fun EffectShader.setUniformsLikeTheLayer() {
    setFloat2("u_size", 360f, 84f)
    for (name in listOf("u_px", "u_t", "u_time", "u_sec", "u_focus", "u_tau")) setFloat(name, 0.5f)
}
