package com.pixel.pokecollect.model

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.pokecollect.query.Catalog
import com.pixel.pokecollect.query.CollectionQuery
import com.pixel.pokecollect.query.EvalCases
import com.pixel.pokecollect.query.EvalTypes
import com.pixel.pokecollect.query.HeldOutV1Cases
import com.pixel.pokecollect.query.HeldOutV2Cases
import com.pixel.pokecollect.query.Narrower
import com.pixel.pokecollect.query.NoNarrowing
import com.pixel.pokecollect.query.Prompt
import com.pixel.pokecollect.query.QueryPlanner
import com.pixel.pokecollect.query.QuestionCues
import com.pixel.pokecollect.query.QuestionCuesV1
import com.pixel.pokecollect.query.RulesOnlyModel
import com.pixel.pokecollect.query.WireLabels
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scores query planning on the device, in airplane mode, with the real model. A report, not a
 * gate: per-question lines and a RESULT line per configuration, under the "PokeCollect.Eval" tag.
 * Needs the model pushed to the device. Run one or more configurations with
 *
 *   ./gradlew :shared:connectedAndroidDeviceTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.pixel.pokecollect.model.ModelEvalTest#tuneHybrid
 *
 * Tuning uses only questions already seen ([EvalCases] + [HeldOutV1Cases]). Final numbers come
 * from held-out v2, which no configuration was tuned on.
 */
@RunWith(AndroidJUnit4::class)
class ModelEvalTest {

    private val tuningPool = EvalCases + HeldOutV1Cases
    private val yesNo = WireLabels(have = "yes", missing = "no")

    // --- Tuning (seen questions only)

    @Test fun tuneHybrid(): Unit = score("tune / rules v2 + prompt r2", tuningPool, QuestionCues, Prompt.Round2)

    @Test fun tuneHybridYesNo(): Unit = score("tune / rules v2 + prompt r2 + yes/no", tuningPool, QuestionCues, Prompt.Round2, yesNo)

    @Test fun tuneHybridRound1Prompt(): Unit = score("tune / rules v2 + prompt r1", tuningPool, QuestionCues, Prompt.Round1)

    @Test fun tuneRulesOnly(): Unit = score("tune / rules only", tuningPool, QuestionCues, Prompt.Round2, model = Rules)

    // --- Final: held-out v2, every configuration on every question, interleaved

    @Test fun finalV2Part1(): Unit = finalRun(part = 1)

    @Test fun finalV2Part2(): Unit = finalRun(part = 2)

    @Test fun finalV2Part3(): Unit = finalRun(part = 3)

    @Test fun finalV2Part4(): Unit = finalRun(part = 4)

    /** Same engine, constraint switched off per call. */
    private class Unconstrained(private val gemma: LiteRtLmModel) : LocalModel {
        override val displayName = gemma.displayName
        override suspend fun load() = gemma.load()
        override suspend fun generateJson(instructions: String, input: String, jsonSchema: String) =
            gemma.generateJson(instructions, input, jsonSchema, constrained = false)
    }

