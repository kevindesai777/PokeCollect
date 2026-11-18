package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CardMotion
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.easeIn
import com.pixel.pokecollect.ui.catcheffects.easeInOut
import com.pixel.pokecollect.ui.catcheffects.easeOut
import com.pixel.pokecollect.ui.catcheffects.easeOutBack
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.lerp
import com.pixel.pokecollect.ui.catcheffects.progress
import com.pixel.pokecollect.ui.catcheffects.starPath
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Fighting: a boxing glove peeks in from the right, winds up, and punches the circle. The card is knocked left. */
internal object FightingEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 950, peakMs = 440)
    override val motion = CardMotion.Beat(liftDp = 0f)

    override fun start(card: CardGeometry, random: Random): EffectRun = FightingRun(card)
}

private val Red = typeFill("fighting")
private val Yellow = typeFill("electric")

/** Where the glove's knuckles are, how squashed it is, and whether it's mid-punch. */
private class GlovePlace(val x: Float, val squash: Float, val punching: Boolean)

private class FightingRun(private val card: CardGeometry) : EffectRun {
    private val peak = FightingEffect.timing.peakMs.toFloat()
    private val hit = Offset(card.circleCenter.x + card.circleRadius + 1, card.circleCenter.y)

    private fun place(ms: Float): GlovePlace {
        val w = card.size.width
        val offscreen = w + 30
        return when {
            ms < 150 -> GlovePlace(lerp(offscreen, w - 56, easeOut(progress(ms, 0f, 150f))), 1f, false) // peeks in
            ms < peak - 150 -> GlovePlace(lerp(w - 56, w - 24, easeInOut(progress(ms, 150f, peak - 150))), 1f, false) // winds up
            ms < peak -> GlovePlace(lerp(w - 24, hit.x, easeIn(progress(ms, peak - 150, peak))), 1f, true) // punches
            ms < peak + 80 -> GlovePlace(hit.x, lerp(0.82f, 1f, progress(ms, peak, peak + 80)), false) // lands
            else -> GlovePlace(lerp(hit.x, offscreen + 30, easeIn(progress(ms, peak + 80, peak + 330))), 1f, false) // pulls back
        }
    }

    override fun DrawScope.inFront(ms: Float) {
        val glove = place(ms)
        val s = ms - peak
        if (s in 0f..320f) {
            val radius = 34 * easeOutBack((s / 110).coerceIn(0f, 1f)) * (1 - easeIn(progress(s, 170f, 320f)))
            inkFill(starPath(hit, radius, radius * 0.58f, points = 9, rotation = 0.2f), Yellow, 2.4f, StrokeJoin.Miter)
            inkFill(starPath(hit, radius * 0.5f, radius * 0.3f, points = 9, rotation = 0.45f), Color.White, 1.8f, StrokeJoin.Miter)
        }
        if (glove.punching) {
            for (dy in listOf(-13f, 0f, 13f)) {
                val from = Offset(glove.x + 80, hit.y + dy)
                drawLine(PokeColors.Ink, from, from + Offset(30 + abs(dy), 0f), 2.5f, StrokeCap.Round)
            }
        }
        if (glove.x < card.size.width + 10) glove(Offset(glove.x, hit.y), scale = 1.1f, squash = glove.squash)
    }

    override fun shake(ms: Float): Offset {
        val s = ms - peak
        if (s < 0f) return Offset.Zero
        val k = exp(-s / 110)
        return Offset(-7 * k * cos(s * 0.06f), 1.5f * k * sin(s * 0.17f))
    }
}

/** A boxing glove with its knuckles at [knuckles], facing left. At [scale] 1 it is 42 dp tall. */
private fun DrawScope.glove(knuckles: Offset, scale: Float, squash: Float) {
    withTransform({
        translate(knuckles.x, knuckles.y)
        scale(scale * squash, scale, Offset.Zero)
    }) {
        val line = 2.2f / scale
        inkFill(Path().apply { addRoundRect(RoundRect(40f, -13f, 66f, 13f, CornerRadius(5f))) }, Color.White, line) // cuff
        drawLine(PokeColors.Ink, Offset(54f, -13f), Offset(54f, 13f), line * 0.8f)
        inkFill(Path().apply { addRoundRect(RoundRect(0f, -21f, 48f, 21f, CornerRadius(19f))) }, Red, line) // mitt
        inkFill(Path().apply { addOval(Rect(13f, -23f, 41f, -7f)) }, Red, line) // thumb
        drawPath(
            Path().apply {
                moveTo(10f, -6f)
                quadraticTo(14f, 0f, 10f, 6f)
            },
            PokeColors.Ink,
            style = Stroke(line * 0.75f, cap = StrokeCap.Round),
        )
        drawOval(Color.White, topLeft = Offset(12f, 5.8f), size = Size(16f, 6.4f)) // shine
    }
}
