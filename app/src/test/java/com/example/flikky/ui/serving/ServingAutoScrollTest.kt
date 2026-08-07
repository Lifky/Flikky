package com.example.flikky.ui.serving

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServingAutoScrollTest {
    @Test
    fun `scrolls when messages are appended`() {
        assertTrue(shouldAutoScrollToLatestMessage(previousMessageCount = 12, currentMessageCount = 13))
    }

    @Test
    fun `does not scroll when a middle message is restored`() {
        assertFalse(
            shouldAutoScrollToLatestMessage(
                previousMessageCount = 12,
                currentMessageCount = 13,
                previousLastMessageId = 42L,
                currentLastMessageId = 42L,
            ),
        )
    }

    @Test
    fun `does not scroll when messages are removed`() {
        assertFalse(shouldAutoScrollToLatestMessage(previousMessageCount = 12, currentMessageCount = 11))
    }

    @Test
    fun `does not scroll when message count is unchanged`() {
        assertFalse(shouldAutoScrollToLatestMessage(previousMessageCount = 12, currentMessageCount = 12))
    }

    @Test
    fun `keeps latest message visible through consecutive viewport shrink steps`() {
        assertTrue(
            shouldKeepLatestMessageVisibleAfterViewportResize(
                previousViewportHeight = 1200,
                currentViewportHeight = 1000,
                wasAtBottomBeforeResize = true,
                currentMessageCount = 12,
            ),
        )
        assertTrue(
            shouldKeepLatestMessageVisibleAfterViewportResize(
                previousViewportHeight = 1000,
                currentViewportHeight = 800,
                wasAtBottomBeforeResize = true,
                currentMessageCount = 12,
            ),
        )
    }

    @Test
    fun `does not move viewport when user was away from bottom before resize`() {
        assertFalse(
            shouldKeepLatestMessageVisibleAfterViewportResize(
                previousViewportHeight = 1200,
                currentViewportHeight = 800,
                wasAtBottomBeforeResize = false,
                currentMessageCount = 12,
            ),
        )
    }

    @Test
    fun `does not scroll when viewport height is unchanged`() {
        assertFalse(
            shouldKeepLatestMessageVisibleAfterViewportResize(
                previousViewportHeight = 800,
                currentViewportHeight = 800,
                wasAtBottomBeforeResize = true,
                currentMessageCount = 12,
            ),
        )
    }

    @Test
    fun `does not scroll when viewport grows`() {
        assertFalse(
            shouldKeepLatestMessageVisibleAfterViewportResize(
                previousViewportHeight = 800,
                currentViewportHeight = 1200,
                wasAtBottomBeforeResize = true,
                currentMessageCount = 12,
            ),
        )
    }

    @Test
    fun `does not scroll before viewport has an initial height`() {
        assertFalse(
            shouldKeepLatestMessageVisibleAfterViewportResize(
                previousViewportHeight = 0,
                currentViewportHeight = 1200,
                wasAtBottomBeforeResize = true,
                currentMessageCount = 12,
            ),
        )
    }

    @Test
    fun `does not scroll after viewport resize without messages`() {
        assertFalse(
            shouldKeepLatestMessageVisibleAfterViewportResize(
                previousViewportHeight = 1200,
                currentViewportHeight = 800,
                wasAtBottomBeforeResize = true,
                currentMessageCount = 0,
            ),
        )
    }
}
