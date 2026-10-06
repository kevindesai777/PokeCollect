package com.pixel.pokecollect.query

import com.pixel.pokecollect.query.QueryIntent.COUNT
import com.pixel.pokecollect.query.QueryIntent.LIST

/**
 * Questions with the query a careful human would write. Shared so every platform's model is
 * scored on the same set. None of these appear as few-shot examples in the prompt.
 */
val EvalCases: List<Pair<String, CollectionQuery>> = listOf(
    "Which Gen 3 cards am I still missing?" to q(LIST, 3, null, false),
    "How many fire types do I have?" to q(COUNT, null, "fire", true),
    "Show me the water cards from Gen 1" to q(LIST, 1, "water", null),
    "How many cards do I have?" to q(COUNT, null, null, true),
    "What am I missing from generation 2?" to q(LIST, 2, null, false),
    "List my psychic cards" to q(LIST, null, "psychic", true),
    "How many Gen 9 cards are there?" to q(COUNT, 9, null, null),
    "Which dragon types have I caught?" to q(LIST, null, "dragon", true),
    "Do I still need any ghost cards from Gen 4?" to q(LIST, 4, "ghost", false),
    "How many electric cards am I missing?" to q(COUNT, null, "electric", false),
    "Show all Gen 6 cards" to q(LIST, 6, null, null),
    "Which grass cards do I own?" to q(LIST, null, "grass", true),
    "How many fairy types are in generation 7?" to q(COUNT, 7, "fairy", null),
    "What's left to collect in the first generation?" to q(LIST, 1, null, false),
    "Show me every steel card" to q(LIST, null, "steel", null),
    "How many Gen 5 cards have I caught?" to q(COUNT, 5, null, true),
    "which bug cards am i missing" to q(LIST, null, "bug", false),
    "How many cards are in Gen 8" to q(COUNT, 8, null, null),
    "Show my fire cards from the second generation" to q(LIST, 2, "fire", true),
    "What do I still need?" to q(LIST, null, null, false),
)

private fun q(intent: QueryIntent, generation: Int?, type: String?, caught: Boolean?) =
    CollectionQuery(intent, generation, type, caught)

val EvalTypes = listOf(
    "bug", "dark", "dragon", "electric", "fairy", "fighting", "fire", "flying", "ghost",
    "grass", "ground", "ice", "normal", "poison", "psychic", "rock", "steel", "water",
)
