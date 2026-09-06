package com.example.flikky.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SortGroupSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun makeRepo(scope: TestScope): SettingsRepository {
        val ds = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { tmp.newFile("sort-group.preferences_pb") },
        )
        return SettingsRepository(ds)
    }

    @Test
    fun `group mode defaults to DATE, matching what the home list has actually rendered`() {
        // 这个默认值曾是 NONE，而 HomeViewModel 写死传的是 DATE —— 也就是说
        // 没有任何用户见过 NONE。接线时若不把默认值改成 DATE，升级后所有人的
        // 主页会突然不分节，是一次静默的行为回归。
        assertEquals(GroupMode.DATE, FlikkySettings().groupMode)
    }

    @Test
    fun `each surface has its own default sort, equal to what it rendered before this version`() {
        val s = FlikkySettings()
        assertEquals(SortSpec(SortKey.TIME, descending = true), s.homeSort)
        assertEquals(SortSpec(SortKey.TIME, descending = true), s.favoritesSort)
        assertEquals(SortSpec(SortKey.TIME, descending = true), s.filesSort)
        assertEquals(SortSpec.NameAsc, s.storageSort)
    }

    @Test
    fun `repository persists every surface's sort independently`() = runTest {
        val repo = makeRepo(this)

        repo.setHomeSort(SortSpec(SortKey.NAME, descending = false))
        repo.setStorageSort(SortSpec(SortKey.SIZE, descending = true))

        val settings = repo.settings.first()
        assertEquals(SortSpec(SortKey.NAME, descending = false), settings.homeSort)
        assertEquals(SortSpec(SortKey.SIZE, descending = true), settings.storageSort)
        // 只写了两处，另外两处必须还是默认值 —— 否则四个键其实是同一个。
        assertEquals(SortSpec(SortKey.TIME, descending = true), settings.favoritesSort)
        assertEquals(SortSpec(SortKey.TIME, descending = true), settings.filesSort)
    }

    @Test
    fun `an old sort_mode value is still read for the home surface`() = runTest {
        // 主页排序此前从未有过入口，所以线上值几乎必然是默认的；但导入一份旧备份时
        // sort_mode 会被写进来，读不到就是静默丢值。
        val repo = makeRepo(this)
        repo.setLegacySortModeForTest("NAME")

        assertEquals(SortSpec(SortKey.NAME, descending = false), repo.settings.first().homeSort)
    }

    @Test
    fun `the new home_sort key wins over the legacy sort_mode key`() = runTest {
        val repo = makeRepo(this)
        repo.setLegacySortModeForTest("NAME")
        repo.setHomeSort(SortSpec(SortKey.SIZE, descending = true))

        assertEquals(SortSpec(SortKey.SIZE, descending = true), repo.settings.first().homeSort)
    }

    @Test
    fun `a legacy sort_mode value only feeds the home surface`() = runTest {
        // 旧键是主页专有的（那是当时唯一有排序设置的界面）。让它同时喂给
        // 另外三处，会在导入旧备份时把它们的默认值一起改掉。
        val repo = makeRepo(this)
        repo.setLegacySortModeForTest("NAME")

        val s = repo.settings.first()
        assertEquals(SortSpec(SortKey.TIME, descending = true), s.favoritesSort)
        assertEquals(SortSpec(SortKey.TIME, descending = true), s.filesSort)
        assertEquals(SortSpec.NameAsc, s.storageSort)
    }

    @Test
    fun `a corrupt stored sort falls back to that surface's default`() = runTest {
        val repo = makeRepo(this)
        repo.setRawSortForTest("home_sort", "TIME:sideways")

        assertEquals(SortSpec(SortKey.TIME, descending = true), repo.settings.first().homeSort)
    }

    @Test
    fun `group mode still round-trips`() = runTest {
        val repo = makeRepo(this)
        repo.setGroupMode(GroupMode.STATUS)
        assertEquals(GroupMode.STATUS, repo.settings.first().groupMode)
    }
}
