package com.example.flikky.ui.settings.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.ui.theme.Spacing

/**
 * 把任意 hue (0..360) 转成 MD3 极浅 tone 的纯色：HSL 高亮度 + 低饱和，保证深色文字可读。
 */
internal fun lightToneFromHue(hue: Float): Long {
    val c = Color.hsl(hue.coerceIn(0f, 360f), saturation = 0.45f, lightness = 0.94f)
    return c.toArgb().toLong() and 0xFFFFFFFFL or 0xFF000000L
}

/**
 * 从 argb 反推 HSL 色相（0..360），用于重新打开面板时让自定义 slider 回到当前背景对应位置。
 * 灰色（无色相）回退默认 210。
 */
internal fun hueFromArgb(argb: Long): Float {
    val color = Color(argb.toInt())
    val r = color.red; val g = color.green; val b = color.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val d = max - min
    if (d <= 0.0001f) return 210f
    val h = when (max) {
        r -> 60f * (((g - b) / d).mod(6f))
        g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    return (h % 360f + 360f) % 360f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(
    current: BackgroundSetting,
    onSelect: (BackgroundSetting) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // distinct(): 某些主题（如珊瑚/蘑菇）secondaryContainer 与 tertiaryContainer 同色，
    // 去重避免两个相同色块、以及按颜色判断选中时的联动高亮。
    val presets: List<Long> = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    ).map { it.toArgb().toLong() and 0xFFFFFFFFL or 0xFF000000L }.distinct()

    // 重新打开面板时 slider 回到当前背景对应色相（解决"背景已变但 slider 不回读"）。
    var hue by remember(current) {
        mutableFloatStateOf(
            if (current is BackgroundSetting.Solid) hueFromArgb(current.argb) else 210f
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenEdge).padding(bottom = Spacing.xxxl),
        ) {
            Text(
                stringResource(R.string.background_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    BackgroundSetting.Default to stringResource(R.string.common_default),
                    BackgroundSetting.Blank to stringResource(R.string.common_blank),
                ).forEach { (setting, label) ->
                    val selected = current == setting
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    if (setting == BackgroundSetting.Default) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.extraSmall,
                                )
                                .clickable { onSelect(setting) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(R.string.background_theme_solid), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                presets.forEach { argb ->
                    val selected = current is BackgroundSetting.Solid && current.argb == argb
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(argb))
                            .border(if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape)
                            .clickable { onSelect(BackgroundSetting.Solid(argb)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(R.string.background_custom_hue), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.sm))
            val previewArgb = lightToneFromHue(hue)
            Box(Modifier.fillMaxWidth().height(40.dp).clip(MaterialTheme.shapes.extraSmall).background(Color(previewArgb)))
            Slider(
                value = hue,
                onValueChange = { hue = it },
                valueRange = 0f..360f,
                onValueChangeFinished = { onSelect(BackgroundSetting.Solid(lightToneFromHue(hue))) },
            )
        }
    }
}
