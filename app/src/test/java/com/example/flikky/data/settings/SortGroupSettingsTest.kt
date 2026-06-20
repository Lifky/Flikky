package com.example.flikky.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
    fun defaults_are_time_and_none() {
        val s = FlikkySettings()

        assertEquals(SortMode.TIME, s.sortMode)
        assertEquals(GroupMode.NONE, s.groupMode)
    }

    @Test
    fun enums_roundtrip_by_name() {
        assertEquals(SortMode.NAME, SortMode.valueOf("NAME"))
        assertEquals(GroupMode.DATE, GroupMode.valueOf("DATE"))
    }

    @Test
    fun repository_persists_sort_and_group_modes() = runTest {
        val repo = makeRepo(this)

        repo.setSortMode(SortMode.NAME)
        repo.setGroupMode(GroupMode.DATE)

        val settings = repo.settings.first()
        assertEquals(SortMode.NAME, settings.sortMode)
        assertEquals(GroupMode.DATE, settings.groupMode)
    }
}
