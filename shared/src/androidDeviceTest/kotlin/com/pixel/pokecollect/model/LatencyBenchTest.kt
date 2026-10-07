package com.pixel.pokecollect.model

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Modality
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.pixel.pokecollect.query.Catalog
import com.pixel.pokecollect.query.CollectionQuery
import com.pixel.pokecollect.query.EvalCases
import com.pixel.pokecollect.query.EvalTypes
import com.pixel.pokecollect.query.HeldOutV1Cases
import com.pixel.pokecollect.query.HeldOutV2Cases
import com.pixel.pokecollect.query.Narrower
import com.pixel.pokecollect.query.NoNarrowing
import com.pixel.pokecollect.query.Options
import com.pixel.pokecollect.query.Prompt
import com.pixel.pokecollect.query.QueryPlanner
import com.pixel.pokecollect.query.QuestionCues
import com.pixel.pokecollect.query.RulesOnlyModel
import com.pixel.pokecollect.query.WireLabels
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Where do the seconds go? Runs the production prompt and per-question schema straight through
 * LiteRT-LM and times each phase (conversation setup, prefill, decode) for several ways of running
 * the same model. A report, not a gate: one BENCHROW line per (variant, question) under the
 * "PokeCollect.Bench" tag; `scripts/aggregate_bench.py` turns them into a table.
 *
 * Engine settings come from instrumentation arguments, so one installed test APK covers every
 * configuration. Run with adb rather than Gradle (Gradle gives up on long runs):
 *
 *   adb shell am instrument -w -e class com.pixel.pokecollect.model.LatencyBenchTest#bench \
 *     -e backend cpu -e threads 4 -e questions 12 -e variants baseline,preface \
 *     com.pixel.pokecollect.shared.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Arguments: backend (cpu|gpu|npu|tensor), threads, maxTokens, activation (f32|f16|i16|i8),
 * questions (how many model-called questions), variants (comma-separated names from [Variant]),
 * tag (free text, copied into every row).
 */
@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
class LatencyBenchTest {

    private val args = InstrumentationRegistry.getArguments()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog = Catalog(maxGeneration = 9, types = EvalTypes)
    private val planner = QueryPlanner(RulesOnlyModel(), QuestionCues, WireLabels.HaveMissing, Prompt.Round2)

    /** `-e narrowing none`: every question gets the full schema (model only), so all 120 reach the model. */
    private val narrower: Narrower = if (args.getString("narrowing") == "none") NoNarrowing else QuestionCues

    private enum class Constraint { JSON, REGEX, NONE }

    /** One way of running the model on a question. The engine is shared; these differ per conversation. */
    private enum class Variant(
        val prefillPrefaceOnInit: Boolean,
        val conversationConstraint: Boolean,
        val messageConstraint: Constraint,
        /** `ExperimentalFlags.enableConversationConstrainedDecoding`, read when the conversation is created. */
        val conversationConstrainedDecoding: Boolean = false,
    ) {
        /** Production as of 2026-10-06: fresh conversation, constrained by JSON schema. */
        BASELINE(prefillPrefaceOnInit = false, conversationConstraint = true, messageConstraint = Constraint.JSON),

        /** System prompt prefilled while the conversation is created (can happen before the user asks). */
        PREFACE(prefillPrefaceOnInit = true, conversationConstraint = true, messageConstraint = Constraint.JSON),

        /** Same allowed strings as the JSON schema, written as one regex over the compact form. */
        REGEX(prefillPrefaceOnInit = false, conversationConstraint = true, messageConstraint = Constraint.REGEX),

        PREFACE_REGEX(prefillPrefaceOnInit = true, conversationConstraint = true, messageConstraint = Constraint.REGEX),

        /** Constraint switched on for the conversation but not sent with the message: isolates setup cost. */
        FLAG_ONLY(prefillPrefaceOnInit = false, conversationConstraint = true, messageConstraint = Constraint.NONE),

        /** No constraint at all (outputs may be invalid; timing reference only). */
        UNCONSTRAINED(prefillPrefaceOnInit = false, conversationConstraint = false, messageConstraint = Constraint.NONE),

        PREFACE_UNCONSTRAINED(prefillPrefaceOnInit = true, conversationConstraint = false, messageConstraint = Constraint.NONE),

        CCD(false, true, Constraint.JSON, conversationConstrainedDecoding = true),
        CCD_PREFACE(true, true, Constraint.JSON, conversationConstrainedDecoding = true),
        CCD_NOFLAG(false, false, Constraint.JSON, conversationConstrainedDecoding = true),
    }

