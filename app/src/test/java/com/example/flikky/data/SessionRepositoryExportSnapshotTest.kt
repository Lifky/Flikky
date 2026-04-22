package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.export.MessageExport
import com.example.flikky.session.Origin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryExportSnapshotTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: FlikkyDatabase
    private lateinit var repo: SessionRepository
    private lateinit var store: SessionFileStore

    private var clock = 5_000L
    private val now: () -> Long = { clock }

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java)
            .allowMainThreadQueries().build()
        store = SessionFileStore(filesDir = tmp.root)
        repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            fileStore = store,
            now = now,
            retainLimit = 20,
        )
    }

    @After fun tearDown() { db.close() }

    private suspend fun insertSession(
        startedAt: Long,
        endedAt: Long?,
        name: String = "s",
        pinned: Boolean = false,
    ): Long = db.sessionDao().insert(SessionEntity(
        startedAt = startedAt, endedAt = endedAt, name = name, pinned = pinned,
    ))

    private suspend fun insertText(
        sessionId: Long, id: Long, ts: Long, content: String, origin: String = "PHONE",
    ) = db.messageDao().insert(MessageEntity(
        id = id, sessionId = sessionId, origin = origin, timestamp = ts,
        kind = "TEXT", content = content,
    ))

    private suspend fun insertFile(
        sessionId: Long, id: Long, ts: Long,
        fileId: String, fileName: String, size: Long, mime: String,
        origin: String = "BROWSER", status: String = "COMPLETED",
    ) = db.messageDao().insert(MessageEntity(
        id = id, sessionId = sessionId, origin = origin, timestamp = ts,
        kind = "FILE",
        fileId = fileId, fileName = fileName, fileSize = size,
        fileMime = mime, fileStatus = status,
    ))

    @Test fun empty_ids_returns_empty_sessions_with_now() = runTest {
        clock = 9_999L
        val snap = repo.exportSnapshot(emptyList())
        assertEquals(emptyList<Any>(), snap.sessions)
        assertEquals(9_999L, snap.exportedAt)
    }

    @Test fun finished_session_with_text_and_file_maps_in_ts_order() = runTest {
        val sid = insertSession(startedAt = 100L, endedAt = 1_000L, name = "demo", pinned = true)
        // Insert out of order to exercise DAO ordering.
        insertFile(sid, id = 2L, ts = 500L,
            fileId = "fA", fileName = "a.bin", size = 1_234L, mime = "application/octet-stream")
        insertText(sid, id = 1L, ts = 200L, content = "hello")

        clock = 7_777L
        val snap = repo.exportSnapshot(listOf(sid))

        assertEquals(7_777L, snap.exportedAt)
        assertEquals(1, snap.sessions.size)
        val s = snap.sessions.first()
        assertEquals(sid, s.id)
        assertEquals("demo", s.name)
        assertEquals(100L, s.startedAt)
        assertEquals(1_000L, s.endedAt)
        assertTrue(s.pinned)
        assertEquals(2, s.messages.size)

        val m0 = s.messages[0] as MessageExport.Text
        assertEquals(200L, m0.ts)
        assertEquals(Origin.PHONE, m0.origin)
        assertEquals("hello", m0.content)

        val m1 = s.messages[1] as MessageExport.File
        assertEquals(500L, m1.ts)
        assertEquals(Origin.BROWSER, m1.origin)
        assertEquals("fA", m1.fileId)
        assertEquals("a.bin", m1.name)
        assertEquals("application/octet-stream", m1.mime)
        assertEquals(1_234L, m1.sizeBytes)
    }

    @Test fun missing_id_is_silently_skipped() = runTest {
        val snap = repo.exportSnapshot(listOf(424242L))
        assertEquals(emptyList<Any>(), snap.sessions)
        assertNotNull(snap.exportedAt)
    }

    @Test fun in_progress_session_skipped() = runTest {
        val live = insertSession(startedAt = 100L, endedAt = null, name = "live")
        insertText(live, id = 1L, ts = 150L, content = "wip")

        val snap = repo.exportSnapshot(listOf(live))
        assertEquals(emptyList<Any>(), snap.sessions)
    }

    @Test fun mix_of_done_live_and_missing_returns_only_done() = runTest {
        val done = insertSession(startedAt = 100L, endedAt = 300L, name = "done")
        insertText(done, id = 10L, ts = 150L, content = "d1")
        val live = insertSession(startedAt = 400L, endedAt = null, name = "live")
        insertText(live, id = 11L, ts = 450L, content = "l1")

        clock = 12_345L
        val snap = repo.exportSnapshot(listOf(done, live, 777_777L))

        assertEquals(1, snap.sessions.size)
        assertEquals(done, snap.sessions[0].id)
        assertEquals("done", snap.sessions[0].name)
        assertEquals(12_345L, snap.exportedAt)
    }

    @Test fun finished_session_with_no_messages_gets_empty_list() = runTest {
        val sid = insertSession(startedAt = 1L, endedAt = 2L, name = "empty-but-done")
        val snap = repo.exportSnapshot(listOf(sid))
        assertEquals(1, snap.sessions.size)
        assertEquals(emptyList<MessageExport>(), snap.sessions[0].messages)
    }

    @Test fun exportedAt_uses_injected_now() = runTest {
        val sid = insertSession(startedAt = 1L, endedAt = 2L)

        clock = 111L
        assertEquals(111L, repo.exportSnapshot(listOf(sid)).exportedAt)

        clock = 222L
        assertEquals(222L, repo.exportSnapshot(listOf(sid)).exportedAt)
    }
}
