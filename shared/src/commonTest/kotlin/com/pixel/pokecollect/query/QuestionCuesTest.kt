package com.pixel.pokecollect.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class QuestionCuesTest {

    private val catalog = Catalog(maxGeneration = 9, types = listOf("dragon", "fairy", "fire", "grass", "psychic", "water"))
    private fun options(question: String) = QuestionCues.options(question, catalog)

    @Test
    fun politeRequestWithIIsNotAboutOwnership() {
        val o = options("can I have a list of gen 4 water cards")
        assertEquals(listOf("4"), o.generation)
        assertEquals(listOf("water"), o.type)
        assertEquals(listOf(ANY), o.caught)
        assertEquals(listOf("list", "count"), o.intent)
    }

    @Test
    fun ownershipLetsTheModelPickADirection() {
        assertEquals(listOf(HAVE, MISSING), options("which ones do I have from the third generation").caught)
        assertEquals(listOf(HAVE, MISSING), options("what don't I have").caught)
        assertEquals(listOf(HAVE, MISSING), options("fairies from generation seven that I still need").caught)
        assertEquals(listOf(ANY), options("I need to see the grass cards").caught)
    }

    @Test
    fun generationPhrasings() {
        assertEquals(listOf("3"), options("Gen III psychics").generation)
        assertEquals(listOf("2"), options("how many gen-2 dragons").generation)
        assertEquals(listOf("7"), options("fairies from generation seven").generation)
        assertEquals(listOf("5"), options("5th gen fire types").generation)
        assertEquals(listOf("6"), options("gen6").generation)
        assertEquals(listOf(ANY), options("show me every fire card").generation)
    }

    @Test
    fun typePluralsAndAbsence() {
        assertEquals(listOf("dragon"), options("how many gen-2 dragons").type)
        assertEquals(listOf("fairy"), options("fairies from generation seven").type)
        assertEquals(listOf(ANY), options("how many cards are in gen 8").type)
    }

    @Test
    fun countCue() {
        assertEquals(listOf("count"), options("what's the total for gen 1").intent)
        assertEquals(listOf("count"), options("number of fire cards").intent)
    }

    @Test
    fun aGenerationThatHasNotShippedIsRejected() {
        assertFailsWith<UnsupportedQuestionException> { options("which gen 10 cards am I missing") }
    }

    @Test
    fun misspellingsOnlyOfferTheirValueNextToAny() {
        assertEquals(listOf("psychic", ANY), options("which pyschic cards do I have").type)
        assertEquals(listOf("5", ANY), options("the genration five cards").generation)
        assertEquals(listOf("dragon"), options("dragon types").type) // exact still decides
    }

    @Test
    fun ownershipConstructionsAcrossAuxiliariesAndNegation() {
        listOf(
            "which gen 2 cards do I already have",
            "fire types I do not have",
            "what fairies am I yet to catch",
            "ones I dont have",
            "what do we still need to collect",
        ).forEach { assertEquals(listOf(HAVE, MISSING), options(it).caught, it) }
        assertEquals(listOf(ANY), options("could I have a list of the gen 2 dragons").caught)
    }

    @Test
    fun negationDetectorForTheRulesOnlyBaseline() {
        assertEquals(true, QuestionCues.negatesOwnership("which ghost cards haven't I caught"))
        assertEquals(true, QuestionCues.negatesOwnership("what's left to collect"))
        assertEquals(false, QuestionCues.negatesOwnership("the water cards I've caught"))
        assertEquals(false, QuestionCues.negatesOwnership("list my dark types"))
    }

    @Test
    fun editDistanceCountsAdjacentSwapsAsOne() {
        assertEquals(1, QuestionCues.editDistance("pyschic", "psychic"))
        assertEquals(1, QuestionCues.editDistance("genration", "generation"))
        assertEquals(0, QuestionCues.editDistance("water", "water"))
    }

    @Test
    fun determinedOnlyWhenEveryFieldHasOneOption() {
        assertEquals(WireQuery("count", "8", ANY, ANY), options("how many cards are in gen 8").determined())
        assertNull(options("show me everything").determined())
    }
}
