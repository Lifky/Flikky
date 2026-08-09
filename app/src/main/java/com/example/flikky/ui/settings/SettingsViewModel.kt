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
import com.example.flikky.network.UpdateChecker
import com.example.flikky.network.UpdateInfo
import com.example.flikky.util.UpdateVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val updateChecker: UpdateChecker = UpdateChecker(),
) : AndroidViewModel(app) {

    val settings: StateFlow<FlikkySettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, FlikkySettings())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    private val _updateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = _updateChecking

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }
    fun setPreset(value: PresetTheme) = viewModelScope.launch { repository.setPresetTheme(value) }
    fun setCustomThemeSeed(value: Long) =
        viewModelScope.launch { repository.setCustomThemeSeed(value) }

    fun setContrast(value: ContrastLevel) = viewModelScope.launch { repository.setContrastLevel(value) }
    fun setDarkMode(value: DarkMode) = viewModelScope.launch { repository.setDarkMode(value) }
    fun setAmoled(value: Boolean) = viewModelScope.launch { repository.setAmoled(value) }
    fun setPhoneAvatar(value: Int) = viewModelScope.launch { repository.setPhoneAvatar(value) }
    fun setPhoneAvatarKey(value: String) = viewModelScope.launch { repository.setPhoneAvatarKey(value) }
    fun setBrowserAvatarKey(value: String) = viewModelScope.launch { repository.setBrowserAvatarKey(value) }
    fun setBackground(value: BackgroundSetting) = viewModelScope.launch { repository.setBackground(value) }
    fun setDeviceName(value: String) = viewModelScope.launch { repository.setDeviceName(value) }
    fun setRecallBeta(value: Boolean) = viewModelScope.launch { repository.setRecallBeta(value) }
    fun setAllowPeerRecall(value: Boolean) =
        viewModelScope.launch { repository.setAllowPeerRecall(value) }

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

    fun setAutoCheckUpdate(value: Boolean) =
        viewModelScope.launch { repository.setAutoCheckUpdate(value) }

    fun checkForUpdate() {
        if (_updateChecking.value) return
        viewModelScope.launch {
            _updateChecking.value = true
            try {
                val info = updateChecker.check()
                if (info == null) {
                    _events.send(getApplication<Application>().getString(R.string.settings_update_failed))
                    return@launch
                }
                if (UpdateVersion.parse(info.tagName) == null) {
                    _events.send(getApplication<Application>().getString(R.string.settings_update_failed))
                    return@launch
                }
                repository.setLastUpdateCheckAt(System.currentTimeMillis())
                if (UpdateVersion.isNewer(info.tagName, currentVersionName())) {
                    _updateAvailable.value = info
                } else {
                    _events.send(getApplication<Application>().getString(R.string.settings_update_latest))
                }
            } finally {
                _updateChecking.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateAvailable.value = null
    }

    private fun currentVersionName(): String? = runCatching {
        val app = getApplication<Application>()
        app.packageManager
            .getPackageInfo(app.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            .versionName
    }.getOrNull()

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
