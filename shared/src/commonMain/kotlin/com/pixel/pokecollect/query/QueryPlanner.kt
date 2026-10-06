package com.pixel.pokecollect.query

import com.pixel.pokecollect.model.LocalModel
import com.pixel.pokecollect.query.QueryIntent.COUNT
import com.pixel.pokecollect.query.QueryIntent.LIST
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration
import kotlin.time.TimeSource

data class PlannedQuery(
    val question: String,
    val query: CollectionQuery,
    /** Exactly what the model emitted, or the rule-built JSON when no model was needed. */
    val rawOutput: String,
    val latency: Duration,
    /** False when the question's words left only one possible query, so the model was skipped. */
    val usedModel: Boolean,
)

class InvalidQueryException(message: String, val rawOutput: String) : Exception(message)

/**
 * What the model actually writes. Every field is a word from a fixed list, with an explicit "any":
 * Gemma 3 1B effectively never emits JSON null (0 of 60 fields in the on-device eval), so a nullable
 * schema made it invent a value instead.
 */
@Serializable
internal data class WireQuery(val intent: String, val generation: String, val type: String, val caught: String)

/** The words the model writes for the two ownership directions. Canonical values stay [HAVE]/[MISSING]. */
internal data class WireLabels(val have: String, val missing: String) {
    fun toWire(canonical: String) = when (canonical) {
        HAVE -> have
        MISSING -> missing
        else -> canonical
    }

    fun fromWire(wire: String) = when (wire) {
        have -> HAVE
        missing -> MISSING
        else -> wire
    }

    companion object {
        val HaveMissing = WireLabels(HAVE, MISSING)
    }
}

/** A few-shot example, stored as data and rendered with whatever [WireLabels] are in use. */
internal data class Example(val question: String, val query: CollectionQuery)

/** Versioned prompts, so earlier configurations can be re-run exactly for comparison. */
internal enum class Prompt(val examples: List<Example>, val spellOutNegation: Boolean) {
    /** As evaluated on 2026-10-06 (dev 19/20, held-out v1 31/40 with round-1 rules). */
    Round1(QueryPlanner.Round1Examples, spellOutNegation = false),

    /** Adds negation guidance and examples: the failure classes seen while tuning. */
    Round2(QueryPlanner.Round2Examples, spellOutNegation = true),
}

