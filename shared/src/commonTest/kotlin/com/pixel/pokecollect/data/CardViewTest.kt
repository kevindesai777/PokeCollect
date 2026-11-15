package com.pixel.pokecollect.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CardViewTest {

    private fun card(id: Int, name: String, generation: Int, caught: Boolean) =
        PokemonWithCollection(PokemonEntity(id, name, name.lowercase(), generation, "normal", null, ""), caught)

    // In number order, as the DAO returns them.
    private val cards = listOf(
        card(1, "Bulbasaur", 1, caught = true),
        card(29, "Nidoran♀", 1, caught = false),
        card(32, "Nidoran♂", 1, caught = true),
        card(122, "Mr. Mime", 1, caught = false),
        card(180, "Flaaffy", 2, caught = true),
        card(258, "Mudkip", 3, caught = false),
        card(669, "Flabébé", 6, caught = false),
        card(841, "Flapple", 8, caught = true),
        card(866, "Mr. Rime", 8, caught = false),
    )

    private fun ids(view: CardView, keep: Set<Int> = emptySet()) = applyView(cards, view, keep).map { it.pokemon.id }

    @Test
    fun defaultIsEveryCardInNumberOrder() {
        assertEquals(cards.map { it.pokemon.id }, ids(CardView()))
    }

    @Test
    fun generationAndCaughtFilter() {
        assertEquals(listOf(1, 29, 32, 122), ids(CardView(generation = 1)))
        assertEquals(listOf(1, 32, 180, 841), ids(CardView(show = CaughtFilter.Have)))
        assertEquals(listOf(29, 122), ids(CardView(generation = 1, show = CaughtFilter.Missing)))
        assertEquals(emptyList(), ids(CardView(generation = 2, show = CaughtFilter.Missing)))
    }

    @Test
    fun numberOrderBothWays() {
        assertEquals(listOf(866, 841, 669, 258, 180, 122, 32, 29, 1), ids(CardView(order = CardOrder.NumberDown)))
    }

    @Test
    fun namesIgnoreCaseAccentsAndPunctuation() {
        val names = applyView(cards, CardView(order = CardOrder.NameUp)).map { it.pokemon.name }
        // Flabébé between Flaaffy and Flapple; "Mr. Mime" and "Mr. Rime" as mrmime / mrrime, before Mudkip;
        // the Nidoran tie by number, ♀ (29) before ♂ (32).
        assertEquals(
            listOf("Bulbasaur", "Flaaffy", "Flabébé", "Flapple", "Mr. Mime", "Mr. Rime", "Mudkip", "Nidoran♀", "Nidoran♂"),
            names,
        )
    }

    @Test
    fun zToAIsAToZReversed() {
        assertEquals(ids(CardView(order = CardOrder.NameUp)).reversed(), ids(CardView(order = CardOrder.NameDown)))
    }

    @Test
    fun orderAppliesAfterTheFilters() {
        assertEquals(listOf(122, 29), ids(CardView(generation = 1, show = CaughtFilter.Missing, order = CardOrder.NumberDown)))
        // Bulbasaur, Flaaffy, Flapple, Nidoran♂.
        assertEquals(listOf(1, 180, 841, 32), ids(CardView(show = CaughtFilter.Have, order = CardOrder.NameUp)))
    }

    @Test
    fun keptCardsStayWhenTheyStopMatching() {
        // Missing on; Bulbasaur (1) was just ticked in the list, so it's caught but stays.
        val view = CardView(generation = 1, show = CaughtFilter.Missing)
        assertEquals(listOf(1, 29, 122), ids(view, keep = setOf(1)))
        // Without the keep it would go.
        assertEquals(listOf(29, 122), ids(view))
    }

    @Test
    fun keptCardsStillNeedTheGeneration() {
        assertEquals(listOf(29, 122), ids(CardView(generation = 1, show = CaughtFilter.Missing), keep = setOf(180)))
    }
}
