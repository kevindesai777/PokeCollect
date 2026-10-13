package com.pixel.pokecollect.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Android's own speech recognizer in on-device mode (`createOnDeviceSpeechRecognizer`, API 31):
 * audio never leaves the phone, and it works in airplane mode once the language pack is installed.
 *
 * The recognizer ends a session at every pause, so a list of names is heard as several sessions,
 * restarted back to back until [stop]. Card names go in as biasing strings (API 33), which makes the
 * recognizer write "Bulbasaur" instead of "bulb a sore" far more often.
 */
class AndroidSpeechToText(
    private val context: Context,
    /** Off only to measure what biasing buys (see VoiceEvalTest). */
    private val biasing: Boolean = true,
) : SpeechToText {

    override val displayName = "Android on-device speech"

    private val main = Handler(Looper.getMainLooper())

    // Main thread only.
    private var stopCurrent: (() -> Unit)? = null

    override fun listen(phrases: List<String>): Flow<String> = callbackFlow {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            throw SpeechUnavailableException("This phone has no on-device speech recognizer.")
        }
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val intent = recognizerIntent(phrases)
        val sessions = mutableListOf<String>()
        var partial = ""
        var stopping = false

        fun transcript() = (sessions + partial).filter { it.isNotBlank() }.joinToString(" ")

        fun restart() = main.post { if (!stopping) recognizer.startListening(intent) }

        fun finish() {
            if (partial.isNotBlank()) sessions += partial
            partial = ""
            trySend(transcript())
            channel.close()
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(results: Bundle) {
                partial = results.best() ?: return
                trySend(transcript())
            }

            override fun onResults(results: Bundle) {
                partial = results.best() ?: partial
                if (partial.isNotBlank()) sessions += partial
                partial = ""
                trySend(transcript())
                Log.i(TAG, "Session result; transcript now: ${transcript()}")
                if (stopping) channel.close() else restart()
            }

            override fun onError(error: Int) {
                Log.i(TAG, "Recognizer error $error (stopping=$stopping)")
                when {
                    stopping -> finish()
                    // A pause with nothing said, or a restart that came too soon: keep listening.
                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart()
                    else -> {
                        if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && Build.VERSION.SDK_INT >= 33) {
                            recognizer.triggerModelDownload(intent)
                        }
                        channel.close(SpeechUnavailableException(describe(error)))
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        stopCurrent = {
            stopping = true
            recognizer.stopListening()
        }
        recognizer.startListening(intent)
        awaitClose {
            stopCurrent = null
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main) // SpeechRecognizer must be created and driven from the main thread.

    override fun stop() {
        main.post { stopCurrent?.invoke() }
    }

    private fun recognizerIntent(phrases: List<String>) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        if (biasing && Build.VERSION.SDK_INT >= 33) {
            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(phrases))
        }
    }

    private fun Bundle.best(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun describe(error: Int) = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PokeCollect needs the microphone to hear your cards. Allow it in Settings."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "The on-device English speech model isn't installed yet. It's downloading now (this needs a network once); try again in a minute."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "This phone's on-device recognizer doesn't support English (US)."
        SpeechRecognizer.ERROR_AUDIO -> "The microphone isn't working (audio error)."
        else -> "Speech recognition stopped (error $error)."
    }

    private companion object {
        const val TAG = "PokeCollect.Speech"
        const val LANGUAGE = "en-US"
    }
}
