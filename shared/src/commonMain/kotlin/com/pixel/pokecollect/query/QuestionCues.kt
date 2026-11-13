package com.pixel.pokecollect.query

internal const val ANY = "any"
internal const val HAVE = "have"
internal const val MISSING = "missing"

/**
 * Several generations travel as one wire value ("3,6"), so a question naming two generations offers
 * the model one option holding both, not a choice between them. A single generation stays "3".
 */
internal fun generationsToWire(generations: List<Int>): String =
    if (generations.isEmpty()) ANY else generations.distinct().sorted().joinToString(",")

internal fun generationsFromWire(wire: String): List<Int> =
    if (wire == ANY) emptyList() else wire.split(',').map { it.trim().toInt() }

/** The question names something the dataset can't answer, e.g. a generation that hasn't shipped. */
class UnsupportedQuestionException(message: String) : Exception(message)

/** The values each field may take for one particular question. Becomes that question's JSON schema. */
internal data class Options(
    val intent: List<String>,
    val generation: List<String>,
    val type: List<String>,
    val caught: List<String>,
) {
    /** The only possible query when every field has a single option; null when the model must choose. */
    fun determined(): WireQuery? =
        if (listOf(intent, generation, type, caught).all { it.size == 1 }) {
            WireQuery(intent.single(), generation.single(), type.single(), caught.single())
        } else {
            null
        }

    companion object {
        fun all(catalog: Catalog) = Options(
            intent = QueryIntent.entries.map { it.name.lowercase() },
            generation = listOf(ANY) + (1..catalog.maxGeneration).map { it.toString() },
            type = listOf(ANY) + catalog.types,
            caught = listOf(ANY, HAVE, MISSING),
        )
    }
}

/** Decides which values each query field may take for one question. */
internal fun interface Narrower {
    fun options(question: String, catalog: Catalog): Options
}

/** No narrowing: every question gets the full schema (the "model only" configuration). */
internal val NoNarrowing = Narrower { _, catalog -> Options.all(catalog) }

/**
 * Narrow without the model, resolve with the model. Deterministic rules read the question and
 * decide which values each field may take; the model only chooses among those.
 *
 * Narrowing is proportional to the evidence: an exact mention decides a field, a misspelled
 * mention (recovered by edit distance) only *offers* its value next to "any", and no mention
 * means "any". So a filter the question never mentions can't be invented, a typo doesn't erase a
 * filter, and a question with one possible reading never reaches the model at all.
 */
internal object QuestionCues : Narrower {

    override fun options(question: String, catalog: Catalog): Options {
        val text = normalize(question)
        val corrected = correctSpelling(text, catalog)
        val all = Options.all(catalog)

        val exactGenerations = generationsMentioned(text).distinct()
        exactGenerations.firstOrNull { it !in 1..catalog.maxGeneration }?.let {
            throw UnsupportedQuestionException("Gen $it isn't in this dataset. It covers Gens 1–${catalog.maxGeneration}.")
        }
        val fuzzyGenerations = (generationsMentioned(corrected).distinct() - exactGenerations.toSet())
            .filter { it in 1..catalog.maxGeneration }
        val exactTypes = catalog.types.filter { typePattern(it).containsMatchIn(text) }
        val fuzzyTypes = catalog.types.filter { typePattern(it).containsMatchIn(corrected) } - exactTypes.toSet()

        return Options(
            intent = if (COUNT.containsMatchIn(text)) listOf(QueryIntent.COUNT.name.lowercase()) else all.intent,
            // Every generation named belongs to one value: "gen 3 and gen 6" means both, never a choice.
            generation = narrowed(exactGenerations.asWireOption(), fuzzyGenerations.asWireOption()),
            type = narrowed(exactTypes, fuzzyTypes),
            // Ownership is discussed: the model must decide which way (negation is its job).
            caught = if (mentionsOwnership(text)) listOf(HAVE, MISSING) else listOf(ANY),
        )
    }

