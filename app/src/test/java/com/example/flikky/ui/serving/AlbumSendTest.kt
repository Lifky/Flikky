package com.example.flikky.ui.serving

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumSendTest {

    @Test
    fun `both send wrappers land on the one shared payload core`() {
        val controller = productCode("service/TransferController.kt")
        val stored = controller.substring(controller.indexOf("fun offerStoredFile"))
            .substringBefore("suspend fun offerStreamedFile")
        val streamed = controller.substring(controller.indexOf("fun offerStreamedFile"))
            .substringBefore("private suspend fun offerFilePayload")

        assertTrue("offerStoredFile no longer uses the shared core", stored.contains("offerFilePayload("))
        assertTrue("offerStreamedFile does not use the shared core", streamed.contains("offerFilePayload("))
        assertFalse("offerStreamedFile copies by itself", streamed.contains("outputStream()"))
        assertFalse("offerStreamedFile broadcasts by itself", streamed.contains("broadcast("))
    }

    @Test
    fun `album sending streams through the content resolver`() {
        val vm = productCode("ui/serving/ServingViewModel.kt")
        val block = vm.substring(vm.indexOf("fun sendAlbumSelection")).take(1500)

        assertTrue("album sending does not use the streamed wrapper", block.contains("offerStreamedFile("))
        assertTrue("album sending does not use ContentResolver", block.contains("openInputStream("))
        assertFalse(
            "album sending first copies into cache",
            block.contains("copyTo(") || block.contains("createTempFile("),
        )
    }

    @Test
    fun `the album tab is not gated by the peer switch`() {
        val screen = productCode("ui/serving/ServingScreen.kt")
        val block = screen.substring(screen.indexOf("ServingAlbumTab(")).take(900)

        assertTrue("album tab does not receive the peer switch", block.contains("peerAlbumEnabled"))
        assertFalse(
            "album tab is gated by the peer switch",
            Regex("""if\s*\([^)]*albumBrowsingEnabled""").containsMatchIn(screen),
        )
    }

    @Test
    fun `the pager carries three pages`() {
        val screen = productCode("ui/serving/ServingScreen.kt")

        assertTrue("tab labels do not include the album", screen.contains("R.string.serving_tab_album"))
        assertTrue("pager has no album page", screen.contains("ServingAlbumTab("))
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
