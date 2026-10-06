package com.pixel.pokecollect.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixel.pokecollect.data.CollectionRepository
import com.pixel.pokecollect.data.PokemonWithCollection
import com.pixel.pokecollect.model.LocalModel
import com.pixel.pokecollect.model.ModelUnavailableException
import com.pixel.pokecollect.query.CollectionQuery
import com.pixel.pokecollect.query.InvalidQueryException
import com.pixel.pokecollect.query.PlannedQuery
import com.pixel.pokecollect.query.QueryPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

data class AskUiState(
    val modelName: String,
    val modelStatus: ModelStatus = ModelStatus.Loading,
    val thinking: Boolean = false,
    val answer: AskAnswer? = null,
    val failure: AskFailure? = null,
)

class AskViewModel(
    private val planner: QueryPlanner,
    private val repository: CollectionRepository,
    private val model: LocalModel,
) : ViewModel() {

    private val modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.Loading)
    private val thinking = MutableStateFlow(false)
    private val planned = MutableStateFlow<PlannedQuery?>(null)
    private val failure = MutableStateFlow<AskFailure?>(null)

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
        combine(modelStatus, thinking, answer, failure) { status, isThinking, currentAnswer, currentFailure ->
            AskUiState(model.displayName, status, isThinking, currentAnswer, currentFailure)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskUiState(model.displayName))

    init {
        viewModelScope.launch {
            modelStatus.value = try {
                model.load()
                ModelStatus.Ready
            } catch (e: ModelUnavailableException) {
                ModelStatus.Unavailable(e.message ?: "Model unavailable")
            }
        }
    }

    fun ask(question: String) {
        if (question.isBlank() || thinking.value || modelStatus.value != ModelStatus.Ready) return
        viewModelScope.launch {
            thinking.value = true
            failure.value = null
            try {
                planned.value = planner.plan(question, repository.catalog())
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidQueryException) {
                planned.value = null
                failure.value = AskFailure(question, e.message ?: "Invalid query", e.rawOutput)
            } catch (e: Exception) {
                planned.value = null
                failure.value = AskFailure(question, e.message ?: e::class.simpleName.orEmpty(), null)
            } finally {
                thinking.value = false
            }
        }
    }

    fun setCaught(pokemonId: Int, caught: Boolean) {
        viewModelScope.launch { repository.setCaught(pokemonId, caught) }
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
