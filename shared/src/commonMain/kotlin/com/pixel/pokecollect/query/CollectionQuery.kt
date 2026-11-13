package com.pixel.pokecollect.query

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the model is allowed to say. It never answers the question itself; it only fills in this
 * object, and Room does the rest. Null (or no generations) means "any".
 */
@Serializable
data class CollectionQuery(
    val intent: QueryIntent,
    /** Ascending and distinct; empty means every generation. "Gen 3 and 6" is two entries. */
    val generations: List<Int>,
    val type: String?,
    val caught: Boolean?,
) {
    /** One generation, or any: how almost every question (and every few-shot example) reads. */
    constructor(intent: QueryIntent, generation: Int?, type: String?, caught: Boolean?) :
        this(intent, listOfNotNull(generation), type, caught)

    /** The non-null filters, in display order, for the "understood as" card. */
    fun filters(): List<Pair<String, String>> = buildList {
        if (generations.isNotEmpty()) add("generation" to generations.joinToString(", "))
        if (type != null) add("type" to type)
        if (caught != null) add("caught" to caught.toString())
    }
}

@Serializable
enum class QueryIntent {
    /** "Which ... ?" Show the matching cards. */
    @SerialName("list") LIST,

    /** "How many ... ?" Show only the number. */
    @SerialName("count") COUNT,
}

/** The values a query may use, read from the dataset rather than hardcoded. */
data class Catalog(val maxGeneration: Int, val types: List<String>)
