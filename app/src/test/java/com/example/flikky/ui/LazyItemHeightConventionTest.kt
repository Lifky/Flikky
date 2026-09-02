package com.example.flikky.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守卫：**数据驱动的 lazy item（`items(...)` / `itemsIndexed(...)`）不许被
 * `AnimatedVisibility` 整个包起来。**
 *
 * ## 为什么这是铁律
 *
 * `AnimatedVisibility(visible = false)` 根本不组合它的内容，于是这个 item 测出来
 * **高度为 0**。而 lazy 布局是靠累加 item 高度决定「视口填满了没有、还要不要往下
 * 组合」的——零高的 item 永远推不动这个累加值，于是它继续往下组合；等那些 item
 * 各自的入场落地、拿到真实高度，布局又变，循环重来。
 *
 * v1.20.0 装机验收的三个症状全部出自这一条：
 *   1. 条目不出现，必须主动下拉到底部才再冒出 1~2 行；
 *   2. 滚出去再滚回来动效重播——`remember` 活在 item 的组合里，而 lazy 布局在 item
 *      离开视口时销毁它的组合，回来是全新组合，初值又是 false；
 *   3. 大目录里切 tab 卡顿——零高导致超量向前组合，每行还带一个 LaunchedEffect。
 *
 * 它同时直接违反「快速滚动 / 回滚不丢行、不错位」，那正是这条规则要保住的东西。
 *
 * ## 为什么只管数据驱动的 items，不管静态 `item { }`
 *
 * `item { }` 里用 `AnimatedVisibility` 是合法的：设置页那种「开关打开才出现的行」，
 * 零高**就是**它要表达的状态，而且那种列表只有几十项，撑不出 lazy 填充问题。
 * 差别在意图——入场特效 vs 条件显隐——而意图静态查不出来。所以这条规则只落在
 * 「行数由数据决定、可能上万」的那一类上，那里零高一定是错的。
 *
 * ## 正确做法
 *
 * item 从一开始就占**完整高度**，只动 alpha / 位移；增删与重排交给共用件
 * `flikkyItemAnimation()`（`animateItem`，由 lazy 布局按 key 自己跟踪，不受回收影响）。
 * 逐行阶梯入场在会回收的列表里得不偿失，App 端不做——节奏交给流式批次的间隔。
 * 浏览器端可以做，因为 DOM 节点不回收（见 `panels.css` 的 `.fk-files-list > .fk-item`）。
 */
class LazyItemHeightConventionTest {

    private val uiRoot = File("src/main/java/com/example/flikky/ui").let {
        if (it.exists()) it else File("app/src/main/java/com/example/flikky/ui")
    }

    private fun stripComments(src: String): String = src
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    private fun sources(): List<File> =
        uiRoot.walkTopDown().filter { it.extension == "kt" }.toList()

    /**
     * 按缩进定界取一段 lambda / 函数正文。
     *
     * 先**跳过可能跨多行的签名**，找到真正打开正文的那一行（以 `{` 或 `->` 结尾），
     * 再从那里按缩进走。少了这一步，`fun Foo(` 换行写参数、`) {` 落回同一缩进，
     * 正文扫描会在签名的收尾括号处就停住——只取到参数列表。
     * 逼红实测：探针断言因此找不到任何用了 AnimatedVisibility 的 composable，
     * 而真正的越界断言空转全绿。
     */
    private fun bodyAfter(lines: List<String>, startLine: Int, indent: Int): String {
        var open = startLine
        while (open < lines.size) {
            val t = lines[open].trimEnd()
            if (t.endsWith("{") || t.endsWith("->")) break
            open++
        }
        if (open >= lines.size) return ""
        val out = StringBuilder()
        var j = open + 1
        while (j < lines.size) {
            val cur = lines[j]
            if (cur.isNotBlank() && cur.takeWhile { it == ' ' }.length <= indent) break
            out.appendLine(cur)
            j++
        }
        return out.toString()
    }

