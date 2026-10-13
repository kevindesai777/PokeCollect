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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.pokecollect.data.PokemonWithCollection
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal
import com.pixel.pokecollect.ui.theme.monoFontFamily
import com.pixel.pokecollect.ui.theme.typeFill

private val ScreenMargin = 20.dp

@Composable
fun CollectionScreen(
    state: CollectionUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectGeneration: (Int?) -> Unit,
    onCaughtChange: (pokemonId: Int, caught: Boolean) -> Unit,
    onAsk: (question: String) -> Unit,
    onVoice: () -> Unit,
) {
    // View-only state. Anything that shapes the data lives in CollectionViewModel.
    var gensExpanded by rememberSaveable { mutableStateOf(false) }
    var browsing by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(PokeColors.Background).safeDrawingPadding()) {
        if (state !is CollectionUiState.Ready) {
            Title()
            return@Box
        }
        val empty = state.caught == 0 && !browsing

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Title() }
            if (!empty) item { SearchBar(query, onQueryChange, onAsk = { onAsk(query) }) }
            // While searching, the matches get the room (the keyboard is up).
            if (!state.searching) item { ProgressCard(caught = state.caught, total = state.total) }

            if (empty) {
                item { EmptyState(onVoice = onVoice, onBrowse = { browsing = true }) }
            } else {
                if (state.searching) {
                    if (state.visible.isEmpty()) item { NoMatch(query, onAsk = { onAsk(query) }) }
                } else {
                    item {
                        GenerationHeader(
                            selected = state.selectedGeneration,
                            expanded = gensExpanded,
                            onToggle = { gensExpanded = !gensExpanded },
                        )
                    }
                    if (gensExpanded) {
                        item {
                            GenerationStrip(
                                generations = state.generations,
                                caught = state.caught,
                                total = state.total,
                                selected = state.selectedGeneration,
                                onSelect = onSelectGeneration,
                            )
                        }
                    }
                }
                items(state.visible, key = { it.pokemon.id }) { entry ->
                    PokemonRow(entry, onCaughtChange = { onCaughtChange(entry.pokemon.id, it) })
                }
            }
        }

        if (!empty) {
            MicFab(onVoice, Modifier.align(Alignment.BottomEnd).padding(end = ScreenMargin, bottom = 24.dp))
        }
    }
}

@Composable
private fun Title() {
    Text(
        "PokeCollect",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * Finds cards by name or dex number as you type, without the model. Ask opens the Ask screen and
 * takes the typed text with it (not sent: the model may still be loading).
 */
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onAsk: () -> Unit) {
    val focus = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .brutal(PokeColors.Surface, PokeShapes.Medium, border = 3.dp, offset = 4.dp)
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(26.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Find a card or ask",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PokeColors.OnSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                // Autocorrect would "fix" card names.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(36.dp).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(22.dp))
            }
        }
        Text(
            "Ask",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp),
            modifier = Modifier
                .brutal(PokeColors.Primary, PokeShapes.Small, offset = 0.dp)
                .clickable(onClick = onAsk)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NoMatch(query: String, onAsk: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("No card matches “${query.trim()}”", style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp))
        Text(
            "Search looks at names and numbers. For anything else, ask the on-device model.",
            style = MaterialTheme.typography.bodyMedium,
            color = PokeColors.OnSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        BrutalButton("Ask about it", PokeColors.Primary, onAsk)
    }
}

@Composable
private fun ProgressCard(caught: Int, total: Int) {
    val fraction = if (total == 0) 0f else caught.toFloat() / total
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .brutal(PokeColors.Secondary, PokeShapes.Large, border = 3.dp, offset = 5.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(
                    caught.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    " / ${total.withThousands()} caught",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .brutal(PokeColors.Primary, PokeShapes.Tag, offset = 0.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        BrutalProgressBar(fraction)
    }
}

@Composable
private fun BrutalProgressBar(fraction: Float) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .brutal(PokeColors.Surface, shape, border = 2.5.dp, offset = 0.dp)
            .padding(2.5.dp),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(PokeColors.Primary, RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
                    .drawBehind {
                        // Ink divider where the fill ends.
                        drawLine(PokeColors.Ink, Offset(size.width, 0f), Offset(size.width, size.height), 2.5.dp.toPx())
                    },
            )
        }
    }
}

@Composable
private fun GenerationHeader(selected: Int?, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("By generation", style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp))
        Text(
            if (selected == null) "All" else "Gen $selected",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
            color = PokeColors.OnSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Box(
            Modifier
                .size(34.dp)
                .brutal(PokeColors.Surface, PokeShapes.Small, offset = 0.dp)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide generations" else "Show generations",
            )
        }
    }
}

@Composable
private fun GenerationStrip(
    generations: List<GenerationSummary>,
    caught: Int,
    total: Int,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val chips = listOf(GenerationSummary(generation = 0, caught = caught, total = total)) + generations
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 6.dp, bottom = 6.dp)) {
        items(chips, key = { it.generation }) { chip ->
            // Generation 0 stands for "All".
            val gen = chip.generation.takeIf { it != 0 }
            val isSelected = gen == selected
            Column(
                modifier = Modifier
                    // Selected chip "presses in": shadow removed, nudged by the shadow distance.
                    .then(if (isSelected) Modifier.offset(3.dp, 3.dp) else Modifier)
                    .brutal(
                        if (isSelected) PokeColors.Primary else PokeColors.Surface,
                        PokeShapes.Small,
                        offset = if (isSelected) 0.dp else 3.dp,
                    )
                    .clickable { onSelect(gen) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(if (gen == null) "All" else "Gen $gen", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
                Text(
                    "${chip.caught}/${chip.total}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFontFamily()),
                    color = PokeColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MicFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(76.dp)
            .brutal(PokeColors.Primary, PokeShapes.Fab, border = 3.dp, offset = 5.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Mic, contentDescription = "Say your cards", modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun EmptyState(onVoice: () -> Unit, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .rotate(-4f)
                .size(92.dp)
                .brutal(PokeColors.Highlight, PokeShapes.Large, border = 3.dp, offset = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Style, contentDescription = null, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Start your collection", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp))
        Spacer(Modifier.height(10.dp))
        Text(
            "Say the cards you own and PokeCollect adds them. Nothing leaves this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = PokeColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        BrutalButton("Say your cards", PokeColors.Primary, onVoice, icon = { Icon(Icons.Default.Mic, null, Modifier.size(22.dp)) })
        Spacer(Modifier.height(14.dp))
        BrutalButton("Add a card by name", PokeColors.Surface, onBrowse)
    }
}

@Composable
private fun BrutalButton(text: String, fill: Color, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .width(240.dp)
            .height(56.dp)
            .brutal(fill, PokeShapes.Medium, border = 3.dp, offset = 4.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp))
    }
}

private fun Int.withThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
