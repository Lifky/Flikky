package com.example.flikky.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.R
import com.example.flikky.data.AppDataWiper
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.settings.AnimationSpeed
import com.example.flikky.data.settings.AppLanguage
import com.example.flikky.data.settings.AppLanguageManager
import com.example.flikky.data.settings.AvatarGroupingMode
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.data.settings.ContrastLevel
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.data.settings.PresetTheme
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel @JvmOverloads constructor(
    app: Application,
    private val repository: SettingsRepository = ServiceLocator.settingsRepository,
    private val sessionRepository: SessionRepository = ServiceLocator.repository,
) : AndroidViewModel(app) {

    val settings: StateFlow<FlikkySettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, FlikkySettings())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }
    fun setPreset(value: PresetTheme) = viewModelScope.launch { repository.setPresetTheme(value) }
    fun setContrast(value: ContrastLevel) = viewModelScope.launch { repository.setContrastLevel(value) }
    fun setDarkMode(value: DarkMode) = viewModelScope.launch { repository.setDarkMode(value) }
    fun setAmoled(value: Boolean) = viewModelScope.launch { repository.setAmoled(value) }
    fun setPhoneAvatar(value: Int) = viewModelScope.launch { repository.setPhoneAvatar(value) }
    fun setPhoneAvatarKey(value: String) = viewModelScope.launch { repository.setPhoneAvatarKey(value) }
    fun setBackground(value: BackgroundSetting) = viewModelScope.launch { repository.setBackground(value) }
    fun setDeviceName(value: String) = viewModelScope.launch { repository.setDeviceName(value) }
    fun setRecallBeta(value: Boolean) = viewModelScope.launch { repository.setRecallBeta(value) }
    fun setFavoriteBeta(value: Boolean) = viewModelScope.launch { repository.setFavoriteBeta(value) }
    fun setRequirePin(value: Boolean) = viewModelScope.launch { repository.setRequirePin(value) }
    fun setMessageActionStyle(value: MessageActionStyle) =
        viewModelScope.launch { repository.setMessageActionStyle(value) }

    fun setAvatarGrouping(value: AvatarGroupingMode) =
        viewModelScope.launch { repository.setAvatarGrouping(value) }

    fun setAnimationSpeed(value: AnimationSpeed) =
        viewModelScope.launch { repository.setAnimationSpeed(value) }

    fun setAllowBackDuringSession(value: Boolean) =
        viewModelScope.launch { repository.setAllowBackDuringSession(value) }

    fun setBubbleCornerRadius(value: Int) =
        viewModelScope.launch { repository.setBubbleCornerRadius(value) }

    fun setHistoryRetainLimit(value: Int) = viewModelScope.launch {
        repository.setHistoryRetainLimit(value)
        // Apply the new limit immediately instead of waiting for the next session boundary.
        runCatching { sessionRepository.fifoSweep() }
    }

    fun deleteAllData(resetSettings: Boolean) {
        val context = getApplication<Application>()
        if (ServiceLocator.session.snapshot.value.currentSessionId != null) {
            _events.trySend(context.getString(R.string.settings_delete_all_blocked))
            return
        }
        ServiceLocator.appScope.launch {
            AppDataWiper(
                clearDatabase = { ServiceLocator.database.clearAllTables() },
                fileStore = ServiceLocator.fileStore,
                favoriteFileStore = ServiceLocator.favoriteFileStore,
                tempFiles = {
                    listOf(
                        File(context.filesDir, "import_temp.zip"),
                        File(context.filesDir, "archive_import_temp.zip"),
                    )
                },
                clearSettings = { repository.clearAll() },
                resetRuntime = { ServiceLocator.reset() },
            ).wipe(resetSettings)
            if (resetSettings) {
                withContext(Dispatchers.Main) {
                    AppLanguageManager.set(context, AppLanguage.SYSTEM)
                }
            }
            _events.trySend(context.getString(R.string.settings_delete_all_done))
        }
    }
}
