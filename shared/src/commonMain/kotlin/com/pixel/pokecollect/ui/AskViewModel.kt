package com.pixel.pokecollect.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixel.pokecollect.data.CollectionRepository
import com.pixel.pokecollect.data.PokemonWithCollection
import com.pixel.pokecollect.model.DeliveryState
import com.pixel.pokecollect.model.LocalModel
import com.pixel.pokecollect.model.ModelDelivery
import com.pixel.pokecollect.model.ModelUnavailableException
import com.pixel.pokecollect.query.CollectionQuery
import com.pixel.pokecollect.query.InvalidQueryException
import com.pixel.pokecollect.query.PlannedQuery
import com.pixel.pokecollect.query.QueryPlanner
import com.pixel.pokecollect.query.RequestKind
import com.pixel.pokecollect.query.RequestRouter
import com.pixel.pokecollect.voice.CardMatcher
import com.pixel.pokecollect.voice.CardName
import com.pixel.pokecollect.voice.CardResolver
import com.pixel.pokecollect.voice.HeardCard
import com.pixel.pokecollect.voice.PickedBy
import com.pixel.pokecollect.voice.SpeechToText
import com.pixel.pokecollect.voice.VoiceResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

sealed interface ModelStatus {
    data object Loading : ModelStatus
    data object Ready : ModelStatus
    data class Unavailable(val reason: String) : ModelStatus
}

data class AskAnswer(
    val planned: PlannedQuery,
    val results: List<PokemonWithCollection>,
    /** Sentence built by Kotlin from SQL counts. The model never produces a number. */
    val summary: String,
)

data class AskFailure(val question: String, val message: String, val rawOutput: String?)

/** One name in a list of cards: the card it became, what was heard or typed, and the person's choice. */
data class HeardItem(
    val heard: String,
    /** Null when nothing fit; the person can pick one of the [alternatives]. */
    val entry: PokemonWithCollection?,
    val alternatives: List<PokemonWithCollection>,
    val pickedBy: PickedBy,
    val needsCheck: Boolean,
    val selected: Boolean,
)

/** Cards found in the input, waiting for "Mark N caught". */
data class HeardCards(
    val items: List<HeardItem>,
    /** Null when every name was clear by spelling and the model wasn't asked. */
    val modelLatency: Duration?,
    val rawOutput: String?,
    val modelError: String?,
) {
    val selectedCount: Int get() = items.count { it.selected && it.entry != null }
}

data class AskUiState(
    val modelName: String,
    /** Android: the model is still on its way from Google Play until this is [DeliveryState.Delivered]. */
    val delivery: DeliveryState = DeliveryState.Checking,
    val modelStatus: ModelStatus = ModelStatus.Loading,
    /** The live transcript while the microphone is on; null when it's off. */
    val listening: String? = null,
    val recognizerName: String = "",
    /** What was last typed or said. The text field shows it, so a misheard word can be fixed and sent again. */
    val input: String = "",
    val thinking: Boolean = false,
    val answer: AskAnswer? = null,
    val cards: HeardCards? = null,
    /** How many cards the last "Mark caught" saved. */
    val marked: Int? = null,
    val failure: AskFailure? = null,
)

/** A heard name and what the person has made of it so far. */
private data class Pick(val heard: HeardCard, val card: CardName?, val selected: Boolean)

private data class CardsFound(val resolution: VoiceResolution, val picks: List<Pick>)

/** Everything on screen except the answer, which follows the database live. */
private data class Session(
    val listening: String? = null,
    val input: String = "",
    val thinking: Boolean = false,
    val cards: CardsFound? = null,
    val marked: Int? = null,
    val failure: AskFailure? = null,
)

/**
 * One screen for questions and for adding cards, typed or said. [RequestRouter] decides which: a
 * question goes to [QueryPlanner] and SQLite; a list of names goes to [CardResolver] and waits for
 * the person to confirm.
 */
