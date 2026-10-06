package com.pixel.pokecollect.model

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.measureTimedValue

/** Gemma 3 1B through LiteRT-LM, CPU backend, JSON-schema-constrained output. */
class LiteRtLmModel(
    private val context: Context,
    /** Off only for measuring what the constraint buys (see ModelEvalTest). */
    private val constrained: Boolean = true,
) : LocalModel {

    override val displayName = "Gemma 3 1B"

    private val mutex = Mutex()
    // One LiteRT-LM engine allows one live session/conversation at a time
    // ("FAILED_PRECONDITION: A session already exists"), so generations are serialized.
    private val generation = Mutex()
    private var engine: Engine? = null

    override suspend fun load() {
        mutex.withLock {
            if (engine != null) return
            val file = candidates().firstOrNull { it.canRead() }
                ?: throw ModelUnavailableException(
                    "Model file not found. Push it with:\n" +
                        "adb push $MODEL_FILE /data/local/tmp/llm/$MODEL_FILE",
                )
            // initialize() blocks for seconds; keep it off the main thread.
            val (loaded, took) = withContext(Dispatchers.Default) {
                measureTimedValue {
                    try {
                        Engine(
                            EngineConfig(
                                modelPath = file.absolutePath,
                                // GPU fails to find OpenCL on some Tensor devices; CPU is the safe default.
                                backend = Backend.CPU(),
                                cacheDir = context.cacheDir.absolutePath,
                            ),
                        ).also { it.initialize() }
                    } catch (e: Exception) {
                        throw ModelUnavailableException("Could not load ${file.name}: ${e.message}", e)
                    }
                }
            }
            Log.i(TAG, "Loaded ${file.absolutePath} (${file.length() / 1_000_000} MB) in ${took.inWholeMilliseconds} ms")
            engine = loaded
        }
    }

    override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String =
        generateJson(instructions, input, jsonSchema, constrained)

    /** Same engine, constraint chosen per call: lets the eval compare both without loading twice. */
    internal suspend fun generateJson(instructions: String, input: String, jsonSchema: String, constrained: Boolean): String {
        load()
        val engine = checkNotNull(engine)
        return generation.withLock { generateOnDefault(engine, instructions, input, jsonSchema, constrained) }
    }

    private suspend fun generateOnDefault(
        engine: Engine,
        instructions: String,
        input: String,
        jsonSchema: String,
        constrained: Boolean,
    ): String {
        return withContext(Dispatchers.Default) {
            // A fresh conversation per question: parsing is stateless, and history would leak between questions.
            engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(instructions),
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0), // greedy: same question, same query
                    enableResponseFormat = constrained,
                ),
            ).use { conversation ->
                val (reply, took) = measureTimedValue {
                    conversation.sendMessage(
                        input,
                        // Token-level constraint (LLGuidance): the model cannot emit JSON that breaks the schema.
                        responseFormat = if (constrained) ResponseFormat.json(jsonSchema) else null,
                        // Guards against known grammar loops on Gemma's tokenizer (LiteRT-LM #3510).
                        maxOutputToken = MAX_OUTPUT_TOKENS,
                    )
                }
                val text = reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                Log.i(TAG, "generateJson ${took.inWholeMilliseconds} ms: $input -> $text")
                text
            }
        }
    }

    /** Frees the native engine (~1 GB). The app keeps one engine for its lifetime; tests close theirs. */
    fun close() {
        engine?.close()
        engine = null
    }

    private fun candidates() = listOfNotNull(
        // Survives uninstall/reinstall, which the dev workflow does often.
        File("/data/local/tmp/llm/$MODEL_FILE"),
        context.getExternalFilesDir(null)?.let { File(it, "models/$MODEL_FILE") },
    )

    private companion object {
        const val TAG = "PokeCollect.Model"
        const val MODEL_FILE = "gemma3-1b-it-int4.litertlm"
        const val MAX_OUTPUT_TOKENS = 64
    }
}
