package com.pixel.pokecollect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal
import com.pixel.pokecollect.ui.theme.monoFontFamily
import com.pixel.pokecollect.ui.theme.typeFill
import com.pixel.pokecollect.voice.PickedBy

@Composable
internal fun HeardCardsSummary(cards: HeardCards) {
    val found = cards.items.count { it.entry != null }
    val toCheck = cards.items.count { it.needsCheck }
    val text = when {
        cards.items.isEmpty() -> "No card names found. Try again a little slower."
        toCheck == 0 -> "Found ${cardCount(found)}"
        else -> "Found ${cardCount(found)} · $toCheck to check"
    }
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

/** One heard name: the card it became, what was actually heard, and (when unsure) the other close names. */
@Composable
internal fun HeardRow(item: HeardItem, onToggle: () -> Unit, onChoose: (cardId: Int) -> Unit) {
    val entry = item.entry
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutal(if (item.needsCheck || entry == null) PokeColors.Caution else PokeColors.Surface, PokeShapes.Medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = entry != null, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (entry != null) {
                val sprite by rememberSprite(entry.pokemon.id)
                DexCircle(entry.pokemon.id, sprite, fill = typeFill(entry.pokemon.type1))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry?.pokemon?.name ?: "Not sure", style = MaterialTheme.typography.titleSmall)
                    if (entry != null) {
                        Text(dexLabel(entry.pokemon.id), fontFamily = monoFontFamily(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PokeColors.OnSurfaceVariant)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("heard \"${item.heard}\"", fontFamily = monoFontFamily(), fontSize = 13.sp, color = PokeColors.Ink)
                    if (item.pickedBy == PickedBy.MODEL) Tag("MODEL", PokeColors.Secondary, Color.White)
                    if (entry?.caught == true) Tag("HAVE IT", PokeColors.Tertiary, PokeColors.Ink)
                }
            }
            if (entry != null) CaughtBox(item.selected) { onToggle() }
        }
        val others = item.alternatives.filter { it.pokemon.id != entry?.pokemon?.id }
        if ((item.needsCheck || entry == null) && others.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Or:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                others.forEach { other ->
                    Text(
                        other.pokemon.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier
                            .brutal(PokeColors.Surface, PokeShapes.Small)
                            .clickable { onChoose(other.pokemon.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Nothing is written until the person taps this. */
@Composable
internal fun MarkCaughtButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .brutal(if (count == 0) PokeColors.Background else PokeColors.Primary, PokeShapes.Medium, border = 3.dp, offset = 4.dp)
            .clickable(enabled = count > 0, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(if (count == 0) "Nothing to add" else "Mark $count caught", style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp))
    }
}

@Composable
private fun Tag(text: String, fill: Color, onFill: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = onFill,
        modifier = Modifier.brutal(fill, PokeShapes.Tag, offset = 0.dp).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

internal fun cardCount(n: Int) = if (n == 1) "1 card" else "$n cards"
