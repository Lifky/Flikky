package com.example.flikky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.flikky.export.SettingsExport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalAddressSettingTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private var storeIndex = 0

    private fun newStore(scope: TestScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { temporaryFolder.newFile("local-address-${storeIndex++}.preferences_pb") },
        )

    private fun newRepository(scope: TestScope) = SettingsRepository(newStore(scope))

    @Test fun `defaults on a fresh install`() = runTest {
        val s = newRepository(this).settings.first()
        assertNull(s.hostNumber)
        assertEquals(8080, s.customPort)
        assertTrue(s.localNameEnabled)
    }

    @Test fun `ensureHostNumber assigns once and then stays put`() = runTest {
        val repo = newRepository(this)
        val first = repo.ensureHostNumber(Random(1))
        assertTrue(first in 1..99)
        assertEquals(first, repo.ensureHostNumber(Random(999)))
        assertEquals(first, repo.settings.first().hostNumber)
    }

    @Test fun `ensureHostNumber keeps a user chosen number`() = runTest {
        val repo = newRepository(this)
        repo.setHostNumber(0)
        assertEquals(0, repo.ensureHostNumber(Random(1)))
    }

    @Test fun `setters round trip`() = runTest {
        val repo = newRepository(this)
        repo.setHostNumber(999); repo.setCustomPort(9000); repo.setLocalNameEnabled(false)
        val s = repo.settings.first()
        assertEquals(999, s.hostNumber); assertEquals(9000, s.customPort); assertFalse(s.localNameEnabled)
    }

    @Test fun `setters ignore out of range values`() = runTest {
        val repo = newRepository(this)
        repo.setHostNumber(37); repo.setCustomPort(9000)
        repo.setHostNumber(1000); repo.setCustomPort(80)
        val s = repo.settings.first()
        assertEquals(37, s.hostNumber); assertEquals(9000, s.customPort)
    }

    @Test fun `backup carries all three`() = runTest {
        val repo = newRepository(this)
        repo.setHostNumber(42); repo.setCustomPort(8181); repo.setLocalNameEnabled(false)
        val b = repo.exportBackup()
        assertEquals(42, b.hostNumber); assertEquals(8181, b.customPort); assertEquals(false, b.localNameEnabled)
    }

    @Test fun `restoring a backup applies all three`() = runTest {
        val repo = newRepository(this)
        repo.importBackup(SettingsExport(hostNumber = 7, customPort = 8200, localNameEnabled = false))
        val s = repo.settings.first()
        assertEquals(7, s.hostNumber); assertEquals(8200, s.customPort); assertFalse(s.localNameEnabled)
    }

    @Test fun `an imported backup with invalid values is ignored field by field`() = runTest {
        val repo = newRepository(this)
        repo.setHostNumber(37); repo.setCustomPort(9000)
        repo.importBackup(SettingsExport(hostNumber = 5000, customPort = 80, localNameEnabled = false))
        val s = repo.settings.first()
        assertEquals(37, s.hostNumber); assertEquals(9000, s.customPort)
        assertFalse("the valid field in the same backup still applies", s.localNameEnabled)
    }

    @Test fun `an old backup without these fields changes nothing`() = runTest {
        val repo = newRepository(this)
        repo.setHostNumber(37)
        repo.importBackup(SettingsExport())
        assertEquals(37, repo.settings.first().hostNumber)
    }
}
