package com.pixel.pokecollect.query

import com.pixel.pokecollect.query.QueryIntent.COUNT
import com.pixel.pokecollect.query.QueryIntent.LIST

/**
 * Held-out v1: written independently of the prompt, schema and planner, then frozen and run once
 * (2026-10-06: model only 17/40, rules + model 31/40). Results have been seen, so since then it is
 * part of the tuning pool with [EvalCases]. Final numbers come from [HeldOutV2Cases].
 */
val HeldOutV1Cases: List<Pair<String, CollectionQuery>> = listOf(
    "Which Gen 8 cards do I already have?" to h(LIST, 8, null, true),
    "How many cards are there altogether?" to h(COUNT, null, null, null),
    "count up all the fire types" to h(COUNT, null, "fire", null),
    "Which pyschic types from the 1st gen am I still missing?" to h(LIST, 1, "psychic", false),
    "Could I see all my electric-type cards?" to h(LIST, null, "electric", true),
    "How many 6th gen cards are left to collect?" to h(COUNT, 6, null, false),
    "pull up generation seven" to h(LIST, 7, null, null),
    "What's the total number of steel types in Gen VIII?" to h(COUNT, 8, "steel", null),
    "What fairies am I missing?" to h(LIST, null, "fairy", false),
    "Show me the Gen 4 rock types I've caught" to h(LIST, 4, "rock", true),
    "how many fighting types don't i have" to h(COUNT, null, "fighting", false),
    "Can I have a list of everything in gen 9?" to h(LIST, 9, null, null),
    "Out of the first generation, how many do I own?" to h(COUNT, 1, null, true),
    "give me every gen3 flying type" to h(LIST, 3, "flying", null),
    "Which dragons from the sixth gen haven't I got yet?" to h(LIST, 6, "dragon", false),
    "which cards have i collected so far" to h(LIST, null, null, true),
    "How many grass types from gen 2 do I own?" to h(COUNT, 2, "grass", true),
    "Can you list the ground-type cards?" to h(LIST, null, "ground", null),
    "Whcih ninth-gen cards am I yet to catch?" to h(LIST, 9, null, false),
    "How many water-type cards have I caught so far?" to h(COUNT, null, "water", true),
    "What dark types came out in gen 5?" to h(LIST, 5, "dark", null),
    "can you show the gen 7 poison types i have" to h(LIST, 7, "poison", true),
    "number of gen 9 ghost types I haven't caught" to h(COUNT, 9, "ghost", false),
    "Which normal-type cards haven't I caught?" to h(LIST, null, "normal", false),
    "How many cards does Gen IV have?" to h(COUNT, 4, null, null),
    "Show me the ice cards I've got" to h(LIST, null, "ice", true),
    "List the dark types from gen two that I haven't collected" to h(LIST, 2, "dark", false),
    "whats the total number of pokemon" to h(COUNT, null, null, null),
    "What have I got from Gen 7?" to h(LIST, 7, null, true),
    "What's the count of Gen III water cards I'm missing?" to h(COUNT, 3, "water", false),
    "I'd like to see all the ghosts" to h(LIST, null, "ghost", null),
    "gen 4 cards i still need" to h(LIST, 4, null, false),
    "count the 5th generation bug cards I've collected" to h(COUNT, 5, "bug", true),
    "Give me a list of the bugs I've caught" to h(LIST, null, "bug", true),
    "number of dragon types in gen one" to h(COUNT, 1, "dragon", null),
    "What ice-type cards from the eighth gen do I not have yet?" to h(LIST, 8, "ice", false),
    "Could you count the second-generation cards for me?" to h(COUNT, 2, null, null),
    "Show me the genration five cards I don't have" to h(LIST, 5, null, false),
    "How many poison-type cards exist?" to h(COUNT, null, "poison", null),
    "Which flying types from Gen 6 have I got?" to h(LIST, 6, "flying", true),
)

private fun h(intent: QueryIntent, generation: Int?, type: String?, caught: Boolean?) =
    CollectionQuery(intent, generation, type, caught)
