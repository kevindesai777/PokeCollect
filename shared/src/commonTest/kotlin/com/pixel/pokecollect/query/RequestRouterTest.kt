package com.pixel.pokecollect.query

import com.pixel.pokecollect.query.RequestKind.CARDS
import com.pixel.pokecollect.query.RequestKind.QUESTION
import com.pixel.pokecollect.voice.CardMatcher
import com.pixel.pokecollect.voice.CardName
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestRouterTest {

    private val catalog = Catalog(maxGeneration = 9, types = listOf("fire", "grass", "water"))
    private val matcher = CardMatcher(
        listOf(
            CardName(1, "Bulbasaur", "bulbasaur"),
            CardName(25, "Pikachu", "pikachu"),
            CardName(29, "Nidoran♀", "nidoran-f"),
            CardName(32, "Nidoran♂", "nidoran-m"),
            CardName(38, "Ninetales", "ninetales"),
            CardName(56, "Mankey", "mankey"),
            CardName(19, "Rattata", "rattata"),
            CardName(20, "Raticate", "raticate"),
            CardName(129, "Magikarp", "magikarp"),
        ),
    )

    private fun kind(text: String) = RequestRouter.kind(text, matcher.mentions(text), catalog)

    @Test
    fun theDemoQuestionsAreQuestions() {
        assertEquals(QUESTION, kind("Which Gen 3 cards am I still missing?"))
        assertEquals(QUESTION, kind("How many fire types do I have?"))
        assertEquals(QUESTION, kind("Show me the water cards from Gen 1"))
    }

    @Test
    fun spokenQuestionsWithoutPunctuationAreQuestions() {
        // Pixel 8, 2026-10-13: said into the mic, came back as Mankey and Ninetales.
        assertEquals(QUESTION, kind("How many fire types do I have"))
        assertEquals(QUESTION, kind("fire types"))
        assertEquals(QUESTION, kind("cards I'm missing from gen 2"))
    }

    @Test
    fun namesAreCardsHoweverTheyAreAskedFor() {
        assertEquals(CARDS, kind("Can you please mark Rattata and Raticate as caught"))
        assertEquals(CARDS, kind("I just got two Pikachu's a Bulbasaur three Magikarp and a Nidoran"))
        assertEquals(CARDS, kind("Pikachu"))
        assertEquals(CARDS, kind("bulb a sore"))
    }

    @Test
    fun nothingRecognisableIsAQuestion() {
        assertEquals(QUESTION, kind("everything"))
    }
}
