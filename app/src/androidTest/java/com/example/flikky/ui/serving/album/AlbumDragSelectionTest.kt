package com.example.flikky.ui.serving.album

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.ui.theme.FlikkyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumDragSelectionTest {
    @get:Rule val rule = createComposeRule()
    private val selected = mutableStateOf(setOf("img:0"))

    private fun show() {
        val items = (0 until 120).map { index ->
            AlbumItemDto(
                id = "img:$index", name = "photo-$index", mime = "image/jpeg",
                takenAtMs = 1000L - index,
                dateKey = if (index < 3) "2026-09-16" else "2026-09-15",
            )
        }
        rule.setContent {
            FlikkyTheme(settings = com.example.flikky.data.settings.FlikkySettings()) {
                ServingAlbumTab(
                    access = com.example.flikky.util.AlbumAccess.Full,
                    items = items, visibleCount = items.size, selected = selected.value,
                    todayKey = "2026-09-16", yesterdayKey = "2026-09-15",
                    buckets = null, openBucket = null, onSelectView = {}, onOpenBucket = {},
                    onLeaveBucket = {}, peerAlbumEnabled = true, onRequestPermission = {},
                    onChangeScope = {}, onSetPeerAlbumEnabled = {},
                    onToggleSelection = { id ->
                        selected.value = if (id in selected.value) selected.value - id else selected.value + id
                    },
                    onClearSelection = { selected.value = emptySet() },
                    onSendSelection = {}, onPreview = {},
                    modifier = Modifier.fillMaxSize().testTag("album"),
                )
            }
        }
    }

    private fun point(index: Int): Offset {
        val root = rule.onNodeWithTag("album").fetchSemanticsNode().boundsInRoot
        return rule.onNodeWithContentDescription("photo-$index").fetchSemanticsNode().boundsInRoot.center - root.topLeft
    }

    @Test fun longPressDragSelectsAcrossDateHeadersAndKeepsExistingSelection() {
        show()
        val start = point(1)
        val end = point(5)
        rule.onNodeWithTag("album").performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(end, 160)
            moveTo(start, 160)
            moveTo(end, 160)
            up()
        }
        rule.runOnIdle { assertEquals((0..5).map { "img:$it" }.toSet(), selected.value) }
    }

    @Test fun ordinarySwipeScrollsWithoutSelectingEvenInSelectionMode() {
        show()
        val before = rule.onNodeWithContentDescription("photo-6").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("album").performTouchInput { swipeUp(durationMillis = 180) }
        rule.runOnIdle { assertEquals(setOf("img:0"), selected.value) }
        val remaining = runCatching { rule.onNodeWithContentDescription("photo-6").fetchSemanticsNode().boundsInRoot.top }.getOrNull()
        assertTrue("a normal swipe must move the grid", remaining == null || remaining < before)
    }

    @Test fun draggingBackShrinksOnlyThisGestureAndCanCrossTheAnchor() {
        selected.value = setOf("img:0", "img:4", "img:20")
        show()
        val start = point(2)
        val end = point(5)
        val back = point(3)
        val otherSide = point(1)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("album").performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(end, 160)
        }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertEquals(setOf("img:0", "img:2", "img:3", "img:4", "img:5", "img:20"), selected.value) }
        rule.onNodeWithTag("album").performTouchInput { moveTo(back, 160) }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertEquals(setOf("img:0", "img:2", "img:3", "img:4", "img:20"), selected.value) }
        rule.onNodeWithTag("album").performTouchInput { moveTo(otherSide, 160) }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertEquals(setOf("img:0", "img:1", "img:2", "img:4", "img:20"), selected.value) }
        rule.onNodeWithTag("album").performTouchInput { moveTo(start, 160); up() }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertEquals(setOf("img:0", "img:2", "img:4", "img:20"), selected.value) }
    }

    @Test fun aNewDragPreservesTheSelectionCommittedByThePreviousDrag() {
        selected.value = emptySet()
        show()
        val first = point(1)
        val third = point(3)
        val fifth = point(5)
        rule.onNodeWithTag("album").performTouchInput {
            down(first); advanceEventTime(700); moveTo(third, 160); up()
        }
        rule.runOnIdle { assertEquals(setOf("img:1", "img:2", "img:3"), selected.value) }
        rule.onNodeWithTag("album").performTouchInput {
            down(third); advanceEventTime(700); moveTo(fifth, 160); moveTo(first, 160); up()
        }
        rule.runOnIdle { assertEquals(setOf("img:1", "img:2", "img:3"), selected.value) }
    }

    @Test fun longPressWithoutMovingSelectsOnceAndTapStillToggles() {
        selected.value = emptySet()
        show()
        rule.onNodeWithContentDescription("photo-1").performTouchInput { longClick() }
        rule.runOnIdle { assertEquals(setOf("img:1"), selected.value) }
        rule.onNodeWithContentDescription("photo-1").performTouchInput { click() }
        rule.runOnIdle { assertTrue(selected.value.isEmpty()) }
    }

    @Test fun heldFingerScrollsAtEdgeAndReleaseStopsSelecting() {
        show()
        val start = point(1)
        val bounds = rule.onNodeWithTag("album").fetchSemanticsNode().boundsInRoot
        val edge = Offset(start.x, bounds.height - 4f)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("album").performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(edge, 160)
        }
        rule.mainClock.advanceTimeBy(1600)
        rule.runOnIdle {
            assertTrue("edge drag should select beyond the original viewport", "img:24" in selected.value)
        }
        rule.onNodeWithTag("album").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(32)
        val afterRelease = selected.value
        val visibleAfterRelease = rule.onAllNodes(hasContentDescription("photo-", substring = true))
            .fetchSemanticsNodes().map { it.id to it.boundsInRoot }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle { assertEquals(afterRelease, selected.value) }
        assertEquals(visibleAfterRelease, rule.onAllNodes(hasContentDescription("photo-", substring = true))
            .fetchSemanticsNodes().map { it.id to it.boundsInRoot })
    }
}
