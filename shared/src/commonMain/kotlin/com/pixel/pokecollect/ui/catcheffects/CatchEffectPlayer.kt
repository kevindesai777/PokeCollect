package com.pixel.pokecollect.ui.catcheffects

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pixel.pokecollect.ui.theme.PokeColors
import kotlinx.coroutines.currentCoroutineContext
import kotlin.random.Random

/**
 * Plays one card's catch effect. There is one per row, kept per card id. Everything that changes
 * every frame is read only inside draw and layer lambdas, so a playing effect never recomposes the
 * row; the row recomposes when the effect starts, when the circle fills, and when it ends.
 */
@Stable
internal class CatchEffectPlayer(caught: Boolean) {
    /** The caught value last acted on. A change from false to true is a catch. */
    private var settledCaught by mutableStateOf(caught)

    /** This card's effect if it would play now (switch on, type built), set on every composition. */
    internal var available: CatchEffect? = null

    private var effect: CatchEffect? by mutableStateOf(null)
    private var run: EffectRun? by mutableStateOf(null)
    private var ms by mutableFloatStateOf(0f)

    internal var cardCoordinates: LayoutCoordinates? = null
    internal var circleCoordinates: LayoutCoordinates? = null

    /** True while an effect plays. */
    val isPlaying: Boolean get() = run != null

    // Recomputed every frame, but only tells readers when it flips: once per catch.
    private val beforeFill by derivedStateOf {
        val e = effect
        val r = run
        e != null && r != null && !r.circleFilled(ms, e.timing)
    }

    /**
     * Whether the dex circle of a card that is [caught] shows its colour. A card ticked this very
     * frame doesn't yet: its effect starts on the next frame and fills the circle at its peak.
     */
    fun circleFilled(caught: Boolean): Boolean {
        if (!caught) return false
        val justTicked = !settledCaught && available != null
        return !justTicked && !beforeFill
    }

    /** Acts on [caught]: plays [effect] if the card was just ticked. Suspends while it plays. */
    suspend fun settle(caught: Boolean, effect: CatchEffect?, density: Density) {
        val wasCaught = settledCaught
        settledCaught = caught
        if (caught && !wasCaught && effect != null) play(effect, density)
    }

    private suspend fun play(effect: CatchEffect, density: Density) {
        val card = geometry(density) ?: return
        val run = effect.start(card, Random(Random.nextInt()))
        // Developer options' animator duration scale slows effects down too: free slow motion.
        val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor?.takeIf { it > 0f } ?: 1f
        this.effect = effect
        this.run = run
        ms = 0f
        try {
            val start = withFrameNanos { it }
            while (ms < effect.timing.endMs) {
                ms = (withFrameNanos { it } - start) / 1_000_000f / scale
            }
        } finally {
            // Also runs when the card is un-ticked mid-effect or scrolls away.
            this.run = null
            this.effect = null
            ms = 0f
        }
    }

    private fun geometry(density: Density): CardGeometry? {
        val card = cardCoordinates?.takeIf { it.isAttached } ?: return null
        val circle = circleCoordinates?.takeIf { it.isAttached } ?: return null
        val topLeft = card.localPositionOf(circle, Offset.Zero)
        val d = density.density
        return CardGeometry(
            size = Size(card.size.width / d, card.size.height / d),
            circleCenter = Offset((topLeft.x + circle.size.width / 2f) / d, (topLeft.y + circle.size.height / 2f) / d),
            circleRadius = circle.size.width / 2f / d,
        )
    }

    fun pose(): CardPose {
        val e = effect ?: return CardPose.Rest
        val r = run ?: return CardPose.Rest
        return cardPose(e.motion, e.timing, r, ms)
    }

    fun circleScale(): Offset {
        val e = effect ?: return Offset(1f, 1f)
        val r = run ?: return Offset(1f, 1f)
        return r.circleScale(ms, e.timing)
    }

    fun cardFill(): Color? = run?.cardFill(ms)

    fun DrawScope.drawEffectBehind() {
        val r = run ?: return
        val t = ms
        inDp { with(r) { behind(t) } }
    }

    fun DrawScope.drawEffectInFront() {
        val r = run ?: return
        val t = ms
        inDp { with(r) { inFront(t) } }
    }
}

/** Effects draw in dp, so the canvas is scaled by the density first. */
private inline fun DrawScope.inDp(crossinline block: DrawScope.() -> Unit) {
    val d = density
    withTransform({ scale(d, d, Offset.Zero) }) { block() }
}

/**
 * The player for one card's row. Ticking the card while the row is on screen plays its effect;
 * a card that is already caught when the row appears never plays (scrolling, filters, launch).
 */
@Composable
internal fun rememberCatchEffectPlayer(pokemonId: Int, caught: Boolean, type1: String, type2: String?): CatchEffectPlayer {
    val player = remember(pokemonId) { CatchEffectPlayer(caught) }
    val effect = if (LocalCatchEffects.current) catchEffectFor(effectTypeFor(type1, type2)) else null
    player.available = effect
    val density = LocalDensity.current
    // Restarts on every change of caught; un-ticking cancels a playing effect.
    LaunchedEffect(player, caught) { player.settle(caught, effect, density) }
    return player
}

/**
 * brutal() for a card that can play a catch effect: the same hard shadow, fill and ink border.
 * While an effect plays, the card moves, its shadow follows the beat, and the effect draws behind
 * and over the card's content, clipped to the card.
 */
internal fun Modifier.catchEffectCard(player: CatchEffectPlayer, fill: Color, shape: Shape): Modifier = this
    .graphicsLayer {
        val pose = player.pose()
        translationX = pose.offset.x * density
        translationY = pose.offset.y * density
        rotationZ = pose.rotation
    }
    .onPlaced { player.cardCoordinates = it }
    .dropShadow(shape) {
        val pose = player.pose()
        radius = 0f
        offset = Offset(pose.shadow.x * density, pose.shadow.y * density)
        color = PokeColors.Ink
    }
    .border(2.dp, PokeColors.Ink, shape)
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }
        onDrawWithContent {
            drawOutline(outline, player.cardFill() ?: fill)
            clipPath(clip) { with(player) { drawEffectBehind() } }
            drawContent()
            clipPath(clip) { with(player) { drawEffectInFront() } }
        }
    }

/** For the dex circle: tells the player where it is, and pops or squashes it as the effect says. */
internal fun Modifier.catchEffectCircle(player: CatchEffectPlayer): Modifier = this
    .onPlaced { player.circleCoordinates = it }
    .graphicsLayer {
        val scale = player.circleScale()
        scaleX = scale.x
        scaleY = scale.y
    }
