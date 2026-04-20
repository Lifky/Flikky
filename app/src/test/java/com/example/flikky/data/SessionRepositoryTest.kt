package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import kotlinx.coroutines.test.runTest
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
}