    @Test
    fun modelInfo() {
        ModelInfo.from(modelFile().absolutePath).use { info ->
            val llm = info as? ModelInfo.Llm
            log("MODELINFO type=${info.modelType} maxContextTokens=${info.maxContextTokens()} " +
                "dynamicContext=${info.isDynamicContext()} backends=${info.supportedBackends(Modality.TEXT)} " +
                "npu=${info.npuBrand(Modality.TEXT)} soc=${info.socName(Modality.TEXT)} " +
                "minRuntime=${info.minRuntimeVersion()} speculative=${llm?.hasSpeculativeDecodingSupport()} " +
                "sampler=${llm?.defaultSamplerParams()}")
        }
    }

    /** No model: scores the questions the rules answer alone (they never reach any model). */
    @Test
    fun rulesDetermined(): Unit = kotlinx.coroutines.runBlocking {
        val rulesPlanner = QueryPlanner(RulesOnlyModel(), QuestionCues, WireLabels.HaveMissing, Prompt.Round2)
        val determined = (EvalCases + HeldOutV1Cases + HeldOutV2Cases)
            .filter { QuestionCues.options(it.first, catalog).determined() != null }
        determined.forEachIndexed { index, (question, expected) ->
            val planned = rulesPlanner.plan(question, catalog)
            log(row("rules", "", "RULES", index, Result(question, 0, 0, planned.rawOutput, "\t\t\t\t\t", planned.query), expected))
        }
        log("END rules determined=${determined.size}")
    }

    @Test
    fun bench() {
        val tag = args.getString("tag") ?: ""
        val variants = (args.getString("variants") ?: "baseline,preface")
            .split(',').map { Variant.valueOf(it.trim().uppercase()) }
        val questionCount = args.getString("questions")?.toInt() ?: 12
        val config = engineConfig()
        val engineTag = listOfNotNull(
            args.getString("backend") ?: "cpu",
            args.getString("threads")?.let { "t$it" },
            args.getString("maxTokens")?.let { "kv$it" },
            args.getString("activation"),
            args.getString("speculative")?.let { "spec$it" },
            args.getString("model")?.substringAfterLast('/')?.substringBefore('.')?.take(24),
        ).joinToString("-")

        ExperimentalFlags.enableBenchmark = true
        log("START $engineTag tag=$tag ${deviceState()} config=$config")
        val loadStart = SystemClock.elapsedRealtime()
        val engine = try {
            Engine(config).also { it.initialize() }
        } catch (e: Exception) {
            log("ENGINEFAIL $engineTag ${e.message?.replace('\n', ' ')}")
            return
        }
        log("ENGINE $engineTag loadMs=${SystemClock.elapsedRealtime() - loadStart} ${deviceState()}")

        val instructions = planner.instructions(catalog)
        try {
            // The first call after loading pays one-off costs; measured, then excluded from the table.
            val warm = runOne(engine, Variant.BASELINE, instructions, "List everything I own")
            log(row(engineTag, tag, "WARMUP", -1, warm, null))

            questions(questionCount).forEachIndexed { index, (question, expected) ->
                // Rotate the order per question so thermal drift hits every variant equally.
                variants.indices.map { variants[(it + index) % variants.size] }.forEach { variant ->
                    val result = runOne(engine, variant, instructions, question)
                    log(row(engineTag, tag, variant.name, index, result, expected))
                }
            }
        } finally {
            engine.close()
        }
        log("END $engineTag ${deviceState()}")
    }

