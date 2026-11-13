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
    fun severalGenerationsAreOneValueNotAChoice() {
        listOf(
            "show me cards from gen 3 and gen 6 that i am missing",
            "gen 3 and 6 cards i'm missing",
            "gens 3 or 6",
            "generations 6 & 3",
            "gen 3/6 water types",
            "3rd and 6th gen",
            "third and sixth generations",
            "gen three and six",
            "gen iii and vi",
        ).forEach { assertEquals(listOf("3,6"), options(it).generation, it) }
        assertEquals(listOf("1,4,7"), options("gen 1, 4, and 7 fire types").generation)
    }

    @Test
    fun generationRanges() {
        listOf("gen 3-6", "gens 3 to 6", "gen 3 through gen 6", "between gen 3 and 6", "generations 6–3")
            .forEach { assertEquals(listOf("3,4,5,6"), options(it).generation, it) }
    }

    @Test
    fun generationListsNeedASeparatorAndANumeral() {
        assertEquals(listOf("4"), options("gen iv and i have which ones").generation) // "i" is the pronoun
        assertEquals(listOf("3"), options("gen 3 25 cards").generation)
        assertEquals(listOf("2"), options("gen 2 and what else").generation)
    }

    @Test
    fun aListWithAnUnshippedGenerationIsRejected() {
        assertFailsWith<UnsupportedQuestionException> { options("gen 9 and 10 cards") }
        assertFailsWith<UnsupportedQuestionException> { options("gens 8 to 10") }
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
        assertEquals(WireQuery("count", "3,6", ANY, ANY), options("how many gen 3 and gen 6 cards are there").determined())
        assertNull(options("show me everything").determined())
    }
}
