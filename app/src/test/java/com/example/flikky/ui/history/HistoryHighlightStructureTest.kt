package com.example.flikky.ui.history

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.24 装机（Screenshot_6）：从搜索定位到历史消息时的底色只铺到两侧头像外缘，不是整行。
 * 左右留白原先加在 LazyColumn 本身上，条目背景画不到留白里；改为条目先铺底色、再在里面留白。
 */
class HistoryHighlightStructureTest {
    private val screen: String by lazy {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { File(dir, "$it/com/example/flikky/ui/history/HistoryScreen.kt") }
                .firstOrNull { it.isFile }?.let { return@lazy it.readText().replace("\r\n", "\n") }
            dir = dir.parentFile
        }
        error("missing HistoryScreen.kt")
    }

    @Test fun `the located message is tinted across the whole row`() {
        assertFalse(
            "the list itself must not inset its rows, or the tint stops short of the edges",
            screen.contains("Modifier.fillMaxSize().maxContentWidth().padding(horizontal = Spacing.md)"),
        )
        assertTrue(
            "each row paints the tint first, then insets its content",
            screen.contains(".background(highlightColor)\n                        .padding(horizontal = Spacing.md)"),
        )
    }
}
