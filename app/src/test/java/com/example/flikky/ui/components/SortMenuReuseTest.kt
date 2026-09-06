package com.example.flikky.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「复用 = 视觉零差异」的守卫。
 *
 * 排序菜单出现在三个界面上。任何一处自己写一份 `DropdownMenu`，视觉就会漂移 ——
 * 而漂移在单测里是看不见的（三处各自都能跑通）。所以这里直接钉源码：
 * 三个界面都必须引用共享件，且不许自己再写一个排序菜单。
 *
 * 主页**不在这份名单里**：它有两个轴（排序键 + 分节方式），用的是 `HomeSortSheet`。
 * 那不是不一致 —— 控件跟着内容走，一个轴用菜单、两个轴用 sheet。
 *
 * 寻根写法与 `ui/serving/ServingTabsStructureTest` 逐字相同（测试的 user.dir 是模块目录
 * 还是仓库根取决于调用方式，写死任一个都会在另一种下 FileNotFound）。
 */
class SortMenuReuseTest {

    private fun mainJavaRoot(): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/java/com/example/flikky").isDirectory) {
                return File(dir, "src/main/java")
            }
            if (File(dir, "app/src/main/java/com/example/flikky").isDirectory) {
                return File(dir, "app/src/main/java")
            }
            dir = dir.parentFile
        }
        error("cannot locate src/main/java from user.dir=" + System.getProperty("user.dir").orEmpty())
    }

    private fun source(relative: String): String {
        val f = File(mainJavaRoot(), relative)
        assertTrue("missing source file: $relative", f.isFile)
        return f.readText()
    }

    private val surfaces = listOf(
        "com/example/flikky/ui/files/FilesScreen.kt",
        "com/example/flikky/ui/favorites/FavoritesScreen.kt",
        "com/example/flikky/ui/serving/storage/ServingStorageTab.kt",
    )

    @Test
    fun `every surface uses the shared SortMenuAction`() {
        surfaces.forEach { path ->
            assertTrue("$path 没有引用共享的 SortMenuAction", source(path).contains("SortMenuAction("))
        }
    }

    @Test
    fun `no surface declares its own sort menu`() {
        surfaces.forEach { path ->
            assertFalse(
                "$path 自己声明了排序菜单，应当改用 ui/components/SortMenuAction",
                source(path).contains("private fun SortMenuAction"),
            )
        }
    }

    @Test
    fun `the shared menu takes its time label from the caller`() {
        // 三个界面的「时间」是三个不同的字段（消息时间 / 收藏时间 / 文件修改时间）。
        // 组件里硬编码任何一处的文案，另外两处就会说错话 —— 与 GroupWording 同源。
        val component = source("com/example/flikky/ui/components/SortMenuAction.kt")
        assertTrue("SortMenuAction 应当收 timeLabel 参数", component.contains("timeLabel: Int"))
        assertFalse(
            "SortMenuAction 里不该出现任何界面专有的时间文案",
            component.contains("R.string.files_sort_time") ||
                component.contains("R.string.favorites_sort_time") ||
                component.contains("R.string.serving_storage_sort_time"),
        )
    }
}
