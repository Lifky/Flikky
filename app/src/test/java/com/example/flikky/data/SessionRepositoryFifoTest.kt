package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.data.db.entities.SessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M6 regression: fifoSweep reads limit from a provider lambda, handles -1/0/N semantics.
 * -1 = unlimited (keep all); 0 = evict all non-pinned; N = keep newest N non-pinned.
 * Pinned sessions are always kept regardless of limit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryFifoTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: FlikkyDatabase
    private lateinit var repo: SessionRepository
    private lateinit var store: SessionFileStore

    private var limit = 20
    private val limitProvider: suspend () -> Int = { limit }

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java)
            .allowMainThreadQueries().build()
        store = SessionFileStore(filesDir = tmp.root)
        repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            groupDao = db.groupDao(),
            fileStore = store,
            now = { 1_000L },
            retainLimitProvider = limitProvider,
        )
    }

    @After fun tearDown() { db.close() }

    /** Insert n finished non-pinned sessions with ascending startedAt (1..n). */
    private suspend fun insertFinished(n: Int) {
        for (i in 1..n) {
            db.sessionDao().insert(SessionEntity(
                startedAt = i.toLong(),
                endedAt = i.toLong() + 1,
                name = "s$i",
                pinned = false,
            ))
        }
    }

    /** Insert n finished pinned sessions. */
    private suspend fun insertPinnedFinished(n: Int) {
        for (i in 1..n) {
            db.sessionDao().insert(SessionEntity(
                startedAt = (1000 + i).toLong(),
                endedAt = (1000 + i).toLong() + 1,
                name = "pinned$i",
                pinned = true,
            ))
        }
    }

    private suspend fun nonPinnedCount(): Int =
        db.sessionDao().nonPinnedOldestFirst().size

    private suspend fun pinnedCount(): Int {
        // observeAll() returns a Flow; collect one emission via a direct DB approach.
        // We inserted pinned sessions with name "pinnedN" — find them by querying
        // nonPinnedOldestFirst (pinned=0 only) and comparing to findByNameAndStartedAt.
        // Simpler: use Room's findByNameAndStartedAt for each expected pinned session.
        // We know insertPinnedFinished(1) inserts with startedAt=1001, name="pinned1".
        val row = db.sessionDao().findByNameAndStartedAt("pinned1", 1001L)
        return if (row?.pinned == true) 1 else 0
    }

    @Test fun limit_minus_one_keeps_all() = runTest {
        limit = -1
        insertFinished(25)
        repo.fifoSweep()
        assertEquals(25, nonPinnedCount())
    }

    @Test fun limit_zero_removes_all_nonpinned_keeps_pinned() = runTest {
        limit = 0
        insertFinished(5)
        insertPinnedFinished(1)
        repo.fifoSweep()
        assertEquals(0, nonPinnedCount())
        assertEquals(1, pinnedCount())
    }

    @Test fun limit_n_keeps_latest_n() = runTest {
        limit = 3
        insertFinished(5)  // startedAt 1..5 → keep 3,4,5
        repo.fifoSweep()
        assertEquals(3, nonPinnedCount())
        // Oldest two (startedAt=1, startedAt=2) should be gone
        assertEquals(null, db.sessionDao().getById(1))
        assertEquals(null, db.sessionDao().getById(2))
    }
}
