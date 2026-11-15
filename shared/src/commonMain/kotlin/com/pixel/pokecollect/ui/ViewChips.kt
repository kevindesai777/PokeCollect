package com.pixel.pokecollect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.pokecollect.data.CardOrder
import com.pixel.pokecollect.data.CardView
import com.pixel.pokecollect.data.CaughtFilter
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal
import com.pixel.pokecollect.ui.theme.contentColorOn
import com.pixel.pokecollect.ui.theme.monoFontFamily
import com.pixel.pokecollect.ui.theme.mutedColorOn

/**
 * Generation, Have / Missing and order on one line, each a [MenuChip]. Replaces the old
 * "By generation" header and its strip, whose chips now live in the generation menu.
 */
@Composable
internal fun ViewChips(
    view: CardView,
    generations: List<GenerationSummary>,
    caught: Int,
    total: Int,
    inView: GenerationSummary,
    onSelectGeneration: (Int?) -> Unit,
    onShow: (CaughtFilter) -> Unit,
    onOrderChange: (CardOrder) -> Unit,
) {
    val density = LocalDensity.current
    var rowWidth by remember { mutableStateOf(Dp.Unspecified) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
            .onSizeChanged { rowWidth = with(density) { it.width.toDp() } },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuChip(
            label = view.generation?.let { "Gen $it" } ?: "All gens",
            changed = view.generation != null,
            description = "Generation: " + (view.generation?.toString() ?: "all"),
            // The grid of generations is as wide as this row and the cards under it (it opens under
            // the first chip), so their edges line up and its shadow covers theirs.
            menuWidth = rowWidth,
        ) { close ->
            GenerationGrid(generations, caught, total, selected = view.generation) {
                onSelectGeneration(it)
                close()
            }
        }
        MenuChip(
            label = view.show.label,
            changed = view.show != CaughtFilter.All,
            description = "Show: " + view.show.spoken,
        ) { close ->
            CaughtFilter.entries.forEachIndexed { i, filter ->
                if (i > 0) MenuDivider()
                MenuOption(
                    text = filter.label,
                    selected = filter == view.show,
                    onClick = { onShow(filter); close() },
                    count = when (filter) {
                        CaughtFilter.All -> inView.total
                        CaughtFilter.Have -> inView.caught
                        CaughtFilter.Missing -> inView.total - inView.caught
                    },
                    mark = { CaughtMark(filter) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        MenuChip(
            label = view.order.short,
            changed = view.order != CardOrder.NumberUp,
            description = "Order: " + view.order.spoken,
            alignEnd = true,
        ) { close ->
            CardOrder.entries.forEachIndexed { i, order ->
                if (i > 0) MenuDivider()
                MenuOption(
                    text = order.long,
                    selected = order == view.order,
                    onClick = { onOrderChange(order); close() },
                    description = order.spoken.replaceFirstChar { it.uppercase() },
                )
            }
        }
    }
}

/** The old generation strip's chips, two to a row. Generation 0 stands for "All". */
@Composable
private fun GenerationGrid(
    generations: List<GenerationSummary>,
    caught: Int,
    total: Int,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val chips = listOf(GenerationSummary(generation = 0, caught = caught, total = total)) + generations
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Plain rows, not a lazy grid: the menu sizes itself from its content, which lazy layouts can't report.
        chips.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { chip ->
                    val gen = chip.generation.takeIf { it != 0 }
                    val isSelected = gen == selected
                    val fill = if (isSelected) PokeColors.Primary else PokeColors.Surface
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (isSelected) Modifier.offset(3.dp, 3.dp) else Modifier)
                            .brutal(fill, PokeShapes.Small, offset = if (isSelected) 0.dp else 3.dp)
                            .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(gen) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (gen == null) "All" else "Gen $gen",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                            color = contentColorOn(fill),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${chip.caught}/${chip.total}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFontFamily()),
                            color = mutedColorOn(fill),
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The row's own symbols: a green tick for Have, a dashed circle for Missing. */
@Composable
private fun CaughtMark(filter: CaughtFilter) {
    when (filter) {
        CaughtFilter.All -> Box(Modifier.size(22.dp))
        CaughtFilter.Have -> Box(
            Modifier.size(22.dp).brutal(PokeColors.Tertiary, RoundedCornerShape(6.dp), offset = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = PokeColors.Ink, modifier = Modifier.size(16.dp))
        }
        CaughtFilter.Missing -> Box(
            Modifier.size(22.dp).drawBehind {
                val stroke = 2.dp.toPx()
                drawCircle(
                    color = PokeColors.Ink,
                    radius = size.minDimension / 2 - stroke / 2,
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                )
            },
        )
    }
}

private val CaughtFilter.label: String
    get() = when (this) {
        CaughtFilter.All -> "All cards"
        CaughtFilter.Have -> "Have"
        CaughtFilter.Missing -> "Missing"
    }

private val CaughtFilter.spoken: String
    get() = when (this) {
        CaughtFilter.All -> "all cards"
        CaughtFilter.Have -> "cards you have"
        CaughtFilter.Missing -> "cards you're missing"
    }

/** On the chip. */
private val CardOrder.short: String
    get() = when (this) {
        CardOrder.NumberUp -> "1–9"
        CardOrder.NumberDown -> "9–1"
        CardOrder.NameUp -> "A–Z"
        CardOrder.NameDown -> "Z–A"
    }

/** In the menu. */
private val CardOrder.long: String
    get() = when (this) {
        CardOrder.NumberUp -> "Number, 1–9"
        CardOrder.NumberDown -> "Number, 9–1"
        CardOrder.NameUp -> "Name, A–Z"
        CardOrder.NameDown -> "Name, Z–A"
    }

/** For screen readers, which read "1–9" badly. */
private val CardOrder.spoken: String
    get() = when (this) {
        CardOrder.NumberUp -> "number, lowest first"
        CardOrder.NumberDown -> "number, highest first"
        CardOrder.NameUp -> "name, A to Z"
        CardOrder.NameDown -> "name, Z to A"
    }