    /**
     * One conversation answers question after question: the constraint setup and the system prompt
     * are paid once, and each question prefills only its own turn. Earlier questions and answers stay
     * in the context, so outputs are compared against fresh conversations (BASELINE rows) afterwards.
     * `recreateEvery` > 0 starts a new conversation after that many questions. `prime=true` sends one
     * fixed question with a single-answer schema right after creating a conversation, so the system
     * prompt is prefilled before a real question arrives (in the app this happens in the background);
     * its cost is logged as a PRIME row and not charged to the next question.
     */
    @Test
    fun reuse() {
        val tag = args.getString("tag") ?: ""
        val recreateEvery = args.getString("recreateEvery")?.toInt() ?: 0
        val prime = args.getString("prime") == "true"
        val variantName = "REUSE" + (if (recreateEvery > 0) recreateEvery else "") + (if (prime) "P" else "") +
            (if (narrower === NoNarrowing) "-MODELONLY" else "")
        val questionCount = args.getString("questions")?.toInt() ?: 12
        val engineTag = listOfNotNull(
            args.getString("backend") ?: "cpu",
            args.getString("threads")?.let { "t$it" },
            args.getString("speculative")?.let { "spec$it" },
            args.getString("model")?.substringAfterLast('/')?.substringBefore('.')?.take(24),
        ).joinToString("-")
        ExperimentalFlags.enableBenchmark = true
        val loadStart = SystemClock.elapsedRealtime()
        val engine = Engine(engineConfig()).also { it.initialize() }
        log("ENGINE $engineTag loadMs=${SystemClock.elapsedRealtime() - loadStart} ${deviceState()}")
        val instructions = planner.instructions(catalog)
        var conversation: com.google.ai.edge.litertlm.Conversation? = null
        var used = 0
        try {
            questions(questionCount).forEachIndexed { index, (question, expected) ->
                var createMs = 0L
                if (conversation == null || (recreateEvery > 0 && used >= recreateEvery)) {
                    conversation?.close()
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    conversation = engine.createConversation(
                        conversationConfig(instructions, prefillPrefaceOnInit = false, constrained = true),
                    )
                    createMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                    used = 0
                    if (prime) {
                        val t = SystemClock.elapsedRealtimeNanos()
                        val primed = checkNotNull(conversation).sendMessage(
                            PRIME_QUESTION, responseFormat = ResponseFormat.json(planner.jsonSchema(PRIME_ANSWER)), maxOutputToken = 64,
                        ).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        val primeMs = (SystemClock.elapsedRealtimeNanos() - t) / 1_000_000
                        log(row(engineTag, tag, "PRIME", index, Result(PRIME_QUESTION, createMs, primeMs, primed, "\t\t\t\t\t", null), null))
                        createMs = 0
                    }
                }
                val c = checkNotNull(conversation)
                val options = narrower.options(question, catalog)
                val t1 = SystemClock.elapsedRealtimeNanos()
                val reply = runCatching {
                    c.sendMessage(question, responseFormat = ResponseFormat.json(planner.jsonSchema(options)), maxOutputToken = 64)
                        .contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                }
                val sendMs = (SystemClock.elapsedRealtimeNanos() - t1) / 1_000_000
                used++
                val info = runCatching { c.getBenchmarkInfo() }.getOrNull()
                val benchmark = info?.let {
                    listOf(
                        it.lastPrefillTokenCount, "%.1f".format(it.lastPrefillTokensPerSecond),
                        it.lastDecodeTokenCount, "%.1f".format(it.lastDecodeTokensPerSecond),
                        "%.3f".format(it.timeToFirstTokenInSecond), runCatching { c.getTokenCount() }.getOrDefault(-1),
                    ).joinToString("\t")
                } ?: List(6) { "" }.joinToString("\t")
                val output = reply.getOrElse { "ERROR ${it.message}" }.replace('\t', ' ').replace('\n', ' ')
                val parsed = reply.getOrNull()?.let { raw -> runCatching { planner.parse(raw, options) }.getOrNull() }
                log(row(engineTag, tag, variantName, index,
                    Result(question, createMs, sendMs, output, benchmark, parsed), expected))
            }
        } finally {
            conversation?.close()
            engine.close()
        }
        log("END $engineTag ${deviceState()}")
    }

