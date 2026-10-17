package com.example.flikky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 「显示发送者名字」：手机三档，浏览器跟随（v1.23 多设备装机反馈）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SenderNameSettingTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private var storeIndex = 0

    private fun newStore(scope: TestScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { temporaryFolder.newFile("sender-name-${storeIndex++}.preferences_pb") },
        )

    private fun newRepository(scope: TestScope) = SettingsRepository(newStore(scope))

    @Test fun `a fresh install shows names automatically`() = runTest {
        assertEquals(SenderNameMode.AUTO, newRepository(this).settings.first().senderNameMode)
    }

    @Test fun `the setter round trips`() = runTest {
        val repository = newRepository(this)
        repository.setSenderNameMode(SenderNameMode.ALWAYS)
        assertEquals(SenderNameMode.ALWAYS, repository.settings.first().senderNameMode)
        repository.setSenderNameMode(SenderNameMode.OFF)
        assertEquals(SenderNameMode.OFF, repository.settings.first().senderNameMode)
    }

    @Test fun `the backup carries the mode and restores it`() = runTest {
        val repository = newRepository(this)
        repository.setSenderNameMode(SenderNameMode.OFF)
        val backup = repository.exportBackup()
        assertEquals("OFF", backup.senderNameMode)
        repository.setSenderNameMode(SenderNameMode.AUTO)
        repository.importBackup(backup)
        assertEquals(SenderNameMode.OFF, repository.settings.first().senderNameMode)
    }

    @Test fun `an unknown value in a backup is ignored`() = runTest {
        val repository = newRepository(this)
        repository.setSenderNameMode(SenderNameMode.ALWAYS)
        repository.importBackup(repository.exportBackup().copy(senderNameMode = "SOMETIMES"))
        assertEquals(SenderNameMode.ALWAYS, repository.settings.first().senderNameMode)
    }
}
