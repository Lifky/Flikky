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

    @Test
    fun `updateBoundPort writes into snapshot and startNew clears it`() {
        val state = SessionState(nowMs = { 0L })
        assertEquals(0, state.snapshot.value.boundPort)

        state.updateBoundPort(8091)
        assertEquals(8091, state.snapshot.value.boundPort)

        state.startNew(sessionId = 7L)
        assertEquals(0, state.snapshot.value.boundPort)

        state.updateBoundPort(8083)
        assertEquals(8083, state.snapshot.value.boundPort)

        state.reset()
        assertEquals(0, state.snapshot.value.boundPort)
    }

    @Test
    fun `default networkStatus is Ok`() {
        val state = SessionState(nowMs = { 0L })
        assertEquals(NetworkStatus.Ok, state.snapshot.value.networkStatus)
    }

    @Test
    fun `updateNetworkStatus walks Switching-Switched and acknowledgeNetworkSwitch returns to Ok`() {
        val state = SessionState(nowMs = { 0L })

        state.updateNetworkStatus(NetworkStatus.Switching)
        assertEquals(NetworkStatus.Switching, state.snapshot.value.networkStatus)

        val switched = NetworkStatus.Switched("http://192.168.2.10:8081")
        state.updateNetworkStatus(switched)
        assertEquals(switched, state.snapshot.value.networkStatus)

        state.acknowledgeNetworkSwitch()
        assertEquals(NetworkStatus.Ok, state.snapshot.value.networkStatus)
    }

    @Test
    fun `networkStatus Lost clears back to Ok via reset and startNew`() {
        val state = SessionState(nowMs = { 0L })
        state.updateNetworkStatus(NetworkStatus.Lost)
        assertEquals(NetworkStatus.Lost, state.snapshot.value.networkStatus)

        state.startNew(sessionId = 42L)
        assertEquals(NetworkStatus.Ok, state.snapshot.value.networkStatus)

        state.updateNetworkStatus(NetworkStatus.Lost)
        state.reset()
        assertEquals(NetworkStatus.Ok, state.snapshot.value.networkStatus)
    }
}
