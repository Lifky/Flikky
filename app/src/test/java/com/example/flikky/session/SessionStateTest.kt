package com.example.flikky.session

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStateTest {
    @Test
    fun `initial snapshot has empty messages and disconnected client`() = runTest {
        val state = SessionState(nowMs = { 1_000L })
        val snap = state.snapshot.value
        assertTrue(snap.messages.isEmpty())
        assertFalse(snap.clientConnected)
        assertEquals(1_000L, snap.serviceStartedAt)
    }

    @Test
    fun `addMessage appends and emits new snapshot`() = runTest {
        val state = SessionState(nowMs = { 0L })
        state.snapshot.test {
            awaitItem()
            state.addMessage(Message.Text(id = 1, origin = Origin.BROWSER, timestamp = 0, content = "hi"))
            val next = awaitItem()
            assertEquals(1, next.messages.size)
            assertEquals("hi", (next.messages.first() as Message.Text).content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setClientConnected toggles flag`() = runTest {
        val state = SessionState(nowMs = { 0L })
        state.setClientConnected(true)
        assertTrue(state.snapshot.value.clientConnected)
        state.setClientConnected(false)
        assertFalse(state.snapshot.value.clientConnected)
    }
}
