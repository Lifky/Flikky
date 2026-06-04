package com.example.flikky.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.FlikkySettings
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
) : AndroidViewModel(app) {

    val settings: StateFlow<FlikkySettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, FlikkySettings())

    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { repo.setThemeMode(v) }
    fun setPreset(v: PresetTheme) = viewModelScope.launch { repo.setPresetTheme(v) }
    fun setDarkMode(v: DarkMode) = viewModelScope.launch { repo.setDarkMode(v) }
    fun setAmoled(v: Boolean) = viewModelScope.launch { repo.setAmoled(v) }
    fun setPhoneAvatar(v: Int) = viewModelScope.launch { repo.setPhoneAvatar(v) }
    fun setBackground(v: BackgroundSetting) = viewModelScope.launch { repo.setBackground(v) }
    fun setDeviceName(v: String) = viewModelScope.launch { repo.setDeviceName(v) }
    fun setRecallBeta(v: Boolean) = viewModelScope.launch { repo.setRecallBeta(v) }
    fun setHistoryRetainLimit(v: Int) = viewModelScope.launch { repo.setHistoryRetainLimit(v) }
}
