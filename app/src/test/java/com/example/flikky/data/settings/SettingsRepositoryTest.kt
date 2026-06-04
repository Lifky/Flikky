package com.example.flikky.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun makeRepo(scope: TestScope): SettingsRepository {
        val ds = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { tmp.newFile("settings.preferences_pb") },
        )
        return SettingsRepository(ds)
    }

    @Test fun defaults_emitted_when_empty() = runTest {
        val repo = makeRepo(this)
        val s = repo.settings.first()
        assertEquals(ThemeMode.DYNAMIC, s.themeMode)
        assertEquals(20, s.historyRetainLimit)
        assertEquals(false, s.recallBetaEnabled)
    }

    @Test fun update_persists_and_emits() = runTest {
        val repo = makeRepo(this)
        repo.setRecallBeta(true)
        repo.setHistoryRetainLimit(-1)
        repo.setDarkMode(DarkMode.DARK)
        val s = repo.settings.first()
        assertTrue(s.recallBetaEnabled)
        assertEquals(-1, s.historyRetainLimit)
        assertEquals(DarkMode.DARK, s.darkMode)
    }

    @Test fun background_roundtrips_gradient() = runTest {
        val repo = makeRepo(this)
        repo.setBackground(BackgroundSetting.Gradient("sunset"))
        val s = repo.settings.first()
        assertEquals(BackgroundSetting.Gradient("sunset"), s.background)
    }
}
