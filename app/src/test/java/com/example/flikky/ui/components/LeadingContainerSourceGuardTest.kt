package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingContainerSourceGuardTest {
    private val uiRoot = File("src/main/java/com/example/flikky/ui")

    private fun withoutCommentsAndImports(source: String): String = source
        .replace(Regex("""(?s)/\*.*?\*/"""), "")
        .replace(Regex("""(?m)//.*$"""), "")
        .replace(Regex("""(?m)^\s*import\s+.*$"""), "")

    @Test
    fun uiLeadingContainersDoNotBypassTheSharedShapeSource() {
        val violations = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { withoutCommentsAndImports(it.readText()).contains("MaterialShapes.") }
            .map { it.relativeTo(uiRoot).invariantSeparatorsPath }
            .toList()

        assertEquals(
            "UI code must resolve every leading shape through LeadingShapeGeometry",
            emptyList<String>(),
            violations,
        )
    }

    @Test
    fun chatFileBubbleUsesTheSharedLeadingVisual() {
        val source = withoutCommentsAndImports(
            File(uiRoot, "components/MessageBubble.kt").readText(),
        )
        val fileBubble = source.substringAfter("private fun FileBubbleContent(")
        assertTrue(
            "The chat file bubble must use FileLeadingVisual so global shape changes reach it",
            fileBubble.contains("FileLeadingVisual("),
        )
    }
}
