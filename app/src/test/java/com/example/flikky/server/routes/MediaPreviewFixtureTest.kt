package com.example.flikky.server.routes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPreviewFixtureTest {

    private val fixture = Json.parseToJsonElement(
        javaClass.classLoader!!.getResourceAsStream("media-preview.json")!!
            .bufferedReader().use { it.readText() },
    ).jsonObject

    private fun values(name: String): Set<String> = fixture[name]!!.jsonArray
        .map { it.jsonPrimitive.content }
        .toSet()

    @Test
    fun `fixture inline list is exactly the server whitelist`() {
        assertEquals(values("inlineable"), INLINE_MIME_WHITELIST)
        assertEquals(9, values("inlineable").size)
    }

    @Test
    fun `svg is explicitly excluded from inline rendering`() {
        assertTrue("SVG can execute script and must never be inline", values("excluded").contains("image/svg+xml"))
        assertTrue("SVG must not be in the inline whitelist", "image/svg+xml" !in INLINE_MIME_WHITELIST)
    }

    @Test
    fun `thumbnailable media and inline media share the declared catalogue`() {
        assertEquals(values("inlineable"), values("thumbnailable"))
        assertEquals(9, values("thumbnailable").size)
        assertEquals(3, values("excluded").size)
    }
}
