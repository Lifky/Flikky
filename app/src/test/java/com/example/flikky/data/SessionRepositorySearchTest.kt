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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 覆盖 SessionRepository.search 的两条分支（FTS / LIKE）、撤回过滤、跨会话命中、
 * 以及 timestamp DESC 排序。fixture 用 inMemoryDatabaseBuilder + 手动分配 message id
 * （MessageEntity.id 不是 autoGenerate）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositorySearchTest {
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

    private suspend fun insertSession(name: String, startedAt: Long = 100L): Long =
        db.sessionDao().insert(SessionEntity(
            startedAt = startedAt, endedAt = startedAt + 1, name = name,
        ))

    private suspend fun insertText(
        sessionId: Long, id: Long, ts: Long, content: String,
        senderId: String? = "tester", recalledAt: Long? = null,
    ) = db.messageDao().insert(MessageEntity(
        id = id, sessionId = sessionId, origin = "PHONE", timestamp = ts,
        kind = "TEXT", content = content, senderId = senderId, recalledAt = recalledAt,
    ))

    private suspend fun insertFile(
        sessionId: Long, id: Long, ts: Long,
        fileId: String, fileName: String, size: Long = 100L,
        senderId: String? = "tester", recalledAt: Long? = null,
    ) = db.messageDao().insert(MessageEntity(
        id = id, sessionId = sessionId, origin = "BROWSER", timestamp = ts,
        kind = "FILE",
        fileId = fileId, fileName = fileName, fileSize = size,
        fileMime = "application/octet-stream", fileStatus = "COMPLETED",
        senderId = senderId, recalledAt = recalledAt,
    ))

    @Test fun empty_query_returns_empty_list() = runTest {
        val sid = insertSession("s")
        insertText(sid, 1L, 100L, "hello world all good")

        assertEquals(emptyList<SessionRepository.SearchHit>(), repo.search(""))
        assertEquals(emptyList<SessionRepository.SearchHit>(), repo.search("   "))
    }

    @Test fun long_query_hits_fts_branch() = runTest {
        // FTS4 unicode61 分词器对英文按空格/标点切词，"hello world" 长度 11 >= 5
        // 进 FTS 分支，匹配 "hello world all good" 中的 hello + world token。
        val sid = insertSession("english")
        insertText(sid, 1L, 100L, "hello world all good")
        insertText(sid, 2L, 200L, "totally unrelated content")

        val hits = repo.search("hello world")

        assertEquals(1, hits.size)
        val hit = hits.first()
        assertEquals(sid, hit.sessionId)
        assertEquals("english", hit.sessionName)
        assertEquals(1L, hit.messageId)
        assertEquals("hello world all good", hit.snippet)
        assertEquals("TEXT", hit.kind)
        assertEquals(100L, hit.timestamp)
    }

    @Test fun short_query_hits_like_branch() = runTest {
        // 中文 "你好" 长度 2 < 5，走 LIKE %你好%。
        // 注：测试环境用 Room 自动生成的 FTS 表（无 categories='L* N* Co'），
        // FTS 对中文不会命中——但这里是 LIKE 分支，跟 FTS 表无关。
        val sid = insertSession("中文会话")
        insertText(sid, 1L, 100L, "你好，朋友")
        insertText(sid, 2L, 200L, "再见")

        val hits = repo.search("你好")

        assertEquals(1, hits.size)
        assertEquals(1L, hits.first().messageId)
        assertEquals("你好，朋友", hits.first().snippet)
    }

    @Test fun recalled_messages_are_excluded() = runTest {
        val sid = insertSession("s")
        // 一条活跃 + 一条已撤回——都含 "hello world" 长 token，都属 FTS 分支可命中范围。
        insertText(sid, 1L, 100L, "hello world alive")
        insertText(sid, 2L, 200L, "hello world recalled", recalledAt = 250L)

        // 注：手动插入带 recalledAt 的行，触发器 messages_fts_ai 的 WHEN 子句会跳过
        // 插入 FTS——所以撤回行根本不在 FTS 表里，DAO 的 WHERE 也再兜底一次。
        val hits = repo.search("hello world")

        assertEquals(1, hits.size)
        assertEquals(1L, hits.first().messageId)
        assertEquals("hello world alive", hits.first().snippet)

        // 同样验证 LIKE 分支：DAO WHERE recalledAt IS NULL 也排除撤回行。
        // 插一行短词命中、且撤回的，确保 LIKE 同样过滤。
        insertText(sid, 3L, 300L, "测试", recalledAt = 350L)
        val shortHits = repo.search("测试")
        assertTrue(shortHits.none { it.messageId == 3L })
    }

    @Test fun cross_session_hits() = runTest {
        val s1 = insertSession("session-A", startedAt = 100L)
        val s2 = insertSession("session-B", startedAt = 200L)
        insertText(s1, 1L, 150L, "hello world from A")
        insertText(s2, 2L, 250L, "hello world from B")

        val hits = repo.search("hello world")

        assertEquals(2, hits.size)
        val sessionNames = hits.map { it.sessionName }.toSet()
        assertEquals(setOf("session-A", "session-B"), sessionNames)
    }

    @Test fun hits_ordered_by_timestamp_desc() = runTest {
        val sid = insertSession("s")
        insertText(sid, 1L, 100L, "hello world oldest")
        insertText(sid, 2L, 300L, "hello world newest")
        insertText(sid, 3L, 200L, "hello world middle")

        val hits = repo.search("hello world")

        assertEquals(3, hits.size)
        assertEquals(listOf(2L, 3L, 1L), hits.map { it.messageId })
        assertEquals(listOf(300L, 200L, 100L), hits.map { it.timestamp })
    }

    @Test fun file_message_snippet_uses_filename() = runTest {
        // 文件 snippet 直接取 fileName。用长度 >= 5 的关键词走 FTS（fileName 也在 FTS index 中）。
        val sid = insertSession("files")
        insertFile(sid, 1L, 100L, fileId = "f1", fileName = "report-final.pdf")

        val hits = repo.search("report-final")

        assertEquals(1, hits.size)
        assertEquals("FILE", hits.first().kind)
        assertEquals("report-final.pdf", hits.first().snippet)
    }

    @Test fun text_snippet_truncated_to_80_chars() = runTest {
        val sid = insertSession("s")
        // 100 个字符的 hello-world 重复段，含搜索词。
        val long = "hello world " + "x".repeat(100)
        insertText(sid, 1L, 100L, long)

        val hits = repo.search("hello world")

        assertEquals(1, hits.size)
        assertEquals(80, hits.first().snippet.length)
        assertEquals(long.take(80), hits.first().snippet)
    }

    @Test fun like_branch_does_not_match_when_no_substring() = runTest {
        val sid = insertSession("s")
        insertText(sid, 1L, 100L, "abc")

        val hits = repo.search("xyz")
        assertFalse(hits.any { it.messageId == 1L })
    }
}
