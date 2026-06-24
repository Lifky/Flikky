package com.example.flikky.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.flikky.data.db.entities.FavoriteEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FavoriteDaoTest {
    private lateinit var db: TestFavoriteDatabase
    private lateinit var dao: FavoriteDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TestFavoriteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.favoriteDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun observeAll_orders_by_createdAt_desc_and_findBySource_uses_session_scope() = runTest {
        val older = dao.insert(textFavorite(sourceSessionId = 1L, sourceMessageId = 10L, createdAt = 100L))
        val newer = dao.insert(textFavorite(sourceSessionId = 2L, sourceMessageId = 10L, createdAt = 200L))

        dao.observeAll().test {
            assertEquals(listOf(newer, older), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(older, dao.findBySource(1L, 10L)?.id)
        assertEquals(newer, dao.findBySource(2L, 10L)?.id)
        assertNull(dao.findBySource(3L, 10L))
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun insert_rejects_duplicate_source_session_and_message_pair() = runTest {
        dao.insert(textFavorite(sourceSessionId = 1L, sourceMessageId = 10L))
        dao.insert(textFavorite(sourceSessionId = 1L, sourceMessageId = 10L))
    }

    @Test fun observeFavoritedMessageIds_is_limited_to_session() = runTest {
        dao.insert(textFavorite(sourceSessionId = 1L, sourceMessageId = 10L))
        dao.insert(textFavorite(sourceSessionId = 1L, sourceMessageId = 11L))
        dao.insert(textFavorite(sourceSessionId = 2L, sourceMessageId = 10L))

        dao.observeFavoritedMessageIds(1L).test {
            assertEquals(listOf(10L, 11L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun rehomeGroup_setGroupForFavorites_and_memberIds_roundtrip_membership() = runTest {
        val first = dao.insert(textFavorite(sourceSessionId = 1L, sourceMessageId = 10L, groupId = 7L))
        val second = dao.insert(textFavorite(sourceSessionId = 1L, sourceMessageId = 11L, groupId = 7L))
        val other = dao.insert(textFavorite(sourceSessionId = 1L, sourceMessageId = 12L))

        assertEquals(listOf(first, second), dao.memberIds(7L))

        dao.rehomeGroup(7L)
        assertEquals(emptyList<Long>(), dao.memberIds(7L))
        assertNull(dao.getById(first)?.groupId)

        dao.setGroupForFavorites(listOf(first, other), 9L)
        assertEquals(listOf(first, other), dao.memberIds(9L))
    }

    private fun textFavorite(
        sourceSessionId: Long,
        sourceMessageId: Long,
        groupId: Long? = null,
        createdAt: Long = 1L,
    ) = FavoriteEntity(
        sourceSessionId = sourceSessionId,
        sourceMessageId = sourceMessageId,
        kind = "TEXT",
        textContent = "hello",
        groupId = groupId,
        createdAt = createdAt,
        sourceSessionName = "session",
        origin = "PHONE",
    )

    @Database(
        entities = [FavoriteEntity::class],
        version = 1,
        exportSchema = false,
    )
    abstract class TestFavoriteDatabase : RoomDatabase() {
        abstract fun favoriteDao(): FavoriteDao
    }
}
