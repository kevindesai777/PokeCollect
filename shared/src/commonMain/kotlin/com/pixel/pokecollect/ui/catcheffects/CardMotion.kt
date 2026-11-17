package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min
import kotlin.math.sin

/** The card when its effect starts, in dp: its size and the dex circle. */
@Immutable
data class CardGeometry(val size: Size, val circleCenter: Offset, val circleRadius: Float)

/** brutal()'s shadow offset, dp. */
internal const val SHADOW_DP = 3f

/** How long after the peak the card takes to spring back to rest, ms. */
internal const val SETTLE_MS = 330

/** How long the circle's pop lasts after it fills, ms. */
internal const val POP_MS = 260

/** Where the card is, where its shadow falls (both dp from rest), and its tilt in degrees. */
@Immutable
internal data class CardPose(val offset: Offset, val shadow: Offset, val rotation: Float = 0f) {
    companion object {
        val Rest = CardPose(Offset.Zero, Offset(SHADOW_DP, SHADOW_DP))
    }
}

/**
 * How far the card is lifted toward the top left at [ms], dp. It rises to [liftDp] before the peak,
 * drops to -[SHADOW_DP] (pressed flat into its shadow) just after it, holds, then springs back to 0
 * with a small overshoot.
 */
internal fun lift(ms: Float, peakMs: Int, liftDp: Float): Float {
    if (ms < peakMs) return liftDp * easeOut(min(1f, ms / min(peakMs, 220)))
    val s = ms - peakMs
    return when {
        s < 50 -> lerp(liftDp, -SHADOW_DP, s / 50f)
        s < 110 -> -SHADOW_DP
        s < SETTLE_MS -> -SHADOW_DP + SHADOW_DP * easeOutBack((s - 110) / (SETTLE_MS - 110f))
        else -> 0f
    }
}

/**
 * The card's pose at [ms]. Lifting by d moves the card up and left by d and its shadow down and
 * right by d, so the shadow stays where it was and only the gap grows. Shakes move both.
 */
internal fun cardPose(motion: CardMotion, timing: EffectTiming, run: EffectRun, ms: Float): CardPose = when (motion) {
    is CardMotion.Beat -> {
        val d = lift(ms, timing.peakMs, motion.liftDp)
        val shake = run.shake(ms)
        CardPose(Offset(-d + shake.x, -d + shake.y), Offset(SHADOW_DP + d, SHADOW_DP + d))
    }
    CardMotion.Hover -> {
        val end = timing.durationMs.toFloat()
        val up = HOVER_DP * easeInOut(progress(ms, 0f, 420f)) * (1 - easeInOut(progress(ms, end - 420, end)))
        val k = up / HOVER_DP
        val y = -(up + 2.5f * sin(ms / 140) * k)
        // The card moves up by -y; its shadow moves down by as much, so it stays on the ground.
        CardPose(Offset(3 * sin(ms / 260) * k, y), Offset(SHADOW_DP, SHADOW_DP - y), rotation = 1.6f * sin(ms / 200) * k)
    }
}

/** How high a hovering card floats, dp. */
private const val HOVER_DP = 9f
