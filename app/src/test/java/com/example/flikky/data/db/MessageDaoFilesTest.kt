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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MessageDaoFilesTest {
    private lateinit var db: FlikkyDatabase
    private var sidEnded = 0L
    private var sidLive = 0L

    private fun file(
        id: Long,
        sid: Long,
        ts: Long,
        status: String?,
        mime: String = "image/png",
    ) = MessageEntity(
        id = id,
        sessionId = sid,
        origin = "BROWSER",
        timestamp = ts,
        kind = "FILE",
        fileId = "f$id",
        fileName = "n$id.png",
        fileSize = 100L,
        fileMime = mime,
        fileStatus = status,
    )

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlikkyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sidEnded = db.sessionDao().insert(
            SessionEntity(startedAt = 0L, endedAt = 9L, name = "ended"),
        )
        sidLive = db.sessionDao().insert(
            SessionEntity(startedAt = 0L, endedAt = null, name = "live"),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeAllFiles returns complete file rows with session join newest first`() = runTest {
        val dao = db.messageDao()
        dao.insert(file(1, sidEnded, ts = 10, status = "COMPLETED"))
        dao.insert(file(2, sidEnded, ts = 20, status = null))
        dao.insert(file(3, sidEnded, ts = 30, status = "DELETED"))
        dao.insert(file(4, sidEnded, ts = 40, status = "FAILED"))
        dao.insert(file(5, sidLive, ts = 50, status = "COMPLETED"))
        dao.insert(
            MessageEntity(
                id = 6,
                sessionId = sidEnded,
                origin = "PHONE",
                timestamp = 60,
                kind = "TEXT",
                content = "x",
            ),
        )

        dao.observeAllFiles().test {
            val rows = awaitItem()
            assertEquals(listOf(5L, 2L, 1L), rows.map { it.messageId })
            assertEquals("live", rows[0].sessionName)
            assertEquals(null as Long?, rows[0].sessionEndedAt)
            assertEquals(9L as Long?, rows[1].sessionEndedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markFileDeleted removes row from overview flow but keeps size`() = runTest {
        val dao = db.messageDao()
        dao.insert(file(1, sidEnded, ts = 10, status = "COMPLETED"))

        dao.observeAllFiles().test {
            assertEquals(1, awaitItem().size)
            dao.markFileDeleted(1L)
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("DELETED", dao.getById(1L)?.fileStatus)
        assertEquals(100L, dao.getById(1L)?.fileSize)
    }
}
