package com.pixel.pokecollect.model

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.measureTimedValue

/**
 * Gemma 3 1B through LiteRT-LM, CPU backend, JSON-schema-constrained output.
 *
 * Latency (Pixel 8): creating a conversation with constrained
 * decoding costs ~4 s (LLGuidance indexes Gemma's 262k-token vocabulary per conversation), and its
 * first message prefills the ~465-token system prompt (~3 s on CPU). So one conversation answers
 * several questions, each prefilling only its own turn, and a replacement is created and primed in
 * the background before it is needed.
 */
class LiteRtLmModel(
    private val context: Context,
    /** Off only for measuring what the constraint buys (see ModelEvalTest). */
    private val constrained: Boolean = true,
    /** Questions one conversation answers before it is replaced. 1 = a fresh conversation per question. */
    private val questionsPerConversation: Int = 1,
    /** Where the model is: the app passes [AiPackModelDelivery.modelFile]; tests use the adb-pushed file. */
    private val modelFile: () -> File? = { developerModelFile(context) },
) : LocalModel {

    override val displayName = "Gemma 3 1B"

    private val mutex = Mutex()
    // One LiteRT-LM engine allows one live session/conversation at a time
    // ("FAILED_PRECONDITION: A session already exists"), so generations are serialized.
    private val generation = Mutex()
    private var engine: Engine? = null
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Guarded by [generation].
    private var conversation: Conversation? = null
    private var conversationKey: Warmup? = null
    private var answered = 0

    override suspend fun load() {
        mutex.withLock {
            if (engine != null) return
            val file = withContext(Dispatchers.IO) { modelFile() }
                ?: throw ModelUnavailableException(
                    "$displayName isn't on this phone yet: Google Play downloads it after install." +
                        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                            " Development build: adb push $MODEL_FILE /data/local/tmp/llm/$MODEL_FILE"
                        } else {
                            ""
                        },
                )
            // initialize() blocks for seconds; keep it off the main thread.
            val (loaded, took) = withContext(Dispatchers.Default) {
                measureTimedValue {
                    try {
                        Engine(
                            EngineConfig(
                                modelPath = file.absolutePath,
                                // GPU prefills ~5x faster on a Pixel 8 but decodes constrained output
                                // ~2.5x slower; with conversations reused, decode dominates.
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

    override suspend fun prepare(instructions: String, warmupInput: String, warmupSchema: String) {
        load()
        val engine = checkNotNull(engine)
        generation.withLock {
            withContext(Dispatchers.Default) { readyConversation(engine, Warmup(instructions, warmupInput, warmupSchema)) }
        }
    }

    override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String =
        generateJson(instructions, input, jsonSchema, constrained)

    /** Same engine, constraint chosen per call: lets the eval compare both without loading twice. */
    internal suspend fun generateJson(instructions: String, input: String, jsonSchema: String, constrained: Boolean): String {
        load()
        val engine = checkNotNull(engine)
        var usedUp: Warmup? = null
        val text = generation.withLock {
            withContext(Dispatchers.Default) {
                val key = conversationKey
                if (key != null && key.instructions == instructions && constrained == this@LiteRtLmModel.constrained) {
                    val target = readyConversation(engine, key)
                    try {
                        send(target, input, jsonSchema.takeIf { constrained })
                    } catch (e: Exception) {
                        closeConversation() // a conversation that failed mid-message is not reused
                        throw e
                    }.also {
                        answered++
                        if (answered >= questionsPerConversation) usedUp = key
                    }
                } else {
                    // Not prepared, or a different prompt or constraint setting: one fresh conversation.
                    closeConversation()
                    newConversation(engine, instructions, constrained).use { send(it, input, jsonSchema.takeIf { constrained }) }
                }
            }
        }
        // Build the next conversation now, while the answer is on screen, not when the next question arrives.
        usedUp?.let { key ->
            background.launch {
                runCatching { prepare(key.instructions, key.input, key.schema) }
                    .onFailure { Log.w(TAG, "Background prepare failed: ${it.message}") }
            }
        }
        return text
    }

    /** The current conversation if it has questions left, otherwise a new, primed one. Call under [generation]. */
    private fun readyConversation(engine: Engine, key: Warmup): Conversation {
        conversation?.let { if (conversationKey == key && answered < questionsPerConversation) return it }
        closeConversation()
        val created = newConversation(engine, key.instructions, constrained)
        // Prefills the system prompt now, so the next real question prefills only its own turn.
        val (_, took) = measureTimedValue { send(created, key.input, key.schema.takeIf { constrained }) }
        Log.i(TAG, "Primed a new conversation in ${took.inWholeMilliseconds} ms")
        conversation = created
        conversationKey = key
        answered = 0
        return created
    }

    private fun newConversation(engine: Engine, instructions: String, constrained: Boolean): Conversation {
        val (created, took) = measureTimedValue {
            engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(instructions),
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0), // greedy: same question, same query
                    enableResponseFormat = constrained,
                ),
            )
        }
        Log.i(TAG, "createConversation ${took.inWholeMilliseconds} ms (constrained=$constrained)")
        return created
    }

    private fun send(conversation: Conversation, input: String, jsonSchema: String?): String {
        val (reply, took) = measureTimedValue {
            conversation.sendMessage(
                input,
                // Token-level constraint (LLGuidance): the model cannot emit JSON that breaks the schema.
                responseFormat = jsonSchema?.let { ResponseFormat.json(it) },
                // Guards against known grammar loops on Gemma's tokenizer (LiteRT-LM #3510).
                maxOutputToken = MAX_OUTPUT_TOKENS,
            )
        }
        val text = reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        Log.i(TAG, "generateJson ${took.inWholeMilliseconds} ms: $input -> $text")
        return text
    }

    private fun closeConversation() {
        conversation?.close()
        conversation = null
        conversationKey = null
        answered = 0
    }

    /** Frees the native engine (~1 GB). The app keeps one engine for its lifetime; tests close theirs. */
    fun close() {
        closeConversation()
        engine?.close()
        engine = null
    }

    /** What a conversation was primed with; a conversation is reused only for the same instructions. */
    private data class Warmup(val instructions: String, val input: String, val schema: String)

    private companion object {
        const val TAG = "PokeCollect.Model"
        const val MAX_OUTPUT_TOKENS = 64
    }
}

internal const val MODEL_FILE = "gemma3-1b-it-int4.litertlm"

/** A model pushed with adb for development. Survives uninstall/reinstall, which the dev workflow does often. */
internal fun developerModelFile(context: Context): File? = listOfNotNull(
    File("/data/local/tmp/llm/$MODEL_FILE"),
    context.getExternalFilesDir(null)?.let { File(it, "models/$MODEL_FILE") },
).firstOrNull { it.canRead() }
