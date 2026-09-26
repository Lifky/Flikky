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
            source("com/example/flikky/ui/components/PeerChannelLockFab.kt"),
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
            "ServingScreen must take the header subtitle from the shared summary",
            screen.contains("peerVisibleSummary("),
        )
        assertTrue(
            "the shared summary must derive from the visible-channel labels",
            sheet.contains("visibleChannelLabels("),
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
            sheet.contains("peer_permissions_visible_none"),
        )
    }

    @Test
    fun `the stop action keeps its distinct error color`() {
        val stopAt = header.indexOf("ic_power")
        assertTrue("the header must expose the stop action", stopAt > 0)
        val buttonAt = header.lastIndexOf("FilledTonalIconButton(", stopAt)
        val button = call(header.substring(buttonAt), "FilledTonalIconButton")
        assertTrue("stop must keep its error color", button.contains("errorContainer"))
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
    fun `header actions and visibility use the live session state`() {
        val headerCall = call(screen, "ConversationHeader")
        assertTrue(headerCall.contains("onPermissionsClick = { showPeerPermissions = true }"))
        assertTrue(headerCall.contains("onSettingsClick = { showQuickSettings = true }"))
        assertTrue(headerCall.contains("onStopClick = { viewModel.stopService(); onStopped() }"))
        assertTrue(headerCall.contains("subtitle = visibleText"))
        assertFalse(headerCall.contains("conversation_connected"))
        assertFalse(screen.contains("CompactActionGroup("))
        assertFalse(screen.contains("showFilesQuickSheet"))
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

    @Test
    fun `the see group carries all three peer visible channels`() {
        assertTrue(sheet.contains("R.string.peer_permissions_files"))
        assertTrue(sheet.contains("R.string.peer_permissions_album"))
        assertTrue(sheet.contains("R.string.peer_permissions_favorites"))
    }

    @Test
    fun `the see group indices span three rows, not two`() {
        val seeAt = sheet.indexOf("peer_permissions_section_see")
        assertTrue("sanity: no peer-visible section", seeAt >= 0)
        val seeGroup = sheet.substring(seeAt).take(2500)

        assertFalse("still carries the old two-row total", seeGroup.contains("total = 2"))
        assertTrue("the peer-visible group must contain three rows", seeGroup.contains("total = 3"))
        listOf("index = 0", "index = 1", "index = 2").forEach {
            assertTrue("missing $it", seeGroup.contains(it))
        }
    }

    @Test
    fun `the album row derives its state from the shared helper`() {
        val albumAt = sheet.indexOf("R.string.peer_permissions_album")
        assertTrue("sanity: no album channel row", albumAt >= 0)
        val albumBlock = sheet.substring(maxOf(0, albumAt - 600)).take(900)

        assertTrue("the album row bypasses peerChannelState", albumBlock.contains("peerChannelState("))
        assertTrue("album availability does not use the permission scope", albumBlock.contains("AlbumAccess.None"))
    }

    @Test
    fun `the album row never toggles the app side setting`() {
        assertTrue(
            "sanity: the album row must expose the peer gate setter",
            sheet.contains("onSetAlbumBrowsing"),
        )
        assertFalse(
            "the peer panel must not write an app-side album setting",
            sheet.contains("setAlbumEnabled") || sheet.contains("albumBetaEnabled"),
        )
    }

    @Test
    fun `the header subtitle can list three channels`() {
        val at = sheet.indexOf("fun peerVisibleSummary(")
        assertTrue("sanity: no shared peerVisibleSummary found", at >= 0)
        val summary = sheet.substring(at).take(1600)
        for (channel in listOf("files", "album", "favorites")) {
            assertTrue(
                "the shared summary must list the $channel channel",
                summary.contains("R.string.peer_permissions_$channel)"),
            )
        }
    }

    private val mainActivity
        get() = stripComments(source("com/example/flikky/MainActivity.kt"))
    private val settingsScreen
        get() = stripComments(source("com/example/flikky/ui/settings/SettingsScreen.kt"))

    @Test
    fun `peer gates are revoked whenever the app comes back to the foreground`() {
        // D76：系统权限可能在 App 不在前台时被撤销。只在某个页面里检查，
        // 用户停在别的页面回来时，一个「开着但没权限」的通道就躲过去了。
        val at = mainActivity.indexOf("override fun onResume(")
        assertTrue("MainActivity must override onResume to run the D76 guard", at >= 0)
        val body = mainActivity.substring(at).take(600)
        assertTrue(
            "onResume must revoke peer gates whose prerequisite is gone: " + body,
            body.contains("revokeUnavailablePeerGates("),
        )
    }

    @Test
    fun `peer gates are revoked whenever a peer gate value changes`() {
        // D76：导入备份发生在前台、不经过 onResume。只挂 onResume 的话，
        // 导入一份开着文件/相册的备份后，通道会以「开」的状态藏在不可用态后面。
        val at = mainActivity.indexOf("revokeUnavailablePeerGates(", mainActivity.indexOf("setContent"))
        assertTrue("MainActivity must also run the guard from composition", at >= 0)
        val effectAt = mainActivity.lastIndexOf("LaunchedEffect(", at)
        val effect = mainActivity.substring(effectAt, at)
        for (key in listOf("storageBrowsingEnabled", "albumBrowsingEnabled", "favoriteBrowsingEnabled")) {
            assertTrue("the guard effect must re-run when $key changes: " + effect, effect.contains(key))
        }
    }

    @Test
    fun `the settings peer row shows what is visible as its trailing value`() {
        // 2026-09-27 用户：与「消息操作样式 → 常驻按钮」同形，值放在行尾而不是标题下面。
        val screen = settingsScreen
        assertTrue(screen.contains("trailingValue = peerVisibleSummary("))
        assertFalse(screen.contains("subtitle = peerVisibleSummary("))
    }

    @Test
    fun `the settings page reaches peer gates only through the shared panel`() {
        // 设置页曾经只有「浏览手机存储」一个裸开关：没授权也能打开（正是 D76 那个状态），
        // 相册、收藏在这里根本没有入口。现在统一走同一个面板，三态与会话内零差异。
        assertFalse(
            "the bare storage-browsing switch must be gone from the settings page",
            settingsScreen.contains("settings_storage_browsing"),
        )
        assertTrue(
            "the settings page must open the same PeerPermissionsSheet as the session",
            settingsScreen.contains("PeerPermissionsSheet("),
        )
    }
}
