package com.example.flikky.ui.settings

import android.app.Application
import android.net.Uri
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.FavoritesRepository
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.AvatarGroupingMode
import com.example.flikky.data.settings.AnimationSpeed
import com.example.flikky.data.settings.ContrastLevel
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.data.settings.PresetTheme
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.di.ServiceLocator
import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportScope
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.ZipImporter
import com.example.flikky.service.TransferService
import com.example.flikky.session.SessionState
import com.example.flikky.util.IdGen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.zip.ZipFile

class SettingsViewModel @JvmOverloads constructor(
    app: Application,
    private val repo: com.example.flikky.data.settings.SettingsRepository = ServiceLocator.settingsRepository,
    private val sessionRepo: SessionRepository = ServiceLocator.repository,
    private val favoritesRepo: FavoritesRepository = ServiceLocator.favoritesRepository,
    private val sessionState: SessionState = ServiceLocator.session,
    private val pinGenerator: () -> String = { IdGen.newPin() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : AndroidViewModel(app) {

    sealed class ExportStartResult {
        object Success : ExportStartResult()
        object TransferRunning : ExportStartResult()
        object NoFavorites : ExportStartResult()
        object UseSessionSelection : ExportStartResult()
    }

    data class ImportResult(
        val importedSessions: Int,
        val skippedSessions: Int,
        val importedFavorites: Int,
        val skippedFavorites: Int,
        val settingsImported: Boolean,
        val errors: List<String>,
    )

    val settings: StateFlow<FlikkySettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, FlikkySettings())

    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { repo.setThemeMode(v) }
    fun setPreset(v: PresetTheme) = viewModelScope.launch { repo.setPresetTheme(v) }
    fun setContrast(v: ContrastLevel) = viewModelScope.launch { repo.setContrastLevel(v) }
    fun setDarkMode(v: DarkMode) = viewModelScope.launch { repo.setDarkMode(v) }
    fun setAmoled(v: Boolean) = viewModelScope.launch { repo.setAmoled(v) }
    fun setPhoneAvatar(v: Int) = viewModelScope.launch { repo.setPhoneAvatar(v) }
    fun setPhoneAvatarKey(v: String) = viewModelScope.launch { repo.setPhoneAvatarKey(v) }
    fun setBackground(v: BackgroundSetting) = viewModelScope.launch { repo.setBackground(v) }
    fun setDeviceName(v: String) = viewModelScope.launch { repo.setDeviceName(v) }
    fun setRecallBeta(v: Boolean) = viewModelScope.launch { repo.setRecallBeta(v) }
    fun setFavoriteBeta(v: Boolean) = viewModelScope.launch { repo.setFavoriteBeta(v) }
    fun setRequirePin(v: Boolean) = viewModelScope.launch { repo.setRequirePin(v) }
    fun setMessageActionStyle(v: MessageActionStyle) = viewModelScope.launch { repo.setMessageActionStyle(v) }
    fun setAvatarGrouping(v: AvatarGroupingMode) = viewModelScope.launch { repo.setAvatarGrouping(v) }
    fun setAnimationSpeed(v: AnimationSpeed) = viewModelScope.launch { repo.setAnimationSpeed(v) }
    fun setAllowBackDuringSession(v: Boolean) = viewModelScope.launch { repo.setAllowBackDuringSession(v) }
    fun setBubbleCornerRadius(v: Int) = viewModelScope.launch { repo.setBubbleCornerRadius(v) }
    fun setHistoryRetainLimit(v: Int) = viewModelScope.launch {
        repo.setHistoryRetainLimit(v)
        // Immediately evict excess sessions so the count settles to the new limit
        // without waiting for the next session end or app restart.
        runCatching { sessionRepo.fifoSweep() }
    }

    suspend fun startExport(scope: ExportScope): ExportStartResult {
        if (scope == ExportScope.SESSIONS) return ExportStartResult.UseSessionSelection
        if (isTransferOrExportRunning()) return ExportStartResult.TransferRunning

        val snapshot = when (scope) {
            ExportScope.SESSIONS -> error("Session export is owned by HomeScreen selection")
            ExportScope.FAVORITES -> {
                val favorites = favoritesRepo.exportSnapshot()
                if (favorites.favorites.isEmpty()) return ExportStartResult.NoFavorites
                ExportSnapshot(
                    exportedAt = now(),
                    scope = ExportScope.FAVORITES,
                    favoriteGroups = favorites.groups,
                    favorites = favorites.favorites,
                )
            }
            ExportScope.SETTINGS -> ExportSnapshot(
                exportedAt = now(),
                scope = ExportScope.SETTINGS,
                settings = repo.exportBackup(),
            )
            ExportScope.ALL -> {
                val sessions = sessionRepo.exportAllSnapshot()
                val favorites = favoritesRepo.exportSnapshot()
                sessions.copy(
                    scope = ExportScope.ALL,
                    favoriteGroups = favorites.groups,
                    favorites = favorites.favorites,
                    settings = repo.exportBackup(),
                )
            }
        }

        val currentSettings = repo.settings.first()
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

        val ctx = getApplication<Application>()
        ctx.startForegroundService(
            Intent(ctx, TransferService::class.java).apply {
                action = TransferService.ACTION_EXPORT
            }
        )
        return ExportStartResult.Success
    }

    private fun isTransferOrExportRunning(): Boolean {
        if (sessionState.snapshot.value.currentSessionId != null) return true
        return when (sessionState.exportMode.value) {
            is ExportMode.Armed, is ExportMode.Sending -> true
            else -> false
        }
    }

    /** Copy URI to a private temp file, then restore sessions → favorites → settings. */
    suspend fun importFromZip(uri: Uri): ImportResult {
        val ctx = getApplication<Application>()
        val tempFile = java.io.File(ctx.filesDir, "settings_import_temp.zip")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
            } ?: return ImportResult(0, 0, 0, 0, false, listOf("无法读取文件"))

            val sessionResult = sessionRepo.importSessions(tempFile)
            if (sessionResult.errors.any { it.name == "zip" }) {
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
                sessionResult.imported.forEach { imported ->
                    imported.originalId?.let { put(it, imported.newId) }
                }
                sessionResult.skipped.forEach { skipped ->
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
                        favoritesResult = favoritesRepo.importBackup(backup, zip, sessionIdMap)
                    }
                    backup.settings?.let { settings ->
                        repo.importBackup(settings)
                        settingsImported = true
                    }
                }
            }.onFailure { archiveErrors += it.message ?: "无法解析归档" }

            return ImportResult(
                importedSessions = sessionResult.imported.size,
                skippedSessions = sessionResult.skipped.size,
                importedFavorites = favoritesResult.imported,
                skippedFavorites = favoritesResult.skipped,
                settingsImported = settingsImported,
                errors = sessionResult.errors.map { it.error } + favoritesResult.errors + archiveErrors,
            )
        } finally {
            tempFile.delete()
        }
    }
}