    /**
     * 本地 composable 名的集合：只留「**吞掉 item 内容**的 AnimatedVisibility 包装」。
     *
     * 判据两条同时成立：
     *   1. 它有 `content:` 槽位 —— 调用方把 item 的整块内容交给了它；
     *   2. 它自己的正文里有 `AnimatedVisibility(`。
     *
     * 只查第 2 条会误报：`MessageActionBar` 用 AnimatedVisibility 做消息行内的操作条，
     * 但那个 `visible` 是**调用方传进来的参数**，而且它只占行内一小块 —— 行还有别的
     * 内容撑高度，零高完全正常。逼红实测时它和 StreamedListItem 一起被报成越界，
     * 而它是无辜的。真正危险的形状是「拿走整块内容，再用自己内部管的状态把它藏起来」。
     */
    private fun contentSwallowingWrappers(): Set<String> {
        val out = HashSet<String>()
        sources().forEach { f ->
            val lines = stripComments(f.readText()).lines()
            lines.forEachIndexed { i, line ->
                val m = Regex("""^(\s*)(?:private |internal )?fun\s+(?:[\w.]+\.)?(\w+)\s*\(""")
                    .find(line) ?: return@forEachIndexed
                val indent = m.groupValues[1].length
                val name = m.groupValues[2]
                // 签名可能跨多行：fun 那行到打开正文那行之间都算签名。
                val sig = StringBuilder()
                var j = i
                while (j < lines.size) {
                    sig.appendLine(lines[j])
                    if (lines[j].trimEnd().endsWith("{")) break
                    j++
                }
                val body = bodyAfter(lines, i, indent)
                if (sig.contains("content:") && body.contains("AnimatedVisibility(")) {
                    out += name
                }
            }
        }
        return out
    }

    /** 每个数据驱动 item lambda 的正文。 */
    private fun dataDrivenItemBodies(src: String): List<Pair<Int, String>> {
        val lines = src.lines()
        val out = mutableListOf<Pair<Int, String>>()
        lines.forEachIndexed { i, line ->
            if (!Regex("""\bitems(Indexed)?\s*\(""").containsMatchIn(line)) return@forEachIndexed
            out += (i + 1) to bodyAfter(lines, i, line.takeWhile { it == ' ' }.length)
        }
        return out
    }

    @Test
    fun `no data-driven lazy item is wrapped in an AnimatedVisibility`() {
        assertTrue("ui source root not found: ${uiRoot.absolutePath}", uiRoot.isDirectory)
        val wrappers = contentSwallowingWrappers()
        val offenders = mutableListOf<String>()
        var scanned = 0
        sources().forEach { f ->
            val src = stripComments(f.readText())
            if (!src.contains("Lazy")) return@forEach
            dataDrivenItemBodies(src).forEach { (line, body) ->
                scanned++
                if (body.contains("AnimatedVisibility(")) {
                    offenders += "${f.name}:$line (directly)"
                    return@forEach
                }
                // 解析一层包装：item 里调的本地 composable 若把整块内容藏在
                // AnimatedVisibility 后面，零高问题一模一样，只是隔了一层看不见。
                Regex("""\b([A-Z]\w+)\s*\(""").findAll(body)
                    .map { it.groupValues[1] }
                    .distinct()
                    .filter { it in wrappers }
                    .forEach { name -> offenders += "${f.name}:$line via $name" }
            }
        }
        // 防空转：扫描本身必须真的找到了 item lambda。
        assertTrue("no data-driven item bodies found — the scan is broken", scanned >= 5)
        assertTrue(
            "a data-driven lazy item must never be wrapped in AnimatedVisibility: it measures " +
                "zero height until visible, which breaks the lazy viewport fill and makes fast " +
                "scrolling drop rows. Offenders: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the wrapper discriminator does not flag a legitimate in-row AnimatedVisibility`() {
        // 判据的两侧都要钉住，否则它要么空转、要么误报。
        //
        // MessageActionBar 用 AnimatedVisibility 做消息行内的操作条，`visible` 是调用方
        // 传进来的参数，行还有别的内容撑高度 —— 它必须**不**被判为越界。
        // 第一版只查「正文里有 AnimatedVisibility」，把它和 StreamedListItem 一起报了。
        val wrappers = contentSwallowingWrappers()
        assertTrue(
            "MessageActionBar must not count as a content-swallowing wrapper: $wrappers",
            "MessageActionBar" !in wrappers,
        )
        // 反面：正文扫描必须真的读到了函数体 —— 跨多行签名曾让它只读到参数列表，
        // 于是整条判据空转。
        val bar = File(uiRoot, "components/MessageActionBar.kt").readText()
        assertTrue(
            "the body scan cannot see MessageActionBar AnimatedVisibility at all, " +
                "so the discriminator is untested",
            stripComments(bar).contains("AnimatedVisibility("),
        )
    }

    @Test
    fun `lazy items still get the shared insert and reorder animation`() {
        // 反向守卫：上面只说「别做什么」。这条保证「该做的还在做」——
        // 否则把动效全删了也能过。
        val tab = File(uiRoot, "serving/storage/ServingStorageTab.kt").readText()
        assertTrue(
            "storage rows must reuse flikkyItemAnimation()",
            stripComments(tab).contains("flikkyItemAnimation()"),
        )
    }
}
