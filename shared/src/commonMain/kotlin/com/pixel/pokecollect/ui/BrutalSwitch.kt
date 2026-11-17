package com.pixel.pokecollect.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.brutal

private val TrackWidth = 52.dp
private val TrackHeight = 30.dp
private val KnobSize = 20.dp
private val KnobInset = 5.dp

/**
 * An on / off switch in the app's style: an ink-bordered pill with a hard shadow, yellow when on,
 * and a knob that slides across (filled with ink when off). It only draws: the caller makes the
 * whole row toggleable with Role.Switch, so the row is the touch target and a screen reader hears
 * one switch.
 */
@Composable
internal fun BrutalSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val knobX by animateDpAsState(if (checked) TrackWidth - KnobSize - KnobInset else KnobInset, label = "knob")
    Box(
        modifier
            .size(TrackWidth, TrackHeight)
            .brutal(if (checked) PokeColors.Primary else PokeColors.Surface, CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = (TrackHeight - KnobSize) / 2)
                .size(KnobSize)
                .background(if (checked) PokeColors.Surface else PokeColors.Ink, CircleShape)
                .border(2.dp, PokeColors.Ink, CircleShape),
        )
    }
}
