package com.pixel.pokecollect.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixel.pokecollect.data.CollectionRepository
import com.pixel.pokecollect.data.PokemonWithCollection
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
    ) : CollectionUiState
}

class CollectionViewModel(private val repository: CollectionRepository) : ViewModel() {

    private val selectedGeneration = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<CollectionUiState> =
        combine(repository.observeCollection(), selectedGeneration) { all, generation ->
            CollectionUiState.Ready(
                visible = if (generation == null) all else all.filter { it.pokemon.generation == generation },
                caught = all.count { it.caught },
                total = all.size,
                generations = all.groupBy { it.pokemon.generation }
                    .map { (gen, members) -> GenerationSummary(gen, members.count { it.caught }, members.size) }
                    .sortedBy { it.generation },
                selectedGeneration = generation,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState.Loading)

    fun selectGeneration(generation: Int?) {
        selectedGeneration.value = generation
    }

    fun setCaught(pokemonId: Int, caught: Boolean) {
        viewModelScope.launch { repository.setCaught(pokemonId, caught) }
    }
}
