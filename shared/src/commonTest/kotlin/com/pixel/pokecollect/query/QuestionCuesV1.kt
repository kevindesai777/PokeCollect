package com.pixel.pokecollect.query

// FROZEN: round-1 rules exactly as evaluated on 2026-10-06 (dev 19/20, held-out v1 31/40).
// Kept only so later rounds can be compared against it on the same test set. Do not edit.

/**
 * Narrow without the model, resolve with the model. Deterministic rules read the question and
 * decide which values each field may take; the model only chooses among those. A filter the
 * question never mentions can't be invented, and a question with one possible reading never
 * reaches the model at all.
 */
internal object QuestionCuesV1 : Narrower {

    override fun options(question: String, catalog: Catalog): Options {
        val text = " " + question.lowercase().replace('’', '\'') + " "
        val all = Options.all(catalog)

        val generations = generationsMentioned(text).distinct()
        generations.firstOrNull { it !in 1..catalog.maxGeneration }?.let {
            throw UnsupportedQuestionException("Gen $it isn't in this dataset. It covers Gens 1–${catalog.maxGeneration}.")
        }
        val types = catalog.types.filter { typePattern(it).containsMatchIn(text) }

        return Options(
            intent = if (COUNT.containsMatchIn(text)) listOf(QueryIntent.COUNT.name.lowercase()) else all.intent,
            generation = if (generations.isEmpty()) listOf(ANY) else generations.map { it.toString() },
            type = if (types.isEmpty()) listOf(ANY) else types,
            // Ownership is discussed: the model must decide which way (negation is its job).
            caught = if (mentionsOwnership(text)) listOf(HAVE, MISSING) else listOf(ANY),
        )
    }

    private val COUNT = Regex("""\b(how many|how much|number of|count|total)\b""")

    // --- Generations: "gen 3", "gen3", "generation 3", "3rd gen", "third generation", "generation three", "gen iii"

    private val numberWords = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    private val ordinalWords = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")
    private val romanNumerals = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")
    private const val GEN = "(?:generation|gen)"

    private val digitAfter = Regex("""\b$GEN\s*[-#]?\s*(\d{1,2})\b""")
    private val digitBefore = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s-]*${GEN}s?\b""")
    private val ordinalBefore = Regex("""\b(${ordinalWords.joinToString("|")})[\s-]*${GEN}s?\b""")
    private val wordAfter = Regex("""\b$GEN\s+(${numberWords.joinToString("|")})\b""")
    private val romanAfter = Regex("""\b$GEN\s+(${romanNumerals.sortedByDescending { it.length }.joinToString("|")})\b""")

    private fun generationsMentioned(text: String): List<Int> = buildList {
        digitAfter.findAll(text).forEach { add(it.groupValues[1].toInt()) }
        digitBefore.findAll(text).forEach { add(it.groupValues[1].toInt()) }
        ordinalBefore.findAll(text).forEach { add(ordinalWords.indexOf(it.groupValues[1]) + 1) }
        wordAfter.findAll(text).forEach { add(numberWords.indexOf(it.groupValues[1]) + 1) }
        romanAfter.findAll(text).forEach { add(romanNumerals.indexOf(it.groupValues[1]) + 1) }
    }

    // --- Types: the name or its plural ("dragons", "fairies"), as a whole word.

    private val typePatterns = mutableMapOf<String, Regex>()

    private fun typePattern(type: String): Regex = typePatterns.getOrPut(type) {
        val forms = buildList {
            add(type)
            add(type + "s")
            add(type + "es")
            if (type.endsWith("y")) add(type.dropLast(1) + "ies")
        }
        Regex("""\b(${forms.joinToString("|")})\b""")
    }

    // --- Ownership: words that only make sense if the question is about what the user has or lacks.

    private val ownershipWords = listOf(
        Regex("""\b(my|mine|our)\b"""),
        Regex("""\b(own|owns|owned|caught|collected|missing|lack|lacking|remaining)\b"""),
        Regex("""\bneed(s|ed)?\b(?!\s+to\b)"""), // "still need", but not "I need to see"
        Regex("""\b(left to|what's left|whats left|still have|still got)\b"""),
        Regex("""\b(i've|ive|we've)\b"""),
    )

    // "do I have", "cards I have", "don't I have", but not the polite "can I have a list".
    private val firstPersonHave = Regex("""(?:(\S+)\s+)?\b(?:i|we)\s+(?:have|own|got)\b""")
    private val modals = setOf("can", "could", "may", "might", "would", "will")

    private fun mentionsOwnership(text: String): Boolean =
        ownershipWords.any { it.containsMatchIn(text) } ||
            firstPersonHave.findAll(text).any { it.groupValues[1] !in modals }
}
