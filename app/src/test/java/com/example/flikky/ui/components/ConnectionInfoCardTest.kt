package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionInfoCardTest {
    @Test fun `copy and qr actions sit in the same row`() {
        val card = code(); val row = blockStartingAt(card, "Row(")
        assertTrue(row.contains("R.string.connection_copy_address")); assertTrue(row.contains("R.drawable.ic_qr_code_2"))
    }
    @Test fun `the qr button is not parked at the end of the url line`() {
        val block = code().substringAfter("text = url").take(200)
        assertTrue(!block.contains("ic_qr_code_2"))
    }
    @Test fun `the sheet is owned by the card, not by each screen`() { assertTrue(code().contains("QrCodeSheet(")) }
    private fun code(): String {
        val f = File("src/main/java/com/example/flikky/ui/components/ConnectionInfoCard.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/ui/components/ConnectionInfoCard.kt")
        assertTrue(f.isFile); return f.readText().replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\r\n]*"""), "")
    }

    private fun blockStartingAt(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("Missing block: $marker", start >= 0)
        val openingBrace = source.indexOf('{', start)
        assertTrue("Missing opening brace: $marker", openingBrace >= 0)
        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(start, index + 1)
            }
        }
        throw AssertionError("Missing closing brace: $marker")
    }
}
