package com.example.flikky.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.flikky.data.db.entities.GroupEntity
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
class GroupDaoTest {
    private lateinit var db: TestGroupDatabase
    private lateinit var groupDao: GroupDao
    private lateinit var sessionDao: SessionDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TestGroupDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        groupDao = db.groupDao()
        sessionDao = db.sessionDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun observeAll_orders_by_sortOrder_and_maxSortOrder_tracks_tail() = runTest {
        val lateId = groupDao.insert(GroupEntity(name = "Late", sortOrder = 20, createdAt = 2L))
        val earlyId = groupDao.insert(GroupEntity(name = "Early", sortOrder = 10, createdAt = 1L))

        groupDao.observeAll().test {
            assertEquals(listOf(earlyId, lateId), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(20, groupDao.maxSortOrder())
    }

    @Test fun memberIds_unbindGroup_and_bindSessions_roundtrip_membership() = runTest {
        val groupId = groupDao.insert(GroupEntity(name = "Work", sortOrder = 0, createdAt = 1L))
        val first = sessionDao.insert(SessionEntity(startedAt = 1L, endedAt = 2L, name = "first", groupId = groupId))
        val second = sessionDao.insert(SessionEntity(startedAt = 3L, endedAt = 4L, name = "second", groupId = groupId))
        val other = sessionDao.insert(SessionEntity(startedAt = 5L, endedAt = 6L, name = "other"))

        assertEquals(listOf(first, second), groupDao.memberIds(groupId))

        sessionDao.unbindGroup(groupId)
        assertEquals(emptyList<Long>(), groupDao.memberIds(groupId))

        sessionDao.bindSessions(groupId, listOf(first, other))
        assertEquals(listOf(first, other), groupDao.memberIds(groupId))
    }

    @Database(
        entities = [SessionEntity::class, GroupEntity::class],
        version = 1,
        exportSchema = false,
    )
    abstract class TestGroupDatabase : RoomDatabase() {
        abstract fun sessionDao(): SessionDao
        abstract fun groupDao(): GroupDao
    }
}
