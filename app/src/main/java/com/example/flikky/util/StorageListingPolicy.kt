package com.example.flikky.util

/**
 * 目录里有什么、按什么顺序给出来。
 *
 * **两端共用这一个对象**：服务端路由（浏览器看到的顺序）与 App 端本机浏览器（手机上看到的
 * 顺序）都调它。各写一遍的代价不是多几行代码，是两端顺序不一致——而这种不一致极难被测试
 * 发现，因为两边各自的测试都是绿的。v1.18.0 的时间戳靠「双端同构纯函数」拿到一致性，照此办。
 *
 * 默认顺序为「目录优先 + 名称升序」（[NAME_ORDER]，中文按码位而非拼音）；
 * 排序键与方向由调用方通过 [SortSpec] 指定，两端共用同一套语义。
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
    fun visibleCount(names: Array<String>?, includeHidden: Boolean = false): Int? =
        names?.count { includeHidden || !isHidden(it) }

    /**
     * @param includeHidden 用户在设置里打开了「显示隐藏文件」时为 true。
     *   默认 false —— Android 存储里的隐藏项（`.thumbnails`、`.trashed-*`、`.nomedia`）
     *   数量可观，会把真正想发的文件挤下去。
     *   **[visibleCount] 必须收到同一个值**，否则副标题与列表又会对不上（那正是
     *   2026-09-03 装机验收报的「副标题 5 项、进去只有 4 行」）。
     *
     * @param sort 排序键与方向。默认「名称升序」= 本版之前的唯一顺序。
     *
     * **目录优先不参与方向翻转**：目录的 [size] 恒为 0，混排会把所有文件夹挤到列表
     * 一端，看起来像 bug（2026-09-04 用户裁决）。
     *
     * 末尾恒定按 [NAME_ORDER] **升序**兜底：大小或时间相等时顺序仍然确定，
     * 否则同一个目录两次列举可能给出不同结果。
     */
    fun <T> filterAndSort(
        items: List<T>,
        isDir: (T) -> Boolean,
        name: (T) -> String,
        includeHidden: Boolean = false,
        size: (T) -> Long = { 0L },
        mtime: (T) -> Long = { 0L },
        sort: SortSpec = SortSpec.NameAsc,
    ): List<T> {
        val visible = items.filterNot { !includeHidden && isHidden(name(it)) }
        val dirFirst = compareBy<T> { if (isDir(it)) 0 else 1 }
        val byKey: Comparator<T> = when (sort.key) {
            SortKey.NAME -> compareBy(NAME_ORDER) { name(it) }
            SortKey.TIME -> compareBy { mtime(it) }
            SortKey.SIZE -> compareBy { size(it) }
        }
        val directed = if (sort.descending) byKey.reversed() else byKey
        return visible.sortedWith(dirFirst.then(directed).thenBy(NAME_ORDER) { name(it) })
    }
}
