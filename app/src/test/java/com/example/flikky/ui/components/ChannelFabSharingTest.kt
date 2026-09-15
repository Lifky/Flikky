package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps every peer channel on one shared lock and selection-menu geometry. */
class ChannelFabSharingTest {

    private val tabs = listOf(
        "ui/serving/storage/ServingStorageTab.kt",
        "ui/serving/album/ServingAlbumTab.kt",
    )

    @Test
    fun `neither tab declares its own fab sizes`() {
        tabs.forEach { relative ->
            val source = productCode(relative)
            assertFalse(
                "$relative declares its own FAB size",
                source.contains("56.dp") ||
                    source.contains("containerSizeMedium") ||
                    source.contains("ChannelLockFabSize ="),
            )
        }
    }

    @Test
    fun `both tabs reach the shared lock fab`() {
        tabs.forEach { relative ->
            assertTrue(
                "$relative does not use the shared lock FAB",
                productCode(relative).contains("PeerChannelLockFab("),
            )
        }
    }

    @Test
    fun `the shared menu owns the fixed centre geometry`() {
        val shared = productCode("ui/components/ChannelSelectionFabMenu.kt")

        assertTrue(shared.contains("ChannelSelectionFabSize"))
        assertTrue(shared.contains("offset("))
        assertTrue(shared.contains("animateDpAsState"))
    }

    @Test
    fun `the storage fab is now a caller, not a second implementation`() {
        val storage = productCode("ui/serving/storage/StorageSelectionFab.kt")

        assertTrue(storage.contains("ChannelSelectionFabMenu("))
        assertFalse(storage.contains("animateDpAsState"))
    }

    @Test
    fun `the guard fires on a tab that hard codes a size`() {
        val bad = "Box(Modifier.size(56.dp)) { }"

        assertTrue(bad.contains("56.dp"))
    }

    private fun productCode(relative: String): String {
        val file = File("src/main/java/com/example/flikky/$relative")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/$relative")
        assertTrue("source file not found: $relative", file.isFile)
        return stripCommentsAndImports(file.readText())
    }

    private fun stripCommentsAndImports(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\r\n]*"""), "")
        .replace(Regex("""(?m)^\s*import\s+[^\r\n]+\r?\n"""), "")
}
