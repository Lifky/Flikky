package com.example.flikky.ui.serving.storage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flikky.R
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.ui.theme.FlikkyTheme
import com.example.flikky.ui.serving.captureServingUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageFabLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun bounds(label: Int): Rect = compose.onNodeWithContentDescription(
        context.getString(label),
    ).fetchSemanticsNode().boundsInRoot

    @Test
    fun lockMovesOnlyHorizontallyFromTheSelectionCenterAndReturns() {
        val selected = mutableStateOf(false)
        var lockClicks = 0
        compose.setContent {
            FlikkyTheme(settings = FlikkySettings()) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    ServingStorageTab(
                        hasPermission = true,
                        onRequestPermission = {},
                        state = LocalStorageState(path = "", entries = emptyList(), loading = false),
                        summary = StorageSelectionSummary(if (selected.value) 1 else 0, 0L, 0),
                        peerStorageEnabled = false,
                        onSetPeerStorageEnabled = { lockClicks++ },
                        onOpenDir = {},
                        onToggleSelection = {},
                        onClearSelection = { selected.value = false },
                        onSendSelection = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        val initial = bounds(R.string.storage_lock_peer_off)
        captureServingUi("storage-lock-alone")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { selected.value = true }
        repeat(24) {
            compose.mainClock.advanceTimeBy(16)
            assertEquals("Lock must never move vertically", initial.center.y,
                bounds(R.string.storage_lock_peer_off).center.y, 1f)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val lock = bounds(R.string.storage_lock_peer_off)
        val action = bounds(R.string.serving_storage_actions)
        assertEquals("Initial lock and selection must share X", initial.center.x, action.center.x, 1f)
        assertEquals("Both buttons must share Y", lock.center.y, action.center.y, 1f)
        assertTrue("Lock must be visibly smaller", lock.width < action.width * 0.8f)
        assertTrue("Buttons must not overlap", lock.right < action.left)
        compose.onNodeWithContentDescription(context.getString(R.string.storage_lock_peer_off))
            .performTouchInput { click() }
        assertEquals("Translated lock must still accept touch input", 1, lockClicks)
        captureServingUi("storage-fab-pair")

        compose.onNodeWithContentDescription(context.getString(R.string.serving_storage_actions)).performClick()
        compose.waitForIdle()
        assertEquals("Opening the menu must not move the lock", lock, bounds(R.string.storage_lock_peer_off))
        val close = bounds(R.string.serving_storage_actions_close)
        assertEquals("Menu button must keep its center", action.center.x, close.center.x, 1f)
        assertEquals("Menu button must keep its height center", action.center.y, close.center.y, 1f)
        captureServingUi("storage-menu-expanded")

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { selected.value = false }
        repeat(24) {
            compose.mainClock.advanceTimeBy(16)
            assertEquals(initial.center.y, bounds(R.string.storage_lock_peer_off).center.y, 1f)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(initial, bounds(R.string.storage_lock_peer_off))
    }
}
