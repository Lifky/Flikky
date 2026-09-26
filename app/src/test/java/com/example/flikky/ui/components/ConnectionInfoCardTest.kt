package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionInfoCardTest {
    @Test fun `each address row carries its own copy action and the qr action stays outside`() {
        // 2026-09-26 用户选定：两行地址行尾各一个复制图标，二维码单独一行。
        val row = blockStartingAt(code(), "private fun AddressRow(")
        assertTrue(row.contains("R.drawable.ic_content_copy")); assertTrue(!row.contains("R.drawable.ic_qr_code_2"))
    }
    @Test fun `copy buttons use the same tonal icon button as the qr action`() {
        // 2026-09-27 用户：复制按钮与二维码按钮的背景色对齐。
        val row = blockStartingAt(code(), "private fun AddressRow(")
        assertTrue(row.contains("FilledTonalIconButton(onClick = onCopy)")); assertTrue(!row.contains(" IconButton("))
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
