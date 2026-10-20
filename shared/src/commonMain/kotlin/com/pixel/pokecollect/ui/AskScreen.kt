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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.pokecollect.model.DeliveryState
import com.pixel.pokecollect.query.QueryIntent
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal
import com.pixel.pokecollect.ui.theme.monoFontFamily
import com.pixel.pokecollect.voice.rememberMicrophonePermission

private val Suggestions = listOf(
    "Which Gen 3 cards am I still missing?",
    "How many fire types do I have?",
    "Show me the water cards from Gen 1",
)

internal val TerminalMuted = Color(0xFFBDBAB0)

/** Ask a question or name the cards you got, typed or said. */
@Composable
fun AskScreen(
    state: AskUiState,
    /** Text carried over from the Collection screen's search bar; not sent until the person sends it. */
    initialQuestion: String,
    /** Opened from the mic button: start listening right away. */
    listenOnOpen: Boolean,
    onListenOnOpenHandled: () -> Unit,
    onAsk: (String) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onMicrophoneDenied: () -> Unit,
    onToggleCard: (index: Int) -> Unit,
    onChooseCard: (index: Int, cardId: Int) -> Unit,
    onMarkCaught: () -> Unit,
    onCaughtChange: (pokemonId: Int, caught: Boolean) -> Unit,
    onRetryDownload: () -> Unit,
    onBack: () -> Unit,
) {
    var question by rememberSaveable { mutableStateOf(initialQuestion) }
    // What was said (or last sent) lands in the field, so a misheard word can be fixed and sent again.
    LaunchedEffect(state.input) { if (state.input.isNotEmpty()) question = state.input }
    val focus = LocalFocusManager.current
    val submit = { text: String ->
        question = text
        focus.clearFocus()
        onAsk(text)
    }
    val listen = rememberMicrophonePermission { granted -> if (granted) onStartListening() else onMicrophoneDenied() }
    val confirmDownload = rememberDownloadConfirmation()
    LaunchedEffect(listenOnOpen) {
        if (listenOnOpen) {
            listen()
            onListenOnOpenHandled()
        }
    }
    val listening = state.listening != null
    val busy = state.thinking || listening
    val cards = state.cards

    Box(Modifier.fillMaxSize().background(PokeColors.Background).safeDrawingPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 16.dp, bottom = if (cards != null) 120.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TopBar("Ask", onBack) }
            item {
                QuestionField(
                    value = question,
                    onValueChange = { question = it },
                    enabled = !busy,
                    listening = listening,
                    onSubmit = { submit(question) },
                    onMic = {
                        if (listening) {
                            onStopListening()
                        } else {
                            focus.clearFocus()
                            listen()
                        }
                    },
                )
            }
            if (state.answer == null && state.failure == null && cards == null && state.marked == null && !busy) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Suggestions.forEach { s ->
                            Text(
                                s,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier
                                    .brutal(PokeColors.Surface, PokeShapes.Small)
                                    .clickable { submit(s) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            item { ModelCard(state, onRetryDownload = onRetryDownload, onConfirmDownload = confirmDownload) }

            val answer = state.answer
            if (answer != null && !busy) {
                item {
                    Text(answer.summary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                }
                if (answer.planned.query.intent == QueryIntent.LIST) {
                    items(answer.results, key = { it.pokemon.id }) { entry ->
                        PokemonRow(entry, onCaughtChange = { onCaughtChange(entry.pokemon.id, it) })
                    }
                }
            }
            if (cards != null && !busy) {
                item { HeardCardsSummary(cards) }
                itemsIndexed(cards.items) { index, item ->
                    HeardRow(item, onToggle = { onToggleCard(index) }, onChoose = { onChooseCard(index, it) })
                }
            }
        }
        if (cards != null && !busy && cards.items.isNotEmpty()) {
            MarkCaughtButton(
                cards.selectedCount,
                onMarkCaught,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}

/** Back button, screen title and the "on-device" badge. */
@Composable
internal fun TopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(44.dp).brutal(PokeColors.Surface, PokeShapes.Small).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
private fun QuestionField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    listening: Boolean,
    onSubmit: () -> Unit,
    onMic: () -> Unit,
) {
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
                Text("Ask, or name the cards you got", style = MaterialTheme.typography.bodyLarge, color = PokeColors.OnSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(10.dp))
        // The mic turns into a stop button while listening: tap again when done.
        Box(
            Modifier
                .size(46.dp)
                .brutal(if (listening) PokeColors.Error else PokeColors.Surface, PokeShapes.Small)
                .clickable(enabled = enabled || listening, onClick = onMic),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = if (listening) "Done" else "Speak")
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

/** The one dark block on screen: what was heard, what the model understood, or why it couldn't. */
@Composable
private fun ModelCard(state: AskUiState, onRetryDownload: () -> Unit, onConfirmDownload: () -> Unit) {
    var showRaw by rememberSaveable { mutableStateOf(false) }
    val failure = state.failure
    val answer = state.answer
    val cards = state.cards
    val rawOutput = answer?.planned?.rawOutput ?: cards?.rawOutput ?: failure?.rawOutput
    // Android: the model is still coming from Google Play, and nothing else is on screen yet.
    val delivering = state.delivery != DeliveryState.Delivered && state.modelStatus == ModelStatus.Loading &&
        answer == null && cards == null && failure == null && state.marked == null
    val fill = if (failure != null) PokeColors.Caution else PokeColors.Ink
    val onFill = if (failure != null) PokeColors.Ink else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutal(fill, PokeShapes.Large, offset = 4.dp)
            .clickable(enabled = rawOutput != null && state.listening == null) { showRaw = !showRaw }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label = when {
            state.listening != null -> "LISTENING · ${state.recognizerName}"
            failure != null -> "COULD NOT UNDERSTAND"
            showRaw && rawOutput != null -> "MODEL OUTPUT"
            cards != null -> "HEARD"
            state.marked != null -> "MARKED CAUGHT"
            delivering -> "GETTING THE MODEL"
            else -> "UNDERSTOOD AS"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.listening != null) Box(Modifier.size(10.dp).background(PokeColors.Error, CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (failure != null) PokeColors.Ink else TerminalMuted, modifier = Modifier.weight(1f))
            if (!state.thinking && state.listening == null) {
                val timing = when {
                    answer != null -> if (answer.planned.usedModel) "${answer.planned.latency.inWholeMilliseconds} ms" else "no model needed"
                    cards != null -> cards.modelLatency?.let { "${it.inWholeMilliseconds} ms" } ?: "no model needed"
                    else -> null
                }
                timing?.let { Text(it, fontFamily = monoFontFamily(), fontSize = 13.sp, color = TerminalMuted) }
            }
        }
        val mono = MaterialTheme.typography.bodyLarge.copy(fontFamily = monoFontFamily(), fontSize = 19.sp, lineHeight = 27.sp, color = onFill)
        val small = mono.copy(fontSize = 15.sp, lineHeight = 21.sp)
        when {
            state.listening != null ->
                if (state.listening.isEmpty()) {
                    Text("Ask a question, or say the cards you got. Tap the stop button when you're done.", style = small.copy(color = TerminalMuted))
                } else {
                    Text(state.listening, style = mono)
                }
            state.thinking -> Busy("${state.modelName} is thinking…", mono)
            delivering -> DeliveryStatus(state.delivery, state.modelName, mono, small, onRetryDownload, onConfirmDownload)
            state.modelStatus == ModelStatus.Loading && answer == null && cards == null && failure == null -> Busy("Loading ${state.modelName}…", mono)
            failure != null -> {
                Text(failure.message, style = small)
                failure.rawOutput?.takeIf { showRaw }?.let { Text(it, style = mono.copy(fontSize = 13.sp, lineHeight = 18.sp)) }
            }
            showRaw && rawOutput != null -> Text(rawOutput, style = mono.copy(fontSize = 14.sp, lineHeight = 20.sp))
            cards != null -> {
                Text(state.input, style = small)
                cards.modelError?.let { Text("${state.modelName} unavailable: $it", style = small.copy(fontSize = 13.sp, color = PokeColors.Caution)) }
            }
            state.marked != null -> Text("${cardCount(state.marked)} added to your collection.", style = small)
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
            state.modelStatus is ModelStatus.Unavailable -> Text(state.modelStatus.reason, style = small)
            else -> Text(
                "Ask a question, or name the cards you got. ${state.modelName} turns it into a query; SQLite answers it.",
                style = small.copy(color = TerminalMuted),
            )
        }
    }
}

/** The model on its way from Google Play: progress, or what the person can do about it. */
@Composable
private fun DeliveryStatus(
    delivery: DeliveryState,
    modelName: String,
    mono: TextStyle,
    small: TextStyle,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
) {
    fun mb(bytes: Long) = bytes / 1_000_000
    when (delivery) {
        DeliveryState.Checking, DeliveryState.Delivered -> Busy("Looking for $modelName…", mono)
        is DeliveryState.Downloading -> {
            val total = delivery.totalBytes
            Busy(if (total > 0) "Downloading $modelName · ${mb(delivery.bytes)} of ${mb(total)} MB" else "Downloading $modelName…", mono)
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { (delivery.bytes.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = PokeColors.Primary,
                    trackColor = TerminalMuted.copy(alpha = 0.3f),
                )
            }
            Text("Google Play downloads it once. After that, everything runs on this phone, offline.", style = small.copy(color = TerminalMuted))
        }
        is DeliveryState.WaitingForWifi -> {
            Text("$modelName (${mb(delivery.totalBytes)} MB) is waiting for Wi-Fi.", style = small)
            CardButton("Download on mobile data", onConfirm)
        }
        DeliveryState.NeedsConfirmation -> {
            Text("Google Play needs to confirm the $modelName download.", style = small)
            CardButton("Continue", onConfirm)
        }
        is DeliveryState.Failed -> {
            Text(delivery.reason, style = small)
            CardButton("Try again", onRetry)
        }
    }
}

@Composable
private fun CardButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = PokeColors.Ink,
        modifier = Modifier
            .brutal(PokeColors.Primary, PokeShapes.Small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
internal fun Busy(text: String, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PokeColors.Primary, strokeWidth = 2.5.dp)
        Text(text, style = style.copy(fontSize = 15.sp))
    }
}
