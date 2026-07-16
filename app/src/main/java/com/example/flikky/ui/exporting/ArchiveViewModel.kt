package com.example.flikky.ui.exporting

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.flikky.R
import com.example.flikky.data.FavoritesRepository
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.di.ServiceLocator
import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportScope
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.ZipImporter
import com.example.flikky.service.TransferService
import com.example.flikky.session.SessionState
import com.example.flikky.util.IdGen
import kotlinx.coroutines.flow.first
import java.util.zip.ZipFile

class ArchiveViewModel @JvmOverloads constructor(
    app: Application,
    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository,
    private val sessionRepository: SessionRepository = ServiceLocator.repository,
    private val favoritesRepository: FavoritesRepository = ServiceLocator.favoritesRepository,
    private val sessionState: SessionState = ServiceLocator.session,
    private val pinGenerator: () -> String = { IdGen.newPin() },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val localExportWriter: suspend (Uri, ExportSnapshot) -> Unit = { uri, snapshot ->
        LocalExportWriter.write(
            context = app,
            uri = uri,
            snapshot = snapshot,
            sessionFileResolver = { sessionId, fileId ->
                ServiceLocator.fileStore.fileDir(sessionId).resolve(fileId)
                    .takeIf { it.exists() && it.isFile }
            },
            favoriteFileResolver = { fileId ->
                ServiceLocator.favoriteFileStore.resolve(fileId)
                    .takeIf { it.exists() && it.isFile }
            },
        )
    },
) : AndroidViewModel(app) {

    sealed class ExportStartResult {
        data object Success : ExportStartResult()
        data object TransferRunning : ExportStartResult()
        data object NoFavorites : ExportStartResult()
        data object UseSessionSelection : ExportStartResult()
    }

    data class ImportResult(
        val importedSessions: Int,
        val skippedSessions: Int,
        val importedFavorites: Int,
        val skippedFavorites: Int,
        val settingsImported: Boolean,
        val errors: List<String>,
    )

    suspend fun startExport(scope: ExportScope): ExportStartResult {
        if (scope == ExportScope.SESSIONS) return ExportStartResult.UseSessionSelection
        if (isTransferOrExportRunning()) return ExportStartResult.TransferRunning

        val snapshot = buildExportSnapshot(scope) ?: return ExportStartResult.NoFavorites
        val currentSettings = settingsRepository.settings.first()
        val exportSession = ExportSession(
            sessionIds = snapshot.sessions.map { it.id },
            pin = pinGenerator(),
            createdAt = now(),
            requirePin = currentSettings.requirePin,
            scope = scope,
            favoriteCount = snapshot.favorites.size,
            settingsIncluded = snapshot.settings != null,
        )
        sessionState.clearExport()
        sessionState.armExport(exportSession, snapshot)

        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TransferService::class.java).apply {
                action = TransferService.ACTION_EXPORT
            }
        )
        return ExportStartResult.Success
    }

    suspend fun saveExport(scope: ExportScope, uri: Uri): ExportStartResult {
        if (scope == ExportScope.SESSIONS) return ExportStartResult.UseSessionSelection
        val snapshot = buildExportSnapshot(scope) ?: return ExportStartResult.NoFavorites
        localExportWriter(uri, snapshot)
        return ExportStartResult.Success
    }

    private suspend fun buildExportSnapshot(scope: ExportScope): ExportSnapshot? = when (scope) {
        ExportScope.SESSIONS -> error("Session export is owned by HomeScreen selection")
        ExportScope.FAVORITES -> {
            val favorites = favoritesRepository.exportSnapshot()
            if (favorites.favorites.isEmpty()) null else ExportSnapshot(
                exportedAt = now(),
                scope = ExportScope.FAVORITES,
                favoriteGroups = favorites.groups,
                favorites = favorites.favorites,
            )
        }
        ExportScope.SETTINGS -> ExportSnapshot(
            exportedAt = now(),
            scope = ExportScope.SETTINGS,
            settings = settingsRepository.exportBackup(),
        )
        ExportScope.ALL -> {
            val sessions = sessionRepository.exportAllSnapshot()
            val favorites = favoritesRepository.exportSnapshot()
            sessions.copy(
                scope = ExportScope.ALL,
                favoriteGroups = favorites.groups,
                favorites = favorites.favorites,
                settings = settingsRepository.exportBackup(),
            )
        }
    }

    private fun isTransferOrExportRunning(): Boolean {
        if (sessionState.snapshot.value.currentSessionId != null) return true
        return when (sessionState.exportMode.value) {
            is ExportMode.Armed, is ExportMode.Sending -> true
            else -> false
        }
    }

    suspend fun importFromZip(uri: Uri): ImportResult = importFromZip(uri, favoritesOnly = false)

    suspend fun importFavoritesFromZip(uri: Uri): ImportResult =
        importFromZip(uri, favoritesOnly = true)

    private suspend fun importFromZip(uri: Uri, favoritesOnly: Boolean): ImportResult {
        val context = getApplication<Application>()
        val tempFile = java.io.File(context.filesDir, "archive_import_temp.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return ImportResult(
                0,
                0,
                0,
                0,
                false,
                listOf(context.getString(R.string.archive_read_failed)),
            )

            val sessionResult = if (favoritesOnly) null else sessionRepository.importSessions(tempFile)
            if (sessionResult?.errors?.any { it.name == "zip" } == true) {
                return ImportResult(
                    importedSessions = 0,
                    skippedSessions = 0,
                    importedFavorites = 0,
                    skippedFavorites = 0,
                    settingsImported = false,
                    errors = sessionResult.errors.map { it.error },
                )
            }
            val sessionIdMap = buildMap {
                sessionResult?.imported.orEmpty().forEach { imported ->
                    imported.originalId?.let { put(it, imported.newId) }
                }
                sessionResult?.skipped.orEmpty().forEach { skipped ->
                    val originalId = skipped.originalId
                    val existingId = skipped.existingId
                    if (originalId != null && existingId != null) put(originalId, existingId)
                }
            }

            var favoritesResult = FavoritesRepository.ImportResult(0, 0, emptyList())
            var settingsImported = false
            val archiveErrors = mutableListOf<String>()
            runCatching {
                ZipFile(tempFile).use { zip ->
                    val backup = ZipImporter.parseBackup(zip)
                    if (backup.favorites.isNotEmpty() || backup.favoriteGroups.isNotEmpty()) {
                        favoritesResult = favoritesRepository.importBackup(backup, zip, sessionIdMap)
                    }
                    backup.settings?.takeUnless { favoritesOnly }?.let { settings ->
                        settingsRepository.importBackup(settings)
                        settingsImported = true
                    }
                }
            }.onFailure {
                archiveErrors += it.message ?: context.getString(R.string.archive_parse_failed)
            }

            return ImportResult(
                importedSessions = sessionResult?.imported?.size ?: 0,
                skippedSessions = sessionResult?.skipped?.size ?: 0,
                importedFavorites = favoritesResult.imported,
                skippedFavorites = favoritesResult.skipped,
                settingsImported = settingsImported,
                errors = sessionResult?.errors.orEmpty().map { it.error } +
                    favoritesResult.errors + archiveErrors,
            )
        } finally {
            tempFile.delete()
        }
    }
}
