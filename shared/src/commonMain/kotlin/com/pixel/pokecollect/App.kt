package com.pixel.pokecollect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.pokecollect.ui.AskScreen
import com.pixel.pokecollect.ui.AskViewModel
import com.pixel.pokecollect.ui.CollectionScreen
import com.pixel.pokecollect.ui.CollectionViewModel
import com.pixel.pokecollect.ui.theme.PokeCollectTheme
import org.koin.compose.viewmodel.koinViewModel

private enum class Screen { Collection, Ask }

@OptIn(ExperimentalComposeUiApi::class) // BackHandler
@Composable
fun App() {
    // Two screens; a navigation library would be more ceremony than it saves here.
    var screen by rememberSaveable { mutableStateOf(Screen.Collection) }

    PokeCollectTheme {
        when (screen) {
            Screen.Collection -> {
                val viewModel = koinViewModel<CollectionViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CollectionScreen(
                    state = state,
                    onSelectGeneration = viewModel::selectGeneration,
                    onCaughtChange = viewModel::setCaught,
                    onAsk = { screen = Screen.Ask },
                    onVoice = {}, // voice milestone
                )
            }
            Screen.Ask -> {
                val viewModel = koinViewModel<AskViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                BackHandler { screen = Screen.Collection }
                AskScreen(
                    state = state,
                    onAsk = viewModel::ask,
                    onCaughtChange = viewModel::setCaught,
                    onBack = { screen = Screen.Collection },
                )
            }
        }
    }
}
