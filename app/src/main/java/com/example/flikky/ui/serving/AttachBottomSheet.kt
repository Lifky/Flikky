package com.example.flikky.ui.serving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.components.OptionCard
import com.example.flikky.ui.theme.Spacing

/**
 * Bottom sheet with two square action cards for picking files or images.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachBottomSheet(
    onPickFile: () -> Unit,
    onPickImage: () -> Unit,
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
                "添加",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OptionCard(
                    iconRes = R.drawable.ic_attach_file,
                    label = "文件",
                    onClick = onPickFile,
                    modifier = Modifier.weight(1f),
                    description = "任意类型",
                )
                OptionCard(
                    iconRes = R.drawable.ic_image,
                    label = "图片",
                    onClick = onPickImage,
                    modifier = Modifier.weight(1f),
                    description = "从相册选择",
                )
            }
        }
    }
}
