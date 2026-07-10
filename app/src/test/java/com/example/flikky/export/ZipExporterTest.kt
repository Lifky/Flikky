package com.example.flikky.export

import com.example.flikky.session.Origin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.TimeZone
import java.util.zip.ZipInputStream

class ZipExporterTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    // --- helpers ---

    private data class ZipContent(val name: String, val size: Int, val bytes: ByteArray)

    private fun readAll(zipBytes: ByteArray): List<ZipContent> {
        val result = mutableListOf<ZipContent>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (true) {
                    val n = zis.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
                val bytes = out.toByteArray()
                result.add(ZipContent(entry.name, bytes.size, bytes))
                zis.closeEntry()
            }
        }
        return result
    }

    private fun resolver(disk: Map<String, File>): (Long, String) -> File? =
        { _, fileId -> disk[fileId] }

    private fun writeDiskFile(fileId: String, content: String): File {
        val f = tempFolder.newFile(fileId)
        f.writeText(content)
        return f
    }

    // --- 1: empty snapshot ---

    @Test
    fun `empty snapshot writes manifest and README`() {
        val snapshot = ExportSnapshot(sessions = emptyList(), exportedAt = 0L)
        val out = ByteArrayOutputStream()
        ZipExporter.write(out, snapshot, resolver(emptyMap()), utc)

        val entries = readAll(out.toByteArray())
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.name == "manifest.json" })
        val readme = String(entries.single { it.name == "README.txt" }.bytes, Charsets.UTF_8)
        assertTrue(readme.contains("Sessions: 0"))
        assertTrue(readme.contains("Total messages: 0"))
        assertTrue(readme.contains("Total files: 0"))
        assertTrue(readme.contains("Total bytes: 0"))
        assertTrue(readme.contains("Version: 2.0"))
    }

    // --- 2: single session, 1 text + 1 file ---

    @Test
    fun `single session with text and file`() {
        val fileId = "aaa-bbb"
        val disk = mapOf(fileId to writeDiskFile(fileId, "hello-pdf-content"))

        val session = SessionExport(
            id = 12,
            name = "test",
            startedAt = 1_000L,
            endedAt = 2_000L,
            pinned = false,
            messages = listOf(
                MessageExport.Text(ts = 1_500L, origin = Origin.PHONE, content = "hi"),
                MessageExport.File(
                    ts = 1_800L,
                    origin = Origin.BROWSER,
                    fileId = fileId,
                    name = "doc.pdf",
                    mime = "application/pdf",
                    sizeBytes = 17L,
                ),
            ),
        )
        val snapshot = ExportSnapshot(sessions = listOf(session), exportedAt = 0L)
        val out = ByteArrayOutputStream()
        ZipExporter.write(out, snapshot, resolver(disk), utc)

        val entries = readAll(out.toByteArray()).associateBy { it.name }
        assertTrue(entries.containsKey("README.txt"))
        assertTrue(entries.containsKey("sessions/12_test/messages.txt"))
        assertTrue(entries.containsKey("sessions/12_test/messages.json"))
        assertTrue(entries.containsKey("sessions/12_test/files/doc.pdf"))

        val pdfBytes = entries["sessions/12_test/files/doc.pdf"]!!.bytes
        assertEquals("hello-pdf-content", String(pdfBytes, Charsets.UTF_8))
    }

    // --- 3: multiple sessions and aggregation in README ---

    @Test
    fun `multiple sessions aggregate counts in readme`() {
        val f1 = writeDiskFile("f1", "x".repeat(100))
        val f2 = writeDiskFile("f2", "y".repeat(200))
        val disk = mapOf("f1" to f1, "f2" to f2)

        val s1 = SessionExport(
            id = 1, name = "A", startedAt = 0, endedAt = 1, pinned = false,
            messages = listOf(
                MessageExport.Text(0, Origin.PHONE, "hi"),
                MessageExport.File(1, Origin.PHONE, "f1", "a.bin", "application/octet-stream", 100),
            )
        )
        val s2 = SessionExport(
            id = 2, name = "B", startedAt = 0, endedAt = 1, pinned = true,
            messages = listOf(
                MessageExport.Text(0, Origin.BROWSER, "hello"),
                MessageExport.Text(1, Origin.PHONE, "world"),
                MessageExport.File(2, Origin.BROWSER, "f2", "b.bin", "application/octet-stream", 200),
            )
        )
        val snapshot = ExportSnapshot(sessions = listOf(s1, s2), exportedAt = 0L)
        val out = ByteArrayOutputStream()
        ZipExporter.write(out, snapshot, resolver(disk), utc)

        val entries = readAll(out.toByteArray()).associateBy { it.name }
        val readme = String(entries["README.txt"]!!.bytes, Charsets.UTF_8)
        assertTrue(readme.contains("Sessions: 2"))
        assertTrue(readme.contains("Total messages: 5"))
        assertTrue(readme.contains("Total files: 2"))
        assertTrue(readme.contains("Total bytes: 300"))
        assertTrue(entries.containsKey("sessions/1_A/files/a.bin"))
        assertTrue(entries.containsKey("sessions/2_B/files/b.bin"))
    }

    // --- 4: safeName rules ---

    @Test
    fun `safeName replaces slash and colon and truncates`() {
        val name = "04-22 14:30 / 工作"
        val safe = ZipExporter.safeName(name)
        // ':' -> '·', '/' -> '_'
        assertEquals("04-22 14·30 _ 工作", safe)
        assertFalse(safe.contains(':'))
        assertFalse(safe.contains('/'))

        val longName = "x".repeat(200)
        assertEquals(40, ZipExporter.safeName(longName).length)

        // end-to-end through zip
        val session = SessionExport(
            id = 7, name = name, startedAt = 0, endedAt = 0,
            pinned = false, messages = emptyList()
        )
        val out = ByteArrayOutputStream()
        ZipExporter.write(
            out,
            ExportSnapshot(sessions = listOf(session), exportedAt = 0L),
            resolver(emptyMap()),
            utc,
        )
        val entries = readAll(out.toByteArray()).map { it.name }
        assertTrue(entries.any { it == "sessions/7_04-22 14·30 _ 工作/messages.txt" })
    }

    // --- 5: intra-session filename collisions ---

    @Test
    fun `duplicate filenames get numeric suffixes`() {
        // simple extension
        val seen = mutableMapOf<String, Int>()
        assertEquals("photo.jpg", ZipExporter.nextUniqueName(seen, "photo.jpg"))
        assertEquals("photo_2.jpg", ZipExporter.nextUniqueName(seen, "photo.jpg"))
        assertEquals("photo_3.jpg", ZipExporter.nextUniqueName(seen, "photo.jpg"))

        // no extension
        val seen2 = mutableMapOf<String, Int>()
        assertEquals("note", ZipExporter.nextUniqueName(seen2, "note"))
        assertEquals("note_2", ZipExporter.nextUniqueName(seen2, "note"))

        // multi-dot — last dot is the ext separator
        val seen3 = mutableMapOf<String, Int>()
        assertEquals("archive.tar.gz", ZipExporter.nextUniqueName(seen3, "archive.tar.gz"))
        assertEquals("archive.tar_2.gz", ZipExporter.nextUniqueName(seen3, "archive.tar.gz"))

        // end-to-end: two photo.jpg in the same session get de-duplicated in the zip
        val f1 = writeDiskFile("f1", "A")
        val f2 = writeDiskFile("f2", "B")
        val session = SessionExport(
            id = 5, name = "dup", startedAt = 0, endedAt = 0, pinned = false,
            messages = listOf(
                MessageExport.File(0, Origin.PHONE, "f1", "photo.jpg", "image/jpeg", 1),
                MessageExport.File(1, Origin.PHONE, "f2", "photo.jpg", "image/jpeg", 1),
            )
        )
        val out = ByteArrayOutputStream()
        ZipExporter.write(
            out,
            ExportSnapshot(sessions = listOf(session), exportedAt = 0L),
            resolver(mapOf("f1" to f1, "f2" to f2)),
            utc,
        )
        val entries = readAll(out.toByteArray()).associateBy { it.name }
        assertTrue(entries.containsKey("sessions/5_dup/files/photo.jpg"))
        assertTrue(entries.containsKey("sessions/5_dup/files/photo_2.jpg"))
        assertEquals("A", String(entries["sessions/5_dup/files/photo.jpg"]!!.bytes, Charsets.UTF_8))
        assertEquals("B", String(entries["sessions/5_dup/files/photo_2.jpg"]!!.bytes, Charsets.UTF_8))
    }

    // --- 6: missing file -> MISSING placeholder ---

    @Test
    fun `missing file is recorded as placeholder without throwing`() {
        val session = SessionExport(
            id = 9, name = "miss", startedAt = 0, endedAt = 0, pinned = false,
            messages = listOf(
                MessageExport.File(0, Origin.PHONE, "ghost-id", "orig.bin", "application/octet-stream", 10),
            )
        )
        val out = ByteArrayOutputStream()
        ZipExporter.write(
            out,
            ExportSnapshot(sessions = listOf(session), exportedAt = 0L),
            resolver(emptyMap()),
            utc,
        )
        val entries = readAll(out.toByteArray()).associateBy { it.name }
        assertNull("Original path must not be present", entries["sessions/9_miss/files/orig.bin"])
        val placeholder = entries["sessions/9_miss/files/MISSING_ghost-id.txt"]
        assertNotNull(placeholder)
        val body = String(placeholder!!.bytes, Charsets.UTF_8)
        assertTrue(body.contains("File missing"))
    }

    // --- 7: messages.json is valid JSON (formatter T3 is invoked) ---

    @Test
    fun `messages json is parseable`() {
        val session = SessionExport(
            id = 4, name = "jsonparse", startedAt = 100L, endedAt = 200L, pinned = true,
            messages = listOf(
                MessageExport.Text(150L, Origin.PHONE, "hello"),
            )
        )
        val out = ByteArrayOutputStream()
        ZipExporter.write(
            out,
            ExportSnapshot(sessions = listOf(session), exportedAt = 0L),
            resolver(emptyMap()),
            utc,
        )
        val entries = readAll(out.toByteArray()).associateBy { it.name }
        val jsonText = String(entries["sessions/4_jsonparse/messages.json"]!!.bytes, Charsets.UTF_8)
        val parsed = Json.parseToJsonElement(jsonText).jsonObject
        assertEquals(4L, parsed["sessionId"]!!.jsonPrimitive.long)
    }

    // --- 8: messages.txt includes aligned origin tag (formatter T2 is invoked) ---

    @Test
    fun `messages txt contains aligned phone tag`() {
        val session = SessionExport(
            id = 3, name = "txt", startedAt = 0L, endedAt = 0L, pinned = false,
            messages = listOf(
                MessageExport.Text(0L, Origin.PHONE, "hi"),
            )
        )
        val out = ByteArrayOutputStream()
        ZipExporter.write(
            out,
            ExportSnapshot(sessions = listOf(session), exportedAt = 0L),
            resolver(emptyMap()),
            utc,
        )
        val entries = readAll(out.toByteArray()).associateBy { it.name }
        val txt = String(entries["sessions/3_txt/messages.txt"]!!.bytes, Charsets.UTF_8)
        assertTrue("txt must contain aligned [PHONE  ] tag, was:\n$txt", txt.contains("[PHONE  ]"))
    }
}
