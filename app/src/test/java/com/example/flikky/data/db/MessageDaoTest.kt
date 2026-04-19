package com.example.flikky.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MessageDaoTest {
    private lateinit var db: FlikkyDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var dao: MessageDao
    private var sid: Long = 0

    @Before fun setup() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java)
            .allowMainThreadQueries().build()
        sessionDao = db.sessionDao()
        dao = db.messageDao()
        sid = sessionDao.insert(SessionEntity(startedAt = 0L, endedAt = null, name = "s"))
    }

    @After fun tearDown() { db.close() }

    private fun text(id: Long, t: Long, content: String) = MessageEntity(
        id = id, sessionId = sid, origin = "PHONE", timestamp = t,
        kind = "TEXT", content = content,
    )

    @Test fun observeBySession_orders_by_timestamp_asc() = runTest {
        dao.insert(text(id = 3L, t = 300L, content = "c"))
        dao.insert(text(id = 1L, t = 100L, content = "a"))
        dao.insert(text(id = 2L, t = 200L, content = "b"))
        dao.observeBySession(sid).test {
            val got = awaitItem().map { it.content }
            assertEquals(listOf("a", "b", "c"), got)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun firstTextContent_returns_earliest_text() = runTest {
        dao.insert(MessageEntity(id = 10L, sessionId = sid, origin = "PHONE",
            timestamp = 500L, kind = "FILE", fileId = "f1", fileName = "a.bin"))
        dao.insert(text(id = 11L, t = 100L, content = "hello"))
        dao.insert(text(id = 12L, t = 200L, content = "world"))
        assertEquals("hello", dao.firstTextContent(sid))
    }

    @Test fun firstTextContent_returns_null_when_no_text() = runTest {
        dao.insert(MessageEntity(id = 20L, sessionId = sid, origin = "BROWSER",
            timestamp = 10L, kind = "FILE", fileId = "z"))
        assertNull(dao.firstTextContent(sid))
    }

    @Test fun cascade_deletes_messages_when_session_deleted() = runTest {
        dao.insert(text(id = 1L, t = 10L, content = "a"))
        sessionDao.delete(sessionDao.getById(sid)!!)
        assertEquals(0, dao.countBySession(sid))
    }
}
