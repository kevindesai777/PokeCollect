package com.pixel.pokecollect.query

import com.pixel.pokecollect.model.LocalModel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test

/**
 * Experiment J: Apple Foundation Models on the eval questions, run on the Mac (it can't generate in
 * the Simulator). Three steps, the middle one outside Gradle:
 *
 *   1. `export` writes every model call the planner would make, with its exact prompt and per-question
 *      JSON Schema, to build/fm-eval/requests.jsonl.
 *   2. `swift scripts/fm_eval.swift` answers them with Foundation Models into responses.jsonl.
 *   3. `score` replays each response through [QueryPlanner], so it is parsed and validated exactly
 *      like the app, and writes build/fm-eval/summary.txt and rows.tsv.
 *
 *   ./gradlew :shared:testAndroidHostTest --tests '*FoundationModelsEvalTest*'
 *
 * `score` does nothing until responses.jsonl exists.
 */
class FoundationModelsEvalTest {

    @Serializable
    private class Request(
        val id: String,
        val set: String,
        val config: String,
        val question: String,
        val instructions: String,
        val schema: String,
        val primerInput: String? = null,
        val primerSchema: String? = null,
    )

    @Serializable
    private class Response(val id: String, val raw: String? = null, val ms: Double = -1.0, val error: String? = null)

    /** The prompt, labels and rules the app ships; primed = one warm-up turn first, like Android's [QueryPlanner.prepare]. */
    private class Config(val name: String, val narrower: Narrower, val primed: Boolean)

    private val configs = listOf(
        Config("model-only", NoNarrowing, primed = false),
        Config("model-only primed", NoNarrowing, primed = true),
        Config("narrowed", QuestionCues, primed = false), // what the iOS app does
        Config("narrowed primed", QuestionCues, primed = true),
    )

    private val sets = listOf("dev" to EvalCases, "v1" to HeldOutV1Cases, "v2" to HeldOutV2Cases)
    private val catalog = Catalog(maxGeneration = 9, types = EvalTypes)
    private val planner = QueryPlanner(RulesOnlyModel(), QuestionCues, WireLabels.HaveMissing, Prompt.Round2)
    private val dir = File("build/fm-eval")
    private val json = Json { ignoreUnknownKeys = true }

    private fun id(config: String, set: String, index: Int) = "$config|$set|$index"

    @Test
    fun export() {
        val instructions = planner.instructions(catalog)
        val (warmupQuestion, warmupQuery) = QueryPlanner.Warmup
        val primerSchema = planner.jsonSchema(Options(listOf(warmupQuery.intent.name.lowercase()), listOf(ANY), listOf(ANY), listOf(ANY)))
        val requests = sets.flatMap { (set, cases) ->
            cases.withIndex().flatMap { (index, case) ->
                // Rotated per question, as on the phones, so run order hits every configuration alike.
                configs.indices.map { configs[(it + index) % configs.size] }.mapNotNull { config ->
                    val options = config.narrower.options(case.first, catalog)
                    if (options.determined() != null) return@mapNotNull null // the app wouldn't call the model
                    Request(
                        id = id(config.name, set, index),
                        set = set,
                        config = config.name,
                        question = case.first,
                        instructions = instructions,
                        schema = planner.jsonSchema(options),
                        primerInput = warmupQuestion.takeIf { config.primed },
                        primerSchema = primerSchema.takeIf { config.primed },
                    )
                }
            }
        }
        dir.mkdirs()
        File(dir, "requests.jsonl").writeText(requests.joinToString("\n", postfix = "\n") { json.encodeToString(Request.serializer(), it) })
        println("FM export: ${requests.size} requests -> ${File(dir, "requests.jsonl").absolutePath}")
    }

