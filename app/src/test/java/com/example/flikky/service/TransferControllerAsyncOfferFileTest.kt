package com.example.flikky.service

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.example.flikky.data.SessionFileStore
import com.example.flikky.data.SessionRepository
import com.example.flikky.server.routes.WsHub
import com.example.flikky.session.Message
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * v1.4.0 B7 回归保护：offerFile 异步化后的 IN_PROGRESS → COMPLETED / FAILED 流程。
 *
 * offerFile 同步部分立即建 IN_PROGRESS 消息 + 广播 file_added，再 scope.launch
 * 后台拷贝。后台用 Dispatchers.IO（硬编码），无法用虚拟时间控制，所以测试用真实
 * scope + 轮询 session 状态等待异步完成。
 */
class TransferControllerAsyncOfferFileTest {

    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var session: SessionState
    private lateinit var stats: TransferStats
    private lateinit var fileStore: SessionFileStore
    private lateinit var repository: SessionRepository
    private lateinit var hub: WsHub

    private val sid = 42L

    @Before fun setup() {
        session = SessionState(nowMs = { 1_000L }).apply { startNew(sessionId = sid) }
        stats = TransferStats(nowMs = { 1_000L })
        fileStore = SessionFileStore(filesDir = tmp.root)
        repository = mockk(relaxed = true)
        hub = mockk(relaxed = true)
    }

    @After fun tearDown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun controller() = TransferController(
        session = session,
        stats = stats,
        fileStore = fileStore,
        repository = repository,
        wsHub = { hub },
        nowMs = { 1_000L },
        senderId = "phone-test",
        scope = scope,
    )

    private fun mockResolver(name: String, size: Long, stream: () -> InputStream): ContentResolver {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 1
        every { cursor.getString(0) } returns name
        every { cursor.getLong(1) } returns size
        every { cursor.close() } returns Unit

        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        every { resolver.getType(any()) } returns "text/plain"
        every { resolver.openInputStream(any()) } answers { stream() }
        return resolver
    }

    private suspend fun awaitFileStatus(messageId: Long, expected: Message.File.Status) {
        withTimeout(5_000) {
            while (true) {
                val m = session.snapshot.value.messages
                    .filterIsInstance<Message.File>()
                    .firstOrNull { it.id == messageId }
                if (m?.status == expected) return@withTimeout
                delay(20)
            }
        }
    }

    @Test fun offerFile_creates_IN_PROGRESS_then_transitions_to_COMPLETED() = runBlocking {
        val payload = "hello flikky".toByteArray()
        val resolver = mockResolver("test.txt", payload.size.toLong()) {
            ByteArrayInputStream(payload)
        }
        val uri = mockk<Uri>(relaxed = true)

        controller().offerFile(uri, resolver)

        // Synchronous part: message added as IN_PROGRESS, file_added broadcast.
        val created = session.snapshot.value.messages
            .filterIsInstance<Message.File>().single()
        assertEquals(Message.File.Status.IN_PROGRESS, created.status)
        assertEquals("test.txt", created.name)
        assertEquals(1, stats.fileCount())
        coVerify(timeout = 2_000) { hub.broadcast("file_added", any()) }

        // Async part: copy completes → COMPLETED + file_ready.
        awaitFileStatus(created.id, Message.File.Status.COMPLETED)
        val done = session.snapshot.value.messages
            .filterIsInstance<Message.File>().single()
        assertEquals(payload.size.toLong(), done.sizeBytes)
        coVerify(timeout = 2_000) { hub.broadcast("file_ready", any()) }

        // File landed on disk under sessions/{sid}/files/{fileId}.
        val onDisk = File(fileStore.fileDir(sid), done.fileId)
        assertTrue("file should exist on disk", onDisk.exists())
        assertEquals(payload.size.toLong(), onDisk.length())
        // Progress map cleared after completion.
        assertTrue(session.fileTransferProgress.value[done.id] == null)
    }

    @Test fun offerFile_failure_marks_FAILED_and_broadcasts_file_removed() = runBlocking {
        // Stream that throws partway through the copy.
        val resolver = mockResolver("boom.bin", 1024L) {
            object : InputStream() {
                private var n = 0
                override fun read(): Int = throw java.io.IOException("simulated drop")
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    n++
                    if (n >= 1) throw java.io.IOException("simulated drop")
                    return -1
                }
            }
        }
        val uri = mockk<Uri>(relaxed = true)

        controller().offerFile(uri, resolver)

        val created = session.snapshot.value.messages
            .filterIsInstance<Message.File>().single()
        assertEquals(Message.File.Status.IN_PROGRESS, created.status)

        awaitFileStatus(created.id, Message.File.Status.FAILED)

        coVerify(timeout = 2_000) { hub.broadcast("file_removed", any()) }
        // fileCount incremented on add, decremented on failure → back to 0.
        assertEquals(0, stats.fileCount())
        coVerify(timeout = 2_000) { repository.deleteMessageAndFile(created.id, sid, any()) }
        assertTrue(session.fileTransferProgress.value[created.id] == null)
    }

    @Test fun offerStoredFile_uses_existing_file_as_phone_file_message() = runBlocking {
        val source = tmp.newFile("favorite.txt").apply { writeText("stored favorite") }

        val accepted = controller().offerStoredFile(
            source = source,
            name = "favorite.txt",
            size = source.length(),
            mime = "text/plain",
        )

        assertTrue(accepted)
        val created = session.snapshot.value.messages
            .filterIsInstance<Message.File>().single()
        assertEquals(Message.File.Status.IN_PROGRESS, created.status)
        assertEquals("favorite.txt", created.name)
        coVerify(timeout = 2_000) { hub.broadcast("file_added", any()) }

        awaitFileStatus(created.id, Message.File.Status.COMPLETED)
        val done = session.snapshot.value.messages
            .filterIsInstance<Message.File>().single()
        val onDisk = File(fileStore.fileDir(sid), done.fileId)
        assertTrue(onDisk.exists())
        assertEquals(source.readText(), onDisk.readText())
        coVerify(timeout = 2_000) { hub.broadcast("file_ready", any()) }
    }
}
