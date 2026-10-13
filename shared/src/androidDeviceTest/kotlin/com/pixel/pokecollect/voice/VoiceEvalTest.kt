package com.pixel.pokecollect.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.pokecollect.model.LiteRtLmModel
import com.pixel.pokecollect.model.LocalModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import kotlin.time.measureTimedValue

/**
 * Voice input end to end on a real phone, without anyone talking: plays recorded clips through the
 * phone's own speaker while the production [AndroidSpeechToText] listens on its microphone, then
 * resolves the transcript with [CardResolver] and the real model. A report, not a gate: one VOICEROW
 * line per clip under the "PokeCollect.Voice" tag and in a log file.
 *
 * Clips come from `scripts/voice_eval.sh` (macOS `say`, pushed to /data/local/tmp/voice): `<name>.wav`
 * plus `<name>.txt` with the expected card names, one per line. Media volume must be up.
 *
 * Arguments: clips (comma-separated names, default all), model (off = spelling only),
 * biasing (false = no card names passed to the recognizer), out (log file name).
 */
@RunWith(AndroidJUnit4::class)
class VoiceEvalTest {

    private val args = InstrumentationRegistry.getArguments()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(args.getString("dir") ?: "/data/local/tmp/voice")

    @Serializable
    private class Dataset(val pokemon: List<Species>)

    @Serializable
    private class Species(val id: Int, val name: String, val slug: String)

    @Test
    fun clips(): Unit = runBlocking {
        val cards = Json { ignoreUnknownKeys = true }
            .decodeFromString<Dataset>(File(dir, "pokemon.json").readText())
            .pokemon.map { CardName(it.id, it.name, it.slug) }
        val byName = cards.associateBy { it.name.lowercase() }
        val volume = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_MUSIC)
        log("START biasing=${args.getString("biasing") ?: "true"} model=${args.getString("model") ?: "on"} mediaVolume=$volume")
        check(volume > 0) { "Media volume is 0: the clips would play silently" }

        val speech = AndroidSpeechToText(context, biasing = args.getString("biasing") != "false")
        val model: LocalModel? = if (args.getString("model") == "off") null else LiteRtLmModel(context)
        val resolver = CardResolver(model ?: SpellingOnly)
        val (_, loaded) = measureTimedValue { model?.load() }
        log("LOADED ${loaded.inWholeMilliseconds} ms")

        val names = args.getString("clips")?.split(",") ?: dir.listFiles { f -> f.extension == "wav" }!!.map { it.nameWithoutExtension }.sorted()
        for (name in names) {
            val expected = File(dir, "$name.txt").readLines().filter { it.isNotBlank() }.map { byName.getValue(it.trim().lowercase()).id }
            val transcript = listen(speech, File(dir, "$name.wav"), cards.map { it.name })
            val resolution = resolver.resolve(transcript, cards)
            val got = resolution.cards.mapNotNull { it.card?.id }
            val right = got.count { it in expected }
            log(
                "VOICEROW clip=$name expected=${expected.size} heard=${resolution.cards.size} right=$right wrong=${got.size - right} " +
                    "missed=${expected.count { it !in got }} checks=${resolution.cards.count { it.needsCheck }} " +
                    "model=${resolution.cards.count { it.pickedBy == PickedBy.MODEL }} modelMs=${resolution.latency.inWholeMilliseconds}",
            )
            log("  transcript: $transcript")
            resolution.cards.forEach { h ->
                val mark = when {
                    h.card == null -> "?"
                    h.card.id in expected -> "+"
                    else -> "-"
                }
                log("  $mark \"${h.heard}\" -> ${h.card?.name} [${h.pickedBy}${if (h.needsCheck) ", check" else ""}] alts=${h.alternatives.take(3).map { it.name }}")
            }
            expected.filter { it !in got }.forEach { id -> log("  ! missed ${cards.first { it.id == id }.name}") }
            resolution.rawOutput?.let { log("  model: $it") }
            resolution.modelError?.let { log("  model error: $it") }
        }
        (model as? LiteRtLmModel)?.close()
        log("END")
    }

    /** Plays [clip] on the speaker while [speech] listens on the microphone; returns the final transcript. */
    private suspend fun listen(speech: SpeechToText, clip: File, phrases: List<String>): String = coroutineScopeOnMain {
        var transcript = ""
        val listening = async { speech.listen(phrases).collect { transcript = it } }
        delay(1_500) // recognizer start-up
        val played = CompletableDeferred<Unit>()
        val player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            // The media server may not read adb-pushed files (SELinux), even by descriptor: play a copy
            // from this app's own cache.
            val copy = File(context.cacheDir, clip.name).also { clip.copyTo(it, overwrite = true) }
            FileInputStream(copy).use { setDataSource(it.fd); prepare() }
            setOnCompletionListener { played.complete(Unit) }
            start()
        }
        played.await()
        player.release()
        delay(1_500) // let the last word land, as a person would before tapping Done
        val (_, stopping) = measureTimedValue {
            speech.stop()
            withTimeout(15_000) { listening.await() }
        }
        log("  stop -> final ${stopping.inWholeMilliseconds} ms")
        transcript
    }

    private suspend fun <T> coroutineScopeOnMain(block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T =
        withContext(Dispatchers.Main) { kotlinx.coroutines.coroutineScope(block) }

    private object SpellingOnly : LocalModel {
        override val displayName = "none"
        override suspend fun load() = Unit
        override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String = error("model off")
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        val dir = File(context.getExternalFilesDir(null), "voice").apply { mkdirs() }
        File(dir, "${args.getString("out") ?: "voice"}.log").appendText(line + "\n")
    }

    private companion object {
        const val TAG = "PokeCollect.Voice"
    }
}
