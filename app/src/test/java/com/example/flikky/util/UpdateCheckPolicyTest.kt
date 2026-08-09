package com.example.flikky.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun autoCheck_requires_toggle_on() {
        assertFalse(
            UpdateCheckPolicy.shouldAutoCheck(
                enabled = false,
                lastCheckAtMs = 0L,
                nowMs = day * 10,
            ),
        )
        assertTrue(
            UpdateCheckPolicy.shouldAutoCheck(
                enabled = true,
                lastCheckAtMs = 0L,
                nowMs = day * 10,
            ),
        )
    }

    @Test
    fun autoCheck_throttles_within_24h() {
        val now = day * 10
        assertFalse(UpdateCheckPolicy.shouldAutoCheck(true, now - day + 1, now))
        assertTrue(UpdateCheckPolicy.shouldAutoCheck(true, now - day, now))
    }

    @Test
    fun autoCheck_recovers_from_future_timestamp() {
        val now = day * 10
        assertTrue(UpdateCheckPolicy.shouldAutoCheck(true, now + day * 5, now))
    }

    @Test
    fun autoPrompt_once_per_version() {
        assertTrue(UpdateCheckPolicy.shouldAutoPrompt("v1.18.0", lastPromptedTag = null))
        assertTrue(UpdateCheckPolicy.shouldAutoPrompt("v1.18.0", lastPromptedTag = "v1.17.0"))
        assertFalse(UpdateCheckPolicy.shouldAutoPrompt("v1.18.0", lastPromptedTag = "v1.18.0"))
    }
}
