package com.pixel.pokecollect.model

/**
 * The seam between shared code and an on-device language model.
 *
 * An interface (not an expect class) so the planner can be tested with a fake; each platform
 * binds its implementation in `platformModule` (expect/actual).
 */
interface LocalModel {
    /** Shown in the UI, e.g. "Gemma 3 1B · LiteRT-LM". */
    val displayName: String

    /** Loads the model. Idempotent. Throws [ModelUnavailableException] with a reason a person can act on. */
    suspend fun load()

    /**
     * Returns JSON text for [input] that conforms to [jsonSchema]. Implementations use the
     * runtime's constrained decoding where it exists; callers still validate the result.
     */
    suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String
}

class ModelUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
