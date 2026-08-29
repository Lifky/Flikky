package com.example.flikky.ui.serving.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.ui.theme.Spacing

/**
 * 会话页「文件」tab：本机共享存储浏览器。
 *
 * 本任务（Task 11）只做两态骨架——未授权时的权限解释卡、已授权时的空占位。
 * 目录列表在 Task 13 接上。
 *
 * ## 这个 composable 刻意不知道 `storageBrowsingEnabled`
 *
 * `storageBrowsingEnabled` 是**给对端浏览器用的**主开关（默认关）。App 端浏览自己的存储
 * 只受系统权限约束。四态矩阵里最容易写错的一格是「开关关 + 已授权」——
 * 用同一个布尔量把两端一起门控，会让用户在自己手机上也看不到文件，
 * 而设置项的文案说的是「允许电脑端浏览」。
 *
 * 因此这里连参数都不收：拿不到的东西没法误用。守卫见
 * `ui/serving/ServingStorageTabScopeTest`。
 */
@Composable
fun ServingStorageTab(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!hasPermission) {
        StoragePermissionCard(onRequestPermission = onRequestPermission, modifier = modifier)
        return
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.serving_storage_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 权限解释卡。变体裁决 D：filled 路线 + `surfaceContainerHigh`、零阴影，
 * 与 `ConnectionInfoCard` / `OptionCard` 同一画法（官方 filled 用的是
 * `surfaceContainerHighest`，本项目既有约定是 High，跟项目走）。
 *
 * 文案如实说明「Android 没有只读版的该权限」——不含糊其辞是这张卡存在的理由。
 */
@Composable
private fun StoragePermissionCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sectionGap),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.sectionGap),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.serving_storage_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.serving_storage_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.serving_storage_permission_grant)) }
            }
        }
    }
}
