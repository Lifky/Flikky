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
    private val transferService get() = stripComments(source("com/example/flikky/service/TransferService.kt"))

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
        // 而设置项文案说的是「允许电脑端浏览」。v1.21.0 起 tab 左下的通道锁需要读这个值，
        // 但它只能从参数直接流进 PeerChannelLockFab，不能参与任何内容渲染。
        assertTrue(
            "ServingStorageTab must receive the peer gate under its role-specific name",
            storageTab.contains("peerStorageEnabled: Boolean"),
        )
        assertEquals(
            "peerStorageEnabled may appear only in the parameter and the channel-lock call; " +
                "any other use risks gating the phone-side contents",
            2,
            Regex("""\bpeerStorageEnabled\b""").findAll(storageTab).count(),
        )
        val lockCall = Regex("""PeerChannelLockFab\(([\s\S]*?)\n\s+\)""").find(storageTab)
        assertTrue("no PeerChannelLockFab call found in ServingStorageTab", lockCall != null)
        assertTrue(
            "the peer gate must flow directly into the lock FAB: ${lockCall!!.value}",
            lockCall.value.contains("peerEnabled = peerStorageEnabled"),
        )

        // 传入侧要接到真实门控值，但仍不得把它与 hasPermission 组合后再传入。
        val call = Regex("""ServingStorageTab\(([\s\S]*?)\n {12}\)""").find(servingScreen)
        assertTrue("no ServingStorageTab call site found in ServingScreen", call != null)
        assertTrue(
            "the lock FAB state must come from the persisted peer gate: ${call!!.value}",
            call.value.contains("peerStorageEnabled = settings.storageBrowsingEnabled"),
        )
        assertTrue(
            "the lock FAB must write the same gate as the permissions panel: ${call.value}",
            call.value.contains("onSetPeerStorageEnabled = viewModel::setStorageBrowsingEnabled"),
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
        val call = tab.substring(at, minOf(at + 800, tab.length))
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
            tab.contains("ChannelSelectionFabSize + Spacing.xxxl"),
        )
    }

    @Test
    fun `the selection FAB only exists while something is selected`() {
        // 没选任何东西时它没有可做的事。常驻一个「清除选择（0 项）」是噪音。
        val fab = stripComments(
            source("com/example/flikky/ui/serving/storage/StorageSelectionFab.kt"),
        )
        val shared = stripComments(
            source("com/example/flikky/ui/components/ChannelSelectionFabMenu.kt"),
        )
        assertTrue(
            "visibility must be derived from the selection count",
            fab.contains("summary.count > 0"),
        )
        // 选择被清空时菜单必须跟着收起，否则下次有选中时它是展开状态。
        assertTrue(
            "clearing the selection must collapse the menu",
            shared.contains("expanded = false"),
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
        // 判据从**包住 tab 栏的那个门控结构**开始，不是「往前数 N 个字符」。
        //
        // 原来取的是 rowAt - 700 的字符窗口。2026-09-14 在 tab 栏前面加了 12 行
        // 注释（说明面板按钮为什么并进这一行），门控就被挤出窗口 —— 守卫红了，
        // 而它守的规则一点没变。字符窗口是本项目第四次栽的地方，一律改成贴语法边界。
        val gateAt = listOf(
            servingScreen.lastIndexOf("AnimatedVisibility(", rowAt),
            servingScreen.lastIndexOf("if (ui.clientConnected)", rowAt),
        ).filter { it >= 0 }.maxOrNull() ?: -1
        assertTrue(
            "no gating structure found before the tab row -- it must be wrapped in " +
                "AnimatedVisibility(visible = ui.clientConnected) or an equivalent if",
            gateAt >= 0,
        )
        val gate = servingScreen.substring(gateAt, rowAt)
        assertTrue(
            "the tab row must be gated on ui.clientConnected; gating block:" +
                System.lineSeparator() + gate.take(400),
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

    @Test
    fun `going back to a visited directory uses the cache instead of re-enumerating`() {
        // 用户 2026-09-03 第 7 条：「回退不重新加载，状态完美恢复」。
        // StorageDirectoryCacheTest 管缓存本身的判据；这一条管**它真的被用上了**。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val open = functionBody(vm, "fun openStorageDir(", 4)
        assertTrue("no openStorageDir body found", open.isNotEmpty())
        // 命中缓存必须在**起协程之前**就返回，否则还是会去枚举文件系统。
        val beforeLaunch = open.substringBefore("viewModelScope.launch")
        assertTrue(
            "the cache must be consulted before any coroutine is started; head was: $beforeLaunch",
            beforeLaunch.contains("storageCache.get("),
        )
        assertTrue(
            "a hit must return early rather than fall through to the stream",
            beforeLaunch.contains("return"),
        )
        // 只存完整的那份：加载中或空的列表存进去会让「秒回」回一份残缺的。
        assertTrue(
            "only a settled listing may be cached",
            beforeLaunch.contains("!leaving.loading"),
        )
        // 手动刷新必须绕过缓存，否则那个按钮什么也刷不了。
        val refresh = functionBody(vm, "fun refreshStorageDir(", 4)
        assertTrue("no refreshStorageDir", refresh.isNotEmpty())
        assertTrue("refresh must force past the cache", refresh.contains("force = true"))
    }

    @Test
    fun `the phone offers a manual refresh, because the cache never expires`() {
        // 缓存刻意没有任何时效判断（自动重取会把「秒回」变回「每次都等」），
        // 所以必须有一个手动出口 —— 否则用户永远拿不到变化后的内容。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        assertTrue(
            "the storage tab needs a refresh affordance",
            tab.contains("R.drawable.ic_refresh") && tab.contains("onRefresh"),
        )
        assertTrue(
            "and it must be labelled, or it is an unnamed button to a screen reader",
            tab.contains("R.string.serving_storage_refresh"),
        )
        // refreshStorage 也必须绕过缓存。它是「授权完成 / 回到前台后重新读一遍」，
        // 不 force 的话会命中缓存、原样放回刚才那份 —— 加缓存时差点悄悄废掉它。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val refresh = functionBody(vm, "fun refreshStorage(", 4)
        assertTrue("no refreshStorage body", refresh.isNotEmpty())
        assertTrue(
            "refreshStorage must force past the cache; body: $refresh",
            refresh.contains("force = true"),
        )
    }

    @Test
    fun `a superseded listing cannot write into the current directory`() {
        // 装机验收（2026-09-03，秒回上线后的严重回归）：
        // 「进入深度子文件夹并返回后，大概率会重叠渲染进入的多个文件夹中的文件列表」。
        //
        // `Job.cancel()` 是协作式的，而 `flowOn(IO)` 中间还有一个 channel ——
        // 取消之后仍可能有一批已派发到 Main 的数据跑完，把上一个目录的条目接到
        // 当前列表上。撞 key 之后 LazyColumn 会把行画在同一个位置上（视觉损坏）。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val open = functionBody(vm, "fun openStorageDir(", 4)
        assertTrue("no openStorageDir body", open.isNotEmpty())
        // 世代号必须在**方法一开始**就递增 —— 缓存命中那条路径会提前 return，
        // 递增写在后面的话，被它抢占的那个流就没被拦住。
        val head = open.take(400)
        assertTrue(
            "the generation must be bumped before any early return; head was: $head",
            head.contains("++storageGen"),
        )
        assertTrue(
            "every chunk must be fenced off when superseded",
            open.contains("if (gen != storageGen) return@collect"),
        )
        // 纯逻辑层的第二道：append 只接属于当前路径的批次。
        assertTrue(
            "append must be told which directory the batch belongs to",
            open.contains("StorageNavigation.append(") && open.contains("_storageState.value.path"),
        )
    }

    @Test
    fun `a refresh drops only the directory being refreshed`() {
        // 针对性审查发现（2026-09-03）：App 端 force 走的是 clear()，而浏览器端只丢
        // 目标那一个。手机上刷新一次 —— 或授权完成、回到前台 —— 就把所有目录的缓存
        // 全扔了，秒回的好处一次性归零。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val open = functionBody(vm, "fun openStorageDir(", 4)
        assertTrue(
            "force must drop only the target directory",
            open.contains("storageCache.remove(target)"),
        )
        assertFalse(
            "force must not wipe the whole cache",
            open.contains("storageCache.clear()"),
        )
        // 「列举规则变了」那种全都不可信的情况仍然要整份清掉 —— 由观察者显式做。
        assertTrue(
            "a listing-rule change must still clear everything",
            vm.contains("storageCache.clear()"),
        )
    }

    @Test
    fun `a remembered scroll position carries the directory it belongs to`() {
        // 与 currentPath 那个缺陷同一个形状：**一份没有身份的状态**。
        // A → B → 在 B 还没停稳就退回 A，报上来的仍是 A 的位置，而这时正要把 B
        // 存进缓存 —— 不核对路径就会把 A 的位置存到 B 头上。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        assertTrue(
            "the reported position must carry its path",
            vm.contains("fun rememberStorageScroll(path: String"),
        )
        val open = functionBody(vm, "fun openStorageDir(", 4)
        assertTrue(
            "and storing it must check that path against the directory being left",
            open.contains("storageScrollPath == leaving.path"),
        )
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        // 断言必须**限定在这次调用里**。`state.path,` 在这个文件里还出现在面包屑的
        // `path = state.path,` 上，不限定范围的话，把这里换成常量照样全绿
        // （逼红实测：零条红 —— 与恢复滚动那条犯的是同一个错）。
        val report = tab.substringAfter("onScrollChanged(", "").take(200)
        assertTrue("no onScrollChanged call found", report.isNotEmpty())
        assertTrue(
            "the list must report which directory the position is for; call was: $report",
            report.contains("state.path,"),
        )
    }

    @Test
    fun `changing directory replaces the list instead of diffing it`() {
        // 装机验收 2026-09-03（**第二次**看到行叠着行，与上一次根因不同）：
        //
        // `animateItem` 的 fadeOutSpec 会把**消失的 item 继续组合并画在它原来的偏移上**
        // 直到淡出跑完。换目录时旧目录的 key 全部消失、新目录的 key 全部出现，于是
        // 同一帧里两份列表都在画 —— 而它们的偏移不同（新目录从顶部起，旧目录停在
        // 用户离开时的位置），看起来就是两份列表错位叠在一起。
        //
        // 加缓存之前这条路走不到：换目录会先经过 `entries.isEmpty() && loading` 那个
        // 分支，那里**整个 LazyColumn 都不在**，旧行是被直接销毁的、没有淡出。
        // 缓存让状态从「A 的条目」直接变成「B 的条目」，一个此前不存在的转换。
        //
        // 修法是给列表按路径 key：**不同目录的行不是同一个列表**，
        // 换目录就该替换而不是逐项 diff。同目录内（流式追加、刷新）仍然 diff，
        // 那才是 animateItem 该管的事。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        val at = tab.indexOf("LazyColumn(")
        assertTrue("no LazyColumn found", at > 0)
        // 匹配前缀而不是整串：v1.20.0 起 key 里还带了排序（改排序也要重建列表），
        // 而这条守的是「按目录分 key」这件事本身，与 key 里还有几个参数无关。
        val keyAt = tab.indexOf("key(state.path")
        assertTrue("no key(state.path...)", keyAt in 1 until at)
        // 列表**与它的滚动状态**必须在同一个 per-directory key 块里：
        // 那才能保证换目录时两者一起重建。
        val span = tab.substring(keyAt, at)
        assertTrue(
            "the list state must live in the same key block as the list; span was: $span",
            span.contains("rememberLazyListState("),
        )
        assertTrue(
            "and nothing may close the key block in between; span was: $span",
            span.length < 2000,
        )
    }

    @Test
    fun `changing directory slides without fading through emptiness`() {
        // 装机验收（2026-09-03）：「App 端进出文件夹路径时，
        // 好像 listitem 整体闪了一下」。
        //
        // 根因是上一轮为了修「行叠行」加的 `key(state.path)`：
        // 它把旧列表**一帧内**销毁，而新列表的方向横移是从
        // `alpha = 0` 起步的 —— 于是第一帧是空的。两个单独都对的
        // 决定碰在一起产生了第三个现象。
        //
        // 保留位移（空间感就靠它），去掉淡入：新列表从第一帧就是
        // 不透明的，只是从旁边滑过来。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        val at = tab.indexOf("graphicsLayer {")
        assertTrue("no graphicsLayer on the list", at > 0)
        val layer = tab.substring(at, minOf(tab.length, at + 300))
        assertTrue(
            "the directional slide must still translate: $layer",
            layer.contains("translationX"),
        )
        assertFalse(
            "it must not fade: with the list keyed per directory the old rows are already " +
                "gone, so fading in from zero shows an empty frame. Layer was: $layer",
            layer.contains("alpha"),
        )
    }

    @Test
    fun `the loading bar collapses instead of vanishing`() {
        // 装机验收：「进度条收回的效果在 App 端上好像没有效果」。
        // 确实没有 —— 上一轮只做了浏览器端。
        //
        // 这里的 AnimatedVisibility 是安全的：它在 LazyColumn **之外**，
        // 不是 lazy item，不会触发那条零高度禁令（守卫见
        // LazyItemHeightConventionTest，它只管 items(...) 的正文）。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        val at = tab.indexOf("LinearProgressIndicator")
        assertTrue("no progress indicator", at > 0)
        val before = tab.substring(maxOf(0, at - 400), at)
        assertTrue(
            "the bar must animate in and out rather than appear and vanish; " +
                "preceding source was: $before",
            before.contains("AnimatedVisibility"),
        )
        // 高度必须参与：只淡入淡出的话，下面的列表仍然硬跳一整条的高度。
        assertTrue(
            "the collapse must animate height, or the list still jumps: $before",
            before.contains("expandVertically") && before.contains("shrinkVertically"),
        )
        // 弹簧走 spatial（带位移），与主页 chips 分组同一档。
        assertTrue(
            "a size change is spatial motion: $before",
            before.contains("Motion.spatial"),
        )
    }

    @Test
    fun `each directory gets its own list state, starting where it should`() {
        // 装机验收（2026-09-03，最严重的一个）：在父目录往下滚 3 行再进子目录，
        // 子目录**从第 4 项开始显示**；退回再进就正常了。
        //
        // 根因：`listState` 是在 composable 顶层 remember 的，**跟着面板而不是
        // 跟着目录**。新目录直接继承上一个目录的 firstVisibleItemIndex，
        // 而恢复 effect 在 restoredScrollIndex 为 -1（全新目录）时直接 return，
        // 没人把它归零。又一次「一份状态没带着它属于谁」。
        //
        // 修法：把列表状态放进 `key(state.path)`，并用**初值**告诉它该从哪儿开始。
        // 于是第一帧就在正确位置 —— 不需要事后 scrollToItem，也没有中间帧的跳动。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        // 匹配前缀，理由同上：key 现在是 (state.path, sortSpec.format())。
        val keyAt = tab.indexOf("key(state.path")
        val stateAt = tab.indexOf("rememberLazyListState(")
        assertTrue("no key(state.path...)", keyAt > 0)
        assertTrue("no rememberLazyListState", stateAt > 0)
        assertTrue(
            "the list state must be created inside key(state.path), so a new directory " +
                "gets a fresh one instead of inheriting the previous directory's position",
            stateAt > keyAt,
        )
        val decl = tab.substring(stateAt, minOf(tab.length, stateAt + 300))
        assertTrue(
            "it must start at the remembered position, not at whatever it inherited: $decl",
            decl.contains("initialFirstVisibleItemIndex"),
        )
        assertTrue(
            "including the offset: $decl",
            decl.contains("initialFirstVisibleItemScrollOffset"),
        )
        // 既然初值就把位置安排好了，事后那套恢复机制就全部多余了。
        assertFalse(
            "the post-hoc restore effect must be gone: initial values already place the list, " +
                "and two mechanisms for one position is how they end up fighting",
            tab.contains("scrollToItem("),
        )
        assertFalse(
            "and so must its once-per-directory marker",
            tab.contains("restoredFor"),
        )
        // 上报也必须在这个块里 —— 它报的是哪个目录的位置，
        // 跟着目录才说得通，而且必须带上路径（否则调用方无从判断
        // 这份位置是不是它要存的那个目录的）。
        val reportAt = tab.indexOf("onScrollChanged(")
        assertTrue("no scroll reporting", reportAt > keyAt)
        val report = tab.substring(reportAt, minOf(tab.length, reportAt + 200))
        assertTrue("the report must carry its path: $report", report.contains("state.path,"))
        // 切 tab 回来的那个场景现在是**结构保证**的，HorizontalPager 会把离屏的页
        // 从组合里移除，而 rememberLazyListState 自己的 saveable 会在回来时胜出
        // （初值只在没有存档时才用）—— 用户离开时的位置照样保住，
        // 而且没有第二套机制会把他弹回旧位置。上一版那个
        // `restoredFor` 标记存在的唯一理由就是抵消那个冲突，现在冲突源头没了。
    }

    @Test
    fun `the path a listing starts with is the one the server will confirm`() {
        // 审查发现（2026-09-03）：`begin` 收到的是**未规范化**的 relative，
        // 而头行回来的是规范化过的路径。两者不一致时（比如带了尾斜杠）
        // `state.path` 会在加载中途变一次 —— 而列表现在正是按 `state.path` key 的，
        // 那会把刚建好的列表连同滚动位置一起重建，并重放一次方向横移。
        //
        // 以前这只是一个潜伏的不一致；把路径提升为 key 之后它变成了承重的。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val open = functionBody(vm, "fun openStorageDir(", 4)
        assertTrue("no openStorageDir", open.isNotEmpty())
        assertTrue(
            "begin must be given the normalised path, the same one the cache and the " +
                "server round-trip agree on",
            open.contains("StorageNavigation.begin(_storageState.value, target,")
                || open.contains("StorageNavigation.begin(_storageState.value, target)"),
        )
    }

    @Test
    fun `refreshing a directory keeps you where you were`() {
        // 把列表状态改成每个目录一份之后，刷新会让列表先清空
        // （entries 空 + loading）再重建 —— 不把位置带过去就会弹回顶部。
        // 用户按刷新是想看更新后的内容，不是想被弹回顶部。
        //
        // 位置仍然走 restoredScrollIndex → 列表初值这条路，
        // 不引入第二套事后滚动机制。
        val vm = stripComments(source("com/example/flikky/ui/serving/ServingViewModel.kt"))
        val open = functionBody(vm, "fun openStorageDir(", 4)
        assertTrue(
            "a forced refresh of the same directory must carry the current position over",
            open.contains("keepIndex") && open.contains("keepOffset"),
        )
        assertTrue(
            "and it must only apply when refreshing the directory you are already in",
            open.contains("force && leaving.path == target && storageScrollPath == target"),
        )
        // 位置必须经由纯函数进入状态 —— 在 ViewModel 里 copy() 会绕过
        // StorageNavigation 及其测试（旁边那条守卫已经盯着这件事）。
        assertTrue(
            "the resume position must be handed to StorageNavigation.begin",
            open.contains("keepIndex, keepOffset"),
        )
    }

    @Test
    fun `the storage list is keyed by path and sort, so changing sort starts at the top`() {
        // 改排序不改路径。列表若只按 path 分 key，状态会存活下来、停在旧下标上 ——
        // 顺序全变之后那个位置已经没有意义了（与本版「位置错乱」同族）。
        val tab = source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt")
        assertTrue(
            "列表没有按 路径 + 排序 + 关键词 一起 key —— 三者任一变化都该从顶部开始",
            Regex("""key\(\s*state\.path\s*,\s*sortSpec\.format\(\)\s*,\s*query\s*\)""")
                .containsMatchIn(tab),
        )
    }

    @Test
    fun `switching sort clears the directory cache, which holds the old order`() {
        // 缓存里存的是旧顺序的条目。不清的话返回上级会看到按旧排序排的列表 ——
        // 「一份状态没跟着它的依据一起更新」，本版那六个缺陷的同一个形状。
        val vm = source("com/example/flikky/ui/serving/ServingViewModel.kt")
        // 切到下一个顶层 fun 之前。用 "    fun " 而不是带行尾的模式：
        // 这个文件的行尾取决于 git autocrlf，写死任一种都会在另一种下切错。
        val fn = vm.substring(vm.indexOf("fun setStorageSort") + 1)
            .substringBefore("    fun ")
        assertTrue("setStorageSort 没有清缓存", fn.contains("storageCache.clear()"))
        assertTrue("setStorageSort 没有原地重排当前目录", fn.contains("StorageNavigation.resort"))
    }

    @Test
    fun `changing directory clears the search query`() {
        // 带着上个目录的关键词进新目录，看到的是一个「空目录」假象。
        // 清空必须发生在**发起列举之前**，否则第一批到达时还在按旧词过滤。
        val vm = source("com/example/flikky/ui/serving/ServingViewModel.kt")
        val open = vm.substring(vm.indexOf("fun openStorageDir"))
            .substringBefore("    fun ")
        assertTrue(
            "openStorageDir 没有清空关键词",
            open.contains("_storageQuery.value = \"\""),
        )
        val beforeLaunch = open.substringBefore("viewModelScope.launch")
        assertTrue(
            "关键词必须在发起列举之前清掉",
            beforeLaunch.contains("_storageQuery.value = \"\""),
        )
    }

    @Test
    fun `the list, the count and the empty state all read the filtered rows`() {
        // 用 state.entries 判空会在「目录非空但没有命中」时显示
        // 「这个文件夹是空的」—— 那句话此刻是假的。
        val tab = stripComments(source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"))
        assertTrue("没有派生过滤后的行", tab.contains("StorageNavigation.filter(state.entries, query)"))
        assertTrue("列表没有用过滤后的行", tab.contains("itemsIndexed(shown"))
        // 断言**分支结构**而不是「出现过 shown.isEmpty()」：进度条留白那处也用它，
        // 所以只查子串时改坏空态分支照样能过（逼红实测零条红）。
        assertTrue(
            "空态分支没有用过滤后的行",
            Regex("""else\s+if\s*\(\s*shown\.isEmpty\(\)\s*\)""").containsMatchIn(tab),
        )
        assertTrue(
            "首批未到的分支也要用过滤后的行",
            Regex("""if\s*\(\s*shown\.isEmpty\(\)\s*&&\s*state\.loading\s*\)""")
                .containsMatchIn(tab),
        )
        assertTrue(
            "过滤无命中时必须说「没有匹配」，不能说「文件夹是空的」",
            tab.contains("serving_storage_search_empty"),
        )
    }

    @Test
    fun `the breadcrumb cannot squeeze the action buttons off the row`() {
        // 2026-09-08 装机反馈：路径一长，右侧的搜索 / 排序 / 刷新被推出屏幕，
        // 完全点不到。原因是面包屑直接摊在外层 Row 里、靠一个
        // `Spacer(weight(1f))` 把按钮推到右边 —— 面包屑撑满时 Spacer 只能拿到 0。
        //
        // 修法是把面包屑包进**自己那一格**并给它 weight：它最多吃掉剩余空间，
        // 按钮那几格永远保得住；内容超出就横向滚动，而不是挤别人。
        val at = storageTab.indexOf("""private fun StorageBreadcrumbRow""")
        assertTrue("""StorageBreadcrumbRow 不见了""", at > 0)
        val fn = storageTab.substring(at, storageTab.indexOf("""@Composable""", at + 10))

        assertTrue(
            "面包屑没有被包进带 weight 的自己那一格 —— 长路径会挤掉右侧按钮",
            fn.contains(Regex("""Modifier\s*
?\s*\.weight\(1f\)\s*
?\s*\.horizontalScroll""")),
        )
        assertFalse(
            "还留着 Spacer(weight(1f))：两个 weight 会平分，面包屑仍然能挤掉按钮",
            fn.contains(Regex("""Spacer\(Modifier\.weight\(1f\)\)""")),
        )
    }

    @Test
    fun `the breadcrumb scrolls to the current directory, not the root`() {
        // 自动滚到末尾。当前目录是这一行最重要的信息；停在根上等于把
        // 「我在哪」藏在滚动区外面。
        val at = storageTab.indexOf("""private fun StorageBreadcrumbRow""")
        val fn = storageTab.substring(at, storageTab.indexOf("""@Composable""", at + 10))
        assertTrue(
            "路径变化后没有把面包屑滚到末尾",
            fn.contains(Regex("""LaunchedEffect\([^)]*maxValue[^)]*\)""")),
        )
        assertTrue("没有滚到 maxValue", fn.contains("scrollTo("))
    }

    @Test
    fun `the peer gate for favourites never leaks into the app side chat tab`() {
        assertTrue(
            "sanity: ServingChatTab should still read the app-side beta flag",
            chatTab.contains("favoriteBetaEnabled"),
        )
        assertFalse(
            "ServingChatTab must not read favoriteBrowsingEnabled: that is the peer gate, " +
                "and gating the app side with it makes the user's own favourites vanish (D33)",
            chatTab.contains("favoriteBrowsingEnabled"),
        )
    }

    @Test
    fun `the service feeds the peer DTO both axes, not just the beta flag`() {
        val line = transferService
            .lineSequence()
            .firstOrNull { it.contains("favoriteEnabled = ") && it.contains("latestSettings") }
        assertTrue("sanity: no favoriteEnabled wiring found in TransferService", line != null)
        assertTrue(
            "favoriteEnabled must combine BOTH axes, found: $line",
            line!!.contains("favoriteBetaEnabled") && line.contains("favoriteBrowsingEnabled"),
        )
    }
}
