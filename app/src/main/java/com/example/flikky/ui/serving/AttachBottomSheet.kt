package com.example.flikky.ui.serving

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R

/**
 * 「添加」底部面板：两个 Tab（附件 / 图片），各一张操作卡片。
 * 点卡片即启动对应选择器并由调用方关闭面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachBottomSheet(
    onPickFile: () -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("添加", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("附件") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("图片") })
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> AttachOptionCard(
                    iconRes = R.drawable.ic_attach_file,
                    title = "选择文件",
                    desc = "任意类型，使用系统文件选择器",
                    onClick = onPickFile,
                )
                else -> AttachOptionCard(
                    iconRes = R.drawable.ic_image,
                    title = "选择图片",
                    desc = "从相册选择（Android 照片选择器）",
                    onClick = onPickImage,
                )
            }
        }
    }
}

@Composable
private fun AttachOptionCard(
    iconRes: Int,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
