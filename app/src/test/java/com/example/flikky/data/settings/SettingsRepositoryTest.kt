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
    private var storeIndex = 0

    private fun makeRepo(scope: TestScope): SettingsRepository {
        val ds = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { tmp.newFile("settings-${storeIndex++}.preferences_pb") },
        )
        return SettingsRepository(ds)
    }

    @Test fun defaults_emitted_when_empty() = runTest {
        val repo = makeRepo(this)
        val s = repo.settings.first()
        assertEquals(ThemeMode.DYNAMIC, s.themeMode)
        assertEquals(20, s.historyRetainLimit)
        assertEquals(false, s.recallBetaEnabled)
        assertEquals(false, s.favoriteBetaEnabled)
        assertEquals(true, s.requirePin)
    }

    @Test fun update_persists_and_emits() = runTest {
        val repo = makeRepo(this)
        repo.setRecallBeta(true)
        repo.setFavoriteBeta(true)
        repo.setHistoryRetainLimit(-1)
        repo.setDarkMode(DarkMode.DARK)
        val s = repo.settings.first()
        assertTrue(s.recallBetaEnabled)
        assertTrue(s.favoriteBetaEnabled)
        assertEquals(-1, s.historyRetainLimit)
        assertEquals(DarkMode.DARK, s.darkMode)
    }

    @Test fun history_retain_limit_clamps_below_unlimited_sentinel() = runTest {
        val repo = makeRepo(this)
        repo.setHistoryRetainLimit(-99)
        assertEquals(-1, repo.settings.first().historyRetainLimit)
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

    @Test fun require_pin_roundtrips() = runTest {
        val repo = makeRepo(this)
        assertEquals(true, repo.settings.first().requirePin)
        repo.setRequirePin(false)
        assertEquals(false, repo.settings.first().requirePin)
        repo.setRequirePin(true)
        assertEquals(true, repo.settings.first().requirePin)
    }

    @Test fun active_group_id_roundtrips_and_null_clears() = runTest {
        val repo = makeRepo(this)
        assertEquals(null, repo.settings.first().activeGroupId)

        repo.setActiveGroup(42L)
        assertEquals(42L, repo.settings.first().activeGroupId)

        repo.setActiveGroup(null)
        assertEquals(null, repo.settings.first().activeGroupId)
    }

    @Test fun active_favorite_group_id_roundtrips_clamps_invalid_and_is_independent() = runTest {
        val repo = makeRepo(this)
        assertEquals(null, repo.settings.first().activeFavoriteGroupId)

        repo.setActiveGroup(7L)
        repo.setActiveFavoriteGroup(42L)

        var settings = repo.settings.first()
        assertEquals(7L, settings.activeGroupId)
        assertEquals(42L, settings.activeFavoriteGroupId)

        repo.setActiveFavoriteGroup(0L)
        settings = repo.settings.first()
        assertEquals(7L, settings.activeGroupId)
        assertEquals(null, settings.activeFavoriteGroupId)

        repo.setActiveFavoriteGroup(-1L)
        assertEquals(null, repo.settings.first().activeFavoriteGroupId)
    }

    @Test fun recent_favorite_ids_keep_latest_unique_five() = runTest {
        val repo = makeRepo(this)
        assertEquals(emptyList<Long>(), repo.settings.first().recentFavoriteIds)

        listOf(1L, 2L, 3L, 4L, 5L, 6L, 3L, -1L, 0L).forEach {
            repo.recordRecentFavorite(it)
        }

        assertEquals(listOf(3L, 6L, 5L, 4L, 2L), repo.settings.first().recentFavoriteIds)
    }

    @Test fun backup_export_and_import_roundtrip_user_settings_without_navigation_state() = runTest {
        val source = makeRepo(this)
        source.setThemeMode(ThemeMode.PRESET)
        source.setPresetTheme(PresetTheme.ANAN_BLUE)
        source.setDarkMode(DarkMode.DARK)
        source.setDeviceName("Backup phone")
        source.setRequirePin(false)
        source.setHistoryRetainLimit(-1)
        source.setActiveGroup(99L)
        source.setActiveFavoriteGroup(98L)
        source.recordRecentFavorite(97L)

        val backup = source.exportBackup()
        val target = makeRepo(this)
        target.setActiveGroup(5L)
        target.setActiveFavoriteGroup(6L)
        target.recordRecentFavorite(7L)
        target.importBackup(backup)

        val restored = target.settings.first()
        assertEquals(ThemeMode.PRESET, restored.themeMode)
        assertEquals(PresetTheme.ANAN_BLUE, restored.presetTheme)
        assertEquals(DarkMode.DARK, restored.darkMode)
        assertEquals("Backup phone", restored.deviceName)
        assertEquals(false, restored.requirePin)
        assertEquals(-1, restored.historyRetainLimit)
        assertEquals(5L, restored.activeGroupId)
        assertEquals(6L, restored.activeFavoriteGroupId)
        assertEquals(listOf(7L), restored.recentFavoriteIds)
    }
}
