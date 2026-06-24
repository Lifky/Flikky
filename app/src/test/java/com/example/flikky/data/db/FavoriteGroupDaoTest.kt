package com.example.flikky.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.flikky.data.db.entities.FavoriteGroupEntity
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
class FavoriteGroupDaoTest {
    private lateinit var db: TestFavoriteGroupDatabase
    private lateinit var dao: FavoriteGroupDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TestFavoriteGroupDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.favoriteGroupDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun observeAll_orders_by_sortOrder_and_maxSortOrder_tracks_tail() = runTest {
        val lateId = dao.insert(FavoriteGroupEntity(name = "Late", sortOrder = 20, createdAt = 2L))
        val earlyId = dao.insert(FavoriteGroupEntity(name = "Early", sortOrder = 10, createdAt = 1L))

        dao.observeAll().test {
            assertEquals(listOf(earlyId, lateId), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(20, dao.maxSortOrder())
    }

    @Test fun get_update_and_delete_roundtrip() = runTest {
        val id = dao.insert(FavoriteGroupEntity(name = "Draft", sortOrder = 0, createdAt = 1L))

        assertEquals("Draft", dao.getById(id)?.name)

        dao.update(FavoriteGroupEntity(id = id, name = "Saved", sortOrder = 1, createdAt = 1L))
        assertEquals("Saved", dao.getById(id)?.name)

        dao.deleteById(id)
        assertEquals(null, dao.getById(id))
    }

    @Database(
        entities = [FavoriteGroupEntity::class],
        version = 1,
        exportSchema = false,
    )
    abstract class TestFavoriteGroupDatabase : RoomDatabase() {
        abstract fun favoriteGroupDao(): FavoriteGroupDao
    }
}
