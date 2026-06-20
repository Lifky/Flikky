package com.example.flikky.ui.settings.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.PresetTheme
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.presetScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    current: FlikkySettings,
    onSelectMode: (ThemeMode) -> Unit,
    onSelectPreset: (PresetTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dark = isSystemInDarkTheme()

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
                text = "主题",
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
                        text = "跟随壁纸（动态色）",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Android 12+ 自动提取主色",
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

            // Preset swatches — visible when PRESET mode
            if (current.themeMode == ThemeMode.PRESET) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "预设色彩",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    PresetTheme.values().forEach { preset ->
                        val scheme = presetScheme(preset, dark)
                        val isSelected = current.presetTheme == preset
                        val label = when (preset) {
                            PresetTheme.CORAL    -> "珊瑚"
                            PresetTheme.MUSHROOM -> "蘑菇"
                            PresetTheme.TEAL     -> "青黛"
                            PresetTheme.MIST     -> "雾霭"
                        }
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
                                            Modifier.border(
                                                3.dp,
                                                scheme.primaryContainer,
                                                CircleShape,
                                            )
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
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
