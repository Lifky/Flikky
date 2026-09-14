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
        val headerCall = call(screen, "ConversationHeader")
        val groupCall = call(headerCall, "CompactActionGroup")
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
    fun `the lock fab and the selection fab sit on opposite sides`() {
        assertTrue(
            "sanity: the selection fab should still be mounted",
            storageTabSrc.contains("StorageSelectionFab("),
        )
        assertTrue(
            "the lock fab must be mounted in the storage tab",
            storageTabSrc.contains("StorageChannelLockFab("),
        )
        assertTrue(
            "the lock fab must be aligned to the start side, away from the selection fab",
            storageTabSrc.contains("BottomStart"),
        )
        // 两个 FAB 尺寸不同（锁 56dp / 选择 large≈80dp），底边对齐会看着一高一低
        // （装机反馈 2026-09-14 Screenshot_1）。锁必须补上尺寸差的一半，
        // 让两个圆心落在同一条水平线上。
        //
        // 判据落在那个具名常量上而不是某个 dp 字面量：常量的 KDoc 写明了它与
        // StorageSelectionFab 档位的绑定关系，换档时两处要一起改。
        assertTrue(
            "the lock fab must offset its bottom padding to centre against the larger " +
                "selection fab -- plain screenEdge padding puts them at different heights",
            storageTabSrc.contains("bottom = LockFabBottomPadding"),
        )
        assertTrue(
            "LockFabBottomPadding must account for the size difference, not just the screen edge",
            Regex("""val LockFabBottomPadding = Spacing\.screenEdge \+ \d+\.dp""")
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
        // 装机反馈 Screenshot_2：四个按钮与加长的副标题抢同一行宽度，
        // 「可见：文件、收藏」被压成「...」—— 而那句话正是这一版新加的核心信息。
        //
        // 根因：Row 里 trailing 不可压缩、文字那列是 weight(1f)，所以永远先压文字；
        // ButtonGroup 因此拿得到全部想要的宽度，官方 overflow 也就永远不触发。
        // 唯一真正消除宽度竞争的办法是分行。
        val headerCall = call(screen, "ConversationHeader")
        assertTrue(
            "the panel actions must go in the second-row actions slot, not trailing: " + headerCall,
            headerCall.contains("actions = {"),
        )
        assertTrue(
            "sanity: the compact group should be the thing that moved",
            headerCall.contains("CompactActionGroup("),
        )
        // trailing 只剩停止服务。判据：trailing 那一段里不许出现 CompactActionGroup。
        val trailingAt = headerCall.indexOf("trailing = {")
        assertTrue("sanity: no trailing slot found in the header call", trailingAt >= 0)
        val actionsAt = headerCall.indexOf("actions = {")
        assertTrue("sanity: no actions slot found in the header call", actionsAt >= 0)
        val trailingBody =
            if (actionsAt > trailingAt) headerCall.substring(trailingAt, actionsAt)
            else headerCall.substring(trailingAt)
        assertFalse(
            "the trailing slot must hold only the destructive stop button: " + trailingBody,
            trailingBody.contains("CompactActionGroup"),
        )
        assertTrue(
            "the stop button must stay in the trailing slot: " + trailingBody,
            trailingBody.contains("ic_power"),
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
