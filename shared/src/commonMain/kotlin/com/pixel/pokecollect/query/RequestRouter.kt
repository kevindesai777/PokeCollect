package com.pixel.pokecollect.query

import com.pixel.pokecollect.voice.Mention

enum class RequestKind { QUESTION, CARDS }

/**
 * One input for two jobs: a question about the collection, or cards to mark caught. Typed or said,
 * the same words go the same way. Deterministic and cheap; the model is never asked to route.
 *
 * In order: a question's opening words (or "how many", or a "?") make it a question; an exact card
 * name makes it a list of cards ("Can you mark Rattata and Raticate as caught"); words only a
 * question uses (a type, "gen", "missing") make it a question even when a word sounds like a card
 * ("fire types" is not Ninetales); any other card-like words make it a list of cards.
 */
object RequestRouter {

    fun kind(text: String, mentions: List<Mention>, catalog: Catalog): RequestKind {
        val words = text.lowercase().split(NonWord).filter { it.isNotEmpty() }
        if (text.trimEnd().endsWith('?') || words.firstOrNull() in QuestionOpeners || "how many" in words.joinToString(" ")) {
            return RequestKind.QUESTION
        }
        if (mentions.any { it.best.score == 1.0 }) return RequestKind.CARDS
        if (words.any { it in QueryWords || it in catalog.types }) return RequestKind.QUESTION
        return if (mentions.isNotEmpty()) RequestKind.CARDS else RequestKind.QUESTION
    }

    private val NonWord = Regex("[^a-z0-9']+")

    private val QuestionOpeners = setOf(
        "how", "which", "what", "what's", "whats", "show", "list", "do", "does", "did", "am", "are", "is",
        "count", "tell", "give", "where", "who",
    )

    private val QueryWords = setOf("gen", "generation", "generations", "type", "types", "missing", "collection")
}
