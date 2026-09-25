package com.example.flikky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.flikky.export.SettingsExport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D76 fail-closed：前置条件缺失的对端通道必须被关掉，而不是「藏起开关、保留开启」。
 *
 * 后者就是 2026-09-26 那个漏洞：导入一份开着文件/相册的备份、但新装的 App 没授权 ——
 * 面板只显示「去授权」、用户关不掉；一授权，浏览器立刻看到内容，事后才能关。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PeerGateRevocationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private var storeIndex = 0

    private fun newStore(scope: TestScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = {
                temporaryFolder.newFile("peer-gates-${storeIndex++}.preferences_pb")
            },
        )

    private fun newRepository(scope: TestScope) = SettingsRepository(newStore(scope))

    private suspend fun SettingsRepository.openAllPeerGates() {
        setFavoriteBeta(true)
        setStorageBrowsingEnabled(true)
        setAlbumBrowsingEnabled(true)
        setFavoriteBrowsingEnabled(true)
    }

    @Test fun `missing storage permission closes the files gate`() = runTest {
        val repository = newRepository(this)
        repository.openAllPeerGates()

        repository.revokeUnavailablePeerGates(storageAvailable = false, albumAvailable = true)

        val s = repository.settings.first()
        assertFalse(s.storageBrowsingEnabled)
        assertTrue("an available channel must be left alone", s.albumBrowsingEnabled)
        assertTrue("an available channel must be left alone", s.favoriteBrowsingEnabled)
    }

    @Test fun `missing photo access closes the album gate`() = runTest {
        val repository = newRepository(this)
        repository.openAllPeerGates()

        repository.revokeUnavailablePeerGates(storageAvailable = true, albumAvailable = false)

        val s = repository.settings.first()
        assertFalse(s.albumBrowsingEnabled)
        assertTrue(s.storageBrowsingEnabled)
    }

    @Test fun `favourites feature off closes the favourites gate`() = runTest {
        val repository = newRepository(this)
        repository.importBackup(
            SettingsExport(favoriteEnabled = false, favoriteBrowsingEnabled = true),
        )

        repository.revokeUnavailablePeerGates(storageAvailable = true, albumAvailable = true)

        assertFalse(repository.settings.first().favoriteBrowsingEnabled)
    }

    @Test fun `turning the favourites feature off closes its peer gate in the same write`() = runTest {
        val repository = newRepository(this)
        repository.openAllPeerGates()

        repository.setFavoriteBeta(false)

        assertFalse(repository.settings.first().favoriteBrowsingEnabled)
    }

    @Test fun `turning the favourites feature on does not open its peer gate`() = runTest {
        val repository = newRepository(this)

        repository.setFavoriteBeta(true)

        assertFalse(repository.settings.first().favoriteBrowsingEnabled)
    }

    @Test fun `granting the permission afterwards does not reopen the gate`() = runTest {
        val repository = newRepository(this)
        repository.openAllPeerGates()
        repository.revokeUnavailablePeerGates(storageAvailable = false, albumAvailable = false)

        // 用户去授权了：前置条件恢复，但对端开关必须由用户显式再打开。
        repository.revokeUnavailablePeerGates(storageAvailable = true, albumAvailable = true)

        val s = repository.settings.first()
        assertFalse(s.storageBrowsingEnabled)
        assertFalse(s.albumBrowsingEnabled)
    }

    @Test fun `an imported backup with open gates is closed on an unauthorised install`() = runTest {
        val repository = newRepository(this)
        repository.importBackup(
            SettingsExport(
                favoriteEnabled = true,
                favoriteBrowsingEnabled = true,
                storageBrowsingEnabled = true,
                albumBrowsingEnabled = true,
            ),
        )

        repository.revokeUnavailablePeerGates(storageAvailable = false, albumAvailable = false)

        val s = repository.settings.first()
        assertFalse(s.storageBrowsingEnabled)
        assertFalse(s.albumBrowsingEnabled)
        assertTrue("favourites has no system permission; its feature is on", s.favoriteBrowsingEnabled)
    }

    @Test fun `everything available changes nothing`() = runTest {
        val repository = newRepository(this)
        repository.openAllPeerGates()

        repository.revokeUnavailablePeerGates(storageAvailable = true, albumAvailable = true)

        val s = repository.settings.first()
        assertTrue(s.storageBrowsingEnabled)
        assertTrue(s.albumBrowsingEnabled)
        assertTrue(s.favoriteBrowsingEnabled)
    }
}
