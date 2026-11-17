package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.pixel.pokecollect.ui.catcheffects.effects.ElectricRun
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
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
        for (type in listOf("normal", "electric")) {
            val timing = assertNotNull(catchEffectFor(type), type).timing
            assertTrue(timing.peakMs in 1 until timing.durationMs, type)
            assertTrue(timing.fillMs <= timing.durationMs, type)
            assertTrue(timing.durationMs <= 1500, "$type runs longer than 1.5 s")
            assertTrue(timing.endMs >= timing.durationMs, type)
        }
    }

    @Test
    fun typesNotBuiltYetJustFill() {
        assertEquals(null, catchEffectFor("fire"))
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
}
