package com.pixel.pokecollect.voice

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * The seam over each platform's on-device speech recognizer: Android's `SpeechRecognizer` in
 * on-device mode, Apple's Speech framework on iOS. An interface (like `LocalModel`) so the
 * ViewModel can be tested with a fake; each platform binds its implementation in `platformModule`.
 */
interface SpeechToText {
    /** Shown in the UI, e.g. "Android on-device speech". */
    val displayName: String

    /**
     * Listens until [stop] (or until the collector cancels), emitting the whole transcript so far
     * each time it changes, and completes after the final words are in. [phrases] bias recognition
     * toward these words where the platform supports it. Throws [SpeechUnavailableException] with a
     * reason a person can act on.
     */
    fun listen(phrases: List<String>): Flow<String>

    /** Ends the current [listen] gracefully: what was just said is still recognized, then the flow completes. */
    fun stop()
}

class SpeechUnavailableException(message: String) : Exception(message)

/**
 * Returns a function that asks for the microphone and reports whether it's granted. On Android this
 * is the runtime permission dialog (it needs the Activity, so it lives in UI code); on iOS the
 * recognizer asks the system itself when it starts.
 */
@Composable
expect fun rememberMicrophonePermission(onResult: (granted: Boolean) -> Unit): () -> Unit
