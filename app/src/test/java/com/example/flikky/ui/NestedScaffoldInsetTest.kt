package com.example.flikky.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 嵌套 `Scaffold` 的 window inset **不许算两次**（2026-09-09 装机反馈）。
 *
 * ## 现象
 *
 * 会话进行中的页面，状态栏与「对端设备」那一行之间空了一大块（Screenshot_17 框 1），
 * 没有任何内容。
 *
 * ## 机制
 *
 * `MainActivity` 的外层 `Scaffold` 给出的 `innerPadding` **含 systemBars**。
 * 逐目的地施加时写的是：
 *
 * ```kotlin
 * Box(Modifier.padding(innerPadding)) { ServingScreen(...) }
 * ```
 *
 * `padding` 只是**留出空白**，并没有把 inset 标记为已消费 —— 于是 `ServingScreen`
 * 自己的 `Scaffold` 再次读到完整的 systemBars，又留一次。状态栏高度因此被算两次。
 *
 * `consumeWindowInsets(innerPadding)` 才是「这部分我已经处理了」的声明；
 * 加上它之后内层 `Scaffold` 看到的剩余 inset 为零。
 *
 * ## 为什么只管其中一部分目的地
 *
 * `transfer` / `settings` / `favorites` 传的是 `PaddingValues(bottom = ...)`，
 * 刻意**不消费顶部 inset** —— 那是留给它们自己的 `SearchBar` / `LargeTopAppBar`
 * 铺到状态栏下方用的（见 MainActivity 里那段注释）。它们不在本守卫范围内。
 *
 * 本守卫盯的是「既拿了完整 innerPadding、内部又有自己的 Scaffold」这个组合。
 */
class NestedScaffoldInsetTest {

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/java/com/example/flikky").isDirectory) return dir
            if (File(dir, "app/src/main/java/com/example/flikky").isDirectory) return File(dir, "app")
            dir = dir.parentFile
        }
        error("cannot locate the app module from user.dir=" + System.getProperty("user.dir").orEmpty())
    }

    private fun source(relative: String): String {
        val f = File(repoRoot(), "src/main/java/com/example/flikky/$relative")
        assertTrue("missing source file: $relative", f.isFile)
        return f.readText()
    }

    private val mainActivity by lazy { source("MainActivity.kt") }

    /** 内部自带 `Scaffold` 的目的地 —— 它们会再读一次 inset。 */
    private val nestedScaffoldScreens = mapOf(
        "serving" to "ui/serving/ServingScreen.kt",
        "exporting" to "ui/exporting/ExportingScreen.kt",
        "files" to "ui/files/FilesScreen.kt",
        // history 是这次顺带发现的：它也拿完整 innerPadding、也自带 Scaffold，
        // 所以同一个缺陷也在会话记录页，只是装机时还没走到那里。
        "history" to "ui/history/HistoryScreen.kt",
    )

    @Test
    fun `the screens this guard covers really do host their own Scaffold`() {
        // 前提记录。哪天某个屏不再自带 Scaffold，这条守卫对它就没意义了。
        nestedScaffoldScreens.forEach { (route, path) ->
            assertTrue(
                "$route 对应的 $path 里已经没有自己的 Scaffold —— " +
                    "请重新评估 NestedScaffoldInsetTest 对它的约束",
                source(path).contains("Scaffold("),
            )
        }
    }

    @Test
    fun `every destination that takes the full innerPadding also consumes it`() {
        // 判据：`Modifier.padding(innerPadding)` 必须紧跟
        // `.consumeWindowInsets(innerPadding)`。只 padding 不 consume 就是算两次。
        val offenders = Regex("""Modifier\.padding\(innerPadding\)(?!\s*\.consumeWindowInsets)""")
            .findAll(mainActivity)
            .map { m ->
                // 报出它属于哪个 composable("...")，让失败信息能直接定位
                val before = mainActivity.substring(0, m.range.first)
                val route = Regex("""composable\("([^"]+)"\)""")
                    .findAll(before).lastOrNull()?.groupValues?.get(1) ?: "(未知目的地)"
                route
            }
            .toList()
        assertEquals(
            "这些目的地拿了完整 innerPadding 却没有 consumeWindowInsets —— " +
                "内层 Scaffold 会把 systemBars 再留一次，顶部多出一条状态栏高度的空白：",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `every nested-Scaffold destination is wired this way`() {
        // 防「有人新增一个自带 Scaffold 的目的地却忘了 consume」：
        // 上面列出的每一个都必须出现在 consume 的那一侧。
        val consumed = Regex("""Modifier\.padding\(innerPadding\)\s*\.consumeWindowInsets\(innerPadding\)""")
            .findAll(mainActivity).count()
        assertEquals(
            "自带 Scaffold 的目的地共 ${nestedScaffoldScreens.size} 个，" +
                "但 consumeWindowInsets 只写了 $consumed 处",
            nestedScaffoldScreens.size,
            consumed,
        )
    }

    @Test
    fun `the serving screen still applies its own insets once`() {
        // 外层 consume 之后，内层那套 padding/consume/imePadding 组合必须保持原样：
        // 它才是键盘弹起时 IME inset 只生效一次的保证（test2 §5 复盘）。
        val serving = source("ui/serving/ServingScreen.kt")
        listOf(".padding(padding)", ".consumeWindowInsets(padding)", ".imePadding()").forEach {
            assertTrue("ServingScreen 丢了 $it —— 键盘弹起时 inset 会再次错乱", serving.contains(it))
        }
    }
}
