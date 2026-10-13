package com.pixel.pokecollect.ui

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.pokecollect.data.PokemonWithCollection
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal
import com.pixel.pokecollect.ui.theme.monoFontFamily
import com.pixel.pokecollect.ui.theme.typeFill

/** One card in a list: sprite or dex circle, name, generation, type chips, caught box. */
@Composable
internal fun PokemonRow(entry: PokemonWithCollection, onCaughtChange: (Boolean) -> Unit) {
    val p = entry.pokemon
    val sprite by rememberSprite(p.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutal(PokeColors.Surface, PokeShapes.Medium)
            .clickable { onCaughtChange(!entry.caught) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DexCircle(p.id, sprite, fill = if (entry.caught) typeFill(p.type1) else null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall)
                // With a sprite in the circle, the dex number moves next to the name.
                if (sprite != null) {
                    Text(
                        dexLabel(p.id),
                        fontFamily = monoFontFamily(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PokeColors.OnSurfaceVariant,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Gen ${p.generation}",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
                    color = PokeColors.OnSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp),
                )
                listOfNotNull(p.type1, p.type2).forEach { TypeChip(it) }
            }
        }
        CaughtBox(entry.caught, onCaughtChange)
    }
}

@Composable
internal fun DexCircle(id: Int, sprite: Sprite?, fill: Color?) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .then(
                if (fill != null) {
                    Modifier.brutal(fill, CircleShape, offset = 0.dp)
                } else {
                    // Not caught yet: dashed outline, no fill.
                    Modifier.drawBehind {
                        val stroke = 2.dp.toPx()
                        drawCircle(
                            color = PokeColors.Ink,
                            radius = size.minDimension / 2 - stroke / 2,
                            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                }
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (sprite != null) {
            SpriteImage(
                sprite,
                modifier = Modifier.size(40.dp),
                // Not caught: silhouette.
                colorFilter = if (fill == null) ColorFilter.tint(PokeColors.OnSurfaceVariant.copy(alpha = 0.35f), BlendMode.SrcIn) else null,
            )
        } else {
            val label = dexLabel(id)
            Text(
                label,
                fontFamily = monoFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = if (label.length > 4) 12.sp else 14.sp,
                color = if (fill != null) PokeColors.Ink else PokeColors.OnSurfaceVariant,
            )
        }
    }
}

internal fun dexLabel(id: Int) = "#" + id.toString().padStart(3, '0')

@Composable
internal fun TypeChip(type: String) {
    Text(
        type.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .brutal(typeFill(type), PokeShapes.Tag, offset = 0.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
internal fun CaughtBox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .brutal(if (checked) PokeColors.Tertiary else PokeColors.Surface, RoundedCornerShape(7.dp), offset = 0.dp)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = "Caught", modifier = Modifier.size(22.dp))
    }
}
