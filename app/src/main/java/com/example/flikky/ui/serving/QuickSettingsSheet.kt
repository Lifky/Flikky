package com.example.flikky.ui.serving

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.settings.BUBBLE_CORNER_MAX
import com.example.flikky.data.settings.BUBBLE_CORNER_MIN
import com.example.flikky.data.settings.AvatarGroupingMode
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.ui.settings.localizedLabel
import com.example.flikky.ui.theme.Spacing

/**
 * 进行中会话的快捷设置 bottom sheet。
 *
 * 会话运行期间底部「设置」tab 被锁（[MainActivity] 的 `settingsEnabled = !servingActive`），
 * 用户进不了设置页。这里把会话中最常调的两项——气泡圆角、深色模式——就近放到会话顶栏，
 * 写的是与设置页同一份 settings（经 [ServingViewModel]→SettingsRepository），
 * 所以一处改动即 App 气泡 + 已连浏览器气泡 + 设置页全同步。
 *
 * 气泡圆角 slider 复用设置页样板（SettingsScreen 的「气泡圆角」段）：本地 [radiusDraft]
 * 实时反馈，松手才提交到 DataStore。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    bubbleCornerRadius: Int,
    avatarGrouping: AvatarGroupingMode,
    darkMode: DarkMode,
    onSetBubbleCorner: (Int) -> Unit,
    onSetAvatarGrouping: (AvatarGroupingMode) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxxl),
        ) {
            Text(
                stringResource(R.string.quick_settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )

            // ── 气泡圆角 ──
            var radiusDraft by remember(bubbleCornerRadius) {
                mutableStateOf(bubbleCornerRadius.toFloat())
            }
            Text(
                stringResource(R.string.quick_settings_bubble_corner, radiusDraft.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = radiusDraft,
                onValueChange = { radiusDraft = it },
                valueRange = BUBBLE_CORNER_MIN.toFloat()..BUBBLE_CORNER_MAX.toFloat(),
                steps = BUBBLE_CORNER_MAX - BUBBLE_CORNER_MIN - 1,
                onValueChangeFinished = { onSetBubbleCorner(radiusDraft.toInt()) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.quick_settings_avatar_display),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
            )
            val avatarModes = listOf(
                AvatarGroupingMode.FIRST to stringResource(R.string.quick_settings_avatar_first),
                AvatarGroupingMode.LAST to stringResource(R.string.quick_settings_avatar_last),
                AvatarGroupingMode.EACH to stringResource(R.string.quick_settings_avatar_each),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                avatarModes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = avatarGrouping == mode,
                        onClick = { onSetAvatarGrouping(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = avatarModes.size),
                    ) {
                        Text(label)
                    }
                }
            }

            // ── 深色模式 ──
            Text(
                stringResource(R.string.quick_settings_dark_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
            )
            val modes = DarkMode.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = darkMode == mode,
                        onClick = { onSetDarkMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(
                            if (mode == DarkMode.SYSTEM) {
                                stringResource(R.string.quick_settings_dark_system)
                            } else {
                                mode.localizedLabel()
                            },
                        )
                    }
                }
            }
        }
    }
}
