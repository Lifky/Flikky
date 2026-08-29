package com.example.flikky.ui

import java.io.File

/**
 * 连接式列表组的圆角靠 `SettingItem(index, total)` 判首尾，所以**每一区手写的 total
 * 必须等于该区实际行数，且最大 index 必须等于 total - 1**。
 *
 * v1.19.0 用户截图 29：「会话行为」区 total 写了 5 而该区只有 4 行，末行永远拿不到
 * `index == total - 1`，底部圆角一直是中间行的小圆角。差别只有几个 dp，肉眼极难发现，
 * 而每次增删行都可能重犯。
 *
 * 这个对象是**两个界面共用的判据**：快捷设置（marker `val total =`）与正式设置页
 * （marker `val sectionItems =`）。2026-08-29 发现原守卫只覆盖快捷设置——正式设置页的
 * 算术改坏后一条测试都不红，于是抽出共用件把两边都钉上。
 */
object SettingItemShapeGuard {

    data class SectionReport(
        val ordinal: Int,
        val totalExpr: String,
        val declaredMax: Int,
        val rowCount: Int,
        val maxIndex: Int,
        /** 无法解析的 index 表达式。非空即判红——静默跳过等于给自己发通行证。 */
        val unparsedIndices: List<String>,
        /**
         * 抓到的 index 表达式条数，必须等于 [rowCount]。多了说明正则抓到了别的东西
         * （踩过一次：SegmentedButtonDefaults.itemShape(index = index, ...) 被抓进来），
         * 少了说明某行的 index 没被看见，而上面两条断言会在「没看见的行」上照样全绿。
         */
        val indexCount: Int,
    )

    /**
     * @param totalMarker 该文件声明每区行数的变量名（`total` 或 `sectionItems`）
     */
    fun analyze(source: String, totalMarker: String): List<SectionReport> {
        val marks = Regex("""val $totalMarker = ([^\n]+)""").findAll(source).toList()
        return marks.mapIndexed { i, m ->
            val sliceEnd = marks.getOrNull(i + 1)?.range?.first ?: source.length
            val slice = source.substring(m.range.last, sliceEnd)
            val totalExpr = m.groupValues[1].trim()
            val declaredMax = literals(totalExpr).max()

            // 同一区内声明的偏移变量，如 `val followingIndexOffset = if (recall) 1 else 0`。
            // 只认 if 形式：其他形式会让下面的 index 解析失败并判红，而不是被猜错。
            val offsets = Regex("""val (\w+) = (if [^\n]+)""").findAll(slice)
                .associate { it.groupValues[1] to literals(it.groupValues[2]).max() }

            val unparsed = mutableListOf<String>()
            var maxIndex = -1
            var indexCount = 0
            for (raw in Regex("""index = ([^,]+), total =""").findAll(slice).map { it.groupValues[1].trim() }) {
                indexCount++
                val value = resolve(raw, totalMarker, declaredMax, offsets)
                if (value == null) unparsed += raw else maxIndex = maxOf(maxIndex, value)
            }
            SectionReport(
                ordinal = i + 1,
                totalExpr = totalExpr,
                declaredMax = declaredMax,
                rowCount = Regex("""SettingItem\(""").findAll(slice).count(),
                maxIndex = maxIndex,
                unparsedIndices = unparsed,
                indexCount = indexCount,
            )
        }
    }

    /**
     * 把一个 index 表达式解析成「它可能取到的最大值」。只认三种真实出现过的写法；
     * 其余返回 null 交给调用方判红——**猜一个值比报错危险得多**。
     */
    private fun resolve(
        expr: String,
        totalMarker: String,
        declaredMax: Int,
        offsets: Map<String, Int>,
    ): Int? = when {
        // `sectionItems - 1` / `total - 1`：符号写法，天然就是末行
        expr.contains(totalMarker) && expr.contains("- 1") -> declaredMax - 1
        // `if (cond) 3 else 2`：取分支里的较大值
        expr.startsWith("if ") -> literals(expr).maxOrNull()
        // `3 + followingIndexOffset`：字面量加上该偏移变量的上界
        expr.contains('+') -> {
            val base = literals(expr).sum()
            val extra = offsets.entries.filter { it.key in expr }.sumOf { it.value }
            if (offsets.keys.none { it in expr }) null else base + extra
        }
        // 纯字面量
        Regex("""^\d+$""").matches(expr) -> expr.toInt()
        else -> null
    }

    private fun literals(expr: String): List<Int> =
        Regex("""\b\d+\b""").findAll(expr).map { it.value.toInt() }.toList()

    fun readSource(relativeToJava: String): String {
        val root = File("src").takeIf { it.isDirectory } ?: File("app/src")
        return root.resolve("main/java/com/example/flikky/$relativeToJava").readText()
    }
}