    private fun List<Int>.asWireOption() = if (isEmpty()) emptyList() else listOf(generationsToWire(this))

    /** Exact mentions decide; spelling-corrected mentions only offer; nothing means "any". */
    private fun narrowed(exact: List<String>, fuzzy: List<String>): List<String> = when {
        exact.isNotEmpty() -> exact
        fuzzy.isNotEmpty() -> fuzzy + ANY
        else -> listOf(ANY)
    }

    /**
     * Whether an ownership question points at cards the user does NOT have. Not used by the app
     * (the model decides direction); it is the deterministic baseline the model is measured against.
     */
    fun negatesOwnership(question: String): Boolean = NEGATION.containsMatchIn(normalize(question))

    private fun normalize(question: String) = " " + question.lowercase().replace('’', '\'') + " "

    private val COUNT = Regex("""\b(how many|how much|number of|count|total)\b""")

    private val NEGATION = Regex(
        """\b(missing|need|needs|needed|lack|lacking|left|remaining|uncaught|not|never|without|""" +
            """don't|dont|doesn't|doesnt|didn't|didnt|haven't|havent|hasn't|hasnt|aren't|arent)\b|\b(yet|still) to\b""",
    )

    // --- Generations: "gen 3", "gen3", "generation 3", "3rd gen", "third generation", "generation three", "gen iii",
    // lists ("gen 3 and 6", "gens 3, 4 or 6", "third and sixth generations") and ranges ("gen 3-6",
    // "gens 3 to 6", "between gen 3 and 6"). A card has one generation, so any list means all of them.

    private val numberWords = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    private val ordinalWords = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")
    private val romanNumerals = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")
    private const val GEN = "(?:generations?|gens?)"

    /** "3, 4 and 6", "3 or 6", "3 & 6", "3/6". Needs a separator: "gen 3 25 cards" is not a list. */
    private const val AND = """\s*(?:,\s*(?:(?:and|or)\s+)?|&\s*|\+\s*|/\s*|\b(?:and|or)\s+)"""

    private fun series(item: String, more: String = item) = """($item(?:$AND$more)*)"""
    private fun alternatives(words: List<String>) = words.sortedByDescending { it.length }.joinToString("|")

    private val digitsAfter = Regex("""\b$GEN\s*[-#]?\s*${series("""\d{1,2}\b""")}""")
    private val digitsBefore = Regex("""\b${series("""\d{1,2}(?:st|nd|rd|th)?\b""")}[\s-]*$GEN\b""")
    private val ordinalsBefore = Regex("""\b${series("""(?:${alternatives(ordinalWords)})\b""")}[\s-]*$GEN\b""")
    private val wordsAfter = Regex("""\b$GEN\s+${series("""(?:${alternatives(numberWords)})\b""")}""")

    // A lone "i" after the first numeral is the pronoun ("gen iv and i ..."), not Gen 1.
    private val romanAfter = Regex(
        """\b$GEN\s+${series("""(?:${alternatives(romanNumerals)})\b""", """(?:${alternatives(romanNumerals - "i")})\b""")}""",
    )

    private const val TO = """\s*(?:-|–|—|\bto\b|\bthrough\b|\bthru\b|\buntil\b)\s*"""
    private val rangeAfter = Regex("""\b$GEN\s*[-#]?\s*(\d{1,2})$TO(?:$GEN\s*[-#]?\s*)?(\d{1,2})\b""")
    private val rangeBetween = Regex("""\bbetween\s+$GEN\s*[-#]?\s*(\d{1,2})\s+and\s+(?:$GEN\s*[-#]?\s*)?(\d{1,2})\b""")

    private val digits = Regex("""\d+""")
    private fun wordsOf(text: String, words: List<String>) =
        Regex("""\b(${alternatives(words)})\b""").findAll(text).map { words.indexOf(it.value) + 1 }

