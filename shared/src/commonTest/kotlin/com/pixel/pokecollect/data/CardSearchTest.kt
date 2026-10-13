package com.pixel.pokecollect.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CardSearchTest {

    private val cards = listOf(
        2 to "Ivysaur",
        19 to "Rattata",
        29 to "Nidoran♀",
        32 to "Nidoran♂",
        83 to "Farfetch’d",
        122 to "Mr. Mime",
        128 to "Tauros",
        233 to "Porygon2",
        669 to "Flabébé",
        785 to "Tapu Koko",
        883 to "Arctovish",
    ).map { (id, name) ->
        PokemonWithCollection(PokemonEntity(id, name, name.lowercase(), 1, "normal", null, ""), caught = false)
    }

    private fun search(text: String) = searchCards(cards, text).map { it.pokemon.name }

    @Test
    fun dexNumberFindsThatCardOnly() {
        assertEquals(listOf("Arctovish"), search("883"))
        assertEquals(listOf("Arctovish"), search(" #0883 "))
        assertEquals(listOf("Arctovish"), search("No. 883"))
        // A number is a dex number, not part of a name.
        assertEquals(listOf("Ivysaur"), search("2"))
        assertEquals(emptyList(), search("999"))
    }

    @Test
    fun namesIgnoreCaseAccentsAndPunctuation() {
        assertEquals(listOf("Arctovish"), search("ARCTO"))
        assertEquals(listOf("Mr. Mime"), search("mr mime"))
        assertEquals(listOf("Farfetch’d"), search("farfetchd"))
        assertEquals(listOf("Flabébé"), search("flabebe"))
        assertEquals(listOf("Nidoran♀", "Nidoran♂"), search("nidoran"))
        assertEquals(listOf("Tapu Koko"), search("koko"))
        assertEquals(listOf("Porygon2"), search("porygon2"))
    }

    @Test
    fun namesStartingWithTheTextComeFirst() {
        assertEquals(listOf("Tauros", "Tapu Koko", "Rattata"), search("ta"))
    }

    @Test
    fun blankOrSymbolOnlyTextMatchesEverything() {
        assertEquals(cards.size, search("").size)
        assertEquals(cards.size, search("#").size)
    }

    @Test
    fun aQuestionMatchesNoCard() {
        assertEquals(emptyList(), search("how many fire types do I have"))
    }
}
