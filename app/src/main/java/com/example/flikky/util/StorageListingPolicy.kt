package com.example.flikky.util

/**
 * 目录里有什么、按什么顺序给出来。
 *
 * **两端共用这一个对象**：服务端路由（浏览器看到的顺序）与 App 端本机浏览器（手机上看到的
 * 顺序）都调它。各写一遍的代价不是多几行代码，是两端顺序不一致——而这种不一致极难被测试
 * 发现，因为两边各自的测试都是绿的。v1.18.0 的时间戳靠「双端同构纯函数」拿到一致性，照此办。
 *
 * 排序为「目录优先 + 名称不区分大小写」，中文按码位而非拼音：简单可预测，拼音排序在 backlog。
 */
object StorageListingPolicy {

    /** 即便持有 MANAGE_EXTERNAL_STORAGE 也读不到的两个沙箱目录（Android 11+ 单独锁死）。 */
    private val RESTRICTED_ROOTS = listOf("Android/data", "Android/obb")

    fun isHidden(name: String): Boolean = name.startsWith(".")

    /** 相对路径是否落在系统锁死的沙箱里——**含子层级**，理由见 StorageListingPolicyTest。 */
    fun isRestricted(relativePath: String): Boolean {
        val path = relativePath.trim().trim('/')
        return RESTRICTED_ROOTS.any { path == it || path.startsWith("$it/") }
    }

    /**
     * 一个目录里**会被列出来**的条目数。
     *
     * `names` 直接收 `File.list()` 的返回值，`null`（读不到）原样传回 `null`——
     * 当成 0 会让副标题理直气壮地说「0 项」，而真相是「不知道」。
     *
     * 为什么计数也归这个对象：装机验收里父目录报「5 项」、进去只有 4 行。第 5 项是个
     * `.` 开头的隐藏文件，[filterAndSort] 刻意过滤了它，而四处 `childCount` 都是裸的
     * `File.list()?.size`。顺序和过滤共用了策略，计数没有，于是二者悄悄分了叉——
     * 而这种分叉两端各自的测试都是绿的。计数必须和列举同源。
     */
    fun visibleCount(names: Array<String>?): Int? =
        names?.count { !isHidden(it) }

    fun <T> filterAndSort(
        items: List<T>,
        isDir: (T) -> Boolean,
        name: (T) -> String,
    ): List<T> = items
        .filterNot { isHidden(name(it)) }
        .sortedWith(
            compareBy<T> { if (isDir(it)) 0 else 1 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { name(it) },
        )
}
