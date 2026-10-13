package com.pixel.pokecollect.voice

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A card as the voice stage sees it. [slug] is space-free, so it can be a constrained-decoding enum value. */
data class CardName(val id: Int, val name: String, val slug: String)

/** [score] is 1.0 for the same letters, falling with each edit (see [similarity]). */
data class Candidate(val card: CardName, val score: Double)

/**
 * A run of transcript words that sounds like a card, with the closest names first. [hasTwin]: an
 * exact match, but another card's name is the same (plain "Nidoran": ♀ or ♂) or one letter away
 * (Latias/Latios; the recognizer wrote "Mr. Rime" when Mr. Mime was said). Text can't tell them
 * apart, so the person picks; 26 of 1,025 names have such a twin.
 */
data class Mention(val heard: String, val candidates: List<Candidate>, val hasTwin: Boolean = false) {
    val best: Candidate get() = candidates.first()

    /** Closest name is near-exact and well ahead of the next one: no need to ask the model. */
    val isClear: Boolean
        get() {
            val runnerUp = candidates.getOrNull(1)?.score ?: 0.0
            return !hasTwin && best.score >= CardMatcher.CLEAR_SCORE && best.score - runnerUp >= CardMatcher.CLEAR_MARGIN
        }
}

/**
 * Stage one of voice input: string distance, no model.
 *
 * Speech recognizers mishear names by sound and split or join words unpredictably ("bulb a sore"),
 * so names are compared with spaces and punctuation removed. Every run of up to [MAX_WORDS] words is
 * scored against every name, and dynamic programming picks the runs that cover the transcript best.
 * Filler and number words ("two pikachu", "and") may sit inside a run but not start or end one.
 */
class CardMatcher(cards: List<CardName>) {

    private class NameKey(val letters: String, val sound: String, val card: CardName)

    private val keys: List<NameKey> = cards.flatMap { card ->
        val spelled = NameKey(matchKey(card.name), soundOf(card.name.split(NameWordSeparators).map(::matchKey)), card)
        listOf(spelled) + SpokenAliases[card.slug].orEmpty().map { NameKey(it, soundOf(listOf(it)), card) }
    }

    private val cache = HashMap<String, List<Candidate>>()

    fun mentions(transcript: String): List<Mention> {
        val words = words(transcript)
        val n = words.size
        // best[i]: highest total gain for words[0 until i]; from[i]: start of the run ending at i, or -1 (word skipped).
        val best = DoubleArray(n + 1)
        val from = IntArray(n + 1) { -1 }
        for (i in 1..n) {
            best[i] = best[i - 1]
            for (start in max(0, i - MAX_WORDS) until i) {
                val run = words.subList(start, i)
                val top = candidates(run).firstOrNull() ?: continue
                // A filler at either end must earn its place: it improves the match the other words make
                // ("magic or" → Magikarp beats "magic" alone, "two pikachu" is no better than "pikachu"), or,
                // if they match nothing alone, the whole run must match well ("gang are" → Gengar, but not
                // "you please" → Illumise). An exact match always counts ("porygon two").
                if (top.score < 1.0 && (run.first().isFiller || run.last().isFiller)) {
                    val core = run.dropWhile { it.isFiller }.dropLastWhile { it.isFiller }
                    if (core.isEmpty()) continue
                    val coreTop = candidates(core).firstOrNull()
                    if (if (coreTop != null) top.score <= coreTop.score else top.score < FILLER_EDGE_SCORE) continue
                }
                // Weighted by length, so one long good match beats a short one plus leftovers.
                val gain = run.sumOf { it.key.length } * (top.score - MIN_SCORE)
                if (gain > 0 && best[start] + gain > best[i]) {
                    best[i] = best[start] + gain
                    from[i] = start
                }
            }
        }
        val mentions = ArrayList<Mention>()
        var i = n
        while (i > 0) {
            val start = from[i]
            if (start < 0) {
                i--
            } else {
                val run = words.subList(start, i)
                val candidates = candidates(run)
                val best = candidates.first()
                val twin = best.score == 1.0 && candidates.drop(1).any {
                    it.score == 1.0 || withinOneEdit(matchKey(best.card.name), matchKey(it.card.name))
                }
                mentions += Mention(run.joinToString(" ") { it.text }, candidates, twin)
                i = start
            }
        }
        return mentions.asReversed()
    }