class AskViewModel(
    private val planner: QueryPlanner,
    private val resolver: CardResolver,
    private val repository: CollectionRepository,
    private val model: LocalModel,
    private val delivery: ModelDelivery,
    private val speech: SpeechToText,
) : ViewModel() {

    private val modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.Loading)
    private val session = MutableStateFlow(Session())
    private val planned = MutableStateFlow<PlannedQuery?>(null)
    private var listeningJob: Job? = null
    private var cardNames: List<CardName>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val answer = planned.flatMapLatest { p ->
        if (p == null) {
            flowOf(null)
        } else {
            // Two live SQL queries: the matches, and the same scope ignoring caught (for "of N").
            combine(
                repository.observeMatching(p.query),
                repository.observeMatching(p.query.copy(caught = null)),
            ) { matches, scope -> AskAnswer(p, matches, summarize(p.query, matches.size, scope.size)) }
        }
    }

    val uiState: StateFlow<AskUiState> =
        combine(delivery.state, modelStatus, session, answer, repository.observeCollection()) { delivered, status, s, currentAnswer, collection ->
            AskUiState(
                modelName = model.displayName,
                delivery = delivered,
                modelStatus = status,
                listening = s.listening,
                recognizerName = speech.displayName,
                input = s.input,
                thinking = s.thinking,
                answer = currentAnswer,
                cards = s.cards?.toUi(collection),
                marked = s.marked,
                failure = s.failure,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskUiState(model.displayName))

    init {
        viewModelScope.launch {
            delivery.request()
            delivery.state.first { it == DeliveryState.Delivered }
            modelStatus.value = try {
                model.load()
                // Ready means fast: on Android this creates and primes the conversation the first question uses.
                prepareForQuestions()
                ModelStatus.Ready
            } catch (e: ModelUnavailableException) {
                ModelStatus.Unavailable(e.message ?: "Model unavailable")
            }
        }
    }

    /** After a failed download: ask Google Play again. */
    fun retryDownload() = delivery.request()

    /** Typed, tapped (a suggestion) or said: the same words are handled the same way. */
    fun ask(text: String) {
        val input = text.trim()
        if (input.isEmpty() || session.value.thinking || listeningJob?.isActive == true) return
        viewModelScope.launch { handle(input) }
    }

    fun startListening() {
        if (listeningJob?.isActive == true || session.value.thinking) return
        planned.value = null
        session.value = Session(listening = "")
        listeningJob = viewModelScope.launch {
            var transcript = ""
            try {
                val phrases = cardNames().flatMap { CardMatcher.spokenForms(it.name) }.distinct()
                speech.listen(phrases).collect {
                    transcript = it
                    session.update { s -> s.copy(listening = it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                session.value = Session(input = transcript, failure = AskFailure(transcript, e.message ?: e::class.simpleName.orEmpty(), null))
                return@launch
            }
            if (transcript.isBlank()) {
                session.value = Session(failure = AskFailure("", "Didn't hear anything. Tap the mic and try again.", null))
            } else {
                handle(transcript)
            }
        }
    }

    /** Done talking: what was just said is still recognized, then it's handled like typed input. */
    fun stopListening() = speech.stop()

    /** Leaving the screen: the microphone goes off and nothing is handled. */
    fun leave() {
        listeningJob?.cancel()
        session.update { it.copy(listening = null) }
    }

    fun microphoneDenied() {
        session.value = Session(failure = AskFailure("", "PokeCollect needs the microphone to hear you. Allow it in Settings, then try again.", null))
    }

    fun toggleCard(index: Int) = updatePick(index) { it.copy(selected = !it.selected) }

    /** The person picked a different card for a heard name. */
    fun chooseCard(index: Int, cardId: Int) = updatePick(index) { pick ->
        pick.copy(card = pick.heard.alternatives.first { it.id == cardId }, selected = true)
    }

    fun markCaught() {
        val found = session.value.cards ?: return
        val ids = found.picks.filter { it.selected }.mapNotNull { it.card?.id }.distinct()
        viewModelScope.launch {
            repository.markCaught(ids)
            session.update { it.copy(cards = null, marked = ids.size) }
        }
    }

    fun setCaught(pokemonId: Int, caught: Boolean) {
        viewModelScope.launch { repository.setCaught(pokemonId, caught) }
    }

    private suspend fun handle(input: String) {
        planned.value = null
        session.value = Session(input = input, thinking = true)
        try {
            val cards = cardNames()
            val catalog = repository.catalog()
            when (RequestRouter.kind(input, resolver.mentions(input, cards), catalog)) {
                RequestKind.QUESTION -> planned.value = planner.plan(input, catalog)
                RequestKind.CARDS -> {
                    val resolution = resolver.resolve(input, cards)
                    log(resolution)
                    session.update { it.copy(cards = CardsFound(resolution, resolution.cards.map { h -> Pick(h, h.card, selected = h.card != null) })) }
                    // On Android the model just answered under the names prompt; get the question prompt ready again.
                    if (resolution.rawOutput != null) viewModelScope.launch { prepareForQuestions() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidQueryException) {
            session.update { it.copy(failure = AskFailure(input, e.message ?: "Invalid query", e.rawOutput)) }
        } catch (e: Exception) {
            session.update { it.copy(failure = AskFailure(input, e.message ?: e::class.simpleName.orEmpty(), null)) }
        } finally {
            session.update { it.copy(thinking = false) }
        }
    }

    private suspend fun prepareForQuestions() {
        try {
            planner.prepare(repository.catalog())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not fatal: the next question sets up its own conversation instead.
        }
    }

    private suspend fun cardNames(): List<CardName> = cardNames ?: repository.cardNames().also { cardNames = it }

    private fun updatePick(index: Int, change: (Pick) -> Pick) {
        session.update { s ->
            val found = s.cards ?: return@update s
            s.copy(cards = found.copy(picks = found.picks.mapIndexed { i, p -> if (i == index) change(p) else p }))
        }
    }

    private fun CardsFound.toUi(collection: List<PokemonWithCollection>): HeardCards {
        val byId = collection.associateBy { it.pokemon.id }
        return HeardCards(
            items = picks.map { pick ->
                HeardItem(
                    heard = pick.heard.heard,
                    entry = pick.card?.let { byId[it.id] },
                    alternatives = pick.heard.alternatives.take(ALTERNATIVES).mapNotNull { byId[it.id] },
                    pickedBy = pick.heard.pickedBy,
                    needsCheck = pick.heard.needsCheck && pick.card == pick.heard.card,
                    selected = pick.selected,
                )
            },
            modelLatency = resolution.latency.takeIf { resolution.rawOutput != null || resolution.modelError != null },
            rawOutput = resolution.rawOutput,
            modelError = resolution.modelError,
        )
    }

    /** One line per list of cards in logcat / the Xcode console, for checking runs on the phones. */
    private fun log(resolution: VoiceResolution) = println(
        "PokeCollect.Voice \"${resolution.transcript}\" -> " +
            resolution.cards.joinToString { "${it.heard}=${it.card?.name}${if (it.needsCheck) "?" else ""}" } +
            (resolution.rawOutput?.let { " | model ${resolution.latency.inWholeMilliseconds} ms: $it" } ?: "") +
            (resolution.modelError?.let { " | model error: $it" } ?: ""),
    )

    private companion object {
        const val ALTERNATIVES = 4
    }
}

internal fun summarize(query: CollectionQuery, matching: Int, inScope: Int): String {
    val scope = listOfNotNull(
        query.generation?.let { "Gen $it" },
        query.type?.let { it.replaceFirstChar(Char::uppercaseChar) + "-type" },
    ).joinToString(" ")
    fun cards(n: Int) = (if (scope.isEmpty()) "" else "$scope ") + if (n == 1) "card" else "cards"
    return when (query.caught) {
        false -> "You're missing $matching of $inScope ${cards(inScope)}"
        true -> "You have $matching of $inScope ${cards(inScope)}"
        null -> "There ${if (matching == 1) "is" else "are"} $matching ${cards(matching)}"
    }
}
