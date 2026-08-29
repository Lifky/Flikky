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
