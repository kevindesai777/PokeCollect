package com.pixel.pokecollect.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixel.pokecollect.data.CardOrder
import com.pixel.pokecollect.data.CardView
import com.pixel.pokecollect.data.CaughtFilter
import com.pixel.pokecollect.data.CollectionRepository
import com.pixel.pokecollect.data.PokemonWithCollection
import com.pixel.pokecollect.data.applyView
import com.pixel.pokecollect.data.searchCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GenerationSummary(val generation: Int, val caught: Int, val total: Int)

sealed interface CollectionUiState {
    data object Loading : CollectionUiState

    data class Ready(
        val visible: List<PokemonWithCollection>,
        val caught: Int,
        val total: Int,
        val generations: List<GenerationSummary>,
        /** The chosen generation, Have / Missing and order. Ignored while [searching]. */
        val view: CardView,
        /** Caught and total in the chosen generation, or in all of them: the Show menu's counts. */
        val inView: GenerationSummary,
        /** True while the search bar has text: [visible] holds its matches from every card. */
        val searching: Boolean,
    ) : CollectionUiState
}

class CollectionViewModel(private val repository: CollectionRepository) : ViewModel() {

    /**
     * The view, plus the cards ticked or unticked in the list since it last changed (see
     * [applyView]). One flow, so changing the view and forgetting those cards is one update.
     * Not saved: every launch starts with every card in number order.
     */
    private val shown = MutableStateFlow(Shown(CardView(), keep = emptySet()))

    private data class Shown(val view: CardView, val keep: Set<Int>)

    /**
     * The search bar's text. Compose state rather than a flow: a text field must read back what was
     * typed in the same frame, or fast typing drops characters.
     */
    var query by mutableStateOf("")
        private set

    val uiState: StateFlow<CollectionUiState> =
        combine(repository.observeCollection(), shown, snapshotFlow { query }) { all, (view, keep), text ->
            val generations = all.groupBy { it.pokemon.generation }
                .map { (gen, members) -> GenerationSummary(gen, members.count { it.caught }, members.size) }
                .sortedBy { it.generation }
            val caught = all.count { it.caught }
            CollectionUiState.Ready(
                // A search ignores the view: the card you're after may be in another generation, or caught.
                visible = if (text.isNotBlank()) searchCards(all, text) else applyView(all, view, keep),
                caught = caught,
                total = all.size,
                generations = generations,
                view = view,
                inView = generations.firstOrNull { it.generation == view.generation }
                    ?: GenerationSummary(generation = 0, caught = caught, total = all.size),
                searching = text.isNotBlank(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState.Loading)

    fun search(text: String) {
        query = text
    }

    fun selectGeneration(generation: Int?) = changeView { it.copy(generation = generation) }

    fun show(filter: CaughtFilter) = changeView { it.copy(show = filter) }

    fun setOrder(order: CardOrder) = changeView { it.copy(order = order) }

    fun setCaught(pokemonId: Int, caught: Boolean) {
        // Ticked in the list, it stays in the list. Ticked in search results, it follows the view
        // once the search is cleared: that list wasn't on screen when it was ticked.
        if (query.isBlank()) shown.update { it.copy(keep = it.keep + pokemonId) }
        viewModelScope.launch { repository.setCaught(pokemonId, caught) }
    }

    private fun changeView(change: (CardView) -> CardView) {
        shown.update { Shown(change(it.view), keep = emptySet()) }
    }
}
