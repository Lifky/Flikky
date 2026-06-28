package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.theme.Spacing

/**
 * 共用的 MD3 floating toolbar 容器（胶囊浮动操作栏）。
 *
 * material3 1.4.0 stable 把 Expressive 的 `HorizontalFloatingToolbar` 关在 internal
 * 注解后用不了（见 traps T17），所以按 MD3 floating toolbar 规格用稳定组件手搓：
 * - 容器高度固定 [FlikkyFloatingToolbarHeight]（64dp），让胶囊「饱满」而不是紧贴 48dp 图标按钮显得又瘦又挤；
 * - 全圆角胶囊（`percent = 50`）、`surfaceContainer` 容器色（MD3 standard floating
 *   toolbar 规格容器色，比 `surfaceContainerHighest` 浅一档，更轻盈）；
 * - 内部横向留 [Spacing.sm] padding、元素间 [Spacing.xs] 间距，纵向居中。
 *
 * **不加海拔阴影**（按设计要求去掉）：胶囊靠 `surfaceContainer` 与页面背景的色阶差与页面内容拉开层次，
 * 保持扁平。
 *
 * 调用方只传 [content]（一串 IconButton），首页多选栏 / 收藏页多选栏 / 消息操作栏共用同一规格。
 */
@Composable
fun FlikkyFloatingToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.height(FlikkyFloatingToolbarHeight),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            content = content,
        )
    }
}

/**
 * 浮动工具栏胶囊高度（64dp）。对外暴露：History/Serving 用它把 snackbar 抬到浮动栏上方，
 * 避免底部中央的 snackbar 与同样底部中央的浮动操作栏相互遮挡。
 */
val FlikkyFloatingToolbarHeight = 64.dp
