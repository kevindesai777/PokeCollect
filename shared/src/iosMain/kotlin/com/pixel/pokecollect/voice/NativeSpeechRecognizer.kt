package com.pixel.pokecollect.voice

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Implemented in Swift (`SpeechRecognizerBridge`), like [com.pixel.pokecollect.model.NativeLanguageModel]:
 * the audio engine, permissions and Speech framework are far simpler to drive from Swift than through
 * Kotlin/Native's Objective-C interop, so Swift implements this callback interface and Kotlin adapts it.
 */
interface NativeSpeechRecognizer {
    val displayName: String

    /**
     * Asks for permission if needed, then listens. Calls [onTranscript] with the whole transcript so far
     * each time it changes, and [onFinish] exactly once: null after [stop], or a reason a person can act on.
     */
    fun start(phrases: List<String>, onTranscript: (String) -> Unit, onFinish: (error: String?) -> Unit)

    /** Stops listening; the final transcript arrives, then onFinish(null). Safe to call when idle. */
    fun stop()
}

class SwiftSpeechToText(private val native: NativeSpeechRecognizer) : SpeechToText {
    override val displayName: String get() = native.displayName

    override fun listen(phrases: List<String>): Flow<String> = callbackFlow {
        native.start(
            phrases,
            onTranscript = { trySend(it) },
            onFinish = { error -> if (error == null) channel.close() else channel.close(SpeechUnavailableException(error)) },
        )
        awaitClose { native.stop() }
    }

    override fun stop() = native.stop()
}
