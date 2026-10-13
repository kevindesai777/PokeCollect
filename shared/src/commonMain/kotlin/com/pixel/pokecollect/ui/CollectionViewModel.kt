package com.pixel.pokecollect.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixel.pokecollect.data.CollectionRepository
import com.pixel.pokecollect.data.PokemonWithCollection
import com.pixel.pokecollect.data.searchCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GenerationSummary(val generation: Int, val caught: Int, val total: Int)

sealed interface CollectionUiState {
    data object Loading : CollectionUiState

    data class Ready(
        val visible: List<PokemonWithCollection>,
        val caught: Int,
        val total: Int,
        val generations: List<GenerationSummary>,
        val selectedGeneration: Int?,
        /** True while the search bar has text: [visible] holds its matches from every generation. */
        val searching: Boolean,
    ) : CollectionUiState
}

class CollectionViewModel(private val repository: CollectionRepository) : ViewModel() {

    private val selectedGeneration = MutableStateFlow<Int?>(null)

    /**
     * The search bar's text. Compose state rather than a flow: a text field must read back what was
     * typed in the same frame, or fast typing drops characters.
     */
    var query by mutableStateOf("")
        private set

    val uiState: StateFlow<CollectionUiState> =
        combine(repository.observeCollection(), selectedGeneration, snapshotFlow { query }) { all, generation, text ->
            CollectionUiState.Ready(
                // A search ignores the selected generation: the card you're after may be in another.
                visible = when {
                    text.isNotBlank() -> searchCards(all, text)
                    generation == null -> all
                    else -> all.filter { it.pokemon.generation == generation }
                },
                caught = all.count { it.caught },
                total = all.size,
                generations = all.groupBy { it.pokemon.generation }
                    .map { (gen, members) -> GenerationSummary(gen, members.count { it.caught }, members.size) }
                    .sortedBy { it.generation },
                selectedGeneration = generation,
                searching = text.isNotBlank(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState.Loading)

    fun search(text: String) {
        query = text
    }

    fun selectGeneration(generation: Int?) {
        selectedGeneration.value = generation
    }

    fun setCaught(pokemonId: Int, caught: Boolean) {
        viewModelScope.launch { repository.setCaught(pokemonId, caught) }
    }
}
