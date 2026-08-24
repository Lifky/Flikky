package com.example.flikky.ui.settings.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.settings.ContrastLevel
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.PresetTheme
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.customScheme
import com.example.flikky.ui.theme.presetScheme
import com.example.flikky.ui.settings.localizedLabel
import com.example.flikky.util.ThemeHsv
import com.example.flikky.util.formatThemeSeed
import com.example.flikky.util.parseThemeSeed
import com.example.flikky.util.themeHsvToSeed
import com.example.flikky.util.themeSeedToHsv

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemePickerSheet(
    current: FlikkySettings,
    onSelectMode: (ThemeMode) -> Unit,
    onSelectPreset: (PresetTheme) -> Unit,
    onSelectCustomSeed: (Long) -> Unit,
    /**
     * 对比度回调。传 null 即**不渲染**对比度那一段。
     *
     * 用「回调是否存在」当开关，而不是再加一个 showContrast 布尔：两个参数会造出
     * 「隐藏了却还接着一个活回调」和「显示了却没有回调」这两种不可能状态。
     * 会话中的快捷设置传 null —— 对比度不进 PeerInfoDto，浏览器跟不了，
     * 而快捷设置的承诺是「这里每一项都会同步」（用户裁决）。
     */
    onSelectContrast: ((ContrastLevel) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dark = isSystemInDarkTheme()
    var customSeedInput by rememberSaveable(current.customThemeSeedArgb) {
        mutableStateOf(formatThemeSeed(current.customThemeSeedArgb))
    }
    var showCustomColorDialog by rememberSaveable { mutableStateOf(false) }
    var customHue by rememberSaveable { mutableFloatStateOf(0f) }
    var customSaturation by rememberSaveable { mutableFloatStateOf(0f) }
    var customValue by rememberSaveable { mutableFloatStateOf(0f) }
    val parsedCustomSeed = remember(customSeedInput) { parseThemeSeed(customSeedInput) }
    val customSeedBodyLength = customSeedInput.trim().removePrefix("#").length
    val showCustomSeedError = parsedCustomSeed == null && customSeedBodyLength >= 6

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxxl),
        ) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )

            // Dynamic toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectMode(
                            if (current.themeMode == ThemeMode.DYNAMIC) ThemeMode.PRESET
                            else ThemeMode.DYNAMIC
                        )
                    }
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.theme_dynamic),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.theme_dynamic_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = current.themeMode == ThemeMode.DYNAMIC,
                    onCheckedChange = { on ->
                        onSelectMode(if (on) ThemeMode.DYNAMIC else ThemeMode.PRESET)
                    },
                )
            }

            // Dynamic color hides manual controls; preset and custom share the same contrast setting.
            if (current.themeMode != ThemeMode.DYNAMIC) {
                Spacer(Modifier.height(Spacing.lg))
                val customSelected = current.themeMode == ThemeMode.CUSTOM
                val customPreviewScheme = remember(current.customThemeSeedArgb, dark) {
                    customScheme(current.customThemeSeedArgb, dark)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            customSeedInput = formatThemeSeed(current.customThemeSeedArgb)
                            themeSeedToHsv(current.customThemeSeedArgb).let { hsv ->
                                customHue = hsv.hue
                                customSaturation = hsv.saturation
                                customValue = hsv.value
                            }
                            showCustomColorDialog = true
                        }
                        .padding(vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.theme_custom),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (customSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = formatThemeSeed(current.customThemeSeedArgb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(customPreviewScheme.primary)
                            .then(
                                if (customSelected) {
                                    Modifier.border(
                                        3.dp,
                                        customPreviewScheme.primaryContainer,
                                        CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (customSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = customPreviewScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(R.string.theme_presets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
                // 8 个命名主题：4 列 × 2 行自动换行；swatch 取标准对比度的 primary 作为主题身份色。
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    maxItemsInEachRow = 4,
                ) {
                    PresetTheme.entries.forEach { preset ->
                        val scheme = presetScheme(preset, dark)
                        val isSelected = current.themeMode == ThemeMode.PRESET &&
                            current.presetTheme == preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(scheme.primary)
                                    .then(
                                        if (isSelected)
                                            Modifier.border(3.dp, scheme.primaryContainer, CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        onSelectMode(ThemeMode.PRESET)
                                        onSelectPreset(preset)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = scheme.onPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = preset.localizedLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 对比度档：跟随系统 / 标准 / 中 / 高（每个主题都备有三套 role）。
                if (onSelectContrast != null) {
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        text = stringResource(R.string.theme_contrast),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val levels = ContrastLevel.entries
                        levels.forEachIndexed { index, level ->
                            SegmentedButton(
                                selected = current.contrastLevel == level,
                                onClick = { onSelectContrast(level) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
                            ) {
                                Text(level.localizedLabel())
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomColorDialog) {
        AlertDialog(
            onDismissRequest = { showCustomColorDialog = false },
            title = { Text(stringResource(R.string.theme_custom)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ThemeColorPicker(
                        hsv = ThemeHsv(customHue, customSaturation, customValue),
                        onHsvChange = { hsv ->
                            customHue = hsv.hue
                            customSaturation = hsv.saturation
                            customValue = hsv.value
                            customSeedInput = formatThemeSeed(themeHsvToSeed(hsv))
                        },
                    )
                    OutlinedTextField(
                        value = customSeedInput,
                        onValueChange = { value ->
                            if (value.length <= 7) {
                                customSeedInput = value
                                parseThemeSeed(value)?.let { seed ->
                                    themeSeedToHsv(seed).let { hsv ->
                                        customHue = hsv.hue
                                        customSaturation = hsv.saturation
                                        customValue = hsv.value
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.theme_custom_hex)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (showCustomSeedError) {
                                        R.string.theme_custom_invalid
                                    } else {
                                        R.string.theme_custom_summary
                                    },
                                ),
                            )
                        },
                        isError = showCustomSeedError,
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsedCustomSeed?.let { seed ->
                            onSelectCustomSeed(seed)
                            onSelectMode(ThemeMode.CUSTOM)
                            showCustomColorDialog = false
                        }
                    },
                    enabled = parsedCustomSeed != null,
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomColorDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
