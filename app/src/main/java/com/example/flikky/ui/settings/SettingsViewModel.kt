package com.example.flikky.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.SessionRepository
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel @JvmOverloads constructor(
    app: Application,
    private val repo: com.example.flikky.data.settings.SettingsRepository = ServiceLocator.settingsRepository,
    private val sessionRepo: SessionRepository = ServiceLocator.repository,
) : AndroidViewModel(app) {

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

    /** Replicates HomeViewModel.importFromZip: copy URI → temp zip → importSessions. */
    suspend fun importFromZip(uri: Uri): SessionRepository.ImportResult {
        val ctx = getApplication<Application>()
        val tempFile = java.io.File(ctx.filesDir, "settings_import_temp.zip")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
            } ?: return SessionRepository.ImportResult(
                emptyList(), emptyList(),
                listOf(SessionRepository.ImportError("zip", "无法读取文件")),
            )
            return sessionRepo.importSessions(tempFile)
        } finally {
            tempFile.delete()
        }
    }
}
