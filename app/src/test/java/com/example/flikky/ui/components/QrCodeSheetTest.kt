package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeSheetTest {
    // Color, corners and scan readability are verified from actual rendered
    // pixels in androidTest/QrCodeRenderingTest, including light/dark surfaces.
    @Test fun `quiet zone comes from the shared constant`() { assertTrue(productCode().contains("QrMatrix.QUIET_ZONE")) }
    @Test fun `sheet does not repeat the url or pin already on the card`() {
        val sheet = productCode()
        assertFalse(sheet.contains("R.string.connection_copy_address"))
        assertFalse(Regex("""Text\(\s*text\s*=\s*url""").containsMatchIn(sheet))
    }
    private fun productCode(): String {
        val file = File("src/main/java/com/example/flikky/ui/components/QrCodeSheet.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/ui/components/QrCodeSheet.kt")
        assertTrue(file.isFile)
        return file.readText().replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\r\n]*"""), "")
    }
}
