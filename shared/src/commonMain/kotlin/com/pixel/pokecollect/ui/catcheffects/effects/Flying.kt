package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CardMotion
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.leaf
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.catcheffects.puffs
import com.pixel.pokecollect.ui.catcheffects.taperPath
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flying: the card floats up into the air. Clouds drift past beneath it at different speeds, straight
 * streaks of wind rush by, and feathers flutter down. (The first design's curly wind streaks read as
 * waves, Kevin 2026-11-16; these are straight.)
 */
internal object FlyingEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1500, peakMs = 700)
    override val motion = CardMotion.Hover

    override fun start(card: CardGeometry, random: Random): EffectRun = FlyingRun(card, random)
}

private val Sky = typeFill("flying")

/** One cloud: overlapping circles round its centre (offset, radius), outlined as one shape. */
private val CloudShape = listOf(
    Offset(-15f, 4f) to 7f,
    Offset(-7f, -2f) to 9f,
    Offset(4f, -6f) to 11f,
    Offset(15f, -1f) to 9f,
    Offset(3f, 4f) to 8f,
    Offset(23f, 5f) to 6f,
)

/** A cloud drifting left at [speed] dp/s: the card is flying right, past it. */
private class Cloud(val start: Offset, val scale: Float, val speed: Float)

/** A straight streak of wind rushing right. */
private class Gust(val y: Float, val bornMs: Float, val length: Float)

private class Feather(val x: Float, val bornMs: Float, val phase: Float, val length: Float)

private class FlyingRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val w = card.size.width
    private val h = card.size.height
    private val end = FlyingEffect.timing.durationMs.toFloat()

    // Spread across and beyond the right edge, fast enough to stream past rather than sit there.
    private val clouds = List(4) { i ->
        Cloud(
            start = Offset(w * (0.25f + i * 0.3f) + random.nextFloat() * 30, h * (0.3f + 0.55f * random.nextFloat())),
            scale = 1f + random.nextFloat() * 0.5f,
            speed = 150 + random.nextFloat() * 120,
        )
    }
    private val gusts = List(6) { i -> Gust(y = h * (0.15f + 0.7f * random.nextFloat()), bornMs = i * 150f + random.nextFloat() * 80, length = 40 + random.nextFloat() * 35) }
    private val feathers = List(4) { i ->
        Feather(card.circleCenter.x + 30 + i * 55f + random.nextFloat() * 20, 350 + i * 110f, random.nextFloat() * 6, 22 + random.nextFloat() * 5)
    }

    override fun DrawScope.behind(ms: Float) {
        // Quick in, quick out: a cloud that shrinks slowly turns into a cluster of bubbles.
        val life = easeOut(progress(ms, 0f, 180f)) * (1 - easeIn(progress(ms, end - 220, end)))
        for (cloud in clouds) {
            val center = cloud.start - Offset(cloud.speed * ms / 1000, 0f)
            puffs(CloudShape.map { (offset, radius) -> center + offset * cloud.scale to radius * cloud.scale * life }, Color.White, outline = 3.5f)
        }
    }

    override fun DrawScope.inFront(ms: Float) {
        for (gust in gusts) {
            val t = ms - gust.bornMs
            if (t < 0f || ms > end - 200) continue
            val head = Offset(-20 + t * 0.8f, gust.y - t * 0.01f) // rising very slightly
            if (head.x - gust.length > w) continue
            val tail = head - Offset(gust.length, -gust.length * 0.08f)
            // Thin at the tail, thickest just behind the head: a speed line, not a wave.
            inkFill(taperPath(listOf(tail, (tail + head) / 2f, head), 0.4f, 3.6f), Color.White, 1.4f)
        }
        val shrink = 1 - easeIn(progress(ms, end - 240, end))
        for ((i, feather) in feathers.withIndex()) {
            val t = (ms - feather.bornMs) / 1000
            if (t < 0f) continue
            val sway = sin(t * 4 + feather.phase)
            val at = Offset(feather.x + sway * 12, -12 + t * 48)
            val angle = PI.toFloat() / 2 + sway * 0.7f + i * 0.1f
            leaf(at, feather.length * shrink, angle, Sky, widthRatio = 0.38f)
            // The quill sticking out at the base.
            if (shrink > 0.1f) drawLine(PokeColors.Ink, at, at - Offset(cos(angle), sin(angle)) * 4f, 1.4f)
        }
    }
}
