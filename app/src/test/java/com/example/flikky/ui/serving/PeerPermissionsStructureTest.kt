package com.example.flikky.ui.serving

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards peer-permission UI rules that remain compilable when wired incorrectly. */
class PeerPermissionsStructureTest {

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
        val file = File(mainJavaRoot(), relative)
        assertTrue("missing source file: " + relative, file.isFile)
        return file.readText(Charsets.UTF_8)
    }

    private fun stripComments(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")
        .replace(Regex("""(?m)^import .*$"""), "")

    private fun call(source: String, name: String): String {
        val at = source.indexOf(name + "(")
        assertTrue("sanity: no $name call found", at >= 0)
        val open = source.indexOf('(', at)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(at, index + 1)
                }
            }
        }
        error("unterminated $name call")
    }

    private val sheet
        get() = stripComments(
            source("com/example/flikky/ui/serving/PeerPermissionsSheet.kt"),
        )
    private val screen
        get() = stripComments(source("com/example/flikky/ui/serving/ServingScreen.kt"))
    private val header
        get() = stripComments(source("com/example/flikky/ui/components/ConversationHeader.kt"))
    private val quickSettings
        get() = stripComments(source("com/example/flikky/ui/serving/QuickSettingsSheet.kt"))
    private val lockFab
        get() = stripComments(
            source("com/example/flikky/ui/serving/storage/StorageChannelLockFab.kt"),
        )
    private val storageTabSrc
        get() = stripComments(
            source("com/example/flikky/ui/serving/storage/ServingStorageTab.kt"),
        )

    @Test
    fun `the panel derives every row state from the shared helper`() {
        assertTrue(
            "the panel must use peerChannelState, not hand-rolled conditionals",
            sheet.contains("peerChannelState("),
        )
    }

    @Test
    fun `an unavailable channel offers no switch`() {
        val at = sheet.indexOf("PeerChannelState.Unavailable")
        assertTrue("sanity: no Unavailable branch found in the panel", at > 0)
        val rest = sheet.substring(at)
        val next = listOf(
            rest.indexOf("PeerChannelState.Off", 1),
            rest.indexOf("PeerChannelState.On", 1),
        ).filter { it > 0 }.minOrNull() ?: rest.length
        val branch = rest.substring(0, next)
        assertFalse(
            "an Unavailable channel must not render a Switch: " + branch,
            branch.contains("Switch("),
        )
    }

    @Test
    fun `both group headings always render, even a one-row group`() {
        assertTrue(
            "missing the 'can see' section heading",
            sheet.contains("peer_permissions_section_see"),
        )
        assertTrue(
            "missing the 'can do' section heading -- a single-row group still needs it",
            sheet.contains("peer_permissions_section_do"),
        )
    }

    @Test
    fun `the panel never touches the app side feature flags`() {
        assertFalse(
            "the panel must not call setFavoriteBeta: that is the app-side feature flag (D33)",
            sheet.contains("setFavoriteBeta("),
        )
        assertTrue(
            "sanity: the panel should call the peer gate setter",
            sheet.contains("onSetFavoriteBrowsing"),
        )
    }

    @Test
    fun `the header subtitle says what the peer can see`() {
        assertTrue(
            "ServingScreen must compute the visible-channel labels for the header",
            screen.contains("visibleChannelLabels("),
        )
        assertTrue(
            "ConversationHeader must accept a subtitle instead of hard-coding connected",
            header.contains("subtitle"),
        )
        val headerCall = call(screen, "ConversationHeader")
        assertTrue(
            "ServingScreen must pass the live visible-channel subtitle to ConversationHeader",
            headerCall.contains("subtitle ="),
        )
    }

    @Test
    fun `nothing shared is stated, not left blank`() {
        assertTrue(
            "missing the explicit nothing-shared copy -- a blank subtitle is ambiguous",
            screen.contains("peer_permissions_visible_none"),
        )
    }

    @Test
    fun `the destructive stop button stays outside the button group`() {
        // 面板动作 2026-09-14 起在 **tab 栏那一行**，不在 header 调用里了
        // （头部从三层压回两层，见另一条守卫）。所以 group 要在整个屏幕里找，
        // 而不是在 ConversationHeader 的实参里找。
        //
        // 这一条守的意图没变：破坏性动作不许与「打开面板」编在同一组 ——
        // group 视觉上在说「这些是一伙的」，把 errorContainer 色的停止服务
        // 放进去等于邀请误触。
        val headerCall = call(screen, "ConversationHeader")
        val groupCall = call(screen, "CompactActionGroup")
        assertFalse(
            "the stop action must stay outside CompactActionGroup",
            groupCall.contains("ic_power"),
        )
        val stopAt = headerCall.indexOf("ic_power")
        assertTrue("sanity: no stop button found (ic_power)", stopAt > 0)
        val stopButtonAt = headerCall.lastIndexOf("FilledTonalIconButton(", stopAt)
        assertTrue("sanity: no FilledTonalIconButton owns ic_power", stopButtonAt >= 0)
        val stopButton = call(headerCall.substring(stopButtonAt), "FilledTonalIconButton")
        assertTrue(
            "the stop button should retain its errorContainer colour",
            stopButton.contains("errorContainer"),
        )
    }

    @Test
    fun `quick settings no longer mixes peer gates in with appearance`() {
        assertFalse(
            "storageBrowsingEnabled must move to the peer permissions panel",
            quickSettings.contains("settings_storage_browsing"),
        )
        assertFalse(
            "allowPeerRecall must move to the peer permissions panel",
            quickSettings.contains("settings_allow_peer_recall"),
        )
        assertTrue(
            "the favourites beta flag is an app-side switch and stays in quick settings",
            quickSettings.contains("settings_favorites"),
        )
    }

    @Test
    fun `the lock fab sits next to the selection fab`() {
        assertTrue(
            "sanity: the selection fab should still be mounted",
            storageTabSrc.contains("StorageSelectionFab("),
        )
        assertTrue(
            "the lock fab must be mounted in the storage tab",
            storageTabSrc.contains("StorageChannelLockFab("),
        )
        assertTrue(
            "both fabs must be on the end side -- they are a neighbouring pair, not two corners",
            storageTabSrc.contains("Alignment.BottomEnd"),
        )
        // 两个 FAB 是**紧邻的一对**（都在 BottomEnd，锁在选择 FAB 左侧），
        // 不是分居屏幕两端 —— 后者是我读错参考图做出来的形态（装机反馈 Screenshot_5）。
        assertTrue(
            "the lock fab must sit next to the selection fab, both at the end side",
            storageTabSrc.contains("end = lockEndPadding"),
        )
        // 让位宽度必须**从官方尺寸函数推导**，不许写死 dp。上一版我把选择 FAB
        // 从 medium(80dp) 改成 large(96dp)，装机一眼看出不协调；换档时
        // 让位宽度要自动跟着走，不能靠记住一个魔数。
        assertTrue(
            "the gap must derive from the official container size, not a hard-coded dp",
            storageTabSrc.contains("ToggleFloatingActionButtonDefaults.containerSizeMedium()(0f)"),
        )
        // 位置随邻居在不在而变，且是动画（用户 2026-09-14 要求）：选择 FAB 只在
        // 有选中项时出现，它不在时锁若还守着让位后的坐标，右边就空着 92dp。
        assertTrue(
            "the lock fab must animate its position when the selection fab appears or leaves",
            storageTabSrc.contains("animateDpAsState"),
        )
        assertTrue(
            "the position animation must use the spatial spec -- it is movement, not a fade",
            Regex("""animateDpAsState\([\s\S]{0,400}?Motion\.spatial\(\)""")
                .containsMatchIn(storageTabSrc),
        )
    }

    @Test
    fun `the lock fab uses a size the spec still offers`() {
        // M3 Expressive 把 small（40dp）FAB 标记为 deprecated（本地文档
        // components/FloatingActionButton.md：「Deprecated **small** FAB size」），
        // 而本项目 Shapes.medium 是 16dp —— 40dp 上圆角占边长 40%，读起来是「圆」
        // 而不是「圆角方」（装机反馈 Screenshot_1）。56dp 上同一个圆角只占 29%。
        assertFalse(
            "SmallFloatingActionButton is deprecated in M3 Expressive and reads as a circle " +
                "at this project 16dp corner radius",
            lockFab.contains("SmallFloatingActionButton"),
        )
        assertTrue(
            "sanity: the lock fab should still be a FAB",
            lockFab.contains("FloatingActionButton("),
        )
    }

    @Test
    fun `the header keeps its panel actions off the title row`() {
        // 装机反馈两轮的合并结论：
        //
        // Screenshot_2 —— 四个按钮与加长的副标题抢同一行，「可见：文件、收藏」
        // 被压成省略号。Row 里 trailing 不可压缩而文字带 weight(1f)，永远先压文字；
        // ButtonGroup 的官方 overflow 也兜不住（它在那个 Row 里拿得到全部宽度）。
        //
        // Screenshot_4 —— 给动作单开一行之后头部变成三层 ≈168dp，「太厚」。
        //
        // 现在的形态：可见性是副标题旁的**可点 chip**（状态与入口合一，
        // 所以盾形按钮取消），面板动作并进 **tab 栏那一行**右侧的空地。
        // 两个问题同时消失，且没有任何一层多出来的高度。
        val headerCall = call(screen, "ConversationHeader")
        assertTrue(
            "the visibility state must be a tappable chip in the header: " + headerCall,
            headerCall.contains("statusChip = {") && headerCall.contains("PeerVisibilityChip("),
        )
        assertFalse(
            "the panel actions must not sit in the header call -- that is what made it thick",
            headerCall.contains("CompactActionGroup("),
        )
        // 它们必须落在 tab 栏那一行，而且 tab 栏必须显式让出宽度 ——
        // SecondaryTabRow 默认铺满整宽，不给 weight 会把按钮挤出屏幕**且不报错**。
        val tabRowAt = screen.indexOf("SecondaryTabRow(")
        assertTrue("sanity: no SecondaryTabRow found", tabRowAt > 0)
        val tabRowCall = call(screen.substring(tabRowAt), "SecondaryTabRow")
        assertTrue(
            "SecondaryTabRow must yield width with weight(1f), or the actions get pushed off-screen",
            tabRowCall.contains("Modifier.weight(1f)"),
        )
        assertTrue(
            "the panel actions must live on the tab row",
            screen.indexOf("CompactActionGroup(") > tabRowAt,
        )
        // trailing 只剩停止服务。
        assertTrue(
            "the stop button must stay in the header trailing slot",
            headerCall.contains("ic_power"),
        )
    }

    @Test
    fun `the lock never pretends to revoke the system permission`() {
        assertFalse(
            "the lock fab must not call any permission-revoking API: it gates the peer, not the app",
            lockFab.contains("revokeOwnPermission"),
        )
        assertTrue(
            "sanity: the lock fab should toggle the peer gate",
            lockFab.contains("onToggle"),
        )
    }
}