    /**
     * The app's own path: QueryPlanner -> LiteRtLmModel with `questionsPerConversation` (default 1),
     * primed by `planner.prepare`. Each row's sendMs is what the user waits for (`plan`); createMs is
     * the background preparation that follows an answer, run here to completion before the next
     * question as if the user paused to read. `-e prepare false` never calls `prepare`, which is the
     * app's old path (a fresh conversation per question), for a like-for-like before/after.
     */
    @Test
    fun appPath(): Unit = kotlinx.coroutines.runBlocking {
        val tag = args.getString("tag") ?: ""
        val perConversation = args.getString("questionsPerConversation")?.toInt() ?: 1
        val prepare = args.getString("prepare")?.toBooleanStrict() ?: true
        val variant = if (prepare) "APP$perConversation" else "APP-FRESH"
        val model = LiteRtLmModel(context, questionsPerConversation = perConversation)
        val appPlanner = QueryPlanner(model)
        val loadStart = SystemClock.elapsedRealtime()
        model.load()
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        val prepareStart = SystemClock.elapsedRealtime()
        if (prepare) appPlanner.prepare(catalog)
        log("ENGINE app-$variant loadMs=$loadMs prepareMs=${SystemClock.elapsedRealtime() - prepareStart} ${deviceState()}")
        val warmup = QueryPlanner.Warmup
        try {
            questions(args.getString("questions")?.toInt() ?: 12).forEachIndexed { index, (question, expected) ->
                val t0 = SystemClock.elapsedRealtimeNanos()
                val planned = runCatching { appPlanner.plan(question, catalog) }
                val planMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                val t1 = SystemClock.elapsedRealtimeNanos()
                if (prepare) appPlanner.prepare(catalog) // no-op unless the conversation is used up
                val prepareMs = (SystemClock.elapsedRealtimeNanos() - t1) / 1_000_000
                val output = planned.getOrNull()?.rawOutput ?: ("ERROR " + planned.exceptionOrNull()?.message)
                log(row("app", tag, variant, index,
                    Result(question, prepareMs, planMs, output.replace('\t', ' ').replace('\n', ' '), "\t\t\t\t\t", planned.getOrNull()?.query), expected))
            }
        } finally {
            model.close()
        }
        log("END app ${deviceState()} warmup=${warmup.question}")
    }

    private class Result(
        val question: String,
        val createMs: Long,
        val sendMs: Long,
        val output: String,
        val benchmark: String,
        val parsed: CollectionQuery?,
    )

    private fun runOne(engine: Engine, variant: Variant, instructions: String, question: String): Result {
        val options = narrower.options(question, catalog)
        ExperimentalFlags.enableConversationConstrainedDecoding = variant.conversationConstrainedDecoding
        val t0 = SystemClock.elapsedRealtimeNanos()
        val conversation = engine.createConversation(
            conversationConfig(instructions, variant.prefillPrefaceOnInit, variant.conversationConstraint),
        )
        val t1 = SystemClock.elapsedRealtimeNanos()
        return conversation.use {
            val reply = runCatching {
                conversation.sendMessage(
                    question,
                    responseFormat = when (variant.messageConstraint) {
                        Constraint.JSON -> ResponseFormat.json(planner.jsonSchema(options))
                        Constraint.REGEX -> ResponseFormat.regex(regex(options))
                        Constraint.NONE -> null
                    },
                    maxOutputToken = 64,
                ).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
            }
            val t2 = SystemClock.elapsedRealtimeNanos()
            val info = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
            val benchmark = info?.let {
                listOf(
                    it.lastPrefillTokenCount, "%.1f".format(it.lastPrefillTokensPerSecond),
                    it.lastDecodeTokenCount, "%.1f".format(it.lastDecodeTokensPerSecond),
                    "%.3f".format(it.timeToFirstTokenInSecond), "%.3f".format(it.initTimeInSecond),
                ).joinToString("\t")
            } ?: List(6) { "" }.joinToString("\t")
            val output = reply.getOrElse { "ERROR ${it.message}" }.replace('\t', ' ').replace('\n', ' ')
            val parsed = reply.getOrNull()?.let { raw -> runCatching { planner.parse(raw, options) }.getOrNull() }
            Result(question, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, output, benchmark, parsed)
        }
    }

