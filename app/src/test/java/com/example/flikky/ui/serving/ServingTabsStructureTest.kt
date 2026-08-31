package com.example.flikky.ui.serving

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.20.0 会话页 tab 的结构守卫。
 *
 * 这五条都是「改坏了照样编译、照样跑、只有人眼在真机上才看得出」的规则，
 * 而其中三条的症状是间歇性的（切 tab 才复现），人眼也容易漏。
 *
 * 为什么用源码扫描而不是仪器测试：这几条要的是「这段代码在哪个 composable 里」，
 * 而不是「跑起来什么样」。要用仪器测试验第 1 条，得把整个 `ServingScreen` 连
 * ViewModel + ServiceLocator + Room 一起立起来；那样的测试又慢又脆，
 * 而且验的是间接结果（window flag），改坏时的报错不会指向真正的原因。
 * 源码守卫直接说出规则本身，且能在 `test/` 秒级跑完。
 * 同类先例见 `data/SessionPathConventionTest`。
 */
class ServingTabsStructureTest {

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
        return f.readText(Charsets.UTF_8)
    }

    /** 去掉行注释与块注释：注释里会提到这些规则，扫原文会在「注释提到它」上误判。 */
    private fun stripComments(src: String): String = src
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    private val servingScreen get() = stripComments(source("com/example/flikky/ui/serving/ServingScreen.kt"))
    private val chatTab get() = stripComments(source("com/example/flikky/ui/serving/ServingChatTab.kt"))
    private val storageTab get() = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))

    @Test
    fun `keep screen on stays in ServingScreen and never moves into a tab`() {
        // 常亮覆盖整个会话页含文件 tab（v1.18.0 的 D9）。把 DisposableEffect 下移进
        // ServingChatTab，滑到文件 tab 时 chat 页被 pager 回收 → onDispose 清 flag → 灭屏。
        assertTrue(
            "FLAG_KEEP_SCREEN_ON must be handled in ServingScreen (covers every tab)",
            servingScreen.contains("FLAG_KEEP_SCREEN_ON"),
        )
        assertFalse(
            "FLAG_KEEP_SCREEN_ON must NOT live in a single tab: switching tabs would clear it",
            chatTab.contains("FLAG_KEEP_SCREEN_ON"),
        )
    }

    @Test
    fun `switching tabs clears the message action target`() {
        // 现有清理只挂在 listState.isScrollInProgress 上，pager 换页不触发它。
        // 少了这条，浮动工具栏会浮在文件列表上方，指向一条看不见的消息。
        val effect = Regex("""LaunchedEffect\(pagerState\.currentPage\)\s*\{[^}]*}""")
            .find(servingScreen)
        assertTrue(
            "no LaunchedEffect(pagerState.currentPage) — the floating toolbar would survive a tab switch",
            effect != null,
        )
        assertTrue(
            "the tab-change effect must clear actionTarget, found: ${effect!!.value}",
            effect.value.contains("actionTarget = null"),
        )
    }

    @Test
    fun `the storage back handler yields to the toolbar handler`() {
        // 三级 BackHandler：1 关工具栏 → 2 文件 tab 上一级 → 3 拦截/退出。
        // 第 2 级的 enabled 漏了 actionTarget == null 就会抢在第 1 级前面，工具栏关不掉。
        val handler = Regex("""BackHandler\(\s*enabled = [^)]*pagerState\.currentPage == 1[^)]*\)""")
            .find(servingScreen)
        assertTrue("no storage-tab BackHandler keyed on the files page", handler != null)
        assertTrue(
            "the storage BackHandler must require actionTarget == null, found: ${handler!!.value}",
            handler.value.contains("actionTarget == null"),
        )
    }

    @Test
    fun `the app side storage tab is not gated by the browser master switch`() {
        // 四态矩阵最容易写错的一格：「主开关关 + 已授权」。storageBrowsingEnabled 门控的是
        // 对端浏览器；拿同一个布尔量把两端一起门控，用户在自己手机上也看不到文件，
        // 而设置项文案说的是「允许电脑端浏览」。ServingStorageTab 连这个参数都不该收。
        assertFalse(
            "ServingStorageTab must not know about storageBrowsingEnabled — " +
                "the master switch gates the browser peer, not the phone itself",
            storageTab.contains("storageBrowsingEnabled"),
        )
        // 传入侧同样不许把两者与起来。
        val call = Regex("""ServingStorageTab\(([\s\S]*?)\n {12}\)""").find(servingScreen)
        assertTrue("no ServingStorageTab call site found in ServingScreen", call != null)
        assertFalse(
            "the ServingStorageTab call site must not mix in storageBrowsingEnabled: ${call!!.value}",
            call.value.contains("storageBrowsing"),
        )
    }

    @Test
    fun `the secondary tab indicator is explicitly set to content width`() {
        // 裁决 A：指示器贴文字宽。Compose 的 SecondaryTabRow 默认铺满整个 tab 宽，
        // 不传 indicator 就静默拿到「铺满」——与裁决不符，且没有任何其他测试会发现。
        val row = Regex("""SecondaryTabRow\(([\s\S]*?)\n {12}\)\s*\{""").find(servingScreen)
        assertTrue("no SecondaryTabRow found in ServingScreen", row != null)
        assertTrue(
            "SecondaryTabRow must pass an explicit indicator (default is full-tab width): ${row!!.value}",
            row.value.contains("indicator ="),
        )
        assertTrue(
            "the indicator must use the content-width offset (matchContentSize): ${row.value}",
            row.value.contains("matchContentSize = true"),
        )
    }

    @Test
    fun `the storage tab reuses the shared file row visuals`() {
        // 「复用」的标准是视觉零差异。自己写一套 Box + Icon 就是 v1.17.1 花一整轮消掉的重复，
        // 而它不会有任何测试转红——只有人眼并排比对才看得出行首大小/形状/对齐不一样。
        // 这里退一步钉「用的是共用件」，把「零差异」变成结构事实而不是自觉。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        for (shared in listOf("FileLeadingVisual(", "SegmentedListItem(",
                              "ListItemDefaults.segmentedShapes", "ListItemDefaults.segmentedColors",
                              "FileLeadingSpec.rowAlignment")) {
            assertTrue("storage rows must reuse $shared like FilesScreen does", tab.contains(shared))
        }
        // 裁决 B：不用 checkbox。全项目 list 行没有一个，加进来会成为唯一的异类。
        assertFalse(
            "storage rows must not introduce a Checkbox (ruling B: reuse the tri-state leading)",
            tab.contains("Checkbox("),
        )
        // 逼红实测：只查「文件里出现过 FileLeadingVisual」是不够的——把**目录行**的 leading
        // 换成别的东西，文件行那处调用还在，断言照样绿。必须逐分支核算：
        // leading 有三个 arm（沙箱 / 目录 / 文件），只有沙箱那个用锁图标，另两个必须走共用件。
        val leadingAt = tab.indexOf("val leading: @Composable () -> Unit = {")
        assertTrue("no leading lambda in the storage row", leadingAt > 0)
        val leadingEnd = tab.indexOf("val supporting:", leadingAt)
        assertTrue("cannot bound the leading lambda", leadingEnd > leadingAt)
        val leading = tab.substring(leadingAt, leadingEnd)
        assertEquals(
            "both the directory arm and the file arm must use FileLeadingVisual",
            2,
            leading.windowed(20).count { it.startsWith("FileLeadingVisual(") },
        )
        assertEquals(
            "only the restricted arm may use a bespoke leading",
            1,
            leading.windowed(15).count { it.startsWith("LockedLeading(") },
        )
    }

    @Test
    fun `row behaviour is dispatched through the shared policy, not re-derived in the UI`() {
        // 三条规则（目录不可选 / 文件单击即选 / 沙箱行完全惰性）的事实源是 storageRowAction，
        // 单测在 StorageRowActionTest。UI 里再自己判一遍 isDir/restricted 就会分叉出
        // 「策略说 NONE、UI 照样让点」这种测试测不到的状态。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        assertTrue("no storageRowAction dispatch found", tab.contains("storageRowAction(entry)"))
        assertTrue(
            "the row must branch on the policy result",
            tab.contains("StorageRowAction.OPEN") && tab.contains("StorageRowAction.TOGGLE") &&
                tab.contains("StorageRowAction.NONE"),
        )
        // 关键：点击回调不得绕过策略。目录分支只许 onOpenDir，文件分支只许 onToggleSelection。
        val openBranch = Regex("""StorageRowAction\.OPEN -> SegmentedListItem\(([\s\S]*?)
 {8}\)""")
            .find(tab)
        assertTrue("no OPEN branch", openBranch != null)
        assertFalse(
            "a directory row must not toggle selection: ${openBranch!!.value}",
            openBranch.value.contains("onToggleSelection"),
        )
        val noneBranch = Regex("""StorageRowAction\.NONE -> SegmentedListItem\(([\s\S]*?)
 {8}\)""")
            .find(tab)
        assertTrue("no NONE branch", noneBranch != null)
        assertTrue(
            "a restricted row must be disabled: ${noneBranch!!.value}",
            noneBranch.value.contains("enabled = false"),
        )
        assertFalse(
            "a restricted row must not open or select: ${noneBranch.value}",
            noneBranch.value.contains("onOpenDir") || noneBranch.value.contains("onToggleSelection"),
        )
    }

    @Test
    fun `storageCanGoUp is derived from the path, not hard-coded`() {
        // 写成常量 true 的后果：根目录按返回也被文件 tab 那一级吃掉，用户出不去。
        // 写成常量 false 的后果：文件 tab 里返回键直接退出会话页，目录栈形同虚设。
        val decl = Regex("""val storageCanGoUp = .*""").find(servingScreen)
        assertTrue("no storageCanGoUp declaration", decl != null)
        assertTrue(
            "storageCanGoUp must be derived from the current path, found: ${decl!!.value}",
            decl.value.contains("storageState.path"),
        )
    }

    @Test
    fun `the storage tab loads no remote image source`() {
        // 红线：无运行时外联。Coil 只喂本地 File / StoredVideo model；
        // 一旦有人传了 URL 字符串或加了 coil-network，缩略图就会去联网，而它不会报错。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        assertFalse("no http URL may reach the thumbnail model", tab.contains("http://"))
        assertFalse("no https URL may reach the thumbnail model", tab.contains("https://"))
        val libs = File(mainJavaRoot().parentFile.parentFile.parentFile, "gradle/libs.versions.toml")
        if (libs.isFile) {
            assertFalse(
                "coil-network must never be added (offline-only red line)",
                libs.readText(Charsets.UTF_8).contains("coil-network"),
            )
        }
    }
}
