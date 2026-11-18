package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.TAU
import com.pixel.pokecollect.ui.catcheffects.degrees
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.lerp
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.theme.PokeColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Bug: ladybugs spiral in and circle the circle, then open their wing cases and fly off. */
internal object BugEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1250, peakMs = 620)

    override fun start(card: CardGeometry, random: Random): EffectRun = BugRun(card, random)
}

/** How fast the ladybugs circle, radians per second. */
private const val CIRCLING = 3.4f

private class Ladybug(val angle: Float, val startRadius: Float)

private class BugRun(private val card: CardGeometry, random: Random) : EffectRun {
    private val peak = BugEffect.timing.peakMs.toFloat()
    private val bugs = List(6) { i -> Ladybug(angle = i / 6f * TAU + random.nextFloat() * 0.4f, startRadius = 150 + random.nextFloat() * 60) }

    private fun position(bug: Ladybug, ms: Float): Offset {
        val c = card.circleCenter
        val ring = card.circleRadius + 14
        if (ms < peak) {
            // Spiral in from far out: the orbit starts flat (to fit the card) and rounds out.
            val k = easeOut(progress(ms, 0f, peak))
            val radius = lerp(bug.startRadius, ring, k)
            val angle = bug.angle + CIRCLING * ms / 1000
            return Offset(c.x + cos(angle) * radius, c.y + sin(angle) * radius * lerp(0.4f, 1f, k))
        }
        // Fly off, still turning a little, faster and faster.
        val s = (ms - peak) / 1000
        val angle = bug.angle + CIRCLING * peak / 1000 + 1.5f * s
        val radius = ring + 200 * s + 500 * s * s
        return Offset(c.x + cos(angle) * radius, c.y + sin(angle) * radius * lerp(1f, 0.6f, min(1f, s * 3)))
    }

    override fun DrawScope.inFront(ms: Float) {
        val open = easeOut(progress(ms, peak, peak + 150))
        bugs.forEachIndexed { i, bug ->
            val at = position(bug, ms)
            val ahead = if (ms < 16f) position(bug, ms + 16) else at + (at - position(bug, ms - 16))
            val heading = atan2(ahead.y - at.y, ahead.x - at.x)
            withTransform({
                translate(at.x, at.y)
                rotate(degrees(heading), Offset.Zero)
                scale(1.4f, 1.4f, Offset.Zero)
            }) {
                ladybug(ms, i, open)
            }
        }
    }
}

/** One ladybug facing right, about 16 dp long before scaling. [open] 0..1 lifts its wing cases. */
private fun DrawScope.ladybug(ms: Float, index: Int, open: Float) {
    if (open < 0.5f) {
        for (side in listOf(-1f, 1f)) {
            for (j in -1..1) {
                val wiggle = sin(ms * 0.06f + index * 2 + j * 2) * 2 * side
                drawLine(PokeColors.Ink, Offset(j * 3.2f, side * 3), Offset(j * 4.2f + wiggle, side * 8), 1.5f, StrokeCap.Round)
            }
        }
    }
    if (open > 0f) {
        for (side in listOf(-1f, 1f)) {
            val flap = 0.6f + 0.4f * sin(ms * 0.5f + side)
            val center = Offset(-3f, side * 6 * open)
            rotate(degrees(side * 0.5f * open), center) {
                inkFill(Path().apply { addOval(Rect(center.x - 8, center.y - 3.4f * flap, center.x + 8, center.y + 3.4f * flap)) }, Color.White, 1.2f)
            }
        }
    }
    // The two halves of the red shell, hinged at the front.
    for (side in listOf(-1f, 1f)) {
        rotate(degrees(side * 0.55f * open), Offset(4f, 0f)) {
            val half = Path().apply {
                addArc(Rect(-8f, -6f, 6f, 6f), if (side > 0) 0f else 180f, 180f)
                close()
            }
            inkFill(half, PokeColors.Error, 1.7f)
            for ((x, y, r) in listOf(Triple(-3.5f, 2.8f, 1.5f), Triple(1f, 3.4f, 1.2f), Triple(-0.5f, 1.2f, 0.9f))) {
                drawCircle(PokeColors.Ink, r, Offset(x, side * y))
            }
        }
    }
    val head = Path().apply {
        addArc(Rect(5.5f - 3.8f, -3.8f, 5.5f + 3.8f, 3.8f), -90f, 180f)
        close()
    }
    drawPath(head, PokeColors.Ink)
    for (side in listOf(-1f, 1f)) drawCircle(Color.White, 0.9f, Offset(7.2f, side * 1.7f))
}
