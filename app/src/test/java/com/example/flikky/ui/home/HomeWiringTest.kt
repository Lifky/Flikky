package com.example.flikky.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 主页的排序与分节此前是**做好了没接线**：设置持久化了、导出快照带了、setter 有、
 * 纯函数支持三种分组，唯独 `HomeListBuilder.build` 的两个实参写死。于是
 * `SortKey.NAME` 与 `GroupMode.NONE / STATUS` 全是不可达代码。
 *
 * 这几条守的就是「别再写死回去」。它是源码扫描而不是行为断言，因为
 * 「实参来自哪里」在运行时看不出来 —— 写死 TIME 与设置恰好是 TIME 时表现完全相同。
 *
 * 寻根写法与 `ui/serving/ServingTabsStructureTest` 逐字相同。
 */
class HomeWiringTest {

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

    private val viewModel by lazy { source("com/example/flikky/ui/home/HomeViewModel.kt") }
    private val screen by lazy { source("com/example/flikky/ui/home/HomeScreen.kt") }

    @Test
    fun `home list takes its sort from settings, not a literal`() {
        assertFalse(
            "排序又被写死了",
            viewModel.contains(Regex("""sort\s*=\s*SortKey\.""")),
        )
        assertTrue("排序没有从 settings 取", viewModel.contains("settings.homeSort"))
    }

    @Test
    fun `the home list gets the whole sort spec, not just the key`() {
        // 2026-09-08 装机反馈：`sort = settings.homeSort.key` 把方向丢了。
        // 类型对、编译过、菜单箭头也翻，唯独列表顺序不动 ——
        // **传对了类型不等于传全了维度**。
        assertFalse(
            "方向又被丢掉了（传的是 .key 而不是整个 SortSpec）",
            viewModel.contains(Regex("""sort\s*=\s*settings\.homeSort\.key""")),
        )
        assertTrue("没有把整个 SortSpec 传给列表构建", viewModel.contains("sort = settings.homeSort,"))
    }

    @Test
    fun `home list takes its section mode from settings, not a literal`() {
        assertFalse(
            "分节方式又被写死了",
            viewModel.contains(Regex("""group\s*=\s*GroupMode\.""")),
        )
        assertTrue("分节方式没有从 settings 取", viewModel.contains("settings.groupMode"))
    }

    @Test
    fun `the sort sheet is actually reachable from the screen`() {
        // setSortMode 曾经存在但全项目没有调用方。这条防止历史重演：
        // 有 setter、有 flow、有纯函数支持，但没有任何 UI 能触发。
        assertTrue("HomeScreen 没有引用排序 sheet", screen.contains("HomeSortSheet("))
    }

    @Test
    fun `the section axis is not called a group in user-facing copy`() {
        // 「分组」在本项目已经指用户自建的会话分组（GroupEntity / home_new_group）。
        // 分节方式若也叫「分组」，同屏会出现两个含义不同的同一个词 ——
        // GroupWording.kt 当初就是为消掉这个缺陷而造的（v1.17.0 装机反馈）。
        val zh = File(
            mainJavaRoot().parentFile.parentFile,
            "main/res/values/strings_home.xml",
        ).readText()
        val sectionStrings = Regex("""<string name="home_section_mode[^"]*">([^<]*)</string>""")
            .findAll(zh).map { it.groupValues[1] }.toList()
        assertTrue("分节方式的文案一条都没找到", sectionStrings.size >= 3)
        sectionStrings.forEach {
            assertFalse("分节方式的文案里不该出现「分组」：$it", it.contains("分组"))
        }
    }
    // v1.24 装机（Screenshot_5）：历史很多时最后一项被「启动服务」FAB 盖住一半，点不到它的菜单。
    // 多选时的浮动工具栏早已抬升过底部留白，FAB 显示时没有。按 FAB 实测高度留：字号放大时也盖不住。
    // 收藏页的「添加收藏」FAB 同理（同一个问题，一并修）。
    @Test fun `the last row can scroll above the start service fab`() {
        for (path in listOf("com/example/flikky/ui/home/HomeScreen.kt", "com/example/flikky/ui/favorites/FavoritesScreen.kt")) {
            val screen = source(path)
            assertTrue("$path must measure its FAB", screen.contains(".onSizeChanged { fabHeight = with(density) { it.height.toDp() } }"))
            assertTrue("$path must lift the list above it", screen.contains("else -> fabClearance(fabHeight)"))
        }
    }
}
