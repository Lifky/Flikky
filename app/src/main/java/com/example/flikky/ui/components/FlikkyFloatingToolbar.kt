package com.example.flikky.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 共用的 MD3 floating toolbar 容器（胶囊浮动操作栏）。
 *
 * 已迁到官方 Expressive 组件 [HorizontalFloatingToolbar]（material3 1.5.0-alpha，
 * `@OptIn(ExperimentalMaterial3ExpressiveApi)`）——原手搓的 `Surface + Row` 实现已删除。
 * 容器色 / 全圆胶囊 / 内边距走官方 standard 默认规格；首页多选栏 / 收藏页多选栏 /
 * 消息操作栏共用。
 *
 * - `expanded = true`：常显（不接 scroll-collapse 折叠行为）。
 * - 海拔：组件库 standard 默认是 Level 0（无阴影），按 MD3 floating toolbar spec
 *   显式给 Elevation Level 3（6dp）；`FloatingToolbarDefaults` 无 Level 3 公开常量，故写值。
 *
 * 调用方只传 [content]（一串 IconButton）。
 */
private val FloatingToolbarElevation = 6.dp // MD3 Elevation Level 3

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlikkyFloatingToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        expandedShadowElevation = FloatingToolbarElevation,
        collapsedShadowElevation = FloatingToolbarElevation,
        content = content,
    )
}

/**
 * 浮动工具栏胶囊高度估值（64dp，MD3 floating toolbar 标准高度）。History 用它把 snackbar 抬到
 * 浮动栏上方，避免底部中央的 snackbar 与同样底部中央的浮动操作栏相互遮挡。
 */
val FlikkyFloatingToolbarHeight = 64.dp