    /**
     * Greedy, production prompt. `-e thinking false` switches a thinking model's chat template to its
     * non-thinking form (Qwen3's template reads `enable_thinking`, default true); under a JSON
     * constraint a thinking model would otherwise be forced to skip the `<think>` block it expects.
     */
    private fun conversationConfig(instructions: String, prefillPrefaceOnInit: Boolean, constrained: Boolean): ConversationConfig {
        val thinking = args.getString("thinking")?.toBooleanStrict()
        return ConversationConfig(
            systemInstruction = Contents.of(instructions),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0),
            extraContext = if (thinking == null) emptyMap() else mapOf("enable_thinking" to thinking),
            prefillPrefaceOnInit = prefillPrefaceOnInit,
            thinkingConfig = if (thinking == null) null else ThinkingConfig(enableThinking = thinking),
            enableSpeculativeDecoding = args.getString("speculative")?.toBooleanStrict(),
            enableResponseFormat = constrained,
        )
    }

    /** The JSON schema's allowed strings, as one regex over the compact form the model writes. */
    private fun regex(options: Options): String {
        fun alt(values: List<String>) = values.joinToString("|")
        return """\{"intent":"(${alt(options.intent)})","generation":"(${alt(options.generation)})",""" +
            """"type":"(${alt(options.type)})","caught":"(${alt(options.caught)})"\}"""
    }

    /** Model-called questions only (fully determined ones never reach the model), demo questions first. */
    private fun questions(count: Int): List<Pair<String, CollectionQuery>> {
        val pool = (EvalCases + HeldOutV1Cases + HeldOutV2Cases)
            .filter { narrower.options(it.first, catalog).determined() == null }
        val demo = pool.take(3) // EvalCases starts with the three live-demo questions
        val rest = pool.drop(3)
        if (count < 0) return pool
        val stride = maxOf(1, rest.size / maxOf(1, count - demo.size))
        return (demo + rest.filterIndexed { i, _ -> i % stride == 0 }).take(count)
    }

    private fun row(engineTag: String, tag: String, variant: String, index: Int, r: Result, expected: CollectionQuery?) =
        listOf(
            "BENCHROW", engineTag, tag, variant, index, r.createMs, r.sendMs, r.benchmark,
            if (expected == null) "" else if (r.parsed == expected) 1 else 0,
            deviceState(), r.output, r.question,
        ).joinToString("\t")

    private fun engineConfig(): EngineConfig {
        // Multi-token prediction, for models that ship a drafter (Gemma 4); read at engine creation.
        args.getString("speculative")?.let { ExperimentalFlags.enableSpeculativeDecoding = it.toBooleanStrict() }
        val threads = args.getString("threads")?.toInt()
        val backend = when (args.getString("backend") ?: "cpu") {
            "cpu" -> Backend.CPU(threadCount = threads)
            "gpu" -> Backend.GPU()
            "npu" -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
            "tensor" -> Backend.GOOGLE_TENSOR()
            else -> error("unknown backend")
        }
        return EngineConfig(
            modelPath = modelFile().absolutePath,
            backend = backend,
            maxNumTokens = args.getString("maxTokens")?.toInt(),
            cacheDir = context.cacheDir.absolutePath,
            activationDataType = when (args.getString("activation")) {
                null -> null
                "f32" -> ActivationDataType.FLOAT32
                "f16" -> ActivationDataType.FLOAT16
                "i16" -> ActivationDataType.INT16
                "i8" -> ActivationDataType.INT8
                else -> error("unknown activation")
            },
        )
    }

    private fun modelFile() = File(args.getString("model") ?: "/data/local/tmp/llm/gemma3-1b-it-int4.litertlm")

    /**
     * Logcat plus a file on the device (`-e out <name>` → files/bench/<name>.log under the test app's
     * external files dir), so a run survives a USB disconnect; `scripts/run_bench.sh` pulls the file.
     */
    private fun log(line: String) {
        Log.i(TAG, line)
        val name = args.getString("out") ?: return
        val dir = File(context.getExternalFilesDir(null), "bench").apply { mkdirs() }
        File(dir, "$name.log").appendText(line + "\n")
    }

    /** Battery level and temperature plus the OS thermal status, logged with every row. */
    private fun deviceState(): String {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val power = context.getSystemService(PowerManager::class.java)
        return "bat=$level%/${temp}C thermal=${power.currentThermalStatus} headroom=${"%.2f".format(power.getThermalHeadroom(0))}"
    }

    private companion object {
        const val TAG = "PokeCollect.Bench"

        /** Primes a new conversation. Its only allowed answer is the matching few-shot example's. */
        const val PRIME_QUESTION = "List everything"
        val PRIME_ANSWER = Options(listOf("list"), listOf("any"), listOf("any"), listOf("any"))
    }
}