    @Test
    fun score() = runTest {
        val file = File(dir, "responses.jsonl")
        if (!file.exists()) return@runTest
        val responses = file.readLines().filter { it.isNotBlank() }
            .map { json.decodeFromString(Response.serializer(), it) }
            .associateBy { it.id }

        val rows = mutableListOf<String>()
        val summary = StringBuilder()
        val rulesOnly = QueryPlanner(RulesOnlyModel(), QuestionCues, WireLabels.HaveMissing, Prompt.Round2)

        data class Outcome(val exact: Boolean, val hits: List<Boolean>, val unusable: Boolean, val usedModel: Boolean, val ms: Double)

        suspend fun outcome(planner: QueryPlanner, question: String, expected: CollectionQuery, response: Response?): Outcome {
            val planned = runCatching { planner.plan(question, catalog) }.getOrNull()
            val got = planned?.query
            val hits = listOf(
                got?.intent == expected.intent,
                got?.generation == expected.generation,
                got?.type == expected.type,
                got?.caught == expected.caught,
            )
            return Outcome(hits.all { it }, hits, planned == null, planned?.usedModel == true, response?.ms ?: 0.0)
        }

        val results = mutableMapOf<Pair<String, String>, MutableList<Outcome>>() // (config, set) -> outcomes
        val rulesExact = mutableMapOf<Pair<String, Int>, Boolean>()
        val ceiling = mutableMapOf<String, Int>()

        sets.forEach { (set, cases) ->
            cases.forEachIndexed { index, (question, expected) ->
                rulesExact[set to index] = outcome(rulesOnly, question, expected, null).exact
                if (expectedInMenu(QuestionCues.options(question, catalog), expected)) ceiling[set] = (ceiling[set] ?: 0) + 1
                configs.forEach { config ->
                    val response = responses[id(config.name, set, index)]
                    val model = Replay(response)
                    val replay = QueryPlanner(model, config.narrower, WireLabels.HaveMissing, Prompt.Round2)
                    val o = outcome(replay, question, expected, response)
                    results.getOrPut(config.name to set) { mutableListOf() } += o
                    rows += listOf(
                        config.name, set, index, if (o.exact) 1 else 0, o.hits.joinToString("") { if (it) "1" else "0" },
                        if (o.unusable) 1 else 0, if (o.usedModel) 1 else 0, "%.0f".format(response?.ms ?: 0.0),
                        question, (response?.raw ?: response?.error?.let { "ERROR $it" } ?: "(rules)").replace('\t', ' ').replace('\n', ' '),
                        expected,
                    ).joinToString("\t")
                }
            }
        }

        fun line(label: String, outcomes: List<Outcome>): String {
            val n = outcomes.size
            val field = (0..3).map { f -> outcomes.count { it.hits[f] } }
            val ms = outcomes.filter { it.usedModel }.map { it.ms }.sorted()
            return "%-20s exact %3d/%-3d intent %3d gen %3d type %3d caught %3d unusable %2d modelCalls %3d p50 %5.0f ms".format(
                label, outcomes.count { it.exact }, n, field[0], field[1], field[2], field[3],
                outcomes.count { it.unusable }, outcomes.count { it.usedModel }, ms.getOrElse(ms.size / 2) { 0.0 },
            )
        }

        summary.appendLine("Apple Foundation Models, ${responses.size} responses; prompt r2, have/missing, rules v2, greedy.")
        (sets.map { it.first } + "all").forEach { set ->
            summary.appendLine()
            summary.appendLine("[$set]")
            configs.forEach { config ->
                val outcomes = if (set == "all") sets.flatMap { results[config.name to it.first].orEmpty() } else results[config.name to set].orEmpty()
                summary.appendLine(line(config.name, outcomes))
            }
            val rules = if (set == "all") rulesExact.values.count { it } else rulesExact.filterKeys { it.first == set }.values.count { it }
            val menu = if (set == "all") ceiling.values.sum() else ceiling[set] ?: 0
            summary.appendLine("rules only           exact $rules; the right answer is on the narrowed menu for $menu")
        }

        // Paired against rules only, per question: where the narrowed model and the rules disagree.
        summary.appendLine()
        configs.filter { it.narrower == QuestionCues }.forEach { config ->
            sets.forEach { (set, cases) ->
                val fm = results[config.name to set].orEmpty()
                val onlyFm = cases.indices.count { fm[it].exact && rulesExact[set to it] == false }
                val onlyRules = cases.indices.count { !fm[it].exact && rulesExact[set to it] == true }
                summary.appendLine("${config.name} vs rules only [$set]: only FM right $onlyFm, only rules right $onlyRules")
            }
        }

        File(dir, "rows.tsv").writeText(rows.joinToString("\n", postfix = "\n"))
        File(dir, "summary.txt").writeText(summary.toString())
        println(summary)
    }

    private fun expectedInMenu(options: Options, expected: CollectionQuery): Boolean =
        expected.intent.name.lowercase() in options.intent &&
            (expected.generation?.toString() ?: ANY) in options.generation &&
            (expected.type ?: ANY) in options.type &&
            when (expected.caught) {
                true -> HAVE
                false -> MISSING
                null -> ANY
            } in options.caught

    /** Hands the planner what Foundation Models wrote for this call; a missing or failed call is unusable. */
    private class Replay(private val response: Response?) : LocalModel {
        override val displayName = "Foundation Models (replay)"
        override suspend fun load() = Unit
        override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String =
            response?.raw ?: throw InvalidQueryException(response?.error ?: "no response", "")
    }
}
