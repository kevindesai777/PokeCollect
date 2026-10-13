package com.pixel.pokecollect.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardMatcherTest {

    private val cards = listOf(
        CardName(1, "Bulbasaur", "bulbasaur"),
        CardName(4, "Charmander", "charmander"),
        CardName(5, "Charmeleon", "charmeleon"),
        CardName(7, "Squirtle", "squirtle"),
        CardName(25, "Pikachu", "pikachu"),
        CardName(29, "Nidoran♀", "nidoran-f"),
        CardName(32, "Nidoran♂", "nidoran-m"),
        CardName(83, "Farfetch’d", "farfetchd"),
        CardName(122, "Mr. Mime", "mr-mime"),
        CardName(137, "Porygon", "porygon"),
        CardName(151, "Mew", "mew"),
        CardName(172, "Pichu", "pichu"),
        CardName(233, "Porygon2", "porygon2"),
        CardName(250, "Ho-Oh", "ho-oh"),
        CardName(669, "Flabébé", "flabebe"),
        CardName(785, "Tapu Koko", "tapu-koko"),
        CardName(786, "Tapu Lele", "tapu-lele"),
        CardName(992, "Iron Hands", "iron-hands"),
    )
    private val matcher = CardMatcher(cards)

    private fun bestIds(transcript: String) = matcher.mentions(transcript).map { it.best.card.id }

    @Test
    fun findsEachNameInAPlainList() {
        assertEquals(listOf(25, 4, 1, 7), bestIds("Pikachu Charmander Bulbasaur Squirtle"))
    }

    @Test
    fun ignoresNumbersAndFillerWords() {
        assertEquals(listOf(25, 4), bestIds("I got two Pikachu and a Charmander"))
    }

    @Test
    fun joinsNamesTheRecognizerSplit() {
        val mentions = matcher.mentions("bulb a sore and pick a chew")
        assertEquals(listOf(1, 25), mentions.map { it.best.card.id })
        assertEquals(listOf("bulb a sore", "pick a chew"), mentions.map { it.heard })
    }

    @Test
    fun matchesMultiWordAndPunctuatedNames() {
        assertEquals(listOf(122, 785, 992, 250, 83, 669), bestIds("Mr. Mime, Tapu Koko, Iron Hands, Ho-Oh, Farfetch'd, flabebe"))
        assertEquals(listOf(122), bestIds("mister mime"))
    }

    @Test
    fun aNumberCanBePartOfAName() {
        assertEquals(listOf(233, 137), bestIds("Porygon 2 and Porygon"))
        assertEquals(listOf(233), bestIds("porygon two"))
    }

    @Test
    fun plainNidoranHasATwin() {
        val mention = matcher.mentions("Nidoran").single()
        assertTrue(mention.hasTwin)
        assertFalse(mention.isClear)
        assertEquals(setOf(29, 32), mention.candidates.take(2).map { it.card.id }.toSet())
    }

    @Test
    fun spokenFormsSayWhichNidoranAndKeepThePlainName() {
        assertEquals(listOf("Nidoran female", "Nidoran"), CardMatcher.spokenForms("Nidoran♀"))
        assertEquals(listOf("Pikachu"), CardMatcher.spokenForms("Pikachu"))
        val mentions = matcher.mentions("Nidoran female and Nidoran male")
        assertEquals(listOf(29, 32), mentions.map { it.best.card.id })
        assertTrue(mentions.all { it.isClear })
    }

    @Test
    fun anExactNameWithANearTwinIsLeftToThePerson() {
        val twins = CardMatcher(listOf(CardName(122, "Mr. Mime", "mr-mime"), CardName(866, "Mr. Rime", "mr-rime")))
        val mention = twins.mentions("Mr. Rime").single()
        assertEquals(866, mention.best.card.id)
        assertTrue(mention.hasTwin)
        assertFalse(mention.isClear)
        // Two letters apart is not a twin.
        val puffs = CardMatcher(listOf(CardName(39, "Jigglypuff", "jigglypuff"), CardName(40, "Wigglytuff", "wigglytuff")))
        assertTrue(puffs.mentions("Jigglypuff").single().isClear)
    }

    @Test
    fun exactNamesAreClearAndCloseCallsAreNot() {
        assertTrue(matcher.mentions("Charmander").single().isClear)
        assertFalse(matcher.mentions("charmelder").single().isClear) // Charmander vs Charmeleon
    }

    @Test
    fun wordsThatSoundLikeNothingAreDropped() {
        assertEquals(emptyList(), bestIds("um I just bought these today"))
    }
}
