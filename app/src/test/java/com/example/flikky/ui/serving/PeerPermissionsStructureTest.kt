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
}
