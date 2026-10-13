package com.pixel.pokecollect.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The matcher against the full bundled dataset (JVM only: reads the JSON from the source tree). */
class CardMatcherDatasetTest {

    @Serializable
    private class Dataset(val pokemon: List<Species>)

    @Serializable
    private class Species(val id: Int, val name: String, val slug: String)

    private val cards = Json { ignoreUnknownKeys = true }
        .decodeFromString<Dataset>(File("src/commonMain/composeResources/files/pokemon.json").readText())
        .pokemon.map { CardName(it.id, it.name, it.slug) }
    private val matcher = CardMatcher(cards)

    private fun describe(transcript: String) = matcher.mentions(transcript).joinToString("\n") { m ->
        "  \"${m.heard}\" -> " + m.candidates.take(3).joinToString { "${it.card.name} %.2f".format(it.score) } +
            if (m.isClear) "  [clear]" else if (m.hasTwin) "  [twin]" else "  [unclear]"
    }

    @Test
    fun printMishearings() {
        listOf(
            "bulb a sore",
            "pick a chew",
            "squirrel",
            "snore lacks",
            "gang are",
            "magic carp",
            "jiggly puff",
            "psy duck",
            "geo dude",
            "onyx",
            "EV",
            "Eevee",
            "Mew and Mewtwo",
            "mute two",
            "Charizard Blastoise Venusaur",
            "Lucario Greninja Garchomp",
            "I just bought these cards today: Pikachu, two Charmander, a Gengar and Sprigatito",
            "far fetched",
            "Type Null",
            "hoe oh",
        ).forEach { println("$it\n${describe(it)}") }
    }

    @Test
    fun aRealTranscriptWithCommonWordsAndASplitName() {
        // iOS Simulator listening through the Mac's microphone to macOS `say` (2026-10-13).
        // "a Charmander" came out as "at your", which nothing can recover.
        val heard = matcher.mentions("I just bought two Pikachu at your three magic or but the Snorlax")
        assertEquals(listOf("Pikachu", "Magikarp", "Snorlax"), heard.map { it.best.card.name })
    }

    @Test
    fun aRequestPhrasedAsASentence() {
        // iPhone 16e, Kevin speaking (2026-10-13).
        val heard = matcher.mentions("Can you please mark Rattata and Raticate as caught")
        println("sentence: " + heard.map { "${it.heard} -> ${it.best.card.name} ${"%.2f".format(it.best.score)}" })
        assertEquals(listOf("Rattata", "Raticate"), heard.map { it.best.card.name })
    }

    @Test
    fun aListReadOutOnTheIphone() {
        // iPhone 16e, Apple on-device speech, Kevin reading 10 names (2026-10-13).
        val heard = matcher.mentions(
            "Today I collected Pikachu Charizard Gengar Snorlax EV Lucario Mr. Mr. Mime far-fetched Sprigatito and Iron Hands",
        )
        assertEquals(
            listOf("Pikachu", "Charizard", "Gengar", "Snorlax", "Eevee", "Lucario", "Mr. Mime", "Farfetch’d", "Sprigatito", "Iron Hands"),
            heard.map { it.best.card.name },
        )
        // Everything clear except Mr. Mime, whose twin Mr. Rime is one letter away.
        assertEquals(listOf("Mr. Mime"), heard.filterNot { it.isClear }.map { it.best.card.name })
        assertTrue(heard.single { !it.isClear }.hasTwin)
    }

    @Test
    fun aListReadOutOnThePixel() {
        // Pixel 8, Android on-device speech, Kevin reading the same 10 names (2026-10-13).
        val heard = matcher.mentions(
            "I just got a Pikachu Charizard Gengars Snorlax Evie Lucario Mr mime farfetched sprigadito and Iron Hands",
        )
        assertEquals(
            listOf("Pikachu", "Charizard", "Gengar", "Snorlax", "Eevee", "Lucario", "Mr. Mime", "Farfetch’d", "Sprigatito", "Iron Hands"),
            heard.map { it.best.card.name },
        )
    }

    @Test
    fun twentySixNamesHaveATwin() {
        val keys = cards.map { CardMatcher.matchKey(it.name) }
        assertEquals(26, keys.count { k -> keys.count { CardMatcher.withinOneEdit(k, it) } > 1 })
    }

    @Test
    fun aListOfTwentyFiveExactNamesIsAllClear() {
        val names = cards.shuffled(kotlin.random.Random(7)).take(25)
        val mentions = matcher.mentions(names.joinToString(" ") { it.name })
        assertEquals(names.map { it.id }, mentions.map { it.best.card.id })
        println("unclear: " + mentions.filterNot { it.isClear }.map { it.heard + " -> " + it.candidates.take(2).map { c -> c.card.name } })
    }
}
