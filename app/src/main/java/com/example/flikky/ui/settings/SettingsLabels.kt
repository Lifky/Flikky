package com.example.flikky.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.data.settings.AnimationSpeed
import com.example.flikky.data.settings.AppLanguage
import com.example.flikky.data.settings.AvatarGroupingMode
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.data.settings.ContrastLevel
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.data.settings.PresetTheme

@Composable
internal fun AppLanguage.localizedLabel(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_simplified_chinese
        AppLanguage.ENGLISH -> R.string.language_english
    }
)

@Composable
internal fun PresetTheme.localizedLabel(): String = stringResource(labelResource())

@StringRes
internal fun PresetTheme.labelResource(): Int = when (this) {
    PresetTheme.DANSHU_RED -> R.string.theme_preset_danshu_red
    PresetTheme.DANZI_RED -> R.string.theme_preset_danzi_red
    PresetTheme.CHENGPI_YELLOW -> R.string.theme_preset_chengpi_yellow
    PresetTheme.QIUKUI_YELLOW -> R.string.theme_preset_qiukui_yellow
    PresetTheme.ANAN_BLUE -> R.string.theme_preset_anan_blue
    PresetTheme.ZHUMU_GRAY -> R.string.theme_preset_zhumu_gray
    PresetTheme.YINGWU_GREEN -> R.string.theme_preset_yingwu_green
    PresetTheme.JIEHUA_PURPLE -> R.string.theme_preset_jiehua_purple
}

@Composable
internal fun ContrastLevel.localizedLabel(): String = stringResource(
    when (this) {
        ContrastLevel.SYSTEM -> R.string.theme_contrast_system
        ContrastLevel.STANDARD -> R.string.theme_contrast_standard
        ContrastLevel.MEDIUM -> R.string.theme_contrast_medium
        ContrastLevel.HIGH -> R.string.theme_contrast_high
    }
)

@Composable
internal fun DarkMode.localizedLabel(): String = stringResource(
    when (this) {
        DarkMode.SYSTEM -> R.string.settings_dark_mode_system
        DarkMode.LIGHT -> R.string.settings_dark_mode_light
        DarkMode.DARK -> R.string.settings_dark_mode_dark
    }
)

@Composable
internal fun AnimationSpeed.localizedLabel(): String = stringResource(
    when (this) {
        AnimationSpeed.OFF -> R.string.settings_animation_off
        AnimationSpeed.SLOW -> R.string.settings_animation_slow
        AnimationSpeed.STANDARD -> R.string.settings_animation_standard
        AnimationSpeed.FAST -> R.string.settings_animation_fast
    }
)

@Composable
internal fun AvatarGroupingMode.localizedLabel(): String = stringResource(
    when (this) {
        AvatarGroupingMode.FIRST -> R.string.settings_avatar_first
        AvatarGroupingMode.LAST -> R.string.settings_avatar_last
        AvatarGroupingMode.EACH -> R.string.settings_avatar_each
    }
)

@Composable
internal fun MessageActionStyle.localizedLabel(): String = stringResource(
    when (this) {
        MessageActionStyle.FLOATING -> R.string.settings_message_action_floating
        MessageActionStyle.INLINE -> R.string.settings_message_action_inline
    }
)

@Composable
internal fun BackgroundSetting.localizedLabel(): String = stringResource(
    when (this) {
        BackgroundSetting.Default -> R.string.common_default
        BackgroundSetting.Blank -> R.string.common_blank
        is BackgroundSetting.Solid -> R.string.common_solid_color
    }
)
