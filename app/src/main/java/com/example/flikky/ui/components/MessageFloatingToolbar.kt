package com.example.flikky.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 悬浮消息操作工具栏：一行 icon button，对选中消息的可用操作。复用 [MessageAction]。
 *
 * 容器统一走 [FlikkyFloatingToolbar]（MD3 floating toolbar 规格：surfaceContainer +
 * 50% 圆角 + 抬升），与主页/收藏页的多选浮动栏样式完全一致——此前这里单独用
 * surfaceContainerHigh + extraLarge 圆角导致 history/serving 的工具栏与主页不一样。
 */
@Composable
fun MessageFloatingToolbar(
    actions: List<MessageAction>,
    modifier: Modifier = Modifier,
) {
    FlikkyFloatingToolbar(modifier = modifier) {
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
