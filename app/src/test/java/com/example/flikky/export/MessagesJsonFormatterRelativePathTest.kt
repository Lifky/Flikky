package com.example.flikky.export

import com.example.flikky.session.Origin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesJsonFormatterRelativePathTest {

    private val session = SessionExport(
        id = 1,
        name = "Test",
        startedAt = 1000,
        endedAt = 2000,
        pinned = false,
        messages = listOf(
            MessageExport.File(
                ts = 1001, origin = Origin.PHONE, fileId = "aaa",
                name = "photo.jpg", mime = "image/jpeg", sizeBytes = 100,
            ),
            MessageExport.File(
                ts = 1002, origin = Origin.PHONE, fileId = "bbb",
                name = "photo.jpg", mime = "image/jpeg", sizeBytes = 200,
            ),
        ),
    )

    @Test
    fun `relativePath uses provided map when available`() {
        val map = mapOf(
            "aaa" to "files/photo.jpg",
            "bbb" to "files/photo_2.jpg",
        )
        val jsonStr = MessagesJsonFormatter.format(session, fileIdToRelativePath = map)
        val parsed = Json.parseToJsonElement(jsonStr).jsonObject

        val messages = parsed["messages"]!!.jsonArray
        val file1 = messages[0].jsonObject
        val file2 = messages[1].jsonObject

        assertEquals("files/photo.jpg", file1["relativePath"]!!.jsonPrimitive.content)
        assertEquals("files/photo_2.jpg", file2["relativePath"]!!.jsonPrimitive.content)
    }

    @Test
    fun `relativePath falls back to files-name when map is null`() {
        val jsonStr = MessagesJsonFormatter.format(session, fileIdToRelativePath = null)
        val parsed = Json.parseToJsonElement(jsonStr).jsonObject

        val messages = parsed["messages"]!!.jsonArray
        val file1 = messages[0].jsonObject
        val file2 = messages[1].jsonObject

        assertEquals("files/photo.jpg", file1["relativePath"]!!.jsonPrimitive.content)
        assertEquals("files/photo.jpg", file2["relativePath"]!!.jsonPrimitive.content)
    }
}
