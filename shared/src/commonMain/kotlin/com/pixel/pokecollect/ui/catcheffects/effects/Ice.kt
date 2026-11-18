package com.pixel.pokecollect.ui.catcheffects.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pixel.pokecollect.ui.catcheffects.CardGeometry
import com.pixel.pokecollect.ui.catcheffects.CatchEffect
import com.pixel.pokecollect.ui.catcheffects.EffectRun
import com.pixel.pokecollect.ui.catcheffects.EffectTiming
import com.pixel.pokecollect.ui.catcheffects.ShaderLayer
import com.pixel.pokecollect.ui.catcheffects.ballistic
import com.pixel.pokecollect.ui.catcheffects.inkFill
import com.pixel.pokecollect.ui.catcheffects.polar
import com.pixel.pokecollect.ui.catcheffects.rockPath
import com.pixel.pokecollect.ui.catcheffects.shaders.IceShader
import com.pixel.pokecollect.ui.theme.typeFill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Ice: frost grows in from the card's edges as ice shards (a shader), then shatters; shards fly off the circle. */
internal object IceEffect : CatchEffect {
    override val timing = EffectTiming(durationMs = 1300, peakMs = 540)

    override fun start(card: CardGeometry, random: Random): EffectRun =
        IceRun(card, ShaderLayer(IceShader, card, timing, random.nextFloat() * 10), random)
}

private val ShardFills = listOf(typeFill("ice"), typeFill("steel"), Color.White)

private class Shard(val start: Offset, val velocity: Offset, val size: Float, val rotation: Float, val spin: Float, val corners: FloatArray, val fill: Color)

private class IceRun(private val card: CardGeometry, private val frost: ShaderLayer, random: Random) : EffectRun {
    private val shards = List(11) {
        val angle = -PI.toFloat() / 2 + (random.nextFloat() - 0.5f) * 2.6f
        val speed = 150 + random.nextFloat() * 200
        Shard(
            start = polar(card.circleCenter, card.circleRadius * 0.8f, angle),
            velocity = Offset(cos(angle) * speed, sin(angle) * speed),
            size = 4 + random.nextFloat() * 4,
            rotation = random.nextFloat() * 6,
            spin = (random.nextFloat() - 0.5f) * 14,
            corners = FloatArray(3) { random.nextFloat() },
            fill = ShardFills[random.nextInt(ShardFills.size)],
        )
    }

    override fun DrawScope.behind(ms: Float) = with(frost) { draw(ms) }

    override fun DrawScope.inFront(ms: Float) {
        val s = (ms - IceEffect.timing.peakMs) / 1000
        if (s < 0f) return
        val k = 1 - ((s - 0.35f) / 0.3f).coerceIn(0f, 1f)
        if (k <= 0f) return
        for (shard in shards) {
            inkFill(rockPath(ballistic(shard.start, shard.velocity, s, 900f), shard.size * k, shard.corners, shard.rotation + shard.spin * s), shard.fill, 1.6f)
        }
    }
}
