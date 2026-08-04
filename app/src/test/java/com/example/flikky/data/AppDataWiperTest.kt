package com.example.flikky.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class AppDataWiperTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Recorder {
        val steps = mutableListOf<String>()
    }

    private fun buildWiper(
        recorder: Recorder,
        fileStore: SessionFileStore,
        favoriteStore: FavoriteFileStore,
        tempFiles: List<File>,
        clearDatabase: suspend () -> Unit = { recorder.steps += "db" },
    ) = AppDataWiper(
        clearDatabase = clearDatabase,
        fileStore = fileStore,
        favoriteFileStore = favoriteStore,
        tempFiles = { tempFiles },
        clearSettings = { recorder.steps += "settings" },
        resetRuntime = { recorder.steps += "runtime" },
    )

    @Test fun wipe_clears_everything_in_order_with_settings() = runTest {
        val recorder = Recorder()
        val fileStore = SessionFileStore(filesDir = tmp.root)
        val favoriteStore = FavoriteFileStore(filesDir = tmp.root)
        fileStore.archiveFromStream(1L, "a", ByteArrayInputStream(byteArrayOf(1)))
        favoriteStore.copyIn("d", ByteArrayInputStream(byteArrayOf(2)))
        val temp = File(tmp.root, "import_temp.zip").apply { writeBytes(byteArrayOf(3)) }

        buildWiper(recorder, fileStore, favoriteStore, listOf(temp)).wipe(resetSettings = true)

        assertEquals(listOf("db", "settings", "runtime"), recorder.steps)
        assertTrue(!File(tmp.root, "sessions").exists())
        assertTrue(!File(tmp.root, "favorites").exists())
        assertTrue(!temp.exists())
    }

    @Test fun wipe_without_reset_keeps_settings_untouched() = runTest {
        val recorder = Recorder()
        buildWiper(
            recorder,
            SessionFileStore(filesDir = tmp.root),
            FavoriteFileStore(filesDir = tmp.root),
            emptyList(),
        ).wipe(resetSettings = false)

        assertEquals(listOf("db", "runtime"), recorder.steps)
    }

    @Test fun wipe_continues_past_a_failing_step() = runTest {
        val recorder = Recorder()
        val temp = File(tmp.root, "archive_import_temp.zip").apply { writeBytes(byteArrayOf(1)) }
        buildWiper(
            recorder,
            SessionFileStore(filesDir = tmp.root),
            FavoriteFileStore(filesDir = tmp.root),
            listOf(temp),
            clearDatabase = { recorder.steps += "db"; error("boom") },
        ).wipe(resetSettings = true)

        assertEquals(listOf("db", "settings", "runtime"), recorder.steps)
        assertTrue(!temp.exists())
    }
}
