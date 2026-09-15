package com.example.flikky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlbumSettingTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private var storeIndex = 0

    private fun newStore(scope: TestScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = {
                temporaryFolder.newFile("album-settings-${storeIndex++}.preferences_pb")
            },
        )

    private fun newRepository(scope: TestScope) = SettingsRepository(newStore(scope))

    @Test fun `peer album access is off on a fresh install`() = runTest {
        assertFalse(newRepository(this).settings.first().albumBrowsingEnabled)
    }

    @Test fun `the setter round trips`() = runTest {
        val repository = newRepository(this)
        repository.setAlbumBrowsingEnabled(true)
        assertTrue(repository.settings.first().albumBrowsingEnabled)
        repository.setAlbumBrowsingEnabled(false)
        assertFalse(repository.settings.first().albumBrowsingEnabled)
    }

    @Test fun `there is no migration from any older flag`() = runTest {
        val repository = newRepository(this)
        repository.setStorageBrowsingEnabled(true)
        repository.setFavoriteBrowsingEnabled(true)
        assertFalse(repository.settings.first().albumBrowsingEnabled)
    }

    @Test fun `the backup carries the album gate`() = runTest {
        val repository = newRepository(this)
        repository.setAlbumBrowsingEnabled(true)
        assertEquals(true, repository.exportBackup().albumBrowsingEnabled)
    }

    @Test fun `restoring a backup applies the album gate`() = runTest {
        val repository = newRepository(this)
        val backup = repository.exportBackup().copy(albumBrowsingEnabled = true)
        repository.importBackup(backup)
        assertTrue(repository.settings.first().albumBrowsingEnabled)
    }

    @Test fun `restoring a backup without the field leaves the current value alone`() = runTest {
        val repository = newRepository(this)
        repository.setAlbumBrowsingEnabled(true)
        repository.importBackup(repository.exportBackup().copy(albumBrowsingEnabled = null))
        assertTrue(repository.settings.first().albumBrowsingEnabled)
    }
}