    private fun generationsMentioned(text: String): List<Int> = buildList {
        digitsAfter.findAll(text).forEach { m -> digits.findAll(m.groupValues[1]).forEach { add(it.value.toInt()) } }
        digitsBefore.findAll(text).forEach { m -> digits.findAll(m.groupValues[1]).forEach { add(it.value.toInt()) } }
        ordinalsBefore.findAll(text).forEach { addAll(wordsOf(it.groupValues[1], ordinalWords)) }
        wordsAfter.findAll(text).forEach { addAll(wordsOf(it.groupValues[1], numberWords)) }
        romanAfter.findAll(text).forEach { addAll(wordsOf(it.groupValues[1], romanNumerals)) }
        (rangeAfter.findAll(text) + rangeBetween.findAll(text)).forEach { m ->
            val (from, to) = m.destructured
            val ends = listOf(from.toInt(), to.toInt())
            addAll(ends.min()..ends.max())
        }
    }

    // --- Types: the name or its plural ("dragons", "fairies"), as a whole word.

    private val typePatterns = mutableMapOf<String, Regex>()

    private fun forms(type: String) = buildList {
        add(type)
        add(type + "s")
        add(type + "es")
        if (type.endsWith("y")) add(type.dropLast(1) + "ies")
    }

    private fun typePattern(type: String): Regex = typePatterns.getOrPut(type) {
        Regex("""\b(${forms(type).joinToString("|")})\b""")
    }

    // --- Spelling: snap near-misses of domain words ("pyschic", "genration") to the real word.

    private val WORD = Regex("""[a-z]+""")

    private fun correctSpelling(text: String, catalog: Catalog): String {
        // Only longer words: short ones ("fire", "ice", "gen") are too close to ordinary English.
        val vocabulary = listOf("generation", "generations") + catalog.types.filter { it.length >= 5 }.flatMap(::forms)
        return WORD.replace(text) { match ->
            val word = match.value
            if (word.length < 5 || word in vocabulary) {
                word
            } else {
                vocabulary
                    .map { it to editDistance(word, it) }
                    .filter { (candidate, distance) -> distance <= if (candidate.length >= 8) 2 else 1 }
                    .minByOrNull { it.second }
                    ?.first ?: word
            }
        }
    }

    /** Optimal-string-alignment distance: insertions, deletions, substitutions and adjacent swaps. */
    internal fun editDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }

    // --- Ownership: words and constructions that only make sense if the question is about what
    // the user has or lacks.

    private val ownershipCues = listOf(
        Regex("""\b(my|mine|our)\b"""),
        Regex("""\b(own|owns|owned|caught|uncaught|collected|missing|lack|lacking|remaining)\b"""),
        Regex("""\bneed(s|ed)?\b(?!\s+to\b)"""), // "still need", but not "I need to see"
        Regex("""\bneed(s|ed)?\s+to\s+(catch|collect|get|find)\b"""),
        Regex("""\b(left|yet|still) to (collect|catch|get|find)\b|\bwhat'?s left\b|\bstill (have|got)\b"""),
        Regex("""\b(i've|ive|we've)\b"""),
    )

    // First person, up to two auxiliaries/adverbs/negators, then a possession verb:
    // "do I have", "I already have", "I don't have", "I do not have", "I still need", "haven't I got".
    // The word before "I" must not be a modal, so the polite "can I have a list" doesn't count.
    private val firstPersonPossession = Regex(
        """(?:(\S+)\s+)?\b(?:i|we)\s+""" +
            """(?:(?:do|does|did|don't|dont|doesn't|doesnt|didn't|didnt|not|never|already|still|currently|""" +
            """actually|really|also|just|yet|have|haven't|havent|has|hasn't|hasnt)\s+){0,2}""" +
            """(?:(?:have|has|own|owned|got|gotten|caught|collected|lack|miss|missed)\b|need(?:ed)?\b(?!\s+to\b))""",
    )
    private val modals = setOf("can", "could", "may", "might", "would", "will")

    private fun mentionsOwnership(text: String): Boolean =
        ownershipCues.any { it.containsMatchIn(text) } ||
            firstPersonPossession.findAll(text).any { it.groupValues[1] !in modals }
}
