package com.pixel.pokecollect.model

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.pokecollect.query.Catalog
import com.pixel.pokecollect.query.EvalTypes
import com.pixel.pokecollect.query.Options
import com.pixel.pokecollect.query.QueryPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Bisects which JSON-schema shapes LiteRT-LM's LLGuidance accepts. Logs under "PokeCollect.Probe". */
@RunWith(AndroidJUnit4::class)
class SchemaProbeTest {

    private fun obj(vararg props: Pair<String, String>) =
        """{"type":"object","properties":{${props.joinToString(",") { "\"${it.first}\":${it.second}" }}},""" +
            """"required":[${props.joinToString(",") { "\"${it.first}\"" }}],"additionalProperties":false}"""

    private val intent = "intent" to """{"type":"string","enum":["list","count"]}"""
    private val genStrings = "generation" to """{"type":"string","enum":["any","1","2","3","4","5","6","7","8","9"]}"""
    private val genWords = "generation" to """{"type":"string","enum":["any","gen1","gen2","gen3"]}"""
    private val caught = "caught" to """{"type":"string","enum":["any","have","missing"]}"""
    private val genNullable = "generation" to """{"type":["integer","null"],"minimum":1,"maximum":9}"""
    private val oneEnumNoType = "caught" to """{"enum":["any","have","missing"]}"""

    @Test
    fun probe(): Unit = runBlocking {
        val model = LiteRtLmModel(InstrumentationRegistry.getInstrumentation().targetContext)
        model.load()
        val types = EvalTypes.joinToString(",") { "\"$it\"" }
        val pokemonType = "pokemonType" to """{"type":"string","enum":["any",$types]}"""
        val typeNamedType = "type" to """{"type":"string","enum":["any",$types]}"""
        val planner = QueryPlanner(model).jsonSchema(Options.all(Catalog(9, EvalTypes)))
        Log.i("PokeCollect.Probe", "planner schema: $planner")
        val variants = linkedMapOf(
            "G planner's exact schema" to planner,
            "H intent+pokemonType" to obj(intent, pokemonType),
            "I intent+generation+caught" to obj(intent, genStrings, caught),
            "J intent+generation+pokemonType" to obj(intent, genStrings, pokemonType),
            "K all four, hand-written" to obj(intent, genStrings, pokemonType, caught),
            "L property literally named type" to obj(intent, typeNamedType),
        )
        variants.forEach { (name, schema) ->
            val result = runCatching { model.generateJson("Answer with JSON.", "Which cards am I missing?", schema) }
            Log.i("PokeCollect.Probe", "$name -> ${result.getOrNull() ?: ("ERROR " + result.exceptionOrNull()?.message?.substringAfter("constraint: "))}")
        }
    }
}
