package com.example.flikky.export

import com.example.flikky.session.Origin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesJsonFormatterTest {

    private fun parse(raw: String): JsonObject =
        Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `empty session produces messages empty array`() {
        val session = SessionExport(
            id = 1,
            name = "empty",
            startedAt = 1_000L,
            endedAt = 2_000L,
            pinned = false,
            messages = emptyList(),
        )

        val output = MessagesJsonFormatter.format(session)
        val root = parse(output)

        assertEquals(1L, root["sessionId"]!!.jsonPrimitive.long)
        assertEquals("empty", root["name"]!!.jsonPrimitive.content)
        assertEquals(1_000L, root["startedAt"]!!.jsonPrimitive.long)
        assertEquals(2_000L, root["endedAt"]!!.jsonPrimitive.long)
        assertFalse(root["pinned"]!!.jsonPrimitive.boolean)
        val msgs = root["messages"]!!.jsonArray
        assertEquals(0, msgs.size)
    }

    @Test
    fun `text message carries all fields`() {
        val session = SessionExport(
            id = 12,
            name = "04-22 14:30 与小明",
            startedAt = 1_713_768_615_000L,
            endedAt = 1_713_770_538_000L,
            pinned = true,
            messages = listOf(
                MessageExport.Text(
                    ts = 1_713_768_615_000L,
                    origin = Origin.PHONE,
                    content = "hello world",
                ),
            ),
        )

        val root = parse(MessagesJsonFormatter.format(session))
        val msg = root["messages"]!!.jsonArray.single().jsonObject

        assertEquals("text", msg["type"]!!.jsonPrimitive.content)
        assertEquals(1_713_768_615_000L, msg["ts"]!!.jsonPrimitive.long)
        assertEquals("PHONE", msg["origin"]!!.jsonPrimitive.content)
        assertEquals("hello world", msg["content"]!!.jsonPrimitive.content)
        assertTrue(root["pinned"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `file message carries all fields and relativePath under files`() {
        val session = SessionExport(
            id = 7,
            name = "s",
            startedAt = 0L,
            endedAt = null,
            pinned = false,
            messages = listOf(
                MessageExport.File(
                    ts = 1_713_768_940_000L,
                    origin = Origin.BROWSER,
                    fileId = "a45706d9-a576-42c5-bfd4-de3a06743103",
                    name = "doc.pdf",
                    mime = "application/pdf",
                    sizeBytes = 1_234_567L,
                ),
            ),
        )

        val msg = parse(MessagesJsonFormatter.format(session))["messages"]!!
            .jsonArray.single().jsonObject

        assertEquals("file", msg["type"]!!.jsonPrimitive.content)
        assertEquals(1_713_768_940_000L, msg["ts"]!!.jsonPrimitive.long)
        assertEquals("BROWSER", msg["origin"]!!.jsonPrimitive.content)
        assertEquals(
            "a45706d9-a576-42c5-bfd4-de3a06743103",
            msg["fileId"]!!.jsonPrimitive.content,
        )
        assertEquals("doc.pdf", msg["name"]!!.jsonPrimitive.content)
        assertEquals("application/pdf", msg["mime"]!!.jsonPrimitive.content)
        assertEquals(1_234_567L, msg["sizeBytes"]!!.jsonPrimitive.long)
        assertEquals("files/doc.pdf", msg["relativePath"]!!.jsonPrimitive.content)
    }

    @Test
    fun `null endedAt serialized as JSON null literal`() {
        val session = SessionExport(
            id = 1,
            name = "ongoing",
            startedAt = 1L,
            endedAt = null,
            pinned = false,
            messages = emptyList(),
        )

        val raw = MessagesJsonFormatter.format(session)
        val root = parse(raw)

        // parsed element must be JsonNull (not the string "null" or absent key)
        assertTrue(root.containsKey("endedAt"))
        assertTrue(root["endedAt"] is JsonNull)
        // and raw text actually contains `"endedAt": null`
        assertTrue(
            "raw output should contain endedAt: null, was: $raw",
            raw.contains("\"endedAt\": null"),
        )
    }

    @Test
    fun `origin serialized as string for both PHONE and BROWSER`() {
        val session = SessionExport(
            id = 1,
            name = "s",
            startedAt = 0L,
            endedAt = 0L,
            pinned = false,
            messages = listOf(
                MessageExport.Text(ts = 1L, origin = Origin.PHONE, content = "a"),
                MessageExport.Text(ts = 2L, origin = Origin.BROWSER, content = "b"),
            ),
        )

        val msgs = parse(MessagesJsonFormatter.format(session))["messages"]!!.jsonArray

        val phoneOrigin = msgs[0].jsonObject["origin"]!!
        val browserOrigin = msgs[1].jsonObject["origin"]!!
        assertTrue(phoneOrigin is JsonPrimitive && phoneOrigin.isString)
        assertTrue(browserOrigin is JsonPrimitive && browserOrigin.isString)
        assertEquals("PHONE", phoneOrigin.jsonPrimitive.content)
        assertEquals("BROWSER", browserOrigin.jsonPrimitive.content)
    }

    @Test
    fun `content with newline quote chinese and emoji is escaped correctly`() {
        val tricky = "line1\nline2 \"quoted\" 中文 🚀"
        val session = SessionExport(
            id = 1,
            name = "s",
            startedAt = 0L,
            endedAt = 0L,
            pinned = false,
            messages = listOf(
                MessageExport.Text(ts = 1L, origin = Origin.PHONE, content = tricky),
            ),
        )

        val raw = MessagesJsonFormatter.format(session)
        // raw must be valid JSON (parser would throw otherwise)
        val parsedContent = parse(raw)["messages"]!!.jsonArray.single()
            .jsonObject["content"]!!.jsonPrimitive.content
        // roundtrip must recover exactly the original string, proving escaping
        assertEquals(tricky, parsedContent)
        // ensure the raw form actually escaped the newline and quote
        assertTrue(raw.contains("\\n"))
        assertTrue(raw.contains("\\\""))
    }

    @Test
    fun `output is valid JSON and roundtrips with all fields present`() {
        val session = SessionExport(
            id = 42,
            name = "round-trip",
            startedAt = 111L,
            endedAt = 222L,
            pinned = true,
            messages = listOf(
                MessageExport.Text(ts = 150L, origin = Origin.PHONE, content = "hi"),
                MessageExport.File(
                    ts = 180L,
                    origin = Origin.BROWSER,
                    fileId = "fid-1",
                    name = "report.txt",
                    mime = "text/plain",
                    sizeBytes = 10L,
                ),
            ),
        )

        val raw = MessagesJsonFormatter.format(session)
        val root = Json.parseToJsonElement(raw).jsonObject

        // top-level keys all present
        val expectedKeys = setOf(
            "sessionId", "name", "startedAt", "endedAt", "pinned", "messages",
        )
        assertEquals(expectedKeys, root.keys)

        val msgs = root["messages"]!!.jsonArray
        assertEquals(2, msgs.size)

        val textKeys = msgs[0].jsonObject.keys
        assertEquals(setOf("type", "ts", "origin", "content"), textKeys)

        val fileKeys = msgs[1].jsonObject.keys
        assertEquals(
            setOf(
                "type", "ts", "origin",
                "fileId", "name", "mime", "sizeBytes", "relativePath",
            ),
            fileKeys,
        )
    }

    @Test
    fun `default json is pretty printed with newlines`() {
        val session = SessionExport(
            id = 1,
            name = "pp",
            startedAt = 0L,
            endedAt = null,
            pinned = false,
            messages = listOf(
                MessageExport.Text(ts = 1L, origin = Origin.PHONE, content = "a"),
            ),
        )

        val raw = MessagesJsonFormatter.format(session)
        // pretty-printed output contains at least one newline between fields
        assertTrue("pretty print should emit newlines: $raw", raw.contains("\n"))
        // and an indentation of at least 2 spaces somewhere
        assertTrue(raw.contains("  \""))
        // sanity: default behaviour does not accidentally drop endedAt=null
        assertNull(parse(raw)["endedAt"]?.takeIf { it !is JsonNull })
    }
}
