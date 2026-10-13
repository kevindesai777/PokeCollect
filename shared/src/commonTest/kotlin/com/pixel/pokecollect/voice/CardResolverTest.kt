package com.pixel.pokecollect.voice

import com.pixel.pokecollect.model.LocalModel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardResolverTest {

    private val cards = listOf(
        CardName(1, "Bulbasaur", "bulbasaur"),
        CardName(4, "Charmander", "charmander"),
        CardName(25, "Pikachu", "pikachu"),
        CardName(29, "Nidoran♀", "nidoran-f"),
        CardName(32, "Nidoran♂", "nidoran-m"),
        CardName(94, "Gengar", "gengar"),
        CardName(123, "Scyther", "scyther"),
        CardName(148, "Dragonair", "dragonair"),
        CardName(149, "Dragonite", "dragonite"),
        CardName(207, "Gligar", "gligar"),
        CardName(525, "Boldore", "boldore"),
    )

    private class FakeModel(private val output: (input: String) -> String) : LocalModel {
        override val displayName = "fake"
        val inputs = mutableListOf<String>()
        val schemas = mutableListOf<String>()
        override suspend fun load() = Unit
        override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String {
            inputs += input
            schemas += jsonSchema
            return output(input)
        }
    }

    @Test
    fun clearNamesNeverReachTheModel() = runTest {
        val model = FakeModel { error("not called") }
        val resolution = CardResolver(model).resolve("Pikachu and two Charmander", cards)
        assertEquals(listOf(25, 4), resolution.cards.map { it.card?.id })
        assertTrue(resolution.cards.none { it.needsCheck })
        assertTrue(model.inputs.isEmpty())
        assertNull(resolution.rawOutput)
    }

    @Test
    fun unclearNamesGoToTheModelInOneCallWithTheirOwnOptions() = runTest {
        val model = FakeModel { """{"1":"bulbasaur","2":"gengar"}""" }
        val resolution = CardResolver(model).resolve("bulb a sore, Pikachu, gang are", cards)

        assertEquals(1, model.inputs.size)
        val input = model.inputs.single().lines()
        assertEquals(2, input.size)
        assertTrue(input[0].startsWith("1. heard \"bulb a sore\": bulbasaur"), input[0])
        assertTrue(input[1].startsWith("2. heard \"gang are\": gengar"), input[1])

        val properties = Json.parseToJsonElement(model.schemas.single()).jsonObject["properties"]!!.jsonObject
        val gangAre = properties["2"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals("gengar", gangAre.first())
        assertEquals(CardResolver.NONE, gangAre.last())
        assertFalse("bulbasaur" in gangAre)

        assertEquals(listOf(1, 25, 94), resolution.cards.map { it.card?.id })
        assertEquals(listOf(PickedBy.MODEL, PickedBy.SPELLING, PickedBy.MODEL), resolution.cards.map { it.pickedBy })
        assertTrue(resolution.cards.none { it.needsCheck }) // the model agreed with spelling on good matches
    }

    @Test
    fun aModelPickThatDisagreesWithSpellingIsFlagged() = runTest {
        val model = FakeModel { """{"1":"gligar"}""" }
        val heard = CardResolver(model).resolve("gang are", cards).cards.single()
        assertEquals(207, heard.card?.id)
        assertTrue(heard.needsCheck)
    }

    @Test
    fun noneLeavesTheNameUnresolved() = runTest {
        val model = FakeModel { """{"1":"none"}""" }
        val heard = CardResolver(model).resolve("gang are", cards).cards.single()
        assertNull(heard.card)
        assertTrue(heard.needsCheck)
        assertEquals(94, heard.alternatives.first().id)
    }

    @Test
    fun aFailingModelFallsBackToSpellingAndFlagsTheName() = runTest {
        val model = FakeModel { error("engine gone") }
        val resolution = CardResolver(model).resolve("gang are and Pikachu", cards)
        assertEquals("engine gone", resolution.modelError)
        assertEquals(listOf(94, 25), resolution.cards.map { it.card?.id })
        assertEquals(listOf(true, false), resolution.cards.map { it.needsCheck })
    }

    @Test
    fun anAnswerOutsideTheOptionsIsRejected() = runTest {
        val model = FakeModel { """{"1":"pikachu"}""" }
        val resolution = CardResolver(model).resolve("gang are", cards)
        assertTrue(resolution.modelError!!.contains("not one of its options"), resolution.modelError)
        assertTrue(resolution.cards.single().needsCheck)
    }

    @Test
    fun aTwinIsLeftToThePerson() = runTest {
        val model = FakeModel { error("not called") }
        val heard = CardResolver(model).resolve("Nidoran", cards).cards.single()
        assertTrue(heard.needsCheck)
        assertEquals(setOf(29, 32), heard.alternatives.take(2).map { it.id }.toSet())
    }

    @Test
    fun aTwinDefaultsToTheCardNothingElseClaimed() = runTest {
        // iPhone 16e, 2026-10-13: plain "Nidoran" defaulted to ♀ and swallowed the "Nidoran female" said next to it.
        val model = FakeModel { error("not called") }
        val resolution = CardResolver(model).resolve("Today I caught a Nidoran and a Nidoran female", cards)
        assertEquals(listOf(32, 29), resolution.cards.map { it.card?.id })
        assertEquals(listOf(true, false), resolution.cards.map { it.needsCheck })
    }

    @Test
    fun aCardSaidTwiceIsOneCard() = runTest {
        val model = FakeModel { error("not called") }
        assertEquals(listOf(25), CardResolver(model).resolve("Pikachu Pikachu", cards).cards.map { it.card?.id })
    }
}
