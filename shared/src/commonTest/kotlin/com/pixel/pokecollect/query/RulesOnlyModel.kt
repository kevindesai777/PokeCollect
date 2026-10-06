package com.pixel.pokecollect.query

import com.pixel.pokecollect.model.LocalModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ablation: no language model at all. Reads the narrowed schema the planner built for this
 * question and picks deterministically: "list" unless only "count" is allowed, the first
 * mentioned generation/type, and ownership direction from [QuestionCues.negatesOwnership].
 * Goes through the planner's normal parse and validation, so it is scored exactly like a model.
 */
internal class RulesOnlyModel : LocalModel {
    override val displayName = "rules only"

    override suspend fun load() = Unit

    override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String {
        val properties = Json.parseToJsonElement(jsonSchema).jsonObject["properties"]!!.jsonObject
        fun enumOf(field: String) = properties[field]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        fun firstMention(values: List<String>) = values.firstOrNull { it != ANY } ?: ANY

        val intent = enumOf("intent").let { if ("list" in it) "list" else it.single() }
        val caught = enumOf("caught").let { values ->
            // Narrowed ownership questions offer exactly [have, missing] (in that order, any labels).
            if (values.size == 2 && ANY !in values) values[if (QuestionCues.negatesOwnership(input)) 1 else 0] else values.first()
        }
        return """{"intent":"$intent","generation":"${firstMention(enumOf("generation"))}",""" +
            """"type":"${firstMention(enumOf("type"))}","caught":"$caught"}"""
    }
}
