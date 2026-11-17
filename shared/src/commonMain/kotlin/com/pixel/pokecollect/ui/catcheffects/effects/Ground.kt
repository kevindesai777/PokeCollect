package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CardMotion
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.ballistic
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeInOut
import com.pixel.pokecollect.ui.catcheffects.impact
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.lerp
import com.pixel.pokecollect.ui.catcheffects.popLife
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.catcheffects.puffs
import com.pixel.pokecollect.ui.catcheffects.rockPath
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ground: something digs along under the card from the right, kicking up dirt while the card
 * rumbles, then bursts up under the circle in a spray of clods and dust.
 */
internal object GroundEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1250, peakMs = 600)
    override val motion = CardMotion.Beat(liftDp = 0f)

    override fun start(card: CardGeometry, random: Random): EffectRun = GroundRun(card, random)
}

private val Earth = typeFill("ground")
private val Dust = typeFill("normal")
private val Dark = typeFill("dark")
private val Stone = typeFill("rock")

private class Clod(val bornMs: Float, val start: Offset, val velocity: Offset, val size: Float, val corners: FloatArray, val fill: Color)

private class GroundRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val w = card.size.width
    private val h = card.size.height
    private val peak = GroundEffect.timing.peakMs.toFloat()

    /** Where the digger is under the bottom edge: from off the right side to under the circle. */
    private fun moundX(ms: Float) = lerp(w + 28, card.circleCenter.x, easeInOut(progress(ms, 40f, peak)))

    private val clods: List<Clod> = buildList {
        // Kicked up behind the digger as it goes.
        var t = 70f
        var k = 0
        while (t <= peak - 30) {
            add(
                Clod(
                    bornMs = t,
                    start = Offset(moundX(t) + 6, h - 12),
                    velocity = Offset(60 + random.nextFloat() * 110, -(160 + random.nextFloat() * 140)),
                    size = 2.5f + random.nextFloat() * 2,
                    corners = FloatArray(5) { random.nextFloat() },
                    fill = if (k % 2 == 1) Earth else Dark,
                ),
            )
            t += 38
            k++
        }
        // The burst under the circle.
        repeat(15) { i ->
            add(
                Clod(
                    bornMs = peak,
                    start = Offset(card.circleCenter.x + (random.nextFloat() - 0.5f) * 30, h - 8),
                    velocity = Offset((random.nextFloat() - 0.5f) * 320, -(300 + random.nextFloat() * 260)),
                    size = 3 + random.nextFloat() * 3.5f,
                    corners = FloatArray(6) { random.nextFloat() },
                    fill = listOf(Earth, Stone, Dark)[i % 3],
                ),
            )
        }
    }

    private val cloud = List(9) { Triple((random.nextFloat() - 0.5f) * 90, -random.nextFloat() * 16, 9 + random.nextFloat() * 9) }

    override fun DrawScope.behind(ms: Float) {
        val mx = moundX(ms)
        // The churned-up trail behind the digger sinks back after the burst.
        val trail = 6 * (1 - easeIn(progress(ms, peak + 60, peak + 420)))
        if (ms > 40f && trail > 0.3f) {
            val path = Path().apply {
                moveTo(mx, h + 6)
                var x = mx
                while (x <= w + 10) {
                    lineTo(x, h - trail - 2.2f * sin(x * 0.16f) - 1.2f * sin(x * 0.41f))
                    x += 4
                }
                lineTo(w + 10, h + 6)
                close()
            }
            inkFill(path, Earth)
        }
        // The mound over whatever is digging.
        if (ms < peak + 20) {
            val bob = 1.5f * sin(ms * 0.08f)
            val mound = Path().apply {
                addArc(Rect(mx - 28, h + 2 - (18 + bob), mx + 28, h + 2 + (18 + bob)), 180f, 180f)
                close()
            }
            inkFill(mound, Earth, 2.2f)
            for ((dx, dy, r) in listOf(Triple(-9f, -6f, 2f), Triple(4f, -9f, 1.6f), Triple(10f, -4f, 1.8f), Triple(-2f, -2f, 1.4f))) {
                drawCircle(Dark, r, Offset(mx + dx, h + dy + bob * 0.5f))
            }
        }
        // Dust where it breaks out.
        val s = ms - peak
        if (s in 0f..620f) {
            val k = popLife(s, 620f)
            puffs(cloud.map { (dx, dy, r) -> Offset(card.circleCenter.x + dx * (0.7f + s / 600), h - 4 + dy - s * 0.02f) to r * k }, Dust)
        }
    }

    override fun DrawScope.inFront(ms: Float) {
        for (c in clods) {
            val age = (ms - c.bornMs) / 1000
            if (age < 0f) continue
            val at = ballistic(c.start, c.velocity, age, 1500f)
            if (at.y > h + 10) continue
            inkFill(rockPath(at, c.size, c.corners, age * 8), c.fill, 1.6f)
        }
    }

    // A rumble while it digs, then a jolt when it bursts up.
    override fun shake(ms: Float): Offset {
        if (ms >= peak) return impact(ms, peak, 5f, 90f)
        val k = min(1f, ms / 120)
        return Offset(1.3f * k * sin(ms * 0.21f), 0.9f * k * sin(ms * 0.33f))
    }
}
