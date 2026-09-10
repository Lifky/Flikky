package com.example.flikky.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.flikky.ui.theme.Motion

/**
 * 一个「表头行 + 可展开子行」的设置区块。
 *
 * ## 为什么需要它（2026-09-08 装机反馈）
 *
 * 「导入与导出」从展开缩回时，它与下一行的间隔会先大一截、卡一下才恢复正常。
 *
 * 原因是 [AnimatedVisibility] 此前直接坐在 [SettingSection] 的内层
 * `Column(verticalArrangement = spacedBy(SegmentedGap))` 里。收起动画期间它的高度
 * 趋近 0，但**仍然占着一个子项位**，于是父 Column 在它前后各放一个 gap：
 *
 * ```
 * 表头 |gap| AnimatedVisibility(高≈0) |gap| 下一行     ← 两倍间隔
 * ```
 *
 * 动画结束、节点被移除之后才变回一倍 —— 用户看到的「卡一下」就是这一跳。
 *
 * ## 修法
 *
 * 两件事一起做，缺一件都不够：
 *
 * 1. **表头与展开区收成单一子项**：外层这个 [Column] 对父 Column 只算一个孩子，
 *    所以父 Column 前后各一个 gap 永远是正确的一倍。
 * 2. **gap 移进 [AnimatedVisibility] 内部**（内容的 `padding(top = SegmentedGap)`）。
 *    `shrinkVertically` 裁的是内容高度，内边距随之一起被裁掉；写在外面就等于
 *    永久多一个 gap，收起后表头与下一行会永远贴太开。
 *
 * 外层 Column **刻意不设 `verticalArrangement`**：表头与展开区之间的唯一间距来源
 * 就是上面那个内部 padding。设了就又变成「gap 在外面」，缺陷照旧。
 *
 * 动效规格照搬改动前那两处，逐字相同 —— 这次修的是布局，不是动效。
 * 守卫单测见 `ui/settings/SettingExpandableGapTest`。
 *
 * @param expanded 展开态。
 * @param header 常驻可见的表头行（通常是一个带 chevron 的 [SettingItem]）。
 * @param content 展开后才出现的子行；多行之间按官方 `SegmentedGap` 排布。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingExpandableGroup(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        header()
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
            // 收起用**不回弹**的 spatial：官方 fastSpatial 阻尼比 0.6，收到 0 时会
            // 先冲过头再弹回几个 dp，内容越高越明显（2026-09-10 装机反馈）。
            // 进场保留官方回弹 —— 展开有弹性是对的，收起弹回来像渲染故障。
            exit = shrinkVertically(Motion.spatialFastNoBounce()) + fadeOut(Motion.effectsFast()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ListItemDefaults.SegmentedGap),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                content()
            }
        }
    }
}
