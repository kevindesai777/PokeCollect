package com.pixel.pokecollect.query

import com.pixel.pokecollect.query.QueryIntent.COUNT
import com.pixel.pokecollect.query.QueryIntent.LIST

/**
 * Held-out v2: written independently of the prompt, schema and planner, then frozen before any
 * system it evaluates was tuned. Report results on it; never tune against it.
 */
val HeldOutV2Cases: List<Pair<String, CollectionQuery>> = listOf(
    "Which sixth-gen cards still aren't mine?" to v2(LIST, 6, null, false),
    "How many grass types did generation six bring?" to v2(COUNT, 6, "grass", null),
    "Let me see the fairys I own" to v2(LIST, null, "fairy", true),
    "Count every card in the app, caught or not" to v2(COUNT, null, null, null),
    "missing gen 8 grass" to v2(LIST, 8, "grass", false),
    "What's my caught total?" to v2(COUNT, null, null, true),
    "walk me through the eighth gen flying types" to v2(LIST, 8, "flying", null),
    "Could you tally the Gen 4 poison types in my possession?" to v2(COUNT, 4, "poison", true),
    "show me my missing cards" to v2(LIST, null, null, false),
    "What's the card count for Gen VI?" to v2(COUNT, 6, null, null),
    "psychic types from gen 5 that i own" to v2(LIST, 5, "psychic", true),
    "How many ghost-type cards am I yet to find?" to v2(COUNT, null, "ghost", false),
    "I want to scroll through every single card" to v2(LIST, null, null, null),
    "Which Gen V ice types are already caught?" to v2(LIST, 5, "ice", true),
    "how many more gen 2 cards until i have them all" to v2(COUNT, 2, null, false),
    "What are all the rock types?" to v2(LIST, null, "rock", null),
    "Display my collected ground types" to v2(LIST, null, "ground", true),
    "From gen one, how many electric-types do I still not have?" to v2(COUNT, 1, "electric", false),
    "Give me the full gen 5 lineup" to v2(LIST, 5, null, null),
    "Need a list of the gen 8 cards I haven't grabbed yet" to v2(LIST, 8, null, false),
    "Count my steel types" to v2(COUNT, null, "steel", true),
    "which generation four pokemon are water types" to v2(LIST, 4, "water", null),
    "Could you show me which 4th-gen flying types I'm missing?" to v2(LIST, 4, "flying", false),
    "I'm curious how many gen 9 cards I've managed to catch" to v2(COUNT, 9, null, true),
    "Total ground types?" to v2(COUNT, null, "ground", null),
    "list all uncaught cards" to v2(LIST, null, null, false),
    "Of the bug types in gen 7, which do I have?" to v2(LIST, 7, "bug", true),
    "Gen III bug count?" to v2(COUNT, 3, "bug", null),
    "I need to see which dark types I don't have" to v2(LIST, null, "dark", false),
    "my caught cards please" to v2(LIST, null, null, true),
    "How many cards do I need to finish the fourth generation?" to v2(COUNT, 4, null, false),
    "2nd gen ice types" to v2(LIST, 2, "ice", null),
    "Tell me how mnay 6th gen ghosts I've caught" to v2(COUNT, 6, "ghost", true),
    "Show me what's missing among the seventh-gen water types" to v2(LIST, 7, "water", false),
    "What's in Gen 1?" to v2(LIST, 1, null, null),
    "fighting types ive collected, whats the count" to v2(COUNT, null, "fighting", true),
    "Please list the rock types I'm misisng" to v2(LIST, null, "rock", false),
    "How many Pokémon were introduced in the third generation?" to v2(COUNT, 3, null, null),
    "I only want to see my gen 1 cards" to v2(LIST, 1, null, true),
    "Gen 3 fire-types: how many do I still need?" to v2(COUNT, 3, "fire", false),
    "Search for gen 9 poision types" to v2(LIST, 9, "poison", null),
    "Filter to just the cards I own" to v2(LIST, null, null, true),
    "how many fire tpyes do i still have to get" to v2(COUNT, null, "fire", false),
    "My Gen IX normal-type cards, please" to v2(LIST, 9, "normal", true),
    "Of all the cards, how many are dragon types?" to v2(COUNT, null, "dragon", null),
    "Gen 7 missing list" to v2(LIST, 7, null, false),
    "How many third-gen cards are already mine?" to v2(COUNT, 3, null, true),
    "Electric types from gen seven?" to v2(LIST, 7, "electric", null),
    "show me the first gen steel types im still hunting for" to v2(LIST, 1, "steel", false),
    "Give me a count of the cards I still need" to v2(COUNT, null, null, false),
    "caught normal-types" to v2(LIST, null, "normal", true),
    "how many psychic types first appeared in gen 9" to v2(COUNT, 9, "psychic", null),
    "Which dragon types do I still need to track down?" to v2(LIST, null, "dragon", false),
    "What's caught so far in gen two?" to v2(LIST, 2, null, true),
    "Please show the complete card list" to v2(LIST, null, null, null),
    "Quick count of my Gen 8 dark types?" to v2(COUNT, 8, "dark", true),
    "I'd love a list of fighting-type cards" to v2(LIST, null, "fighting", null),
    "list every gen5 card ive caught" to v2(LIST, 5, null, true),
    "how many cards can be collected in this app" to v2(COUNT, null, null, null),
    "I still don't have every Gen II fairy type. Which ones are left?" to v2(LIST, 2, "fairy", false),
)

private fun v2(intent: QueryIntent, generation: Int?, type: String?, caught: Boolean?) =
    CollectionQuery(intent, generation, type, caught)
