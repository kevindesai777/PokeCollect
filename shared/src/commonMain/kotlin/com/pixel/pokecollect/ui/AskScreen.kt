package com.pixel.pokecollect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.pokecollect.query.QueryIntent
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal
import com.pixel.pokecollect.ui.theme.monoFontFamily

private val Suggestions = listOf(
    "Which Gen 3 cards am I still missing?",
    "How many fire types do I have?",
    "Show me the water cards from Gen 1",
)

private val TerminalMuted = Color(0xFFBDBAB0)

@Composable
fun AskScreen(
    state: AskUiState,
    onAsk: (String) -> Unit,
    onCaughtChange: (pokemonId: Int, caught: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var question by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = { text: String ->
        question = text
        focus.clearFocus()
        onAsk(text)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PokeColors.Background).safeDrawingPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TopBar(onBack) }
        item {
            QuestionField(
                value = question,
                onValueChange = { question = it },
                enabled = state.modelStatus == ModelStatus.Ready && !state.thinking,
                onSubmit = { submit(question) },
            )
        }
        if (state.answer == null && state.failure == null && !state.thinking) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Suggestions.forEach { s ->
                        Text(
                            s,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier
                                .brutal(PokeColors.Surface, PokeShapes.Small)
                                .clickable(enabled = state.modelStatus == ModelStatus.Ready) { submit(s) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        item { ModelCard(state) }

        val answer = state.answer
        if (answer != null && !state.thinking) {
            item {
                Text(answer.summary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            if (answer.planned.query.intent == QueryIntent.LIST) {
                items(answer.results, key = { it.pokemon.id }) { entry ->
                    PokemonRow(entry, onCaughtChange = { onCaughtChange(entry.pokemon.id, it) })
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(44.dp).brutal(PokeColors.Surface, PokeShapes.Small).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Ask", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .brutal(PokeColors.Tertiary, PokeShapes.Tag, offset = 0.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("On-device · no network", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp))
        }
    }
}

@Composable
private fun QuestionField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, onSubmit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .brutal(PokeColors.Surface, PokeShapes.Medium, border = 3.dp, offset = 4.dp)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Ask about your collection", style = MaterialTheme.typography.bodyLarge, color = PokeColors.OnSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(10.dp))
        Box(
            Modifier
                .size(46.dp)
                .brutal(if (enabled) PokeColors.Primary else PokeColors.Background, PokeShapes.Small)
                .clickable(enabled = enabled && value.isNotBlank(), onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "Ask")
        }
    }
}

/** The one dark block on screen: what the model understood, or why it couldn't. */
@Composable
private fun ModelCard(state: AskUiState) {
    var showRaw by rememberSaveable { mutableStateOf(false) }
    val failure = state.failure
    val answer = state.answer
    val fill = if (failure != null) PokeColors.Caution else PokeColors.Ink
    val onFill = if (failure != null) PokeColors.Ink else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutal(fill, PokeShapes.Large, offset = 4.dp)
            .clickable(enabled = answer != null || failure?.rawOutput != null) { showRaw = !showRaw }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label = when {
            failure != null -> "COULD NOT UNDERSTAND"
            showRaw && answer != null -> "MODEL OUTPUT"
            else -> "UNDERSTOOD AS"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (failure != null) PokeColors.Ink else TerminalMuted, modifier = Modifier.weight(1f))
            if (answer != null && !state.thinking) {
                val timing = if (answer.planned.usedModel) "${answer.planned.latency.inWholeMilliseconds} ms" else "no model needed"
                Text(timing, fontFamily = monoFontFamily(), fontSize = 13.sp, color = TerminalMuted)
            }
        }
        val mono = MaterialTheme.typography.bodyLarge.copy(fontFamily = monoFontFamily(), fontSize = 19.sp, lineHeight = 27.sp, color = onFill)
        when {
            state.modelStatus == ModelStatus.Loading -> Busy("Loading ${state.modelName}…", mono)
            state.modelStatus is ModelStatus.Unavailable -> Text((state.modelStatus as ModelStatus.Unavailable).reason, style = mono.copy(fontSize = 15.sp, lineHeight = 21.sp))
            state.thinking -> Busy("${state.modelName} is thinking…", mono)
            failure != null -> {
                Text(failure.message, style = mono.copy(fontSize = 15.sp, lineHeight = 21.sp))
                failure.rawOutput?.takeIf { showRaw }?.let { Text(it, style = mono.copy(fontSize = 13.sp, lineHeight = 18.sp)) }
            }
            answer != null && showRaw -> Text(answer.planned.rawOutput, style = mono.copy(fontSize = 14.sp, lineHeight = 20.sp))
            answer != null -> {
                val query = answer.planned.query
                val lines = buildList {
                    if (query.intent == QueryIntent.COUNT) add("intent" to "count")
                    addAll(query.filters())
                }
                if (lines.isEmpty()) {
                    Text("all cards", style = mono)
                } else {
                    Text(
                        buildAnnotatedString {
                            lines.forEachIndexed { i, (key, value) ->
                                append("$key = ")
                                withStyle(SpanStyle(color = PokeColors.Primary, fontWeight = FontWeight.Bold)) { append(value) }
                                if (i < lines.lastIndex) append(" ·\n")
                            }
                        },
                        style = mono,
                    )
                }
            }
            else -> Text("Ask a question. ${state.modelName} turns it into a query; SQLite answers it.", style = mono.copy(fontSize = 15.sp, lineHeight = 21.sp, color = TerminalMuted))
        }
    }
}

@Composable
private fun Busy(text: String, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PokeColors.Primary, strokeWidth = 2.5.dp)
        Text(text, style = style.copy(fontSize = 15.sp))
    }
}
