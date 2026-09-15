package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeSheetTest {
    @Test fun `code area is hard coded light on dark, never theme driven`() {
        val sheet = productCode()
        assertTrue(sheet.contains("Color.White")); assertTrue(sheet.contains("Color.Black"))
        assertFalse(Regex("""drawRect\([^)]*colorScheme""").containsMatchIn(sheet))
    }
    @Test fun `quiet zone comes from the shared constant`() { assertTrue(productCode().contains("QrMatrix.QUIET_ZONE")) }
    @Test fun `sheet does not repeat the url or pin already on the card`() {
        val sheet = productCode()
        assertFalse(sheet.contains("R.string.connection_copy_address"))
        assertFalse(Regex("""Text\(\s*text\s*=\s*url""").containsMatchIn(sheet))
    }
    @Test fun `the guards actually fire on a theme driven code area`() {
        val bad = "drawRect(color = MaterialTheme.colorScheme.surface)"
        assertTrue(Regex("""drawRect\([^)]*colorScheme""").containsMatchIn(bad)); assertFalse(bad.contains("Color.White"))
    }
    private fun productCode(): String {
        val file = File("src/main/java/com/example/flikky/ui/components/QrCodeSheet.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/ui/components/QrCodeSheet.kt")
        assertTrue(file.isFile)
        return file.readText().replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\r\n]*"""), "")
    }
}