    /**
     * Up to [MAX_CANDIDATES] cards, best first; empty when nothing reaches [minScore]. A card scores
     * the better of two comparisons: by letters, and by a rough pronunciation key ("snore lacks" → snorlax).
     */
    private fun candidates(run: List<Word>): List<Candidate> {
        val letters = run.joinToString("") { it.key }
        val sound = soundOf(run.map { it.key })
        return cache.getOrPut("$letters|$sound") {
            // Too short to compare by distance; only a known spoken form counts ("EV" for Eevee).
            if (letters.length < MIN_KEY_LENGTH) {
                return@getOrPut keys.filter { it.letters == letters }.map { Candidate(it.card, 1.0) }.distinctBy { it.card.id }
            }
            val floor = minScore(letters.length)
            val byCard = HashMap<Int, Candidate>()
            for (name in keys) {
                val score = max(boundedSimilarity(letters, name.letters), boundedSimilarity(sound, name.sound))
                if (score >= floor && score > (byCard[name.card.id]?.score ?: -1.0)) byCard[name.card.id] = Candidate(name.card, score)
            }
            byCard.values.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.card.id }).take(MAX_CANDIDATES)
        }
    }

    /** [similarity], or 0 without computing it when the lengths alone rule out [MIN_SCORE]. */
    private fun boundedSimilarity(a: String, b: String): Double =
        if (abs(a.length - b.length) > max(a.length, b.length) * (1 - MIN_SCORE)) 0.0 else similarity(a, b)

    private class Word(val text: String, val key: String) {
        val isFiller = key in Fillers
    }

    private fun words(transcript: String): List<Word> =
        transcript.split(WordSeparators).mapNotNull { text -> matchKey(text).takeIf { it.isNotEmpty() }?.let { Word(text, it) } }

    internal companion object {
        const val MAX_WORDS = 4
        const val MAX_CANDIDATES = 20
        const val MIN_KEY_LENGTH = 3
        const val MIN_SCORE = 0.55
        const val CLEAR_SCORE = 0.85
        const val CLEAR_MARGIN = 0.1
        const val FILLER_EDGE_SCORE = 0.7

        // Not "-" or "’": they sit inside names (Ho-Oh, Farfetch’d).
        private val WordSeparators = Regex("[\\s,.;:!?]+")

        // For a name's sound key, "Ho-Oh" is two spoken words.
        private val NameWordSeparators = Regex("[\\s,.;:!?-]+")

        /** Rough English spelling-to-sound rules, applied in order to one word's [matchKey]. */
        private val SoundRules = listOf(
            Regex("tch") to "ch",
            Regex("ph") to "f",
            Regex("wh") to "w",
            Regex("qu") to "kw",
            Regex("ck") to "k",
            Regex("c(?=[eiy])") to "s",
            Regex("c(?!h)") to "k",
            Regex("ks") to "x",
            Regex("ee|ea|ey") to "i",
            Regex("oo|ew") to "u",
            Regex("y") to "i",
            Regex("z") to "s",
        )

        /**
         * How a run of words roughly sounds: spellings that sound alike map together, so "snore lacks"
         * and "snorlax", "pick a chew" and "pikachu", "onyx" and "onix" compare as equal.
         */
        fun soundOf(wordKeys: List<String>): String {
            val joined = wordKeys.joinToString("") { word ->
                val s = SoundRules.fold(word) { s, (rule, to) -> rule.replace(s, to) }
                // Silent final e: "sore" → "sor", "hoe" → "ho".
                if (s.length >= 3 && s.endsWith('e')) s.dropLast(1) else s
            }
            // Doubled letters sound single, also across words ("magic carp" → "magikarp").
            return buildString { joined.forEach { if (isEmpty() || last() != it) append(it) } }
        }

        /** Short heard words match short names by accident ("your" ~ Kyogre), so they must match more closely. */
        fun minScore(letters: Int) = when {
            letters <= 4 -> 0.75
            letters <= 6 -> 0.65
            else -> MIN_SCORE
        }

        private val Fillers = setOf(
            "a", "an", "and", "the", "i", "im", "ive", "got", "get", "have", "has", "had", "just", "bought", "caught",
            "also", "plus", "then", "with", "of", "or", "some", "my", "new", "another", "more", "card", "cards",
            "um", "uh", "er", "x", "times", "pokemon", "pack", "packs", "booster",
            "at", "to", "for", "in", "on", "is", "it", "its", "but", "so", "as", "by", "from", "about", "like", "well",
            "you", "your", "me", "we", "our", "he", "she", "they", "them", "this", "that", "these", "those",
            "today", "yesterday", "here", "there", "now", "okay", "ok", "yes", "no", "not", "all", "very", "really",
            "was", "were", "are", "am", "be", "do", "did", "what", "how",
            "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
        ) + (0..99).map(Int::toString)

        /** How the odd names come out of a speech recognizer, by slug. Plain "nidoran" is genuinely ambiguous. */
        private val SpokenAliases = mapOf(
            "nidoran-f" to listOf("nidoran", "nidoranf", "nidorangirl"),
            "nidoran-m" to listOf("nidoran", "nidoranm", "nidoranboy"),
            "mr-mime" to listOf("mistermime"),
            "mime-jr" to listOf("mimejunior"),
            "mr-rime" to listOf("misterrime"),
            "porygon2" to listOf("porygontwo"),
            "porygon-z" to listOf("porygonzee", "porygonzed"),
            "eevee" to listOf("ev", "evie", "eevie"), // what Apple's and Android's recognizers wrote, 2026-10-13
        )

        private val Accents = mapOf('é' to "e", 'è' to "e", 'ê' to "e", 'ë' to "e", 'á' to "a", 'à' to "a", 'ä' to "a", 'ö' to "o", 'ü' to "u")

        /**
         * A name as a person says it, for biasing the recognizer. With "Nidoran♀" as a hint, Apple's
         * recognizer writes "Nidoran♀" for a plain "Nidoran", silently choosing for the person; with only
         * "Nidoran female" it stops hearing plain "Nidoran" at all, so that goes in too.
         */
        fun spokenForms(name: String): List<String> =
            if ('♀' in name || '♂' in name) {
                listOf(name.replace("♀", " female").replace("♂", " male"), name.trimEnd('♀', '♂'))
            } else {
                listOf(name)
            }

        /** Lowercase letters and digits only; ♀/♂ spelled out. "Farfetch’d" → "farfetchd". */
        fun matchKey(text: String): String = buildString {
            for (c in text.lowercase()) {
                when {
                    c in 'a'..'z' || c in '0'..'9' -> append(c)
                    c == '♀' -> append("female")
                    c == '♂' -> append("male")
                    else -> Accents[c]?.let(::append)
                }
            }
        }

        /** One substitution, insertion or deletion apart (Latias/Latios, Woobat/Swoobat). */
        fun withinOneEdit(a: String, b: String): Boolean {
            if (a == b) return true
            if (abs(a.length - b.length) > 1) return false
            val (short, long) = if (a.length <= b.length) a to b else b to a
            var i = 0
            while (i < short.length && short[i] == long[i]) i++
            return if (short.length == long.length) short.substring(i + 1) == long.substring(i + 1) else short.substring(i) == long.substring(i + 1)
        }

        /** 1 − edit distance / longer length. */
        fun similarity(a: String, b: String): Double {
            if (a == b) return 1.0
            return 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
        }

        private fun levenshtein(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = min(substitution, min(previous[j], current[j - 1]) + 1)
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }
    }
}
