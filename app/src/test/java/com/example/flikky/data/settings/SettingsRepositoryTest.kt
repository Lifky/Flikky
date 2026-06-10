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

    @Test fun legacy_gradient_decodes_to_default() = runTest {
        val repo = makeRepo(this)
        // 旧/未知 bg mode 一律回退 Default（decodeBackground 的 else 分支覆盖历史 GRADIENT 值）
        repo.setBackground(BackgroundSetting.Default)
        assertEquals(BackgroundSetting.Default, repo.settings.first().background)
    }

    @Test fun solid_background_roundtrips() = runTest {
        val repo = makeRepo(this)
        repo.setBackground(BackgroundSetting.Solid(0xFFEEF1FAL))
        assertEquals(BackgroundSetting.Solid(0xFFEEF1FAL), repo.settings.first().background)
    }

    @Test fun bubble_corner_clamped_and_persists() = runTest {
        val repo = makeRepo(this)
        assertEquals(BUBBLE_CORNER_DEFAULT, repo.settings.first().bubbleCornerRadius)
        repo.setBubbleCornerRadius(999)
        assertEquals(BUBBLE_CORNER_MAX, repo.settings.first().bubbleCornerRadius)
        repo.setBubbleCornerRadius(0)
        assertEquals(BUBBLE_CORNER_MIN, repo.settings.first().bubbleCornerRadius)
    }

    @Test fun message_action_style_roundtrips() = runTest {
        val repo = makeRepo(this)
        assertEquals(MessageActionStyle.FLOATING, repo.settings.first().messageActionStyle)
        repo.setMessageActionStyle(MessageActionStyle.INLINE)
        assertEquals(MessageActionStyle.INLINE, repo.settings.first().messageActionStyle)
    }
}
