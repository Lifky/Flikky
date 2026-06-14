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
 * 改用 Surface 版本，视觉等价（28dp 圆角 + surfaceContainerHigh），且无实验 API 风险。
 *
 * 去阴影（shadowElevation = 0）：scale-in 动画期间阴影会随缩放抖动，观感廉价；
 * 改用 surfaceContainerHigh 的色阶差自然「浮」于会话之上，无投影 jank。
 */
@Composable
fun MessageFloatingToolbar(
    actions: List<MessageAction>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
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

/**
 * 共享的悬浮工具栏 overlay：Serving 与 History 共用，消除两端重复。
 *
 * 顶层 wrapper 让 AnimatedVisibility 解析到非 scoped 重载——调用方常把此 overlay
 * 放在 Box（嵌于 Column）内，BoxScope/ColumnScope 同时作为隐式 receiver 会让裸
 * AnimatedVisibility 调用产生歧义；提到这里消除歧义。
 */
@Composable
fun MessageFloatingToolbarOverlay(
    visible: Boolean,
    actions: List<MessageAction>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = androidx.compose.animation.scaleIn(
            androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f)
        ) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
    ) {
        MessageFloatingToolbar(actions = actions)
    }
}