    /**
     * One engine, seven configurations. Each question runs through every configuration, in an
     * order rotated by question index, so thermal drift and run order hit all configurations
     * equally. Emits one machine-readable EVALROW line per (configuration, question).
     */
    private fun finalRun(part: Int): Unit = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val gemma = LiteRtLmModel(context)
        val hm = WireLabels.HaveMissing
        val configs: List<Pair<String, QueryPlanner>> = listOf(
            "model-only r1" to QueryPlanner(gemma, NoNarrowing, hm, Prompt.Round1),
            "model-only r2" to QueryPlanner(gemma, NoNarrowing, hm, Prompt.Round2),
            "model-only r2 unconstrained" to QueryPlanner(Unconstrained(gemma), NoNarrowing, hm, Prompt.Round2),
            "hybrid r1" to QueryPlanner(gemma, QuestionCuesV1, hm, Prompt.Round1),
            "hybrid r2" to QueryPlanner(gemma, QuestionCues, hm, Prompt.Round2),
            "hybrid r2 unconstrained" to QueryPlanner(Unconstrained(gemma), QuestionCues, hm, Prompt.Round2),
            "rules-only v2" to QueryPlanner(RulesOnlyModel(), QuestionCues, hm, Prompt.Round2),
        )
        val catalog = Catalog(maxGeneration = 9, types = EvalTypes)
        val slice = HeldOutV2Cases.withIndex().toList().chunked((HeldOutV2Cases.size + 3) / 4)[part - 1]
        gemma.load()
        Log.i(TAG, "PART $part start batteryTempC=${batteryTempC(context)} questions=${slice.first().index}..${slice.last().index}")
        try {
            slice.forEach { (index, case) ->
                val (question, expected) = case
                val order = configs.indices.map { (it + index) % configs.size }
                order.forEach { c ->
                    val (name, planner) = configs[c]
                    val result = runCatching { planner.plan(question, catalog) }
                    val planned = result.getOrNull()
                    val got = planned?.query
                    val hits = listOf(
                        got?.intent == expected.intent,
                        got?.generation == expected.generation,
                        got?.type == expected.type,
                        got?.caught == expected.caught,
                    )
                    val raw = (planned?.rawOutput ?: ("ERROR " + result.exceptionOrNull()?.message)).replace('\t', ' ').replace('\n', ' ')
                    Log.i(TAG, listOf(
                        "EVALROW", name, index, if (hits.all { it }) 1 else 0, hits.joinToString("") { if (it) "1" else "0" },
                        if (planned == null) 1 else 0, if (planned?.usedModel == true) 1 else 0,
                        planned?.latency?.inWholeMilliseconds ?: -1, question, raw, expected,
                    ).joinToString("\t"))
                }
            }
        } finally {
            gemma.close()
        }
        Log.i(TAG, "PART $part end batteryTempC=${batteryTempC(context)}")
    }

    private fun batteryTempC(context: Context): Double {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0
    }

    // --- Configuration plumbing

    private sealed interface ModelChoice
    private data class Gemma(val constrained: Boolean = true) : ModelChoice
    private data object Rules : ModelChoice

    private fun score(
        label: String,
        cases: List<Pair<String, CollectionQuery>>,
        narrower: Narrower,
        prompt: Prompt,
        labels: WireLabels = WireLabels.HaveMissing,
        model: ModelChoice = Gemma(),
    ): Unit = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val localModel: LocalModel = when (model) {
            is Gemma -> LiteRtLmModel(context, model.constrained)
            Rules -> RulesOnlyModel()
        }
        val planner = QueryPlanner(localModel, narrower, labels, prompt)
        val catalog = Catalog(maxGeneration = 9, types = EvalTypes)
        localModel.load()

        var exact = 0
        var modelCalls = 0
        var unusable = 0
        val fieldHits = IntArray(4)
        val latencies = mutableListOf<Long>()
        try {
            cases.forEach { (question, expected) ->
                val result = runCatching { planner.plan(question, catalog) }
                val planned = result.getOrNull()
                if (planned == null) unusable++
                if (planned?.usedModel == true) {
                    modelCalls++
                    latencies += planned.latency.inWholeMilliseconds
                }
                val got: CollectionQuery? = planned?.query
                val hits = listOf(
                    got?.intent == expected.intent,
                    got?.generation == expected.generation,
                    got?.type == expected.type,
                    got?.caught == expected.caught,
                )
                hits.forEachIndexed { i, hit -> if (hit) fieldHits[i]++ }
                if (hits.all { it }) exact++
                val mark = if (hits.all { it }) "PASS" else "FAIL"
                val via = if (planned?.usedModel == false) " (rules)" else ""
                Log.i(TAG, "[$label] $mark$via \"$question\" -> ${planned?.rawOutput ?: result.exceptionOrNull()?.message}" +
                    if (hits.all { it }) "" else "  expected $expected")
            }
        } finally {
            (localModel as? LiteRtLmModel)?.close()
        }
        val n = cases.size
        val sorted = latencies.sorted()
        Log.i(TAG, "[$label] RESULT exact=$exact/$n intent=${fieldHits[0]}/$n generation=${fieldHits[1]}/$n " +
            "type=${fieldHits[2]}/$n caught=${fieldHits[3]}/$n unusable=$unusable modelCalls=$modelCalls/$n " +
            "modelLatencyMs p50=${sorted.getOrNull(sorted.size / 2)} max=${sorted.lastOrNull()}")
    }

    private companion object {
        const val TAG = "PokeCollect.Eval"

    }
}