/** Turns a question into a [CollectionQuery]. The model parses; it never answers. */
class QueryPlanner internal constructor(
    private val model: LocalModel,
    private val narrower: Narrower,
    private val labels: WireLabels,
    private val prompt: Prompt,
) {
    constructor(model: LocalModel) : this(model, QuestionCues, WireLabels.HaveMissing, Prompt.Round2)

    suspend fun plan(question: String, catalog: Catalog): PlannedQuery {
        val input = question.trim()
        val options = narrower.options(input, catalog)

        options.determined()?.let { wire ->
            val raw = json.encodeToString(WireQuery.serializer(), wire.copy(caught = labels.toWire(wire.caught)))
            return PlannedQuery(input, parse(raw, options), raw, Duration.ZERO, usedModel = false)
        }

        val mark = TimeSource.Monotonic.markNow()
        val raw = model.generateJson(instructions(catalog), input, jsonSchema(options))
        val latency = mark.elapsedNow()
        return PlannedQuery(input, parse(raw, options), raw.trim(), latency, usedModel = true)
    }

    /** Validates against this question's [options], not just the global schema. */
    internal fun parse(raw: String, options: Options): CollectionQuery {
        val wire = try {
            json.decodeFromString<WireQuery>(raw.trim())
        } catch (e: SerializationException) {
            throw InvalidQueryException("Model output is not a valid query: ${e.message}", raw)
        } catch (e: IllegalArgumentException) {
            throw InvalidQueryException("Model output is not a valid query: ${e.message}", raw)
        }
        fun check(field: String, value: String, allowed: List<String>): String {
            if (value !in allowed) {
                throw InvalidQueryException("$field \"$value\" is not allowed here (${allowed.joinToString("/")})", raw)
            }
            return value
        }
        return CollectionQuery(
            intent = QueryIntent.entries.first { it.name.lowercase() == check("intent", wire.intent, options.intent) },
            generation = check("generation", wire.generation, options.generation).takeIf { it != ANY }?.toInt(),
            type = check("type", wire.type, options.type).takeIf { it != ANY },
            caught = when (check("caught", labels.fromWire(wire.caught), options.caught)) {
                HAVE -> true
                MISSING -> false
                else -> null
            },
        )
    }

    internal fun jsonSchema(options: Options): String = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            enumField("intent", options.intent)
            enumField("generation", options.generation)
            enumField("type", options.type)
            enumField("caught", options.caught.map(labels::toWire))
        }
        putJsonArray("required") { listOf("intent", "generation", "type", "caught").forEach { add(it) } }
        put("additionalProperties", false)
    }.toString()

    internal fun instructions(catalog: Catalog): String = buildString {
        appendLine("You convert a question about a Pokémon card collection into a JSON query. Do not answer the question.")
        appendLine()
        appendLine("Fields:")
        appendLine("- intent: \"count\" if the question asks how many, otherwise \"list\".")
        appendLine("- generation: \"1\" to \"${catalog.maxGeneration}\" only if the question names a generation (\"Gen 3\", \"third generation\"). Otherwise \"any\".")
        appendLine("- type: one of ${catalog.types.joinToString(", ")} only if the question names that type. Otherwise \"any\".")
        if (prompt.spellOutNegation) {
            appendLine(
                "- caught: \"${labels.have}\" for cards the user has, owns or caught; " +
                    "\"${labels.missing}\" for cards the user does not have, including \"haven't\", \"don't have\", " +
                    "\"not yet\", \"still need\" and \"left to collect\"; \"any\" if the question does not say.",
            )
        } else {
            appendLine(
                "- caught: \"${labels.have}\" for cards the user has, owns or caught; " +
                    "\"${labels.missing}\" for cards they are missing or still need; \"any\" if the question does not say.",
            )
        }
        appendLine()
        appendLine("Use \"any\" whenever the question does not mention that field. Never guess.")
        appendLine()
        appendLine("Examples:")
        prompt.examples.forEach { (question, q) ->
            appendLine("Q: $question")
            appendLine(
                json.encodeToString(
                    WireQuery.serializer(),
                    WireQuery(
                        intent = q.intent.name.lowercase(),
                        generation = q.generation?.toString() ?: ANY,
                        type = q.type ?: ANY,
                        caught = labels.toWire(
                            when (q.caught) {
                                true -> HAVE
                                false -> MISSING
                                null -> ANY
                            },
                        ),
                    ),
                ),
            )
        }
    }.trimEnd()

    // An extension on the builder it writes into. A local fun inside buildJsonObject captured the
    // *outer* builder and silently put every field at the top level instead of under "properties".
    private fun JsonObjectBuilder.enumField(name: String, values: List<String>) = putJsonObject(name) {
        put("type", "string")
        putJsonArray("enum") { values.forEach { add(it) } }
    }

    internal companion object {
        val json = Json { ignoreUnknownKeys = false }

        // Few-shot examples deliberately avoid the live demo questions and every eval question.
        val Round1Examples = listOf(
            Example("Which Gen 5 grass cards don't I have yet?", CollectionQuery(LIST, 5, "grass", false)),
            Example("How many ice types are there?", CollectionQuery(COUNT, null, "ice", null)),
            Example("Show me the cards I own from generation 4", CollectionQuery(LIST, 4, null, true)),
            Example("How many cards am I missing?", CollectionQuery(COUNT, null, null, false)),
            Example("List everything", CollectionQuery(LIST, null, null, null)),
        )

        /** Round 1 plus negation and first-person possession, the two failure classes seen in tuning. */
        val Round2Examples = Round1Examples + listOf(
            Example("which steel cards havent i caught", CollectionQuery(LIST, null, "steel", false)),
            Example("Show me the gen 6 cards I've collected", CollectionQuery(LIST, 6, null, true)),
            Example("What's still left to catch from the ninth generation?", CollectionQuery(LIST, 9, null, false)),
        )
    }
}
