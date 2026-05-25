package com.example.flikky.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipImporterTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
    }

    private fun createZip(builder: ZipOutputStream.() -> Unit): ZipFile {
        val f = File.createTempFile("test", ".zip")
        tempFiles.add(f)
        ZipOutputStream(f.outputStream()).use { it.builder() }
        return ZipFile(f)
    }

    private fun ZipOutputStream.writeText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private val testJson = Json { prettyPrint = true; encodeDefaults = true }

    private fun sessionJson(
        id: Long = 1,
        name: String = "Test",
        startedAt: Long = 1000,
        endedAt: Long? = 2000,
        pinned: Boolean = false,
        messages: List<MessageDto> = emptyList(),
    ): String {
        val dto = SessionDto(id, name, startedAt, endedAt, pinned, messages)
        return testJson.encodeToString(dto)
    }

    @Test
    fun `parse valid v1_4 zip returns session with messages`() {
        val json = sessionJson(
            messages = listOf(
                MessageDto.TextDto(ts = 1001, origin = "PHONE", content = "hello"),
                MessageDto.FileDto(
                    ts = 1002, origin = "BROWSER", fileId = "abc",
                    name = "test.txt", mime = "text/plain", sizeBytes = 5,
                    relativePath = "files/test.txt",
                ),
            ),
        )
        val zip = createZip {
            writeText("README.txt", "Flikky Export\nVersion: 1.4\n")
            writeText("sessions/1_Test/messages.json", json)
            writeText("sessions/1_Test/files/test.txt", "hello")
        }

        val sessions = ZipImporter.parse(zip)
        assertEquals(1, sessions.size)
        val s = sessions[0]
        assertEquals("Test", s.name)
        assertEquals(1000L, s.startedAt)
        assertEquals(2, s.messages.size)
        assertTrue(s.messages[0] is ParsedMessage.Text)
        assertTrue(s.messages[1] is ParsedMessage.File)
        assertEquals("1.4", s.version)
        zip.close()
    }

    @Test
    fun `resolveFileEntry v1_4 uses relativePath directly`() {
        val fileMsg = ParsedMessage.File(
            ts = 1, origin = "PHONE", fileId = "abc",
            name = "test.txt", mime = "text/plain", sizeBytes = 5,
            relativePath = "files/test.txt",
        )
        val zip = createZip {
            writeText("README.txt", "Version: 1.4\n")
            writeText("sessions/1_Test/messages.json", sessionJson())
            writeText("sessions/1_Test/files/test.txt", "hello")
        }

        val entry = ZipImporter.resolveFileEntry(
            "1.4", listOf(fileMsg), "abc", "sessions/1_Test", zip,
        )
        assertNotNull(entry)
        assertEquals("sessions/1_Test/files/test.txt", entry!!.name)
        zip.close()
    }

    @Test
    fun `resolveFileEntry v1_2 replays dedup naming`() {
        val file1 = ParsedMessage.File(
            ts = 1, origin = "PHONE", fileId = "aaa",
            name = "photo.jpg", mime = "image/jpeg", sizeBytes = 100,
            relativePath = "files/photo.jpg",
        )
        val file2 = ParsedMessage.File(
            ts = 2, origin = "PHONE", fileId = "bbb",
            name = "photo.jpg", mime = "image/jpeg", sizeBytes = 200,
            relativePath = "files/photo.jpg",
        )
        val zip = createZip {
            writeText("README.txt", "Version: 1.2\n")
            writeText("sessions/1_Test/messages.json", sessionJson())
            writeText("sessions/1_Test/files/photo.jpg", "first")
            writeText("sessions/1_Test/files/photo_2.jpg", "second")
        }

        val entry1 = ZipImporter.resolveFileEntry(
            "1.2", listOf(file1, file2), "aaa", "sessions/1_Test", zip,
        )
        assertNotNull(entry1)
        assertEquals("sessions/1_Test/files/photo.jpg", entry1!!.name)

        val entry2 = ZipImporter.resolveFileEntry(
            "1.2", listOf(file1, file2), "bbb", "sessions/1_Test", zip,
        )
        assertNotNull(entry2)
        assertEquals("sessions/1_Test/files/photo_2.jpg", entry2!!.name)
        zip.close()
    }

    @Test
    fun `parse skips path traversal entries`() {
        val zip = createZip {
            writeText("README.txt", "Version: 1.4\n")
            writeText("../etc/passwd", "bad")
            writeText("sessions/1_Test/messages.json", sessionJson())
        }

        val sessions = ZipImporter.parse(zip)
        assertEquals(1, sessions.size)
        zip.close()
    }

    @Test
    fun `parse returns empty for zip without sessions`() {
        val zip = createZip {
            writeText("README.txt", "Version: 1.4\n")
            writeText("other/file.txt", "nothing")
        }

        val sessions = ZipImporter.parse(zip)
        assertTrue(sessions.isEmpty())
        zip.close()
    }

    @Test
    fun `resolveFileEntry returns null for missing file`() {
        val fileMsg = ParsedMessage.File(
            ts = 1, origin = "PHONE", fileId = "abc",
            name = "missing.txt", mime = "text/plain", sizeBytes = 5,
            relativePath = "files/missing.txt",
        )
        val zip = createZip {
            writeText("README.txt", "Version: 1.4\n")
            writeText("sessions/1_Test/messages.json", sessionJson())
        }

        val entry = ZipImporter.resolveFileEntry(
            "1.4", listOf(fileMsg), "abc", "sessions/1_Test", zip,
        )
        assertNull(entry)
        zip.close()
    }
}
