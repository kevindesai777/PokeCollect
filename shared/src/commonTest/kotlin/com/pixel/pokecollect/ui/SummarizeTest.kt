package com.pixel.pokecollect.ui

import com.pixel.pokecollect.query.CollectionQuery
import com.pixel.pokecollect.query.QueryIntent
import kotlin.test.Test
import kotlin.test.assertEquals

class SummarizeTest {

    @Test
    fun generationLists() {
        assertEquals("3", generationList(listOf(3)))
        assertEquals("3 and 4", generationList(listOf(3, 4)))
        assertEquals("1, 4 and 7", generationList(listOf(1, 4, 7)))
        assertEquals("3–6", generationList(listOf(3, 4, 5, 6)))
    }

    @Test
    fun answerNamesEveryGeneration() {
        val query = CollectionQuery(QueryIntent.LIST, generations = listOf(3, 6), type = null, caught = false)
        assertEquals("You're missing 120 of 207 Gen 3 and 6 cards", summarize(query, 120, 207))
        assertEquals("You have 5 of 9 Gen 2 Fire-type cards", summarize(CollectionQuery(QueryIntent.COUNT, 2, "fire", true), 5, 9))
    }
}
