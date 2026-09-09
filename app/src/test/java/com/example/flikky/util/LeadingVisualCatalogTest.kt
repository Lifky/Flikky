package com.example.flikky.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingVisualCatalogTest {
    @Test
    fun typeIdsAreUniqueAndNonEmpty() {
        val ids = LeadingVisualCatalog.types.map { it.id }

        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun semanticTypesHaveDistinctHueShifts() {
        val shifts = LeadingVisualCatalog.types
            .filterNot { it.id == "other" }
            .map { it.hueShift }

        assertEquals(shifts.size, shifts.distinct().size)
    }

    @Test
    fun everyTypeHasAMaterialSymbol() {
        assertTrue(LeadingVisualCatalog.types.all { it.symbol.isNotBlank() })
    }

    @Test
    fun mimeCriteriaAreUnambiguous() {
        val samples = LeadingVisualCatalog.types.flatMap { type ->
            type.mimePrefixes.map { "${it}flikky-test" } + type.mimeExact
        }

        samples.forEach { mime ->
            val matches = LeadingVisualCatalog.types.filter { it.matches(mime) }
            assertEquals("$mime matched ${matches.map { it.id }}", 1, matches.size)
        }
    }

    @Test
    fun sharedFixtureMatchesEveryCatalogueRow() {
        val fixture = fixtureTypes()

        assertEquals(LeadingVisualCatalog.types.size, fixture.size)
        LeadingVisualCatalog.types.zip(fixture).forEach { (expected, actual) ->
            val obj = actual.jsonObject
            assertEquals(expected.id, obj.getValue("id").jsonPrimitive.content)
            assertEquals(expected.symbol, obj.getValue("symbol").jsonPrimitive.content)
            assertEquals(expected.mimePrefixes, obj.getValue("mimePrefixes").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(expected.mimeExact, obj.getValue("mimeExact").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(expected.hueShift, obj.getValue("hueShift").jsonPrimitive.content.toInt())
            assertEquals(expected.fixedArgb, obj.getValue("fixedArgb").jsonPrimitive.content.toLong())
        }
    }

    @Test
    fun everyFixedColourIsOpaque() {
        LeadingVisualCatalog.types.forEach { type ->
            assertEquals("${type.id} must be opaque", 0xFFL, type.fixedArgb ushr 24)
        }
    }

    private fun fixtureTypes() = checkNotNull(javaClass.getResourceAsStream("/leading-types.json")) {
        "missing shared fixture leading-types.json"
    }.bufferedReader(Charsets.UTF_8).use { reader ->
        Json.parseToJsonElement(reader.readText()).jsonArray
    }
}
