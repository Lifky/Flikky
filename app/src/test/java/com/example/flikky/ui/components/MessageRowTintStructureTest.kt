package com.example.flikky.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息行的两种底色都要铺满整行（v1.24 装机 Screenshot_6 及复验）：
 * - 历史页从搜索定位过来的高亮（条目容器上的 `highlightColor`）；
 * - 悬浮工具栏模式下点中消息的选中底色（`MessageBubble` 自己那一行），历史页与会话页都有。
 * 左右留白原先加在 LazyColumn 上，两种底色都画不进留白、只铺到两侧头像外缘。
 * 改为列表不内缩：气泡行先铺底色再留白（`horizontalInset`），常驻操作栏自己留白。
 */
class MessageRowTintStructureTest {
    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { File(dir, "$it/com/example/flikky/$relative") }
                .firstOrNull { it.isFile }?.let { return it.readText().replace("\r\n", "\n") }
            dir = dir.parentFile
        }
        error("missing $relative")
    }

    private val screens = listOf("ui/history/HistoryScreen.kt", "ui/serving/ServingChatTab.kt")

    @Test fun `the message lists do not inset their rows`() {
        for (path in screens) {
            val screen = source(path)
            assertFalse(
                "$path: an inset list keeps row tints away from the edges",
                screen.contains("Modifier.fillMaxSize().maxContentWidth().padding(horizontal = Spacing.md)") ||
                    screen.contains("modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),"),
            )
            assertTrue("$path: the bubble insets itself inside its tint", screen.contains("horizontalInset = Spacing.md,"))
            assertTrue(
                "$path: the inline action bar keeps the old inset",
                screen.contains("Modifier.fillMaxWidth().padding(start = Spacing.md, top = 2.dp, end = Spacing.md)"),
            )
        }
    }

    @Test fun `the located history message is tinted edge to edge`() {
        val history = source("ui/history/HistoryScreen.kt")
        assertTrue(history.contains(".fillMaxWidth()\n                        .background(highlightColor),"))
    }

    @Test fun `the selected tint is painted before the bubble's inset`() {
        val bubble = source("ui/components/MessageBubble.kt")
        assertTrue(bubble.contains("horizontalInset: Dp = 0.dp,"))
        val tint = bubble.indexOf("Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))")
        val inset = bubble.indexOf(".padding(horizontal = horizontalInset)")
        assertTrue("selected tint present", tint >= 0)
        assertTrue("inset applied after the tint", inset > tint)
    }
}
