package com.pixel.pokecollect.voice

import com.pixel.pokecollect.model.LocalModel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration
import kotlin.time.TimeSource

/** Who settled a heard name. */
enum class PickedBy { SPELLING, MODEL }

data class HeardCard(
    /** The transcript words, as the recognizer wrote them. */
    val heard: String,
    /** Best guess, or null when the model said none of the options fit. */
    val card: CardName?,
    /** Closest names by spelling, best first: what the person can switch to. */
    val alternatives: List<CardName>,
    val pickedBy: PickedBy,
    /** Not sure enough to mark caught without the person looking at it. */
    val needsCheck: Boolean,
)

data class VoiceResolution(
    val transcript: String,
    val cards: List<HeardCard>,
    /** Exactly what the model emitted, or null when it wasn't asked (every name clear by spelling). */
    val rawOutput: String?,
    val latency: Duration,
    /** Why the model couldn't help, if it failed; its names then fall back to spelling and need a check. */
    val modelError: String? = null,
)

/**
 * Turns a transcript into cards in two stages: [CardMatcher] narrows each heard name to its closest
 * spellings, and only the unclear ones go to the model, which picks from those few options under a
 * schema. The model never sees the collection and can't name a card that isn't an option.
 */
class CardResolver(private val model: LocalModel) {

    private var matcher: Pair<List<CardName>, CardMatcher>? = null

    /** Stage one alone (no model): what in [transcript] sounds like a card. Cheap; used to route input. */
    fun mentions(transcript: String, cards: List<CardName>): List<Mention> = matcherFor(cards).mentions(transcript)

    suspend fun resolve(transcript: String, cards: List<CardName>): VoiceResolution {
        val matcher = matcherFor(cards)
        val mentions = matcher.mentions(transcript)
        val ask = mentions.filter { !it.isClear && !it.hasTwin }.distinct().take(MAX_MODEL_NAMES)

        var raw: String? = null
        var error: String? = null
        var picks: Map<Mention, String> = emptyMap()
        val mark = TimeSource.Monotonic.markNow()
        if (ask.isNotEmpty()) {
            try {
                raw = model.generateJson(instructions(examples(matcher)), input(ask), schema(ask)).trim()
                picks = parse(raw, ask)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e::class.simpleName
            }
        }
        val latency = if (ask.isEmpty()) Duration.ZERO else mark.elapsedNow()

        val heard = mentions.map { mention ->
            val alternatives = mention.candidates.map { it.card }
            val slug = picks[mention]
            when {
                mention.isClear -> HeardCard(mention.heard, mention.best.card, alternatives, PickedBy.SPELLING, needsCheck = false)
                slug == null -> HeardCard(mention.heard, mention.best.card, alternatives, PickedBy.SPELLING, needsCheck = true)
                slug == NONE -> HeardCard(mention.heard, null, alternatives, PickedBy.MODEL, needsCheck = true)
                else -> {
                    val pick = mention.candidates.first { it.card.slug == slug }
                    // Trust the model when it agrees with spelling on a reasonable match; otherwise show it.
                    val agrees = pick == mention.best && pick.score >= AGREE_SCORE
                    HeardCard(mention.heard, pick.card, alternatives, PickedBy.MODEL, needsCheck = !agrees)
                }
            }
        }
        return VoiceResolution(transcript, oneRowPerCard(heard), raw, latency, error)
    }

    /**
     * No quantities: a card said twice is one card. A name still to be checked defaults to its closest
     * card that nothing sure has claimed, so "a Nidoran and a Nidoran female" gives ♂ (to check) and ♀.
     */
    private fun oneRowPerCard(heard: List<HeardCard>): List<HeardCard> {
        val claimed = heard.filter { !it.needsCheck }.mapNotNull { it.card?.id }.toMutableSet()
        val shown = HashSet<Int>()
        return heard.mapNotNull { h ->
            val card = h.card ?: return@mapNotNull h
            if (!h.needsCheck) return@mapNotNull h.takeIf { shown.add(card.id) }
            val free = if (card.id !in claimed) card else h.alternatives.firstOrNull { it.id !in claimed } ?: return@mapNotNull null
            claimed += free.id
            shown += free.id
            h.copy(card = free)
        }
    }

    private fun matcherFor(cards: List<CardName>): CardMatcher =
        matcher?.takeIf { it.first === cards }?.second ?: CardMatcher(cards).also { matcher = cards to it }

    /** One numbered line per name: the heard words and their options (slugs: space-free enum values). */
    internal fun input(mentions: List<Mention>): String =
        mentions.mapIndexed { i, m -> "${i + 1}. heard \"${m.heard}\": ${options(m).joinToString(", ")}" }.joinToString("\n")

    internal fun schema(mentions: List<Mention>): String = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            mentions.forEachIndexed { i, m ->
                putJsonObject("${i + 1}") {
                    put("type", "string")
                    putJsonArray("enum") { options(m).forEach { add(it) } }
                }
            }
        }
        putJsonArray("required") { mentions.indices.forEach { add("${it + 1}") } }
        put("additionalProperties", false)
    }.toString()

    /** Validates every field against its own options; constrained decoding should make this a formality. */
    internal fun parse(raw: String, mentions: List<Mention>): Map<Mention, String> {
        val fields = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: SerializationException) {
            throw IllegalStateException("Model output is not JSON: $raw", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Model output is not a JSON object: $raw", e)
        }
        return mentions.withIndex().associate { (i, m) ->
            val value = (fields["${i + 1}"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            check(value != null && value in options(m)) { "Name ${i + 1} \"$value\" is not one of its options" }
            m to value
        }
    }

    internal fun instructions(examples: List<Pair<Mention, String>>): String = buildString {
        appendLine("A speech recognizer wrote down Pokémon names the way they sounded, often wrongly.")
        appendLine("Each numbered line gives the words it heard and the Pokémon they could be.")
        appendLine("For each line, choose the option that sounds most like the heard words when said out loud.")
        appendLine("Choose \"none\" only if no option sounds like them.")
        appendLine()
        appendLine("Example:")
        appendLine(input(examples.map { it.first }))
        append(json.encodeToString(JsonObject.serializer(), answer(examples)))
    }

    private fun options(mention: Mention) = mention.candidates.take(MODEL_OPTIONS).map { it.card.slug } + NONE

    /** The prompt's examples, with options from the same matcher the real input uses. */
    private fun examples(matcher: CardMatcher): List<Pair<Mention, String>> =
        Examples.mapNotNull { (heard, slug) ->
            matcher.mentions(heard).singleOrNull()?.takeIf { m -> m.candidates.any { it.card.slug == slug } }?.let { it to slug }
        }

    private fun answer(examples: List<Pair<Mention, String>>) =
        JsonObject(examples.withIndex().associate { (i, e) -> "${i + 1}" to JsonPrimitive(e.second) })

    internal companion object {
        const val NONE = "none"

        /** One model call per transcript, small enough for the Android 64-token output cap. */
        const val MAX_MODEL_NAMES = 5
        const val MODEL_OPTIONS = 6
        const val AGREE_SCORE = 0.7

        /** Mishearings the matcher can't settle alone. Kept out of the tests' phrases. */
        val Examples = listOf("dragon knight" to "dragonite", "sigh ther" to "scyther")

        val json = Json
    }
}
