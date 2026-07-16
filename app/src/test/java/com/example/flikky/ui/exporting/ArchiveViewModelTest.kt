package com.example.flikky.ui.exporting

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.FavoritesRepository
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportScope
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.FavoriteExport
import com.example.flikky.export.SettingsExport
import com.example.flikky.export.ZipExporter
import com.example.flikky.service.TransferService
import com.example.flikky.session.SessionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArchiveViewModelTest {
    private lateinit var app: Application
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var sessionRepo: SessionRepository
    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var sessionState: SessionState

    @Before fun setUp() {
        app = spyk(ApplicationProvider.getApplicationContext())
        every { app.startForegroundService(any()) } returns null
        settingsRepo = mockk()
        every { settingsRepo.settings } returns MutableStateFlow(FlikkySettings(requirePin = false))
        sessionRepo = mockk()
        favoritesRepo = mockk()
        sessionState = SessionState(nowMs = { 0L })
    }

    private fun buildVm() = ArchiveViewModel(
        app = app,
        settingsRepository = settingsRepo,
        sessionRepository = sessionRepo,
        favoritesRepository = favoritesRepo,
        sessionState = sessionState,
        pinGenerator = { "246810" },
        now = { 123L },
    )

    private fun buildVm(
        localExportWriter: suspend (Uri, ExportSnapshot) -> Unit,
    ) = ArchiveViewModel(
        app = app,
        settingsRepository = settingsRepo,
        sessionRepository = sessionRepo,
        favoritesRepository = favoritesRepo,
        sessionState = sessionState,
        pinGenerator = { "246810" },
        now = { 123L },
        localExportWriter = localExportWriter,
    )

    @Test fun favorites_export_arms_lan_service_with_scope_and_counts() = runTest {
        coEvery { favoritesRepo.exportSnapshot() } returns FavoritesRepository.ExportData(
            groups = emptyList(),
            favorites = listOf(
                FavoriteExport(
                    id = 1L,
                    sourceSessionId = 2L,
                    sourceMessageId = 3L,
                    kind = "TEXT",
                    textContent = "note",
                    createdAt = 4L,
                )
            ),
        )

        val result = buildVm().startExport(ExportScope.FAVORITES)

        assertTrue(result is ArchiveViewModel.ExportStartResult.Success)
        val armed = sessionState.exportMode.value as ExportMode.Armed
        assertEquals(ExportScope.FAVORITES, armed.snapshot.scope)
        assertEquals(1, armed.snapshot.favorites.size)
        assertEquals(1, armed.session.favoriteCount)
        assertEquals(false, armed.session.requirePin)
        val intents = mutableListOf<Intent>()
        verify { app.startForegroundService(capture(intents)) }
        assertEquals(TransferService.ACTION_EXPORT, intents.single().action)
    }

    @Test fun empty_favorites_do_not_start_export_service() = runTest {
        coEvery { favoritesRepo.exportSnapshot() } returns FavoritesRepository.ExportData(
            groups = emptyList(),
            favorites = emptyList(),
        )

        val result = buildVm().startExport(ExportScope.FAVORITES)

        assertTrue(result is ArchiveViewModel.ExportStartResult.NoFavorites)
        assertTrue(sessionState.exportMode.value is ExportMode.Idle)
        verify(exactly = 0) { app.startForegroundService(any()) }
    }

    @Test fun all_export_combines_sessions_favorites_and_settings() = runTest {
        coEvery { sessionRepo.exportAllSnapshot() } returns ExportSnapshot(exportedAt = 50L)
        coEvery { favoritesRepo.exportSnapshot() } returns FavoritesRepository.ExportData(
            groups = emptyList(),
            favorites = listOf(
                FavoriteExport(
                    id = 1L,
                    sourceSessionId = 2L,
                    sourceMessageId = 3L,
                    kind = "TEXT",
                    textContent = "note",
                    createdAt = 4L,
                )
            ),
        )
        coEvery { settingsRepo.exportBackup() } returns SettingsExport(deviceName = "Phone")

        val result = buildVm().startExport(ExportScope.ALL)

        assertTrue(result is ArchiveViewModel.ExportStartResult.Success)
        val armed = sessionState.exportMode.value as ExportMode.Armed
        assertEquals(ExportScope.ALL, armed.snapshot.scope)
        assertEquals("Phone", armed.snapshot.settings?.deviceName)
        assertEquals(1, armed.session.favoriteCount)
        assertTrue(armed.session.settingsIncluded)
    }

    @Test fun favorites_local_export_writes_without_arming_lan_service() = runTest {
        val uri = Uri.parse("content://flikky-test/favorites.zip")
        coEvery { favoritesRepo.exportSnapshot() } returns FavoritesRepository.ExportData(
            groups = emptyList(),
            favorites = listOf(
                FavoriteExport(
                    id = 1L,
                    sourceSessionId = 2L,
                    sourceMessageId = 3L,
                    kind = "TEXT",
                    textContent = "note",
                    createdAt = 4L,
                ),
            ),
        )
        var written: Pair<Uri, ExportSnapshot>? = null

        val result = buildVm { target, snapshot -> written = target to snapshot }
            .saveExport(ExportScope.FAVORITES, uri)

        assertTrue(result is ArchiveViewModel.ExportStartResult.Success)
        assertEquals(uri, written?.first)
        assertEquals(ExportScope.FAVORITES, written?.second?.scope)
        assertTrue(sessionState.exportMode.value is ExportMode.Idle)
        verify(exactly = 0) { app.startForegroundService(any()) }
    }

    @Test fun settings_local_export_writes_settings_snapshot() = runTest {
        val uri = Uri.parse("content://flikky-test/settings.zip")
        coEvery { settingsRepo.exportBackup() } returns SettingsExport(deviceName = "Phone")
        var written: ExportSnapshot? = null

        val result = buildVm { _, snapshot -> written = snapshot }
            .saveExport(ExportScope.SETTINGS, uri)

        assertTrue(result is ArchiveViewModel.ExportStartResult.Success)
        assertEquals(ExportScope.SETTINGS, written?.scope)
        assertEquals("Phone", written?.settings?.deviceName)
    }

    @Test fun all_local_export_writes_combined_snapshot() = runTest {
        val uri = Uri.parse("content://flikky-test/all.zip")
        coEvery { sessionRepo.exportAllSnapshot() } returns ExportSnapshot(exportedAt = 50L)
        coEvery { favoritesRepo.exportSnapshot() } returns FavoritesRepository.ExportData(
            groups = emptyList(),
            favorites = emptyList(),
        )
        coEvery { settingsRepo.exportBackup() } returns SettingsExport(deviceName = "Phone")
        var written: ExportSnapshot? = null

        val result = buildVm { _, snapshot -> written = snapshot }
            .saveExport(ExportScope.ALL, uri)

        assertTrue(result is ArchiveViewModel.ExportStartResult.Success)
        assertEquals(ExportScope.ALL, written?.scope)
        assertEquals("Phone", written?.settings?.deviceName)
    }

    @Test fun invalid_zip_error_is_reported_once() = runTest {
        val invalidZip = File(app.cacheDir, "invalid.zip").apply { writeText("not a zip") }
        val uri = Uri.fromFile(invalidZip)
        coEvery { sessionRepo.importSessions(any()) } returns SessionRepository.ImportResult(
            imported = emptyList(),
            skipped = emptyList(),
            errors = listOf(SessionRepository.ImportError("zip", "无法打开 zip")),
        )

        val result = buildVm().importFromZip(uri)

        assertEquals(listOf("无法打开 zip"), result.errors)
    }

    @Test fun favorites_import_does_not_restore_sessions_or_settings() = runTest {
        val archive = File(app.cacheDir, "favorites.zip")
        archive.outputStream().use { output ->
            ZipExporter.write(
                out = output,
                snapshot = ExportSnapshot(
                    exportedAt = 123L,
                    scope = ExportScope.FAVORITES,
                    favorites = listOf(
                        FavoriteExport(
                            id = 1L,
                            sourceSessionId = 2L,
                            sourceMessageId = 3L,
                            kind = "TEXT",
                            textContent = "note",
                            createdAt = 4L,
                        ),
                    ),
                ),
                fileResolver = { _, _ -> null },
            )
        }
        coEvery { favoritesRepo.importBackup(any(), any(), any()) } returns
            FavoritesRepository.ImportResult(imported = 1, skipped = 0, errors = emptyList())

        val result = buildVm().importFavoritesFromZip(Uri.fromFile(archive))

        assertEquals(1, result.importedFavorites)
        assertEquals(0, result.importedSessions)
        assertEquals(false, result.settingsImported)
        coVerify(exactly = 0) { sessionRepo.importSessions(any()) }
        coVerify(exactly = 0) { settingsRepo.importBackup(any()) }
    }
}
