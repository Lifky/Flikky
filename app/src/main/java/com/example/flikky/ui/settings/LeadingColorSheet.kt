package com.example.flikky.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.settings.components.SettingItem
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.resolveLeadingColors
import com.example.flikky.util.LeadingColorMode
import com.example.flikky.util.LeadingVisualCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadingColorSheet(
    current: LeadingColorMode,
    onSelect: (LeadingColorMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val dark = colorScheme.background.luminance() < 0.5f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.leading_color_title),
                style = MaterialTheme.typography.titleMedium,
            )

            SettingSection(title = null) {
                LeadingColorMode.entries.forEachIndexed { index, mode ->
                    val preview = resolveLeadingColors(mode, colorScheme, dark)
                    SettingItem(
                        title = mode.localizedOptionLabel(),
                        onClick = { onSelect(mode) },
                        index = index,
                        total = LeadingColorMode.entries.size,
                        trailing = if (current == mode) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = stringResource(R.string.common_enabled),
                                    tint = colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                        content = {
                            Row(
                                modifier = Modifier.padding(top = Spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                LeadingVisualCatalog.types.forEach { type ->
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                color = preview.getValue(type.id).container,
                                                shape = CircleShape,
                                            ),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
