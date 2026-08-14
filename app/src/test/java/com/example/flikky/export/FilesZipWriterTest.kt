package com.example.flikky.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesZipWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `writes entries and dedupes duplicate names`() {
        val f1 = tmp.newFile("x1").apply { writeText("one") }
        val f2 = tmp.newFile("x2").apply { writeText("two") }
        val out = ByteArrayOutputStream()
        FilesZipWriter.write(
            out,
            listOf(FilesZipWriter.Entry("a.txt", f1), FilesZipWriter.Entry("a.txt", f2)),
        )
        val names = mutableListOf<String>()
        val contents = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                names.add(entry.name)
                contents.add(zin.readBytes().decodeToString())
            }
        }
        assertEquals(listOf("a.txt", "a (1).txt"), names)
        assertEquals(listOf("one", "two"), contents)
    }

    @Test
    fun `unique names sanitize separators and blanks`() {
        assertEquals(
            listOf("a_b.txt", "file", "file (1)"),
            FilesZipWriter.uniqueNames(listOf("a/b.txt", "", "")),
        )
    }
}
