package com.pixel.pokecollect.model

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implemented in Swift. Apple's Foundation Models framework is Swift-only (no Objective-C
 * surface), so Kotlin/Native can't call it directly. Swift implements this callback interface and
 * hands it to Kotlin at startup; [SwiftLocalModel] adapts it to the shared suspend seam.
 */
interface NativeLanguageModel {
    val displayName: String

    /** Calls back with null when the model can run, or a reason a person can act on. */
    fun load(completion: (error: String?) -> Unit)

    /** Calls back with (json, null) on success or (null, error) on failure. */
    fun generate(instructions: String, input: String, jsonSchema: String, completion: (json: String?, error: String?) -> Unit)
}

class SwiftLocalModel(private val native: NativeLanguageModel) : LocalModel {
    override val displayName: String get() = native.displayName

    override suspend fun load() = suspendCancellableCoroutine { continuation ->
        native.load { error ->
            if (error == null) continuation.resume(Unit) else continuation.resumeWithException(ModelUnavailableException(error))
        }
    }

    override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String =
        suspendCancellableCoroutine { continuation ->
            native.generate(instructions, input, jsonSchema) { json, error ->
                if (json != null) {
                    continuation.resume(json)
                } else {
                    continuation.resumeWithException(IllegalStateException(error ?: "Foundation Models returned nothing"))
                }
            }
        }
}
