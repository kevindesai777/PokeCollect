package com.pixel.pokecollect.query

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the model is allowed to say. It never answers the question itself; it only fills in this
 * object, and Room does the rest. Every field is required in the schema, with null meaning "any".
 */
@Serializable
data class CollectionQuery(
    val intent: QueryIntent,
    val generation: Int?,
    val type: String?,
    val caught: Boolean?,
) {
    /** The non-null filters, in display order, for the "understood as" card. */
    fun filters(): List<Pair<String, String>> = buildList {
        if (generation != null) add("generation" to generation.toString())
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
