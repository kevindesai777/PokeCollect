package com.pixel.pokecollect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal
import com.pixel.pokecollect.ui.theme.contentColorOn
import com.pixel.pokecollect.ui.theme.monoFontFamily
import com.pixel.pokecollect.ui.theme.mutedColorOn

/** How far a menu's solid shadow sits from its panel. */
private val MenuShadow = 5.dp

/**
 * A chip that shows a value and opens a drop-down menu of the other values under it.
 *
 * [changed] means "not the default": the chip turns yellow and presses in, like a chosen
 * generation did in the old strip. [description] is what a screen reader says for the chip
 * ("Order: name, A to Z"). [menu] gets a `close` to call once something is picked.
 *
 * The menu is Material 3's [DropdownMenu]: it places it (under the chip, flipped above when
 * there's no room, kept on screen), closes it on a tap outside or Back, and moves screen reader
 * focus into it. Its own panel would clip our offset shadow, so that panel is made invisible
 * (clear, square, no elevation) and the panel drawn is ours, with room inside for its shadow.
 * [DropdownMenuPopup], made for custom menus, would skip the invisible panel, but it is
 * experimental on iOS in Material 3 1.12.0-alpha03.
 */
@Composable
internal fun MenuChip(
    label: String,
    changed: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    /** The menu panel's width (its shadow sits outside it); unset, it fits its content. */
    menuWidth: Dp = Dp.Unspecified,
    /**
     * The chip ends its row, so the menu lines up with the chip's end rather than its start.
     * The menu would otherwise line its shadow up with the chip's end, leaving the panel inside it.
     */
    alignEnd: Boolean = false,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val fill = if (changed) PokeColors.Primary else PokeColors.Surface
    val onFill = contentColorOn(fill)
    Box(modifier) {
        Row(
            Modifier
                // A changed chip "presses in": shadow removed, nudged by the shadow distance.
                .then(if (changed) Modifier.offset(3.dp, 3.dp) else Modifier)
                .brutal(fill, PokeShapes.Small, offset = if (changed) 0.dp else 3.dp)
                .clickable(role = Role.DropdownList) { open = true }
                .semantics { contentDescription = description }
                .height(40.dp)
                .padding(start = 12.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = onFill, maxLines = 1)
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = onFill,
                modifier = Modifier.size(22.dp),
            )
        }
        // Its content column already has 8dp above it, so no offset is needed to clear the chip.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(if (alignEnd) MenuShadow else 0.dp, 0.dp),
            shape = RectangleShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            // The popup is a window sized to its content: leave room in it for the panel's shadow.
            Box(
                Modifier
                    .then(if (menuWidth.isSpecified) Modifier.width(menuWidth + MenuShadow) else Modifier)
                    .padding(end = MenuShadow, bottom = MenuShadow),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .brutal(PokeColors.Surface, PokeShapes.Large, border = 3.dp, offset = MenuShadow)
                        .padding(3.dp) // inside the border
                        .clip(RoundedCornerShape(11.dp))
                        .selectableGroup(),
                ) {
                    menu { open = false }
                }
            }
        }
    }
}

/** One choice in a [MenuChip]'s menu: optional mark, text, optional count; the chosen one is yellow. */
@Composable
internal fun MenuOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    /** What a screen reader says instead of [text], when [text] doesn't read well aloud ("1–9"). */
    description: String? = null,
    mark: (@Composable () -> Unit)? = null,
) {
    val fill = if (selected) PokeColors.Primary else PokeColors.Surface
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(min = 220.dp)
            .height(52.dp)
            .background(fill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        mark?.invoke()
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp),
            color = contentColorOn(fill),
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFontFamily(), fontSize = 14.sp),
                color = mutedColorOn(fill),
            )
        }
    }
}

/** The ink line between two [MenuOption]s. */
@Composable
internal fun MenuDivider() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(PokeColors.Ink))
}
