package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SessionRepositoryFileDeleteTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: FlikkyDatabase
    private lateinit var repo: SessionRepository
    private lateinit var store: SessionFileStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlikkyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = SessionFileStore(filesDir = tmp.root)
        repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            groupDao = db.groupDao(),
            fileStore = store,
            now = { 1_000L },
            retainLimitProvider = { 20 },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedFileMessage(
        sessionId: Long,
        messageId: Long,
        fileId: String,
    ): File {
        repo.appendMessage(
            sessionId,
            Message.File(
                id = messageId,
                origin = Origin.BROWSER,
                timestamp = 1L,
                fileId = fileId,
                name = "a.bin",
                sizeBytes = 3L,
                mime = "application/octet-stream",
                status = Message.File.Status.COMPLETED,
            ),
        )
        return store.archiveFromStream(sessionId, fileId, "abc".byteInputStream())
    }

    @Test
    fun `deleteFileBlob removes disk file then marks DELETED`() = runTest {
        val sessionId = repo.beginSession("s", startedAt = 0L)
        val file = seedFileMessage(sessionId, messageId = 1L, fileId = "f1")

        assertTrue(repo.deleteFileBlob(sessionId, 1L, "f1"))
        assertFalse(file.exists())
        assertEquals("DELETED", db.messageDao().getById(1L)?.fileStatus)
    }

    @Test
    fun `deleteFileBlob marks DELETED when blob is already missing`() = runTest {
        val sessionId = repo.beginSession("s", startedAt = 0L)
        seedFileMessage(sessionId, 1L, "f1").delete()

        assertTrue(repo.deleteFileBlob(sessionId, 1L, "f1"))
        assertEquals("DELETED", db.messageDao().getById(1L)?.fileStatus)
    }

    @Test
    fun `deleteFileBlob leaves status unchanged when disk delete fails`() = runTest {
        val sessionId = repo.beginSession("s", startedAt = 0L)
        seedFileMessage(sessionId, 1L, "f1")
        val blob = File(store.fileDir(sessionId), "f1")
        blob.delete()
        blob.mkdirs()
        File(blob, "child").writeText("x")

        assertFalse(repo.deleteFileBlob(sessionId, 1L, "f1"))
        assertEquals("COMPLETED", db.messageDao().getById(1L)?.fileStatus)
    }

    @Test
    fun `overview deletion leaves session aggregates unchanged`() = runTest {
        val sessionId = repo.beginSession("s", startedAt = 0L)
        seedFileMessage(sessionId, 1L, "f1")
        repo.endSession(sessionId, endedAt = 5L)
        val before = requireNotNull(db.sessionDao().getById(sessionId))

        repo.deleteFileBlob(sessionId, 1L, "f1")

        val after = requireNotNull(db.sessionDao().getById(sessionId))
        assertEquals(before.fileCount, after.fileCount)
        assertEquals(before.totalBytes, after.totalBytes)
    }
}
