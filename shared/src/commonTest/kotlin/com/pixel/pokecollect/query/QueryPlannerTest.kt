package com.pixel.pokecollect.query

import com.pixel.pokecollect.model.LocalModel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QueryPlannerTest {

    private val catalog = Catalog(maxGeneration = 9, types = listOf("fire", "grass", "water"))
    private val all = Options.all(catalog)

    private class FakeModel(private val output: String) : LocalModel {
        override val displayName = "fake"
        var lastSchema: String? = null
        override suspend fun load() = Unit
        override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String {
            lastSchema = jsonSchema
            return output
        }
    }

    @Test
    fun parsesTheDemoQuery() = runTest {
        val model = FakeModel("""{"intent":"list","generation":"3","type":"any","caught":"missing"}""")
        val planned = QueryPlanner(model).plan("  Which Gen 3 cards am I still missing? ", catalog)
        assertEquals(CollectionQuery(QueryIntent.LIST, generation = 3, type = null, caught = false), planned.query)
        assertEquals("Which Gen 3 cards am I still missing?", planned.question)
    }

    @Test
    fun prepareWarmsUpWithAnExampleWhoseSchemaAllowsOnlyItsAnswer() = runTest {
        var warmup: Triple<String, String, String>? = null
        val model = object : LocalModel {
            override val displayName = "fake"
            override suspend fun load() = Unit
            override suspend fun generateJson(instructions: String, input: String, jsonSchema: String) = error("not called")
            override suspend fun prepare(instructions: String, warmupInput: String, warmupSchema: String) {
                warmup = Triple(instructions, warmupInput, warmupSchema)
            }
        }
        val planner = QueryPlanner(model)
        planner.prepare(catalog)

        val (instructions, input, schema) = checkNotNull(warmup)
        assertEquals(planner.instructions(catalog), instructions)
        // The warm-up question is one of the prompt's own examples, so priming adds nothing new.
        assertTrue("Q: $input" in instructions)
        val properties = Json.parseToJsonElement(schema).jsonObject["properties"]!!.jsonObject
        val allowed = properties.mapValues { (_, field) -> field.jsonObject["enum"].toString() }
        assertEquals(mapOf("intent" to """["list"]""", "generation" to """["any"]""", "type" to """["any"]""", "caught" to """["any"]"""), allowed)
    }

    @Test
    fun rejectsGenerationOutsideTheDataset() {
        val planner = QueryPlanner(FakeModel(""))
        assertFailsWith<InvalidQueryException> {
            planner.parse("""{"intent":"list","generation":"10","type":"any","caught":"any"}""", all)
        }
    }

    @Test
    fun rejectsUnknownType() {
        val planner = QueryPlanner(FakeModel(""))
        assertFailsWith<InvalidQueryException> {
            planner.parse("""{"intent":"count","generation":"any","type":"cosmic","caught":"have"}""", all)
        }
    }

    @Test
    fun schemaPutsEveryFieldUnderProperties() {
        val schema = Json.parseToJsonElement(QueryPlanner(FakeModel("")).jsonSchema(all)).jsonObject
        assertEquals(setOf("type", "properties", "required", "additionalProperties"), schema.keys)
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertEquals(setOf("intent", "generation", "type", "caught"), schema["properties"]!!.jsonObject.keys)
    }

    @Test
    fun mapsAnyToNoFilter() {
        val planner = QueryPlanner(FakeModel(""))
        assertEquals(
            CollectionQuery(QueryIntent.COUNT, generation = null, type = "fire", caught = true),
            planner.parse("""{"intent":"count","generation":"any","type":"fire","caught":"have"}""", all),
        )
    }

    @Test
    fun rejectsMissingFieldsAndProse() {
        val planner = QueryPlanner(FakeModel(""))
        assertFailsWith<InvalidQueryException> { planner.parse("""{"intent":"list"}""", all) }
        assertFailsWith<InvalidQueryException> { planner.parse("You are missing 88 cards.", all) }
    }

    @Test
    fun schemaComesFromTheCatalogNotHardcodedValues() {
        val schema = QueryPlanner(FakeModel("")).jsonSchema(Options.all(Catalog(maxGeneration = 10, types = listOf("fire"))))
        assertTrue("\"10\"]" in schema, schema)
        assertTrue("\"enum\":[\"any\",\"fire\"]" in schema, schema)
    }

    @Test
    fun narrowedSchemaOnlyOffersWhatTheQuestionMentions() = runTest {
        val model = FakeModel("""{"intent":"list","generation":"3","type":"any","caught":"missing"}""")
        QueryPlanner(model).plan("Which Gen 3 cards am I still missing?", catalog)
        val properties = Json.parseToJsonElement(model.lastSchema!!).jsonObject["properties"]!!.jsonObject
        fun enumOf(field: String) = properties[field]!!.jsonObject["enum"].toString()
        assertEquals("""["3"]""", enumOf("generation"))
        assertEquals("""["any"]""", enumOf("type"))
        assertEquals("""["have","missing"]""", enumOf("caught"))
    }

    @Test
    fun rejectsAValueTheQuestionDidNotAllow() = runTest {
        // Only possible without constrained decoding (or with a runtime that ignores the schema).
        val model = FakeModel("""{"intent":"list","generation":"3","type":"fire","caught":"missing"}""")
        assertFailsWith<InvalidQueryException> { QueryPlanner(model).plan("Which Gen 3 cards am I missing?", catalog) }
    }

    @Test
    fun wireLabelsAreTranslatedBothWays() = runTest {
        val yesNo = WireLabels(have = "yes", missing = "no")
        val model = FakeModel("""{"intent":"list","generation":"3","type":"any","caught":"no"}""")
        val planner = QueryPlanner(model, QuestionCues, yesNo, Prompt.Round2)
        val planned = planner.plan("Which Gen 3 cards am I still missing?", catalog)
        assertEquals(false, planned.query.caught)
        assertTrue("\"enum\":[\"yes\",\"no\"]" in model.lastSchema!!, model.lastSchema)
        assertTrue("\"caught\":\"no\"" in planner.instructions(catalog)) // examples rendered with the labels
    }

    @Test
    fun round1PromptIsReproducedExactly() {
        val prompt = QueryPlanner(FakeModel(""), QuestionCuesV1, WireLabels.HaveMissing, Prompt.Round1).instructions(catalog)
        assertTrue(prompt.endsWith("""Q: List everything
{"intent":"list","generation":"any","type":"any","caught":"any"}"""), prompt)
        assertTrue("\"missing\" for cards they are missing or still need;" in prompt, prompt)
    }

    @Test
    fun rulesOnlyModelPicksDeterministically() = runTest {
        val planner = QueryPlanner(RulesOnlyModel())
        assertEquals(
            CollectionQuery(QueryIntent.LIST, generation = 3, type = null, caught = false),
            planner.plan("Which Gen 3 cards haven't I caught?", catalog).query,
        )
    }

    @Test
    fun skipsTheModelWhenOnlyOneQueryIsPossible() = runTest {
        val model = object : LocalModel {
            override val displayName = "must not be called"
            override suspend fun load() = Unit
            override suspend fun generateJson(instructions: String, input: String, jsonSchema: String): String = error("model was called")
        }
        val planned = QueryPlanner(model).plan("how many gen 8 cards are there", catalog)
        assertEquals(CollectionQuery(QueryIntent.COUNT, generation = 8, type = null, caught = null), planned.query)
        assertEquals(false, planned.usedModel)
    }
}
