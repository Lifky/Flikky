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
}
