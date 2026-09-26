package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionInfoCardStructureTest {
    private val card: String by lazy {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { File(dir, "$it/com/example/flikky/ui/components/ConnectionInfoCard.kt") }
                .firstOrNull { it.isFile }?.let { return@lazy it.readText() }
            dir = dir.parentFile
        }
        error("missing ConnectionInfoCard.kt")
    }

    @Test fun `the qr code encodes the ip address, not the local name`() {
        // 扫码的是手机，而 Android 解析不了 .local（2026-09-26 实验结论）。
        assertTrue(card.contains("QrCodeSheet(url = address.primaryUrl"))
        assertFalse(card.contains("QrCodeSheet(url = address.localUrl"))
    }

    @Test fun `both address lines share one font size and never wrap`() {
        // 用户 2026-09-26：大字号时端口号会被挤到下一行；两行要同样大小。
        // 两行都用同一个 addressStyle（按较长的那行算出来的共享字号），且单行不换行。
        // 只数调用点，排除 `private fun AddressRow(` 声明本身。
        val rows = Regex("(?<!fun )AddressRow\\(([\\s\\S]*?)\\n\\s*\\)").findAll(card).map { it.groupValues[1] }.toList()
        assertTrue("expected two AddressRow calls, found ${rows.size}", rows.size == 2)
        rows.forEach { assertTrue("row must use the shared style: $it", it.contains("style = addressStyle")) }
        assertTrue(card.contains("maxLines = 1"))
        assertTrue(card.contains("softWrap = false"))
        assertTrue("the shared size must come from fitFontSize", card.contains("fitFontSize("))
    }

    @Test fun `the ip address is rendered before the local name`() {
        val primary = card.indexOf("address.primaryUrl")
        val local = card.indexOf("address.localUrl")
        assertTrue(primary in 0 until local)
    }
}
