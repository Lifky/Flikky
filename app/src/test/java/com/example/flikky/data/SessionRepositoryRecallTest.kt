package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 覆盖 SessionRepository.recallMessage 的 D26 行为矩阵：
 * NotFound / Denied (senderId 不匹配 / senderId 为 null) / Success / AlreadyRecalled，
 * 以及 FILE 撤回时的删盘 + 聚合更新。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryRecallTest {
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
        name: String = "s",
        fileCount: Int = 0,
        totalBytes: Long = 0L,
    ): Long = db.sessionDao().insert(SessionEntity(
        startedAt = 100L, endedAt = 200L, name = name,
        fileCount = fileCount, totalBytes = totalBytes,
    ))

    private suspend fun insertText(
        sessionId: Long, id: Long, content: String,
        senderId: String? = "sender-1", recalledAt: Long? = null, ts: Long = 150L,
    ) = db.messageDao().insert(MessageEntity(
        id = id, sessionId = sessionId, origin = "PHONE", timestamp = ts,
        kind = "TEXT", content = content, senderId = senderId, recalledAt = recalledAt,
    ))

    private suspend fun insertFile(
        sessionId: Long, id: Long, fileId: String, fileName: String, size: Long,
        senderId: String? = "sender-1", recalledAt: Long? = null, ts: Long = 150L,
    ) = db.messageDao().insert(MessageEntity(
        id = id, sessionId = sessionId, origin = "BROWSER", timestamp = ts,
        kind = "FILE",
        fileId = fileId, fileName = fileName, fileSize = size,
        fileMime = "application/octet-stream", fileStatus = "COMPLETED",
        senderId = senderId, recalledAt = recalledAt,
    ))

    @Test fun missing_message_returns_NotFound() = runTest {
        val outcome = repo.recallMessage(messageId = 99_999L, callerSenderId = "anyone")
        assertEquals(SessionRepository.RecallOutcome.NotFound, outcome)
    }

    @Test fun mismatched_senderId_returns_Denied_and_does_not_mutate() = runTest {
        val sid = insertSession()
        insertText(sid, id = 1L, content = "secret", senderId = "owner")

        val outcome = repo.recallMessage(messageId = 1L, callerSenderId = "intruder")

        assertEquals(SessionRepository.RecallOutcome.Denied, outcome)
        // 真删模型下：Denied 不动行；getById 仍能拿到原消息。
        assertNotNull("Denied must not delete the row", db.messageDao().getById(1L))
    }

    @Test fun null_senderId_legacy_message_cannot_be_recalled() = runTest {
        // pre-v1.3 行的 senderId 为 null：任何调用方都不能撤回（D26 严格匹配）。
        val sid = insertSession()
        insertText(sid, id = 1L, content = "legacy", senderId = null)

        val outcome = repo.recallMessage(messageId = 1L, callerSenderId = "sender-1")

        assertEquals(SessionRepository.RecallOutcome.Denied, outcome)
        assertNotNull(db.messageDao().getById(1L))
    }

    @Test fun text_message_recall_hard_deletes_row() = runTest {
        // v1.3 D26 修订：Success = 真删行（不再写 recalledAt 标记）。
        val sid = insertSession()
        insertText(sid, id = 1L, content = "hi", senderId = "sender-1")

        val outcome = repo.recallMessage(messageId = 1L, callerSenderId = "sender-1")

        assertTrue(outcome is SessionRepository.RecallOutcome.Success)
        val success = outcome as SessionRepository.RecallOutcome.Success
        assertEquals(1L, success.messageId)
        assertEquals(sid, success.sessionId)

        assertNull("row should be hard-deleted", db.messageDao().getById(1L))
    }

    @Test fun second_recall_after_hard_delete_returns_NotFound() = runTest {
        // 真删后再撤等价 idempotent：行已不存在 → NotFound。上层把它当成功处理
        // （消息节点已经移除了，没什么需要做的）。
        val sid = insertSession()
        insertText(sid, id = 1L, content = "x", senderId = "sender-1")

        val first = repo.recallMessage(messageId = 1L, callerSenderId = "sender-1")
        assertTrue(first is SessionRepository.RecallOutcome.Success)

        val second = repo.recallMessage(messageId = 1L, callerSenderId = "sender-1")
        assertEquals(SessionRepository.RecallOutcome.NotFound, second)
    }

    @Test fun file_message_recall_deletes_row_and_file_and_decrements_aggregates() = runTest {
        val sid = insertSession(fileCount = 1, totalBytes = 1_234L)
        insertFile(sid, id = 1L, fileId = "fA", fileName = "a.bin", size = 1_234L)

        // 落盘真实文件，验证删除后不存在。
        val onDisk = File(store.fileDir(sid), "fA")
        onDisk.writeBytes(ByteArray(1_234))
        assertTrue("setup: file must exist", onDisk.exists())

        val outcome = repo.recallMessage(messageId = 1L, callerSenderId = "sender-1")

        assertTrue(outcome is SessionRepository.RecallOutcome.Success)
        // 行真删
        assertNull("row should be hard-deleted", db.messageDao().getById(1L))
        // 文件已被删除
        assertFalse("file should be deleted on recall", onDisk.exists())
        // 聚合字段减一减字节
        val sessionAfter = db.sessionDao().getById(sid)!!
        assertEquals(0, sessionAfter.fileCount)
        assertEquals(0L, sessionAfter.totalBytes)
    }

    @Test fun file_recall_aggregates_clamp_at_zero_when_underflow() = runTest {
        // 防御：万一 session 聚合字段已经为 0（理论上不该，但 retainLimit / 历史脏数据可能产生），
        // 不应回到负值。
        val sid = insertSession(fileCount = 0, totalBytes = 0L)
        insertFile(sid, id = 1L, fileId = "fA", fileName = "a.bin", size = 9_999L)

        val outcome = repo.recallMessage(messageId = 1L, callerSenderId = "sender-1")
        assertTrue(outcome is SessionRepository.RecallOutcome.Success)

        val sessionAfter = db.sessionDao().getById(sid)!!
        assertEquals(0, sessionAfter.fileCount)
        assertEquals(0L, sessionAfter.totalBytes)
    }

    @Test fun file_recall_when_file_already_missing_still_succeeds() = runTest {
        // 文件已不存在（磁盘清理过/未落盘）：deleteMessageFile 幂等返回 true，撤回仍成功。
        val sid = insertSession(fileCount = 1, totalBytes = 500L)
        insertFile(sid, id = 1L, fileId = "ghost", fileName = "ghost.bin", size = 500L)

        val onDisk = File(store.fileDir(sid), "ghost")
        assertFalse("setup: file must not exist", onDisk.exists())

        val outcome = repo.recallMessage(messageId = 1L, callerSenderId = "sender-1")
        assertTrue(outcome is SessionRepository.RecallOutcome.Success)
        assertNull(db.messageDao().getById(1L))

        val sessionAfter = db.sessionDao().getById(sid)!!
        assertEquals(0, sessionAfter.fileCount)
        assertEquals(0L, sessionAfter.totalBytes)
    }
}
