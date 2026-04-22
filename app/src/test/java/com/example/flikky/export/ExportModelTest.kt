package com.example.flikky.export

import com.example.flikky.session.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportModelTest {

    @Test
    fun `ExportSession defaults consumed to false`() {
        val session = ExportSession(
            sessionIds = listOf(1L, 2L),
            pin = "123456",
            createdAt = 1_000L,
        )
        assertFalse(session.consumed)
        assertEquals(listOf(1L, 2L), session.sessionIds)
        assertEquals("123456", session.pin)
    }

    @Test
    fun `ExportSnapshot holds sessions with text and file messages`() {
        val text = MessageExport.Text(
            ts = 100L,
            origin = Origin.PHONE,
            content = "hello",
        )
        val file = MessageExport.File(
            ts = 200L,
            origin = Origin.BROWSER,
            fileId = "f1",
            name = "a.txt",
            mime = "text/plain",
            sizeBytes = 42L,
        )
        val session = SessionExport(
            id = 7L,
            name = "Session 7",
            startedAt = 50L,
            endedAt = null,
            pinned = false,
            messages = listOf(text, file),
        )
        val snapshot = ExportSnapshot(
            sessions = listOf(session),
            exportedAt = 999L,
        )

        assertEquals(1, snapshot.sessions.size)
        assertEquals(2, snapshot.sessions[0].messages.size)
        assertNull(snapshot.sessions[0].endedAt)
        assertEquals(Origin.PHONE, (snapshot.sessions[0].messages[0] as MessageExport.Text).origin)
        assertEquals("a.txt", (snapshot.sessions[0].messages[1] as MessageExport.File).name)
    }

    @Test
    fun `MessageExport equality follows data class semantics`() {
        val a = MessageExport.Text(ts = 1L, origin = Origin.PHONE, content = "x")
        val b = MessageExport.Text(ts = 1L, origin = Origin.PHONE, content = "x")
        val c = MessageExport.Text(ts = 1L, origin = Origin.PHONE, content = "y")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `ExportMode states can be constructed and transitioned`() {
        val exportSession = ExportSession(
            sessionIds = listOf(1L),
            pin = "000000",
            createdAt = 0L,
        )
        val snapshot = ExportSnapshot(sessions = emptyList(), exportedAt = 0L)

        val idle: ExportMode = ExportMode.Idle
        val armed: ExportMode = ExportMode.Armed(exportSession, snapshot)
        val sending: ExportMode = ExportMode.Sending(exportSession, bytesSent = 10L, totalBytes = 100L)
        val done: ExportMode = ExportMode.Done(exportSession)

        assertTrue(idle is ExportMode.Idle)
        assertTrue(armed is ExportMode.Armed)
        assertEquals(snapshot, (armed as ExportMode.Armed).snapshot)
        assertEquals(10L, (sending as ExportMode.Sending).bytesSent)
        assertEquals(exportSession, (done as ExportMode.Done).session)
    }
}
