package com.example.flikky.ui.serving.storage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.ui.theme.FlikkyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **列表的行永远不许在屏幕上重叠。**
 *
 * ## 为什么必须是仪器测试
 *
 * v1.20.0 装机验收里「行叠着行」出现了**三次**，每次根因都不同：
 *   1. 入场包装让 item 在可见前高度为 0，破坏了懒加载的视口填充；
 *   2. 被取消的流把上一个目录的条目接到当前列表上，`relativePath` 撞 key；
 *   3. `animateItem` 的 fadeOut 把消失的 item 继续画在旧偏移上，换目录时两份
 *      列表同帧绘制。
 *
 * 三次都是从**同一个视觉症状**去诊断的，每次都找到并修掉了一个真实原因 ——
 * 但症状不足以唯一确定原因，所以修完一个，下一个照样以同样的样子出现。
 *
 * 更根本的是：整套单测里**没有任何一条能看见布局**。纯逻辑测试与源码扫描覆盖不到
 * 「东西被摆在哪儿、动画怎么叠」，于是这一类缺陷对测试套件天然不可见。
 * 这条测试补的就是那个盲区 —— 它断言的不是代码长什么样，而是**真实布局的性质**：
 * 任意两行的垂直区间不许相交。
 *
 * 需要设备：`JAVA_HOME=... ./gradlew connectedAndroidTest`。
 */
@RunWith(AndroidJUnit4::class)
class StorageRowOverlapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entries(dir: String, n: Int): List<LocalEntry> = (1..n).map {
        val rel = if (dir.isEmpty()) "f$it.txt" else "$dir/f$it.txt"
        LocalEntry(
            name = "f$it.txt",
            relativePath = rel,
            absolutePath = "/sdcard/$rel",
            isDir = false,
            size = 1024L * it,
            mtime = 0L,
            mime = null,
            restricted = false,
        )
    }

    /** 屏幕上每一行的垂直区间两两不相交。 */
    private fun assertNoOverlap(where: String) {
        val nodes = composeRule
            .onAllNodesWithTag(StorageRowTestTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("$where: expected at least two rows laid out", nodes.size >= 2)
        val spans = nodes
            .map { it.boundsInRoot.top to it.boundsInRoot.bottom }
            .sortedBy { it.first }
        for (i in 1 until spans.size) {
            val prev = spans[i - 1]
            val cur = spans[i]
            assertTrue(
                "$where: a row starts at ${cur.first} while the one above ends at " +
                    "${prev.second} — rows are drawn on top of each other",
                cur.first >= prev.second - 0.5f,
            )
        }
    }

    private fun content(state: androidx.compose.runtime.MutableState<LocalStorageState>) {
        composeRule.setContent {
            FlikkyTheme(settings = FlikkySettings()) {
                ServingStorageTab(
                    hasPermission = true,
                    onRequestPermission = {},
                    state = state.value,
                    summary = StorageSelectionSummary(0, 0L, 0),
                    onOpenDir = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onSendSelection = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Test
    fun rowsNeverOverlapWhileStreamingIn() {
        val state = mutableStateOf(
            LocalStorageState(path = "", entries = emptyList(), loading = true),
        )
        content(state)
        // 分批到达，每批之后都查一次 —— 追加期间是最容易出错的窗口。
        listOf(24, 72, 168).forEach { total ->
            val all = entries("", total)
            state.value = LocalStorageState(
                path = "",
                entries = all,
                loading = true,
                lastBatchStart = maxOf(0, total - 24),
            )
            composeRule.waitForIdle()
            assertNoOverlap("streaming $total rows")
        }
    }

    @Test
    fun rowsNeverOverlapWhenTheDirectoryChanges() {
        // 第 3 次那个缺陷的正面测试：状态从「A 的条目」直接变成「B 的条目」，
        // 中间**不经过空列表** —— 正是目录缓存引入的那个此前不存在的转换。
        val state = mutableStateOf(
            LocalStorageState(path = "DCIM", entries = entries("DCIM", 60), loading = false),
        )
        content(state)
        composeRule.waitForIdle()
        assertNoOverlap("before the change")

        composeRule.mainClock.autoAdvance = false
        state.value = LocalStorageState(
            path = "Music",
            entries = entries("Music", 60),
            loading = false,
        )
        // 刻意在动画还没跑完时查：旧行的淡出正是在这一段里画在旧偏移上。
        listOf(16L, 48L, 96L, 200L).forEach { at ->
            composeRule.mainClock.advanceTimeBy(at)
            assertNoOverlap("mid-transition, ${at}ms in")
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertNoOverlap("after the transition settled")
    }
}
