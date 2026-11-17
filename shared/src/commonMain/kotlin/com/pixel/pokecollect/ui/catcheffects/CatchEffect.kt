package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.effects.ElectricEffect
import com.pixel.pokecollect.ui.catcheffects.effects.NormalEffect
import kotlin.random.Random

/** When an effect's moments happen, in ms from the tap. */
@Immutable
data class EffectTiming(
    val durationMs: Int,
    /** The peak: the card presses into its shadow. */
    val peakMs: Int,
    /** When the dex circle takes the type colour: the peak, unless the effect hides the circle then. */
    val fillMs: Int = peakMs,
) {
    /** When the effect, the card's spring back and the circle's pop have all finished. */
    val endMs: Int get() = maxOf(durationMs, peakMs + SETTLE_MS, fillMs + POP_MS)
}

/** How the card itself moves while its effect plays. */
sealed interface CardMotion {
    /**
     * The beat every effect shares: the card lifts toward the top left by [liftDp] (its shadow
     * grows), then presses into its shadow at the peak and springs back. Heavy hits use 0: no lift,
     * just the press.
     */
    data class Beat(val liftDp: Float = 4f) : CardMotion
}

/** One type's catch animation. Stateless: [start] makes the state for one catch. */
interface CatchEffect {
    val timing: EffectTiming
    val motion: CardMotion get() = CardMotion.Beat()

    /** Everything random is decided here, so what's drawn depends only on the time since the tap. */
    fun start(card: CardGeometry, random: Random): EffectRun
}

/**
 * One catch in progress. Every function takes the ms since the tap. Drawing is in dp: the player
 * scales the canvas by the density, so the mockup's numbers carry over as they are.
 */
interface EffectRun {
    /** Between the card's fill and its content (the circle, the name, the chips). */
    fun DrawScope.behind(ms: Float) {}

    /** Over the content. */
    fun DrawScope.inFront(ms: Float) {}

    /** Extra card movement from knocks and quakes, dp. */
    fun shake(ms: Float): Offset = Offset.Zero

    /** A colour the card's fill flashes to, or null for its usual fill. */
    fun cardFill(ms: Float): Color? = null

    /** Whether the dex circle shows the type colour yet. */
    fun circleFilled(ms: Float, timing: EffectTiming): Boolean = ms >= timing.fillMs

    /** The dex circle's scale (x, y): by default a small pop just after it fills. */
    fun circleScale(ms: Float, timing: EffectTiming): Offset = circlePop(ms - timing.fillMs, amount = 0.18f)
}

/**
 * The type whose effect a card plays: its first type, except Normal with a second type, which plays
 * the second one (Kevin, 2026-11-16). Pidgey (normal, flying) gets Flying; Eevee gets Normal.
 */
fun effectTypeFor(type1: String, type2: String?): String = if (type1 == "normal" && type2 != null) type2 else type1

/** The effect for a type, or null for types not built yet: those cards just fill, as before. */
internal fun catchEffectFor(type: String): CatchEffect? = when (type) {
    "normal" -> NormalEffect
    "electric" -> ElectricEffect
    else -> null
}
