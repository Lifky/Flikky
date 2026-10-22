package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.24 装机（Screenshot_7）：附件面板「文件 / 图片」格子按下时水波纹是直角矩形，溢出圆角。
 * `.clickable` 加在 Card 外层修饰符上，在形状裁剪之外。用 M3 可点击 Card 的 onClick 重载：
 * 水波纹按卡片形状裁剪，语义上也是按钮（与 v1.23 形状选择格子的修法同类）。
 */
class OptionCardStructureTest {
    private val card: String by lazy {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { File(dir, "$it/com/example/flikky/ui/components/OptionCard.kt") }
                .firstOrNull { it.isFile }?.let { return@lazy it.readText() }
            dir = dir.parentFile
        }
        error("missing OptionCard.kt")
    }

    @Test fun `the ripple is clipped to the card shape`() {
        assertTrue(card.contains("onClick = onClick,"))
        assertFalse("clickable outside the card ignores its rounded shape", card.contains(".clickable("))
    }
}
