package com.example.flikky.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.export.MessageDto
import com.example.flikky.export.SessionDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * v1.5.0 regression: importSessions() called fifoSweep() — which invoked
 * retainLimitProvider() — from INSIDE withContext(Dispatchers.IO). The production
 * retainLimitProvider is `settingsRepository.settings.first().historyRetainLimit`,
 * a DataStore flow read. DataStore 1.1 uses a SingleThreadExecutor-backed actor;
 * calling `data.first()` from Dispatchers.IO can deadlock on Android when the
 * calling IO thread is the same executor thread DataStore needs to dispatch on.
 *
 * Fix (SessionRepository.kt): read retainLimitProvider() BEFORE entering
 * withContext(Dispatchers.IO) and forward the pre-read value into the private
 * importSessionsInIo helper.
 *
 * These tests use a real DataStore instance (PreferenceDataStoreFactory + temp file),
 * which is the setup that would expose the deadlock if the fix were reverted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryImportDataStoreProviderTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: FlikkyDatabase
    private lateinit var store: SessionFileStore

    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private var zipSeq = 0

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java)
            .allowMainThreadQueries().build()
        store = SessionFileStore(filesDir = tmp.root)
    }

    @After fun tearDown() { db.close() }

    private fun makeSettingsRepo(scope: kotlinx.coroutines.CoroutineScope): SettingsRepository =
        SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { tmp.newFile("settings-${System.nanoTime()}.preferences_pb") },
            )
        )

    /** Build a zip containing [n] distinct sessions, each with one text message. */
    private fun buildZip(n: Int): File {
        val f = tmp.newFile("import-${zipSeq++}.zip")
        ZipOutputStream(f.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("Flikky Export\nVersion: 1.4\n".toByteArray())
            zip.closeEntry()
            for (i in 1..n) {
                val startedAt = i.toLong() * 1000L
                val dir = "sessions/${i}_Session$i/"
                val dto = SessionDto(
                    sessionId = i.toLong(),
                    name = "Session$i",
                    startedAt = startedAt,
                    endedAt = startedAt + 100L,
                    pinned = false,
                    messages = listOf(
                        MessageDto.TextDto(ts = startedAt, origin = "PHONE", content = "msg$i"),
                    ),
                )
                zip.putNextEntry(ZipEntry("${dir}messages.json"))
                zip.write(json.encodeToString(SessionDto.serializer(), dto).toByteArray())
                zip.closeEntry()
            }
        }
        return f
    }

    /**
     * Core regression test: import with the production-mirroring DataStore-backed provider
     * must complete successfully without deadlock.
     *
     * Before the fix, this test would hang or throw because retainLimitProvider() was called
     * inside withContext(Dispatchers.IO), creating a deadlock with DataStore's
     * SingleThreadExecutor-backed actor on real Android.
     */
    @Test fun import_with_datastore_retainLimitProvider_completes() = runTest {
        val settingsRepo = makeSettingsRepo(backgroundScope)
        val repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            groupDao = db.groupDao(),
            fileStore = store,
            now = { 1_000L },
            retainLimitProvider = { settingsRepo.settings.first().historyRetainLimit },
        )

        val zip = buildZip(3)
        val result = repo.importSessions(zip)

        assertEquals("all sessions imported", 3, result.imported.size)
        assertEquals(0, result.skipped.size)
        assertEquals(0, result.errors.size)
    }

    /**
     * Verify that a DataStore-backed retainLimit of 2 causes fifoSweep to evict
     * the oldest sessions after importing 5. This proves the limit IS read from
     * DataStore and correctly applied even after the fix.
     */
    @Test fun datastore_retainLimit_2_evicts_oldest_after_import() = runTest {
        val settingsRepo = makeSettingsRepo(backgroundScope)
        settingsRepo.setHistoryRetainLimit(2)

        val repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            groupDao = db.groupDao(),
            fileStore = store,
            now = { 1_000L },
            retainLimitProvider = { settingsRepo.settings.first().historyRetainLimit },
        )

        val zip = buildZip(5)
        val result = repo.importSessions(zip)

        assertEquals("all 5 sessions reported as imported", 5, result.imported.size)
        assertEquals(0, result.errors.size)

        // After fifoSweep with limit=2, only 2 non-pinned sessions remain.
        val remaining = db.sessionDao().nonPinnedOldestFirst()
        assertEquals(2, remaining.size)
        // Oldest 3 (startedAt=1000,2000,3000) were evicted; newest 2 (4000,5000) kept.
        assertTrue(remaining.none { it.name == "Session1" })
        assertTrue(remaining.none { it.name == "Session2" })
        assertTrue(remaining.none { it.name == "Session3" })
        assertTrue(remaining.any { it.name == "Session4" })
        assertTrue(remaining.any { it.name == "Session5" })
    }

    /**
     * Verify that a DataStore-backed retainLimit of -1 (unlimited) keeps all imported sessions.
     */
    @Test fun datastore_retainLimit_unlimited_keeps_all() = runTest {
        val settingsRepo = makeSettingsRepo(backgroundScope)
        settingsRepo.setHistoryRetainLimit(-1)

        val repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            groupDao = db.groupDao(),
            fileStore = store,
            now = { 1_000L },
            retainLimitProvider = { settingsRepo.settings.first().historyRetainLimit },
        )

        val zip = buildZip(30)
        val result = repo.importSessions(zip)

        assertEquals(30, result.imported.size)
        assertEquals(30, db.sessionDao().nonPinnedOldestFirst().size)
    }
}
