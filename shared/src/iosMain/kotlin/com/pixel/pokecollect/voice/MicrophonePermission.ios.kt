package com.pixel.pokecollect.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** iOS asks when the Swift recognizer starts (microphone and speech recognition together), so nothing to do here. */
@Composable
actual fun rememberMicrophonePermission(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onResult)
    return remember { { latest(true) } }
}
