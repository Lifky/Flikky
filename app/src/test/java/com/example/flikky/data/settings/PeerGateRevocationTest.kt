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
        setAllowPeerFavorite(true)
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
        assertTrue(s.allowPeerFavorite)
    }

    // ── 撤回（2026-09-27 用户裁决）：「消息撤回」概念更大，关掉它时「允许撤回对端消息」也不应生效，
    // 重新打开「消息撤回」时对端撤回保持关闭、需要手动再开。与收藏同一条 D76 规则。

    @Test fun `turning recall off closes peer recall in the same write`() = runTest {
        val repository = newRepository(this)
        repository.setAllowPeerRecall(true)

        repository.setRecallBeta(false)

        assertFalse(repository.settings.first().allowPeerRecall)
    }

    @Test fun `turning recall back on does not reopen peer recall`() = runTest {
        val repository = newRepository(this)
        repository.setRecallBeta(false)

        repository.setRecallBeta(true)

        val s = repository.settings.first()
        assertTrue(s.recallBetaEnabled)
        assertFalse(s.allowPeerRecall)
    }

    @Test fun `recall off on disk closes peer recall even when its key was never written`() = runTest {
        // 对端撤回的键缺席时读作默认的「开」：只看「键 == true」会放过这种老数据。
        val repository = newRepository(this)
        repository.importBackup(SettingsExport(recallEnabled = false))

        repository.revokeUnavailablePeerGates(storageAvailable = true, albumAvailable = true)

        assertFalse(repository.settings.first().allowPeerRecall)
    }

    @Test fun `a recall key that was never written counts as on`() = runTest {
        // 全新安装：两个键都缺席、都读作默认的「开」—— 不能被误判成「撤回已关」。
        val repository = newRepository(this)

        repository.revokeUnavailablePeerGates(storageAvailable = true, albumAvailable = true)

        val s = repository.settings.first()
        assertTrue(s.recallBetaEnabled)
        assertTrue(s.allowPeerRecall)
    }

    // ── 对端收藏（D78）：「对端能做」里的第二个开关，前置条件是收藏功能本身，规则与对端撤回同形。

    @Test fun `peer favoriting is off on a fresh install`() = runTest {
        assertFalse(newRepository(this).settings.first().allowPeerFavorite)
    }

    @Test fun `turning the favourites feature off closes peer favoriting in the same write`() = runTest {
        val repository = newRepository(this)
        repository.openAllPeerGates()

        repository.setFavoriteBeta(false)

        assertFalse(repository.settings.first().allowPeerFavorite)
    }

    @Test fun `turning the favourites feature on does not enable peer favoriting`() = runTest {
        val repository = newRepository(this)
        repository.openAllPeerGates()
        repository.setFavoriteBeta(false)

        repository.setFavoriteBeta(true)

        assertFalse(repository.settings.first().allowPeerFavorite)
    }

    @Test fun `favourites off on disk closes peer favoriting`() = runTest {
        val repository = newRepository(this)
        repository.importBackup(SettingsExport(favoriteEnabled = false, allowPeerFavorite = true))

        repository.revokeUnavailablePeerGates(storageAvailable = true, albumAvailable = true)

        assertFalse(repository.settings.first().allowPeerFavorite)
    }

    // ── 导入备份（D78 审查修订）：旧版备份里「撤回关 + 对端撤回开」是常见组合（旧版关撤回不连带关对端撤回）。
    // 回到前台的检查早于导入写入，不能指望它；导入本身就要按同一规则落盘。

    @Test fun `importing recall off with peer recall on stores peer recall off`() = runTest {
        val repository = newRepository(this)

        repository.importBackup(SettingsExport(recallEnabled = false, allowPeerRecall = true))

        assertFalse(repository.settings.first().allowPeerRecall)
        repository.setRecallBeta(true)
        assertFalse("turning recall back on must not bring peer recall with it", repository.settings.first().allowPeerRecall)
    }

    @Test fun `importing favourites off with peer gates on stores them off`() = runTest {
        val repository = newRepository(this)

        repository.importBackup(
            SettingsExport(favoriteEnabled = false, favoriteBrowsingEnabled = true, allowPeerFavorite = true),
        )

        val s = repository.settings.first()
        assertFalse(s.favoriteBrowsingEnabled)
        assertFalse(s.allowPeerFavorite)
    }

    @Test fun `importing a backup with the features on keeps its peer gates`() = runTest {
        val repository = newRepository(this)

        repository.importBackup(
            SettingsExport(
                recallEnabled = true,
                allowPeerRecall = true,
                favoriteEnabled = true,
                favoriteBrowsingEnabled = true,
                allowPeerFavorite = true,
            ),
        )

        val s = repository.settings.first()
        assertTrue(s.allowPeerRecall)
        assertTrue(s.favoriteBrowsingEnabled)
        assertTrue(s.allowPeerFavorite)
    }
}
