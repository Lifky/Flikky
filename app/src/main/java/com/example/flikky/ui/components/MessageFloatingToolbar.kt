package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 悬浮消息操作工具栏：一行 icon button，对选中消息的可用操作。复用 MessageAction。
 *
 * 实现选型：material3 的 HorizontalFloatingToolbar（Expressive）此版本仍是实验 API，
 * 其签名以 expanded/floatingActionButton 为中心，套一排纯 IconButton 并不顺手；
 * 改用 Surface 版本，视觉等价（28dp 圆角 + surfaceContainer + 阴影），且无实验 API 风险。
 */
@Composable
fun MessageFloatingToolbar(
    actions: List<MessageAction>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            actions.forEach { a ->
                IconButton(onClick = a.onClick) {
                    Icon(
                        painter = a.icon,
                        contentDescription = a.label,
                        tint = if (a.danger) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
