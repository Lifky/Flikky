package com.example.flikky.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural guards for the two leading-visual pickers. */
class LeadingSheetsTest {

    @Test
    fun `both leading pickers are reachable from settings`() {
        val screen = productCode("ui/settings/SettingsScreen.kt")

        assertTrue("SettingsScreen 没有引用形状选择器", screen.contains("LeadingShapeSheet("))
        assertTrue("SettingsScreen 没有引用配色选择器", screen.contains("LeadingColorSheet("))
    }

    @Test
    fun `leading shape rows use the outlined interests symbol`() {
        val settingsRow = settingRow(
            productCode("ui/settings/SettingsScreen.kt"),
            "R.string.leading_shape_title",
        )
        val quickSettingsRow = settingRow(
            productCode("ui/serving/QuickSettingsSheet.kt"),
            "R.string.leading_shape_title",
        )

        assertTrue("SettingsScreen 形状行没有使用 ic_interests：\n$settingsRow", settingsRow.contains("R.drawable.ic_interests"))
        assertTrue("QuickSettingsSheet 形状行没有使用 ic_interests：\n$quickSettingsRow", quickSettingsRow.contains("R.drawable.ic_interests"))
    }

    @Test
    fun `leading colour rows use the filled interests symbol`() {
        val settingsRow = settingRow(
            productCode("ui/settings/SettingsScreen.kt"),
            "R.string.leading_color_title",
        )
        val quickSettingsRow = settingRow(
            productCode("ui/serving/QuickSettingsSheet.kt"),
            "R.string.leading_color_title",
        )

        assertTrue("SettingsScreen 配色行没有使用 ic_interests_fill：\n$settingsRow", settingsRow.contains("R.drawable.ic_interests_fill"))
        assertTrue("QuickSettingsSheet 配色行没有使用 ic_interests_fill：\n$quickSettingsRow", quickSettingsRow.contains("R.drawable.ic_interests_fill"))
    }

    @Test
    fun `every shape preview uses the real file leading visual`() {
        val sheet = productCode("ui/settings/LeadingShapeSheet.kt")

        assertTrue("形状预览没有复用 FileLeadingVisual", sheet.contains("FileLeadingVisual("))
        assertFalse(
            "形状预览又用 Box + background(shape) 自绘了一份几何",
            Regex(
                """Box\s*\([\s\S]{0,500}?\.background\s*\([\s\S]{0,300}?shape\s*=""",
            ).containsMatchIn(sheet),
        )
    }

    @Test
    fun `shape grid count follows the enum`() {
        val sheet = productCode("ui/settings/LeadingShapeSheet.kt")

        assertTrue(
            "形状网格格数没有跟随 LeadingShape.entries.size",
            Regex("""items\s*\(\s*count\s*=\s*LeadingShape\.entries\.size""")
                .containsMatchIn(sheet),
        )
    }

    @Test
    fun `every colour mode previews all leading types`() {
        val sheet = productCode("ui/settings/LeadingColorSheet.kt")

        assertTrue(
            "配色选择器没有遍历 LeadingVisualCatalog.types 生成六色预览带",
            sheet.contains("LeadingVisualCatalog.types"),
        )
    }

    private fun productCode(relative: String): String {
        val file = File("src/main/java/com/example/flikky/$relative")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/$relative")
        assertTrue("找不到源码文件：$relative", file.isFile)
        return stripCommentsAndImports(file.readText())
    }

    private fun settingRow(source: String, title: String): String {
        val at = source.indexOf(title)
        assertTrue("找不到设置行：$title", at >= 0)
        return source.substring(at, minOf(source.length, at + 500))
    }

    /** Comments and imports are not evidence that the product is wired. */
    private fun stripCommentsAndImports(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\r\n]*"""), "")
        .replace(Regex("""(?m)^\s*import\s+[^\r\n]+\r?\n"""), "")
}
