package com.example.flikky.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
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
class SessionDaoTest {
    private lateinit var db: FlikkyDatabase
    private lateinit var dao: SessionDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.sessionDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun insert_and_getById_roundtrip() = runTest {
        val id = dao.insert(SessionEntity(startedAt = 100L, endedAt = null, name = "a"))
        val row = dao.getById(id)!!
        assertEquals(100L, row.startedAt)
        assertEquals("a", row.name)
        assertNull(row.endedAt)
    }

    @Test fun observeAll_orders_pinned_first_then_startedAt_desc() = runTest {
        dao.insert(SessionEntity(startedAt = 100L, endedAt = 200L, name = "old"))
        dao.insert(SessionEntity(startedAt = 300L, endedAt = 400L, name = "recent"))
        dao.insert(SessionEntity(startedAt = 50L,  endedAt = 60L,  name = "pinned-old", pinned = true))

        dao.observeAll().test {
            val rows = awaitItem().map { it.name }
            assertEquals(listOf("pinned-old", "recent", "old"), rows)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun nonPinnedOldestFirst_excludes_pinned_and_unfinished() = runTest {
        dao.insert(SessionEntity(startedAt = 100L, endedAt = 200L, name = "a"))
        dao.insert(SessionEntity(startedAt = 300L, endedAt = 400L, name = "b", pinned = true))
        dao.insert(SessionEntity(startedAt = 500L, endedAt = null, name = "c"))
        dao.insert(SessionEntity(startedAt = 50L,  endedAt = 60L,  name = "z"))

        val got = dao.nonPinnedOldestFirst().map { it.name }
        assertEquals(listOf("z", "a"), got)
    }

    @Test fun listUnfinished_returns_endedAt_null_rows() = runTest {
        dao.insert(SessionEntity(startedAt = 1L, endedAt = 2L, name = "done"))
        dao.insert(SessionEntity(startedAt = 3L, endedAt = null, name = "live"))
        val got = dao.listUnfinished().map { it.name }
        assertEquals(listOf("live"), got)
    }
}
