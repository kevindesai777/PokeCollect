package com.pixel.pokecollect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    // What was typed in the Collection screen's search bar when Ask was tapped.
    var askDraft by rememberSaveable { mutableStateOf("") }
    // The mic button opens Ask already listening; the search bar's Ask button opens it ready to type.
    var listenOnOpen by rememberSaveable { mutableStateOf(false) }

    PokeCollectTheme {
        when (screen) {
            Screen.Collection -> {
                val viewModel = koinViewModel<CollectionViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CollectionScreen(
                    state = state,
                    query = viewModel.query,
                    onQueryChange = viewModel::search,
                    onSelectGeneration = viewModel::selectGeneration,
                    onCaughtChange = viewModel::setCaught,
                    onAsk = { text ->
                        askDraft = text.trim()
                        viewModel.search("") // the text moves to the Ask screen
                        screen = Screen.Ask
                    },
                    onVoice = {
                        askDraft = ""
                        listenOnOpen = true
                        screen = Screen.Ask
                    },
                )
            }
            Screen.Ask -> {
                val viewModel = koinViewModel<AskViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                BackHandler { screen = Screen.Collection }
                DisposableEffect(Unit) { onDispose { viewModel.leave() } }
                AskScreen(
                    state = state,
                    initialQuestion = askDraft,
                    listenOnOpen = listenOnOpen,
                    onListenOnOpenHandled = { listenOnOpen = false },
                    onAsk = viewModel::ask,
                    onStartListening = viewModel::startListening,
                    onStopListening = viewModel::stopListening,
                    onMicrophoneDenied = viewModel::microphoneDenied,
                    onToggleCard = viewModel::toggleCard,
                    onChooseCard = viewModel::chooseCard,
                    onMarkCaught = viewModel::markCaught,
                    onCaughtChange = viewModel::setCaught,
                    onBack = { screen = Screen.Collection },
                )
            }
        }
    }
}
