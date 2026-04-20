package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import kotlinx.coroutines.test.runTest
import app.cash.turbine.test
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: FlikkyDatabase
    private lateinit var repo: SessionRepository
    private lateinit var store: SessionFileStore

    private var clock = 1_000L
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

    @Test fun begin_append_end_aggregates_stats_and_preview() = runTest {
        clock = 1_000L
        val sid = repo.beginSession("04-18 14:30 会话", startedAt = clock)

        repo.appendMessage(sid, Message.Text(
            id = 1, origin = Origin.BROWSER, timestamp = 1_100L, content = "hello world",
        ))
        repo.appendMessage(sid, Message.File(
            id = 2, origin = Origin.PHONE, timestamp = 1_200L,
            fileId = "f1", name = "a.bin", sizeBytes = 1_000L,
            mime = "application/octet-stream",
            status = Message.File.Status.COMPLETED,
        ))
        clock = 2_000L
        repo.endSession(sid, endedAt = clock)

        val row = db.sessionDao().getById(sid)!!
        assertEquals(2_000L, row.endedAt)
        assertEquals(2, row.messageCount)
        assertEquals(1, row.fileCount)
        assertEquals(1_000L, row.totalBytes)
        assertEquals("hello world", row.previewText)
    }

    @Test fun endSession_deletes_row_when_empty() = runTest {
        val sid = repo.beginSession("empty", startedAt = 100L)
        repo.endSession(sid, endedAt = 200L)
        val row = db.sessionDao().getById(sid)
        org.junit.Assert.assertNull(row)
    }

    @Test fun endSession_empty_removes_session_dir() = runTest {
        val sid = repo.beginSession("empty", startedAt = 100L)
        store.fileDir(sid)
        org.junit.Assert.assertTrue(java.io.File(tmp.root, "sessions/$sid").exists())
        repo.endSession(sid, endedAt = 200L)
        org.junit.Assert.assertTrue(!java.io.File(tmp.root, "sessions/$sid").exists())
    }

    @Test fun fifoSweep_keeps_20_non_pinned_plus_all_pinned() = runTest {
        val ids = (1..25).map { i ->
            db.sessionDao().insert(
                com.example.flikky.data.db.entities.SessionEntity(
                    startedAt = i * 100L,
                    endedAt   = i * 100L + 10L,
                    name      = "s$i",
                    pinned    = (i in listOf(3, 8, 15)),
                )
            )
        }
        ids.forEach { store.fileDir(it) }

        repo.fifoSweep()

        val remaining = db.sessionDao().nonPinnedOldestFirst().map { it.name }
        org.junit.Assert.assertFalse(remaining.contains("s1"))
        org.junit.Assert.assertFalse(remaining.contains("s2"))
        org.junit.Assert.assertEquals(20, remaining.size)

        db.sessionDao().observeAll().test {
            val rows = awaitItem()
            org.junit.Assert.assertEquals(23, rows.size)
            cancelAndIgnoreRemainingEvents()
        }

        org.junit.Assert.assertTrue(!java.io.File(tmp.root, "sessions/${ids[0]}").exists())
        org.junit.Assert.assertTrue(!java.io.File(tmp.root, "sessions/${ids[1]}").exists())
    }

    @Test fun fifoSweep_ignores_unfinished_sessions() = runTest {
        db.sessionDao().insert(
            com.example.flikky.data.db.entities.SessionEntity(
                startedAt = 100L, endedAt = 200L, name = "done"))
        val liveId = db.sessionDao().insert(
            com.example.flikky.data.db.entities.SessionEntity(
                startedAt = 50L, endedAt = null, name = "live"))
        val aggressive = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            fileStore = store,
            now = now, retainLimit = 0,
        )
        aggressive.fifoSweep()
        org.junit.Assert.assertNotNull(db.sessionDao().getById(liveId))
    }
}
