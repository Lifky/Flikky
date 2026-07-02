package com.example.flikky.ui.settings.sheets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.components.Avatar
import com.example.flikky.ui.components.AvatarContent
import com.example.flikky.ui.components.AvatarKey
import com.example.flikky.ui.components.PRESET_AVATARS
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    title: String = "选择头像",
    currentKey: String,
    fallbackKey: String = AvatarKey.DEFAULT_PHONE,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedKey = AvatarKey.normalize(currentKey, fallbackKey)
    var charDraft by remember(selectedKey) {
        mutableStateOf(
            selectedKey.takeIf { it.startsWith("char:") }?.removePrefix("char:").orEmpty()
        )
    }
    var iconFilled by remember(selectedKey) {
        mutableStateOf((AvatarKey.parse(selectedKey, fallbackKey) as? AvatarContent.Icon)?.filled ?: false)
    }

    fun sameVisual(left: String, right: String): Boolean {
        val a = AvatarKey.parse(left, fallbackKey)
        val b = AvatarKey.parse(right, fallbackKey)
        return when {
            a is AvatarContent.Icon && b is AvatarContent.Icon -> a.name == b.name && a.filled == b.filled
            a is AvatarContent.Char && b is AvatarContent.Char -> a.value == b.value
            else -> false
        }
    }

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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "填充图标",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = iconFilled,
                    onCheckedChange = { iconFilled = it },
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(PRESET_AVATARS.size) { id ->
                    val preset = PRESET_AVATARS[id]
                    val displayKey = AvatarKey.withFilled(preset.key, iconFilled)
                    val isSelected = sameVisual(displayKey, selectedKey)
                    Box(
                        modifier = Modifier
                            .padding(Spacing.sm)
                            .size(64.dp)
                            .then(
                                if (isSelected)
                                    Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape,
                                    )
                                else Modifier
                            )
                            .clip(CircleShape)
                            .clickable { onSelect(displayKey) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Avatar(avatarKey = displayKey, size = Sizes.rowMinH)
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Avatar(
                    avatarKey = if (charDraft.isBlank()) "char:A" else AvatarKey.char(charDraft),
                    size = Sizes.rowMinH,
                )
                OutlinedTextField(
                    value = charDraft,
                    onValueChange = { charDraft = it.take(1) },
                    singleLine = true,
                    placeholder = { Text("单个字符") },
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = { onSelect(AvatarKey.char(charDraft)) },
                    enabled = charDraft.isNotBlank(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}
