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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.flikky.data.settings.BackgroundSetting

/**
 * Returns the gradient brush for the given name.
 * Names: "sunset", "forest", "ocean".
 * Reused by M7's ConversationBackground.
 */
fun gradientBrush(name: String): Brush = when (name) {
    "sunset" -> Brush.linearGradient(listOf(Color(0xFFFF7043), Color(0xFFFF4081)))
    "forest" -> Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFF81C784)))
    "ocean"  -> Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF4FC3F7)))
    else     -> Brush.linearGradient(listOf(Color.Gray, Color.LightGray))
}

private val SOLID_COLORS: List<Long> = listOf(
    0xFFEF5350, // red
    0xFFEC407A, // pink
    0xFF7E57C2, // purple
    0xFF42A5F5, // blue
    0xFF26A69A, // teal
    0xFF66BB6A, // green
)

private val GRADIENTS: List<String> = listOf("sunset", "forest", "ocean")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(
    current: BackgroundSetting,
    onSelect: (BackgroundSetting) -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "会话背景",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Default + Blank options
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(
                    BackgroundSetting.Default to "默认",
                    BackgroundSetting.Blank   to "空白",
                ).forEach { (setting, label) ->
                    val isSelected = current == setting
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (setting == BackgroundSetting.Default)
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                                .then(
                                    if (isSelected)
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(8.dp),
                                        )
                                    else
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(8.dp),
                                        )
                                )
                                .clickable { onSelect(setting) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
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

            Spacer(Modifier.height(20.dp))

            // Solid color swatches
            Text(
                text = "纯色",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SOLID_COLORS.forEach { argb ->
                    val isSelected = current is BackgroundSetting.Solid && current.argb == argb
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .then(
                                if (isSelected)
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                else Modifier
                            )
                            .clickable { onSelect(BackgroundSetting.Solid(argb)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Gradient previews
            Text(
                text = "渐变",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GRADIENTS.forEach { name ->
                    val isSelected = current is BackgroundSetting.Gradient && current.name == name
                    val label = when (name) {
                        "sunset" -> "日落"
                        "forest" -> "森林"
                        "ocean"  -> "海洋"
                        else     -> name
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(gradientBrush(name))
                                .then(
                                    if (isSelected)
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(8.dp),
                                        )
                                    else Modifier
                                )
                                .clickable { onSelect(BackgroundSetting.Gradient(name)) },
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
                        Spacer(Modifier.height(4.dp))
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
