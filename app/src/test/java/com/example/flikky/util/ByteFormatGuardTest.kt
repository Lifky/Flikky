package com.example.flikky.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ByteFormatGuardTest {

    private val sourceRoot = File("src/main/java/com/example/flikky")
    private val localFormatLiteral = Regex("%\\.\\d+f\\s+(?:KB|MB|GB)\"")

    private fun productSources(): List<Pair<String, String>> = listOf("ui", "service", "export")
        .flatMap { packageName ->
            sourceRoot.resolve(packageName).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it.relativeTo(sourceRoot).invariantSeparatorsPath to withoutCommentsAndImports(it.readText()) }
                .toList()
        }

    private fun withoutCommentsAndImports(source: String): String = source
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("(?m)//.*$"), "")
        .replace(Regex("(?m)^\\s*import\\s+.*$"), "")

    @Test
    fun `source guard scans the byte formatter call sites after stripping source noise`() {
        val callSites = productSources().sumOf { (_, source) ->
            Regex("formatBytes\\(").findAll(source).count() +
                Regex("formatSize\\(").findAll(source).count()
        }
        assertTrue("The source slice no longer reaches the byte-formatting call sites", callSites >= 6)
        assertTrue(localFormatLiteral.containsMatchIn("val label = \"%.1f MB\".format(bytes)"))
        assertTrue(!localFormatLiteral.containsMatchIn("val rate = \"%.1f MB/s\".format(bytes)"))
    }

    @Test
    fun `ui service and export packages do not keep private byte formatting literals`() {
        val offenders = productSources().mapNotNull { (path, source) ->
            if (localFormatLiteral.containsMatchIn(source)) path else null
        }
        assertEquals(
            "Byte formatting belongs in util ByteFormat.kt; do not add another local formatter",
            emptyList<String>(),
            offenders,
        )
    }
}
