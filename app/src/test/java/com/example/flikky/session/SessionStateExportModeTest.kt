package com.example.flikky.session

import app.cash.turbine.test
import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStateExportModeTest {

    private fun newState(now: Long = 0L) = SessionState(nowMs = { now })

    private fun sampleSession(
        ids: List<Long> = listOf(1L, 2L),
        pin: String = "123456",
        createdAt: Long = 1_000L,
    ) = ExportSession(sessionIds = ids, pin = pin, createdAt = createdAt)

    private fun sampleSnapshot(exportedAt: Long = 2_000L) =
        ExportSnapshot(sessions = emptyList(), exportedAt = exportedAt)

    @Test
    fun `initial exportMode is Idle`() {
        val state = newState()
        assertTrue(state.exportMode.value is ExportMode.Idle)
    }

    @Test
    fun `armExport from Idle emits Armed with session and snapshot`() = runTest {
        val state = newState()
        val session = sampleSession()
        val snapshot = sampleSnapshot()
        state.exportMode.test {
            assertTrue(awaitItem() is ExportMode.Idle)
            state.armExport(session, snapshot)
            val next = awaitItem()
            assertTrue(next is ExportMode.Armed)
            next as ExportMode.Armed
            assertSame(session, next.session)
            assertSame(snapshot, next.snapshot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `armExport twice without clear throws`() {
        val state = newState()
        state.armExport(sampleSession(), sampleSnapshot())
        try {
            state.armExport(sampleSession(pin = "999999"), sampleSnapshot())
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            // OK
        }
    }

    @Test
    fun `updateExportProgress from Armed transitions to Sending with bytes`() {
        val state = newState()
        val session = sampleSession()
        state.armExport(session, sampleSnapshot())
        state.updateExportProgress(bytesSent = 512L, totalBytes = 2_048L)
        val mode = state.exportMode.value
        assertTrue(mode is ExportMode.Sending)
        mode as ExportMode.Sending
        assertSame(session, mode.session)
        assertEquals(512L, mode.bytesSent)
        assertEquals(2_048L, mode.totalBytes)
    }

    @Test
    fun `updateExportProgress from Sending updates bytes keeping session`() {
        val state = newState()
        val session = sampleSession()
        state.armExport(session, sampleSnapshot())
        state.updateExportProgress(100L, 2_048L)
        state.updateExportProgress(1_024L, 2_048L)
        val mode = state.exportMode.value as ExportMode.Sending
        assertSame(session, mode.session)
        assertEquals(1_024L, mode.bytesSent)
        assertEquals(2_048L, mode.totalBytes)
    }

    @Test
    fun `updateExportProgress from Idle throws`() {
        val state = newState()
        try {
            state.updateExportProgress(10L, 100L)
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            // OK
        }
    }

    @Test
    fun `markExportDone from Sending transitions to Done with session`() {
        val state = newState()
        val session = sampleSession()
        state.armExport(session, sampleSnapshot())
        state.updateExportProgress(2_048L, 2_048L)
        state.markExportDone()
        val mode = state.exportMode.value
        assertTrue(mode is ExportMode.Done)
        mode as ExportMode.Done
        assertSame(session, mode.session)
    }

    @Test
    fun `markExportDone from Armed throws`() {
        val state = newState()
        state.armExport(sampleSession(), sampleSnapshot())
        try {
            state.markExportDone()
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            // OK
        }
    }

    @Test
    fun `markExportDone from Idle throws`() {
        val state = newState()
        try {
            state.markExportDone()
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            // OK
        }
    }

    @Test
    fun `clearExport from Done returns to Idle`() {
        val state = newState()
        state.armExport(sampleSession(), sampleSnapshot())
        state.updateExportProgress(10L, 10L)
        state.markExportDone()
        state.clearExport()
        assertTrue(state.exportMode.value is ExportMode.Idle)
    }

    @Test
    fun `clearExport from Idle is idempotent and does not throw`() {
        val state = newState()
        state.clearExport()
        state.clearExport()
        assertTrue(state.exportMode.value is ExportMode.Idle)
    }

    @Test
    fun `full lifecycle can repeat after clear`() {
        val state = newState()
        val first = sampleSession(pin = "111111")
        state.armExport(first, sampleSnapshot())
        state.updateExportProgress(1L, 10L)
        state.markExportDone()
        state.clearExport()
        assertTrue(state.exportMode.value is ExportMode.Idle)

        val second = sampleSession(pin = "222222", createdAt = 9_000L)
        state.armExport(second, sampleSnapshot(exportedAt = 9_500L))
        val armed = state.exportMode.value as ExportMode.Armed
        assertSame(second, armed.session)
        state.updateExportProgress(5L, 10L)
        state.markExportDone()
        val done = state.exportMode.value as ExportMode.Done
        assertSame(second, done.session)
    }
}
