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
        val rows = addressRowCalls()
        rows.forEach { assertTrue("row must use the shared style: $it", it.contains("style = addressStyle")) }
        assertTrue(card.contains("maxLines = 1"))
        assertTrue(card.contains("softWrap = false"))
        assertTrue("the shared size must come from fitFontSize", card.contains("fitFontSize("))
    }

    @Test fun `both copy icons line up because both rows share one text width`() {
        // 2026-09-26 审查：两行各自居中时，长短不同的地址把行尾复制图标推到不同的横向位置。
        // 两行文字占同一个宽度（较长那行的宽度），整组居中，图标才落在同一条竖线上。
        addressRowCalls().forEach {
            assertTrue("row must use the shared text width: $it", it.contains("textWidth = textWidth"))
        }
        assertTrue(card.contains("Modifier.width(textWidth)"))
    }

    /** 只数调用点，排除 `private fun AddressRow(` 声明本身。 */
    private fun addressRowCalls(): List<String> {
        val rows = Regex("(?<!fun )AddressRow\\(([\\s\\S]*?)\\n\\s*\\)").findAll(card).map { it.groupValues[1] }.toList()
        assertTrue("expected two AddressRow calls, found ${rows.size}", rows.size == 2)
        return rows
    }

    @Test fun `the ip address is rendered before the local name`() {
        val primary = card.indexOf("url = address.primaryUrl")
        val local = card.indexOf("address.localUrl?.let")
        assertTrue(primary in 0 until local)
    }

    @Test fun `the addresses use the app font, not monospace`() {
        // 2026-09-27 用户：系统等宽字体不跟随 MiSans 与字重，小米上和周围文字不搭。PIN 不在此列。
        val base = card.lines().single { it.contains("val base = ") }
        assertFalse(base, base.contains("Monospace"))
    }

    @Test fun `the info button sits on the title line, not on its own hint line`() {
        // 2026-09-27 用户选定方案 C：「以上任意地址均可打开」那一行去掉，说明进标题旁的 ⓘ。
        // ⓘ 与设置页同一实现；只有显示局域网名称那一行时才有 ⓘ（它讲的就是那一行）。
        val title = card.substringAfter("private fun TitleRow(").substringBefore("\n@Composable")
        assertTrue(title.contains("R.string.connection_open_in_browser"))
        assertTrue(title.contains("InfoIconButton("))
        assertTrue(title.contains("R.string.connection_local_info"))
        assertTrue(card.contains("TitleRow(showInfo = address.localUrl != null)"))
        assertFalse(card.contains("R.string.connection_local_hint"))
        val settingItem = source("com/example/flikky/ui/settings/components/SettingItem.kt")
        assertTrue("settings rows must use the same info button", settingItem.contains("InfoIconButton("))
        assertFalse("settings rows must not keep a second info dialog", settingItem.contains("AlertDialog("))
    }

    @Test fun `the gap before the copy button is part of the width budget`() {
        // tonal 圆底让 4dp 的触摸余量显得贴字：加间距，且字号预算必须扣掉它，否则长地址会被挤到换行。
        assertTrue(card.contains("Spacer(Modifier.width(COPY_BUTTON_GAP))"))
        assertTrue(card.contains("maxWidth - COPY_BUTTON_GAP - COPY_BUTTON_WIDTH"))
    }

    @Test fun `the centered title is balanced against its info button`() {
        // 标题居中、ⓘ 挂在右边：左边补一个同宽的空位，标题文字本身才落在正中（与下面几行同一条中轴）。
        val title = card.substringAfter("private fun TitleRow(").substringBefore("\n@Composable")
        assertTrue(title.contains("Spacer(Modifier.width(INFO_BUTTON_SIZE))"))
    }

    @Test fun `the port notice follows both addresses instead of splitting them`() {
        // 2026-09-27 装机：它夹在两行之间，读起来像只说第一行；其实两个地址用的是同一个端口。
        val localRow = card.indexOf("address.localUrl?.let")
        val notice = card.indexOf("R.string.connection_port_moved")
        assertTrue(localRow in 0 until notice)
    }

    @Test fun `groups are spaced by relation, not by one uniform gap`() {
        // 原来整张卡一个 spacedBy(md)：有关系的不靠近、没关系的不拉开，看不出分组。
        val body = card.substringAfter("fun ConnectionInfoCard(").substringBefore("\n/** [ConnectionInfoCard] 的全部输入")
        assertFalse(body.contains("verticalArrangement = Arrangement.spacedBy(Spacing.md)"))
        assertTrue(body.contains("Spacer(Modifier.height(ADDRESS_ROW_GAP))"))
        assertTrue(body.contains("Spacer(Modifier.height(GROUP_GAP))"))
    }

    @Test fun `both screens keep the last card while the page is leaving`() {
        // 2026-09-27 装机：停止服务后返回动画期间地址已清空，卡片消失、下面的内容塌成半截。
        listOf("ui/serving/ServingScreen.kt", "ui/exporting/ExportingScreen.kt").forEach {
            val screen = source("com/example/flikky/$it")
            assertTrue("$it must render the remembered card", screen.contains("rememberLastCardInputs("))
            assertFalse("$it must not drop the card when the address goes null", screen.contains("address?.let"))
        }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { File(dir, "$it/$relative") }
                .firstOrNull { it.isFile }?.let { return it.readText() }
            dir = dir.parentFile
        }
        error("missing $relative")
    }
}
