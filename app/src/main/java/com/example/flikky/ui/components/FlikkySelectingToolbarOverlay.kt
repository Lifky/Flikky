package com.example.flikky.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing

/**
 * 多选态浮动操作栏的共用「外壳」：底部居中悬浮 + 进出上滑动画。首页 / 收藏页共用，消除重复。
 *
 * 关键：它是**悬浮 overlay**，不是 `Scaffold.bottomBar`。floating toolbar 的语义是「浮在内容
 * 之上」（MD3：floats above the body content）——放进 `bottomBar` 槽位会被 Scaffold 当成占
 * 布局空间的栏，预留等高空白把列表顶走（曾导致「空白挡住内容」的 bug）。调用方须把本组件作为
 * 内容区 Box 的子节点，并传入 `Modifier.align(Alignment.BottomCenter)`。
 *
 * 不加 `navigationBarsPadding()`：首页/收藏页在 `MainActivity` 已被外层
 * `Box(padding(bottom = navbar inset))` 抬过一次，这里再加会双重 inset；只留 [Spacing.md] 间距，
 * 与 `MessageFloatingToolbarOverlay`（Serving/History）同范式。
 *
 * @param visible 多选态；切换时整条栏上滑进/出。
 * @param content 具体的工具栏（[com.example.flikky.ui.home] 的 `SelectingFloatingToolbar` /
 *   [com.example.flikky.ui.favorites.FavoritesSelectingToolbar]），内部走 [FlikkyFloatingToolbar]。
 */
@Composable
fun FlikkySelectingToolbarOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(Motion.spatial()) { it },
        exit = slideOutVertically(Motion.spatialFast()) { it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
