package com.example.flikky.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        // 模拟 v1.5.x 升级上来的用户：DataStore 里残留一个 "GRADIENT" 背景值。
        // v1.6.0 删掉了 Gradient 类型，decodeBackground 的 else 分支必须把它静默回退
        // 为 Default 而不是崩溃。直接写裸 preference 值才能真正命中该分支。
        val ds = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tmp.newFile("legacy.preferences_pb") },
        )
        ds.edit {
            it[stringPreferencesKey("bg_mode")] = "GRADIENT"
            it[stringPreferencesKey("bg_value")] = "sunset"
        }
        val repo = SettingsRepository(ds)
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

    @Test fun avatar_grouping_roundtrips() = runTest {
        val repo = makeRepo(this)
        assertEquals(AvatarGroupingMode.FIRST, repo.settings.first().avatarGrouping)
        repo.setAvatarGrouping(AvatarGroupingMode.LAST)
        assertEquals(AvatarGroupingMode.LAST, repo.settings.first().avatarGrouping)
        repo.setAvatarGrouping(AvatarGroupingMode.EACH)
        assertEquals(AvatarGroupingMode.EACH, repo.settings.first().avatarGrouping)
    }

    @Test fun allow_back_during_session_roundtrips() = runTest {
        val repo = makeRepo(this)
        assertEquals(false, repo.settings.first().allowBackDuringSession)
        repo.setAllowBackDuringSession(true)
        assertTrue(repo.settings.first().allowBackDuringSession)
        repo.setAllowBackDuringSession(false)
        assertEquals(false, repo.settings.first().allowBackDuringSession)
    }
}
