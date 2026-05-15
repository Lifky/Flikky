package com.example.flikky.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the FTS4 wiring set up by `FlikkyDatabase` (the onCreate Callback for
 * fresh installs and `MIGRATION_1_2` for upgraders share the same trigger and
 * tokenizer definitions, so an in-memory v2 DB covers both paths):
 *   - `searchMessagesFts` over English + CJK content
 *   - `searchMessagesLike` fallback
 *   - AFTER DELETE / AFTER UPDATE triggers keeping the FTS index in sync
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MessageFtsDaoTest {
    private lateinit var db: FlikkyDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var dao: MessageDao
    private var sid: Long = 0

    @Before fun setup() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.sessionDao()
        dao = db.messageDao()
        sid = sessionDao.insert(SessionEntity(startedAt = 0L, endedAt = null, name = "Session A"))
    }

    @After fun tearDown() { db.close() }

    private fun textMessage(id: Long, content: String, t: Long = 1000L * id) = MessageEntity(
        id = id, sessionId = sid, origin = "PHONE", timestamp = t,
        kind = "TEXT", content = content,
    )

    private fun fileMessage(id: Long, fileName: String, t: Long = 1000L * id) = MessageEntity(
        id = id, sessionId = sid, origin = "PHONE", timestamp = t,
        kind = "FILE", fileId = "f$id", fileName = fileName,
    )

    @Test fun searchMessagesFts_matches_english_token() = runTest {
        dao.insert(textMessage(1L, "hello world"))
        dao.insert(textMessage(2L, "good morning"))

        val hits = dao.searchMessagesFts("hello")
        assertEquals(1, hits.size)
        assertEquals(1L, hits.single().messageId)
        assertEquals("Session A", hits.single().sessionName)
    }

    @Test fun searchMessagesFts_handles_cjk_without_crashing_and_like_fallback_still_hits() = runTest {
        dao.insert(textMessage(10L, "你好世界"))
        dao.insert(textMessage(11L, "再见"))

        // Production tokenizer is `unicode61 categories='L* N* Co'`, which makes CJK
        // Unified Ideographs (Lo) into single-char tokens. Real Android SQLite
        // supports that arg; the sqlite4java build Robolectric uses does NOT, so
        // we cannot assert a CJK FTS hit here — that case lives in instrumented
        // tests. What we DO assert is the contract that survives both worlds:
        //  (a) FTS queries with CJK input never throw, regardless of tokenizer
        //      capability — they just return an empty list at worst.
        //  (b) The LIKE-based fallback that the search Repository will use when
        //      FTS returns empty still hits the row.
        val ftsHits = dao.searchMessagesFts("你好")
        // (a) call returned (didn't throw). It may legitimately be empty under
        //     Robolectric's SQLite, so we only check it didn't surface row 11.
        assertFalse(ftsHits.any { it.messageId == 11L })

        // (b) LIKE fallback is the always-works path for CJK substrings.
        val likeHits = dao.searchMessagesLike("%你好%")
        assertEquals(1, likeHits.size)
        assertEquals(10L, likeHits.single().messageId)
    }

    @Test fun searchMessagesLike_uses_substring_pattern() = runTest {
        dao.insert(textMessage(20L, "晚安，明天见"))
        dao.insert(textMessage(21L, "hello"))

        val hits = dao.searchMessagesLike("%你好%")
        assertTrue("expected no hits", hits.isEmpty())

        val hits2 = dao.searchMessagesLike("%明天%")
        assertEquals(1, hits2.size)
        assertEquals(20L, hits2.single().messageId)
    }

    @Test fun ad_trigger_removes_recalled_or_deleted_row_from_fts() = runTest {
        val msg = textMessage(30L, "delete me later")
        dao.insert(msg)
        assertEquals(1, dao.searchMessagesFts("delete").size)

        // Raw DELETE via SupportSQLiteDatabase since DAO has no delete-by-id
        db.openHelper.writableDatabase.execSQL("DELETE FROM messages WHERE id = 30")
        assertEquals(0, dao.searchMessagesFts("delete").size)
    }

    @Test fun au_trigger_drops_message_from_fts_when_recalled() = runTest {
        dao.insert(textMessage(40L, "regret sending this"))
        assertEquals(1, dao.searchMessagesFts("regret").size)

        // Mark as recalled — au trigger should remove FTS row, and the
        // WHERE-recalledAt-IS-NULL clause in DAO additionally filters it.
        dao.update(textMessage(40L, "regret sending this").copy(recalledAt = 9_999L))
        assertEquals(0, dao.searchMessagesFts("regret").size)
    }

    @Test fun searchMessagesFts_matches_file_by_fileName() = runTest {
        dao.insert(fileMessage(50L, "report.pdf"))
        dao.insert(fileMessage(51L, "notes.txt"))

        val hits = dao.searchMessagesFts("report")
        assertEquals(1, hits.size)
        assertEquals(50L, hits.single().messageId)
        assertEquals("FILE", hits.single().kind)
        assertEquals("report.pdf", hits.single().fileName)
    }
}
