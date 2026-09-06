package com.example.flikky.util

/**
 * 排序键。
 *
 * 三个键是业界核心集（Google Files 就是这三个）。刻意**不含「类型」**：
 * 文件总览页的分类 chips 已经承担筛选，文件浏览的「目录优先」已经承担主要分组。
 *
 * 成员名与它取代的 `SortMode`（主页）/ `FileSort`（文件总览页）相同，
 * 所以 DataStore 里已存的字符串与导出快照都能原样解析，**不需要数据迁移**。
 */
enum class SortKey { NAME, TIME, SIZE }

/**
 * 一次排序的完整描述：按哪个键、朝哪个方向。
 *
 * 两端共用同一套语义，浏览器端镜像见 `assets/web/sort.js`；
 * 两份实现由 `app/src/test/resources/sort-order.json` 这份共享 fixture 钉死。
 */
data class SortSpec(val key: SortKey, val descending: Boolean) {

    /**
     * 持久化形态，例如 `SIZE:desc`。
     *
     * DataStore、localStorage 与 `/api/storage/list?sort=` 三处共用这一种写法 ——
     * 客户端把存的字符串原样传给服务端，两端一个解析器、零转换。
     */
    fun format(): String = "${key.name}:${if (descending) "desc" else "asc"}"

    companion object {
        /** 文件浏览的默认顺序：目录优先 + 名称升序。 */
        val NameAsc = SortSpec(SortKey.NAME, descending = false)

        /**
         * 切到一个**新**键时该用的方向。
         *
         * 名称升序、时间降序（最新在前）、大小降序（最大在前）——
         * Windows 与 Finder 都是这个行为。理由：「按时间排」用户想要的几乎总是
         * 最新的在上面，要求他再点一次翻转是多余的一步。
         */
        fun natural(key: SortKey): SortSpec =
            SortSpec(key, descending = key != SortKey.NAME)

        /**
         * 解析 [format] 的输出。
         *
         * **任何不认识的输入返回 null**，由调用方回落到自己那一处的默认值。
         * 不在这里替调用方猜：四个界面的默认值并不相同。
         */
        fun parse(raw: String?): SortSpec? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val parts = text.split(':')
            if (parts.size != 2) return null
            val key = runCatching { SortKey.valueOf(parts[0]) }.getOrNull() ?: return null
            val descending = when (parts[1]) {
                "desc" -> true
                "asc" -> false
                else -> return null
            }
            return SortSpec(key, descending)
        }
    }
}

/** 用户点一次某个排序键的结果：点当前键翻转方向，点新键用它的自然方向。 */
fun SortSpec.tap(key: SortKey): SortSpec =
    if (key == this.key) copy(descending = !descending) else SortSpec.natural(key)

/**
 * 名称顺序的**唯一定义**，也是两端同构的基石。
 *
 * ## 为什么不用 `String.CASE_INSENSITIVE_ORDER`
 *
 * 那是 Java 特有的逐字符大小写折叠（先比原字符，不等则比大写，仍不等则比小写），
 * JS 无法精确复刻。改成「`lowercase()` 之后比较」以后，两端跑的是**同一个 Unicode
 * 默认大小写映射 + 同一种 UTF-16 码位序**（Kotlin 的 `lowercase()` 与 JS 的
 * `toLowerCase()` 都是 locale-independent；Kotlin 的 `String.compareTo` 与 JS 的
 * `<` 都是码位序）。两端相等因此是**结构事实**，不是「测试碰巧都过」。
 *
 * ## `.thenBy { it }` 不是装饰
 *
 * 没有它，`README` 与 `readme` 的相对顺序不确定，同一个目录两次列举可能给出
 * 不同结果。
 *
 * 中文按码位而非拼音（拼音排序见 backlog B27，它会同时改变英文与数字的相对次序，
 * 是独立的一件事）。
 */
val NAME_ORDER: Comparator<String> =
    compareBy<String> { it.lowercase() }.thenBy { it }
