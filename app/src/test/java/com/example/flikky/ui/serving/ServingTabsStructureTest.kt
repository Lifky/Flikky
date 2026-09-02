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

    /**
     * 去掉行注释、块注释与 import 行。
     *
     * 注释要去掉的理由：注释里会提到这些规则，扫原文会在「注释提到它」上误判。
     *
     * **import 也必须去掉**：逼红实测过一次——把 `contentPadding` 里的
     * `FlikkyFloatingToolbarLift` 换成别的值，断言零条红，因为那行 import 还在，
     * 文件级 `contains` 照样命中。「文件里提到过」不等于「代码里用了」。
     */
    private fun stripComments(src: String): String = src
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")
        .replace(Regex("""(?m)^import .*$"""), "")

    /**
     * 取一个函数从签名到其结束大括号的正文。
     *
     * 用它而不是 `.take(N)`：固定长度的窗口会越过函数边界，把邻居的代码算进来。
     * 实测踩过一次——2400 字符的窗口从 `openStorageDir` 一直延伸到
     * `clearStorageSelection`，于是「不许 copy() 改状态」那条断言被邻居的合法
     * `copy(selected = emptySet())` 误报。
     */
    private fun functionBody(src: String, signature: String, indent: Int = 4): String {
        val at = src.indexOf(signature)
        if (at < 0) return ""
        val closer = System.lineSeparator() + " ".repeat(indent) + "}"
        val lf = 10.toChar().toString() + " ".repeat(indent) + "}"
        val end = listOf(src.indexOf(closer, at), src.indexOf(lf, at)).filter { it > at }.minOrNull()
        return if (end == null) src.substring(at) else src.substring(at, end)
    }

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

    @Test
    fun `the selection summary is a derived flow, not recomputed per composition`() {
        // selectionSummary 要 stat 每个选中文件。ServingScreen 每秒都因 uptimeSeconds 重组一次，
        // 所以做成 composable 里直接调的函数 = 主线程每秒 stat 一遍所有选中文件。
        // 实现时就是这么写的，靠这条钉住不再回退。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        assertTrue(
            "storageSelectionSummary must be a StateFlow",
            vm.contains("val storageSelectionSummary: StateFlow<StorageSelectionSummary>"),
        )
        assertTrue(
            "it must dedupe on the selection set, or every state change re-stats every file",
            vm.contains("distinctUntilChanged()"),
        )
        assertFalse(
            "ServingScreen must read the flow, not call a summary function per composition",
            servingScreen.contains("storageSelectionSummary()"),
        )
    }

    @Test
    fun `the selection affordance floats over the list and never reserves layout height`() {
        // 原则不变（放进 Scaffold 的 bottomBar 槽位会预留等高空白把列表顶走），
        // 但载体从 floating toolbar 换成了官方 FAB 菜单：2026-08-31 用户裁决，
        // 因为 FlikkyFloatingToolbar 的 content 契约是「一串 IconButton」，
        // 塞进选中计数那类自由文本会把容器撑成巨型椭圆（装机验收 Screenshot_4）。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        val at = tab.indexOf("StorageSelectionFab(")
        assertTrue("no selection FAB in the storage tab", at > 0)
        val call = tab.substring(at, minOf(at + 400, tab.length))
        assertTrue(
            "the FAB must align inside the content Box, not sit in the layout flow: $call",
            call.contains("Alignment.BottomEnd") || call.contains("Alignment.BottomCenter"),
        )
        assertFalse("the storage tab must not own a Scaffold", tab.contains("Scaffold("))
        assertFalse("the affordance must not go into a bottomBar slot", tab.contains("bottomBar"))
        // 反向守卫：选择 UI 不许再回到 floating toolbar —— 那是 Screenshot_4 的来路。
        //
        // 匹配的是**调用**（`FlikkyFloatingToolbar {` / `(`），不是裸名字：
        // `FlikkyFloatingToolbarLift` 是共用的「底部锚定内容该抬多少」尺寸常量，
        // 名字里含同一个前缀但与那个组件无关，而且这里正当地在用它。
        // 第一版查裸名字，被这个常量挡成了误报。
        val usesToolbar = { src: String ->
            src.contains("FlikkyFloatingToolbar {") || src.contains("FlikkyFloatingToolbar(")
        }
        assertFalse(
            "the selection UI must not use FlikkyFloatingToolbar: its content slot is " +
                "documented as icon buttons only, and free text blows the capsule up",
            usesToolbar(tab),
        )
        val fab = stripComments(
            source("com/example/flikky/ui/serving/storage/StorageSelectionFab.kt"),
        )
        assertFalse(
            "StorageSelectionFab must not reintroduce the floating toolbar either",
            usesToolbar(fab),
        )
        // 计数必须长在菜单项文案里，而不是作为自由文本塞进容器。
        assertTrue(
            "the count belongs in a menu item label",
            fab.contains("serving_storage_send_n"),
        )
        // 列表底部仍要留出高度，否则最后一行被 FAB 压住、选不到。
        assertTrue(
            "the list must reserve room for the floating affordance",
            tab.contains("FlikkyFloatingToolbarLift"),
        )
    }

    @Test
    fun `the selection FAB only exists while something is selected`() {
        // 没选任何东西时它没有可做的事。常驻一个「清除选择（0 项）」是噪音。
        val fab = stripComments(
            source("com/example/flikky/ui/serving/storage/StorageSelectionFab.kt"),
        )
        assertTrue(
            "visibility must be derived from the selection count",
            fab.contains("summary.count > 0"),
        )
        // 选择被清空时菜单必须跟着收起，否则下次有选中时它是展开状态。
        assertTrue(
            "clearing the selection must collapse the menu",
            fab.contains("expanded = false"),
        )
    }

    @Test
    fun `sending goes through the single shared offerStoredFile entry point`() {
        // 收藏发送与文件总览快发都走 offerStoredFile。另开一条路径迟早在状态机或落盘路径上分叉。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val at = vm.indexOf("fun sendStorageSelection()")
        assertTrue("no sendStorageSelection", at > 0)
        val body = vm.substring(at, minOf(at + 1400, vm.length))
        assertTrue("storage sending must reuse offerStoredFile", body.contains("offerStoredFile("))
        // 发完必须清空选择，否则再点发送会重复发一遍同一批。
        assertTrue("sending must clear the selection", body.contains("clearStorageSelection()"))
        // 跳过数必须如实报出，不静默丢弃。
        assertTrue(
            "the skipped count must reach the user",
            body.contains("serving_storage_sent_skipped"),
        )
    }

    @Test
    fun `the tab row and pager only exist once a browser is connected`() {
        // 装机验收 Screenshot_2：等待连接时页面上就摆着「会话 / 文件」两个 tab，
        // 还能左右滑。未连接时文件 tab 里能做的事全都要连接才有意义（发送要 controller），
        // 摆在那里只是让用户滑过去看一眼空列表再滑回来。
        // 判据是**结果**（tab 栏被连接状态门控），不是某一种写法：
        // 第一版写死了 `if (ui.clientConnected)`，而实现用的是 AnimatedVisibility
        // （顺带解决「切换太硬」那条），断言就误报了。这里只要求门控落在 tab 栏之前。
        val rowAt = servingScreen.indexOf("SecondaryTabRow(")
        assertTrue("no SecondaryTabRow in ServingScreen", rowAt > 0)
        val gate = servingScreen.substring(maxOf(0, rowAt - 700), rowAt)
        assertTrue(
            "the tab row must be gated on ui.clientConnected; preceding code:" +
                System.lineSeparator() + gate.takeLast(400),
            gate.contains("visible = ui.clientConnected") ||
                gate.contains("if (ui.clientConnected)"),
        )
        // pager 换页必须跟着禁掉，否则 tab 栏藏了、手势还在，用户能滑到一个看不见入口的页
        assertTrue(
            "the pager must refuse user scrolling while disconnected",
            servingScreen.contains("userScrollEnabled = ui.clientConnected"),
        )
        // 断连时若停在文件 tab，必须回到会话 tab：否则用户看到的是一个没有 tab 栏、
        // 也没有输入框的空白页，没有任何出路。
        val effect = servingScreen.substringAfter("LaunchedEffect(ui.clientConnected)", "")
        assertTrue("no LaunchedEffect(ui.clientConnected)", effect.isNotEmpty())
        assertTrue(
            "losing the connection must return to the chat page; effect head: " +
                effect.take(240),
            effect.take(240).contains("scrollToPage(0)") ||
                effect.take(240).contains("animateScrollToPage(0)"),
        )
    }

    @Test
    fun `every storage disk read is off the main thread`() {
        // 装机验收：大目录点进去后界面整体冻住，点击排队，旧结果后到把界面拽回去。
        // viewModelScope 的默认上下文是 Main，所以这三处 I/O 都必须显式切走。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        // 1. 目录列举
        val open = functionBody(vm, "fun openStorageDir(")
        // 判据是**结果**（列举不在主线程），不是某一种写法：一次性列举时是
        // withContext，改成流式之后是 flowOn。两者都算，写死一种会在重构时误报。
        assertTrue("openStorageDir must enumerate off Main; body head: " + open.take(600),
            open.contains("flowOn(Dispatchers.IO)") || open.contains("withContext(Dispatchers.IO)"))
        // 2. 选择摘要（每个选中文件一次 stat）
        assertTrue(
            "the selection summary flow must run off Main",
            vm.contains("flowOn(Dispatchers.IO)"),
        )
        // 3. 发送前的存在性解析
        val send = functionBody(vm, "fun sendStorageSelection(")
        assertTrue("resolveExisting must run off Main; body head: " + send.take(400),
            send.contains("withContext(Dispatchers.IO)"))
    }

    @Test
    fun `a new navigation cancels the previous listing`() {
        // 这是「点了别的文件夹，过一会儿又自己跳回刚才那个」的正面修法。
        // 不取消的话旧列举完成后照样落地，把用户拽回他已经离开的目录。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val open = functionBody(vm, "fun openStorageDir(")
        assertTrue(
            "openStorageDir must cancel the in-flight job before starting a new one; head: " +
                open.take(400),
            open.contains("storageJob?.cancel()"),
        )
        // 取消必须在**发起之前**，否则新任务先跑起来、随后被自己的 cancel 干掉。
        val cancelAt = open.indexOf("storageJob?.cancel()")
        val launchAt = open.indexOf("viewModelScope.launch")
        assertTrue("cancel must come before launch (" + cancelAt + " vs " + launchAt + ")",
            cancelAt in 0 until launchAt)
    }

    @Test
    fun `navigation advances the path before the listing returns`() {
        // 只挪到后台线程还不够：点击到列表出现之间界面毫无变化，用户以为没点上。
        // 路径必须立即前进（面包屑先动）并置 loading（画进度）。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val open = functionBody(vm, "fun openStorageDir(")
        val beginAt = open.indexOf("StorageNavigation.begin(")
        val launchAt = open.indexOf("viewModelScope.launch")
        assertTrue("openStorageDir must call StorageNavigation.begin", beginAt >= 0)
        assertTrue(
            "the optimistic advance must happen before the coroutine starts (" +
                beginAt + " vs " + launchAt + ")",
            beginAt < launchAt,
        )
        // 流式路径下每一步都必须经过纯函数，那三个分支的断言在 StorageNavigationTest。
        // 在 ViewModel 里直接 copy() 改状态就绕过了它们。
        for (step in listOf("head(", "append(", "complete(", "settle(")) {
            assertTrue(
                "every state step must go through StorageNavigation." + step +
                    " so the transitions stay covered by StorageNavigationTest",
                open.contains("StorageNavigation." + step),
            )
        }
        assertFalse(
            "openStorageDir must not hand-roll state with copy(): that bypasses " +
                "StorageNavigation and its tests",
            open.contains("_storageState.value.copy("),
        )
    }

    @Test
    fun `the storage list is animated, and the breadcrumb deliberately is not`() {
        // 用户装机验收：「双端文件栏无动画效果，太硬」；随后（2026-09-02）又裁决
        // **去掉面包屑那层动效**（幅度小、意义不大），空间感交给列表整体的方向横移。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        // 行的增删移动走**全项目共用件**（改一处全局生效），不是就地写 animateItem。
        assertTrue(
            "list rows must reuse flikkyItemAnimation(), the shared item-motion extension",
            tab.contains("flikkyItemAnimation()"),
        )
        // 反向：不许再出现逐行入场包装。它让 item 在入场前不占高度，而零高会破坏
        // lazy 视口填充（丢行、必须下拉才出现、切 tab 卡顿）。守卫本体在
        // LazyItemHeightConventionTest；这里只钉住这个调用点不许回来。
        assertFalse(
            "no per-row entrance wrapper in a recycling list",
            tab.contains("StreamedListItem"),
        )
        // 列表整体按方向横移，且**等第一批到达才启动**——容器还空着就跑动画，
        // 等于演给一个空盒子看（浏览器端实测过）。
        val effect = tab.substringAfter("LaunchedEffect(state.path, state.entries.isNotEmpty())", "")
        assertTrue("no path-change slide effect on the list", effect.isNotEmpty())
        val head = effect.take(600)
        assertTrue(
            "the slide must bail out while there are no rows yet; head: $head",
            head.contains("state.entries.isEmpty()) return@LaunchedEffect"),
        )
        assertTrue(
            "direction must come from the shared rule, not a local guess",
            head.contains("StorageNavigationDirection.forward("),
        )
        assertTrue(
            "the same path must not slide again on a refresh",
            head.contains("lastSlidPath == state.path"),
        )
        // 面包屑那层刻意没有动效。
        assertFalse(
            "the breadcrumb animation was removed by user ruling; do not reinstate it",
            tab.contains("AnimatedContent("),
        )
        // 加载态要有进度，否则大目录里点击到列表出现之间界面毫无变化。
        assertTrue(
            "a loading listing must show progress",
            tab.contains("LinearProgressIndicator") && tab.contains("state.loading"),
        )
        // 动效参数一律走 Motion（全局速度档 + prefers-reduced-motion 由它统辖），
        // 不许写死 tween/spring：那样「动画速度」设置对这里就失效了。
        assertTrue("motion must come from the Motion scheme", tab.contains("Motion."))
        assertFalse(
            "no hard-coded durations: the animation-speed setting must reach this screen",
            tab.contains("tween") || tab.contains("spring"),
        )
    }

    @Test
    fun `the storage list ends with a marker, so the user knows they saw everything`() {
        // 用户原话：「有一个潜在致命的逻辑问题：用户如何知道自己是否看到了全部」。
        // 流式加载下「列表停止生长」与「加载完了」在屏幕上长得一样。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        assertTrue(
            "the list must end with a footer item",
            tab.contains("item(key = FOOTER_KEY)"),
        )
        // 两种措辞都要有：加载中报已到数量，完成后报总数。
        // 只有后者的话，加载途中那句「共 N 项」是在骗人。
        assertTrue(
            "the footer must say it is still loading while entries arrive",
            tab.contains("serving_storage_loading_count"),
        )
        assertTrue(
            "and report the total once done",
            tab.contains("serving_storage_total"),
        )
        assertTrue(
            "the wording must be driven by state.loading",
            tab.contains("if (state.loading)"),
        )
        // 页脚必须有稳定 key：不给 key 的 item 用位置当身份，而位置随 entries 增长
        // 一直在变，每来一批都会被当成「删旧加新」，animateItem 跟着演一遍淡出淡入。
        assertTrue("the footer needs a stable key", tab.contains("FOOTER_KEY"))
    }
}
