package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.pixel.pokecollect.ui.catcheffects.effects.ElectricRun
import com.pixel.pokecollect.ui.catcheffects.shaders.BrushShaders
import com.pixel.pokecollect.ui.catcheffects.shaders.ContentShaders
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatchEffectTest {
    private val card = CardGeometry(Size(360f, 84f), circleCenter = Offset(44f, 42f), circleRadius = 28f)

    @Test
    fun theFirstTypeChoosesTheEffect() {
        assertEquals("grass", effectTypeFor("grass", "poison")) // Bulbasaur
        assertEquals("electric", effectTypeFor("electric", null)) // Pikachu
    }

    @Test
    fun normalWithASecondTypePlaysTheSecond() {
        assertEquals("flying", effectTypeFor("normal", "flying")) // Pidgey
        assertEquals("fairy", effectTypeFor("normal", "fairy")) // Jigglypuff
    }

    @Test
    fun pureNormalPlaysNormal() {
        assertEquals("normal", effectTypeFor("normal", null)) // Eevee
    }

    @Test
    fun builtEffectsKeepToTheirTimings() {
        for (type in Built) {
            val timing = assertNotNull(catchEffectFor(type), type).timing
            assertTrue(timing.peakMs in 1 until timing.durationMs, type)
            assertTrue(timing.fillMs <= timing.durationMs, type)
            assertTrue(timing.durationMs <= 1500, "$type runs longer than 1.5 s")
            assertTrue(timing.endMs >= timing.durationMs, type)
        }
    }

    @Test
    fun everyBuiltEffectStartsAndDrawsItsFirstMomentsWithoutCrashing() {
        // start() makes all the randomness; shake and the circle's state are plain functions of time.
        for (type in Built) {
            val effect = assertNotNull(catchEffectFor(type), type)
            val run = effect.start(card, Random(7))
            for (ms in listOf(0f, effect.timing.peakMs.toFloat(), effect.timing.durationMs.toFloat())) {
                val shake = run.shake(ms)
                assertTrue(shake.x.isFinite() && shake.y.isFinite(), "$type shake at $ms")
                run.circleScale(ms, effect.timing)
            }
            assertTrue(run.circleFilled(effect.timing.durationMs.toFloat(), effect.timing), "$type fills by its end")
        }
    }

    @Test
    fun everyTypeHasAnEffect() {
        for (type in Built) assertNotNull(catchEffectFor(type), type)
        assertEquals(18, Built.toSet().size)
        assertEquals(null, catchEffectFor("stellar")) // a type the app doesn't know just fills
    }

    @Test
    fun onlyPsychicBendsTheContent() {
        for (type in Built) {
            val run = assertNotNull(catchEffectFor(type), type).start(card, Random(3))
            assertEquals(type == "psychic", run.warpsContent, type)
        }
    }

    @Test
    fun theGhostCircleFlickersBeforeItStaysFilled() {
        val effect = assertNotNull(catchEffectFor("ghost"))
        val run = effect.start(card, Random(1))
        val fill = effect.timing.fillMs.toFloat()
        val states = listOf(fill - 1, fill + 10, fill + 70, fill + 120, fill + 300).map { run.circleFilled(it, effect.timing) }
        assertEquals(listOf(false, true, false, true, true), states)
    }

    @Test
    fun everyShaderKeepsToWhatAgslAndSkslShare() {
        for ((type, source) in BrushShaders + ContentShaders) {
            val code = source.lines().joinToString("\n") { it.substringBefore("//") } // comments may mention them
            assertTrue("half4 main(float2 p)" in code, "$type has the shared entry point")
            for (word in listOf("fwidth", "dFdx", "dFdy", "gl_FragCoord", "texture2D", "vec2", "vec3", "vec4", "precision")) {
                assertFalse(Regex("\\b$word\\b").containsMatchIn(code), "$type uses $word")
            }
        }
    }

    @Test
    fun everyShaderDeclaresTheUniformsItsLayerSets() {
        for ((type, source) in BrushShaders) {
            for (uniform in listOf("u_size", "u_px", "u_t", "u_time", "u_sec", "u_focus", "u_tau")) {
                assertTrue(Regex("uniform float2? $uniform;").containsMatchIn(source), "$type declares $uniform")
            }
        }
        for ((type, source) in ContentShaders) {
            assertTrue("uniform shader $CONTENT_SHADER;" in source, "$type reads the content")
            for (uniform in listOf("u_dp", "u_t", "u_sec", "u_seed")) {
                assertTrue("uniform float $uniform;" in source, "$type declares $uniform")
            }
        }
    }

    @Test
    fun heavyHitsAndFlyingSkipTheLift() {
        for (type in listOf("fighting", "rock", "ground", "steel", "dragon", "dark")) {
            assertEquals(CardMotion.Beat(liftDp = 0f), catchEffectFor(type)?.motion, type)
        }
        assertEquals(CardMotion.Hover, catchEffectFor("flying")?.motion)
    }

    @Test
    fun aHoveringCardLeavesItsShadowOnTheGround() {
        val timing = EffectTiming(1500, 700)
        for (ms in listOf(200f, 700f, 1100f)) {
            val pose = cardPose(CardMotion.Hover, timing, NormalRunLike(), ms)
            assertTrue(pose.offset.y < 0f, "up at $ms")
            assertEquals(SHADOW_DP, pose.offset.y + pose.shadow.y, absoluteTolerance = 0.001f)
        }
        assertEquals(0f, cardPose(CardMotion.Hover, timing, NormalRunLike(), 1500f).offset.getDistance(), absoluteTolerance = 0.001f)
    }

    @Test
    fun theCardLiftsThenPressesThenRests() {
        val peak = 140
        assertEquals(0f, lift(0f, peak, liftDp = 4f))
        assertEquals(4f, lift(peak.toFloat(), peak, liftDp = 4f))
        assertEquals(-SHADOW_DP, lift(peak + 80f, peak, liftDp = 4f)) // pressed flat into the shadow
        assertEquals(0f, lift(peak + SETTLE_MS.toFloat(), peak, liftDp = 4f))
    }

    @Test
    fun heavyHitsDontLiftFirst() {
        assertEquals(0f, lift(100f, 300, liftDp = 0f))
        assertEquals(-SHADOW_DP, lift(380f, 300, liftDp = 0f))
    }

    @Test
    fun theShadowStaysPutWhileTheCardLifts() {
        val run = NormalRunLike()
        val pose = cardPose(CardMotion.Beat(4f), EffectTiming(650, 140), run, 140f)
        assertEquals(Offset(-4f, -4f), pose.offset)
        // Card up 4, shadow out 7: the shadow's absolute place is still 3 from rest.
        assertEquals(SHADOW_DP, pose.offset.x + pose.shadow.x)
    }

    @Test
    fun aZigzagKeepsItsEndsAndRepeatsForTheSameSeed() {
        val a = zigzag(Random(3), Offset(0f, 0f), Offset(100f, 50f), segments = 5, amplitude = 9f)
        val b = zigzag(Random(3), Offset(0f, 0f), Offset(100f, 50f), segments = 5, amplitude = 9f)
        assertEquals(a, b)
        assertEquals(Offset(0f, 0f), a.first())
        assertEquals(Offset(100f, 50f), a.last())
        assertEquals(6, a.size)
    }

    @Test
    fun theSameCatchDrawsTheSameBolt() {
        val first = ElectricRun(card, Random(42))
        val again = ElectricRun(card, Random(42))
        assertEquals(first.bolt(0, 1, 1f), again.bolt(0, 1, 1f))
        // The next flicker window draws a different bolt, so it flickers.
        assertNotEquals(first.bolt(0, 1, 1f), first.bolt(0, 2, 1f))
        // Every bolt ends on the circle.
        assertEquals(card.circleCenter, first.bolt(1, 0, 0.7f).first.last())
    }

    @Test
    fun theCirclePopsThenSettles() {
        assertEquals(Offset(1f, 1f), circlePop(0f, 0.18f))
        assertEquals(1.18f, circlePop(POP_MS / 2f, 0.18f).x, absoluteTolerance = 0.001f)
        assertEquals(Offset(1f, 1f), circlePop(POP_MS.toFloat(), 0.18f))
    }

    private class NormalRunLike : EffectRun

    private companion object {
        val Built = listOf(
            "normal", "electric", "poison", "fighting", "rock", "ground", "steel", "grass", "bug", "fairy", "dragon", "dark", "flying",
            "fire", "water", "ice", "ghost", "psychic",
        )
    }
}
