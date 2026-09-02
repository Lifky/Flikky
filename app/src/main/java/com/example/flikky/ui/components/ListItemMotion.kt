package com.example.flikky.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.flikky.ui.theme.LocalMotionScale
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.flikky.ui.theme.Motion

/**
 * 列表项增删 / 重排的统一微交互。用官方弹簧 spec —— 位移走 spatial（带轻微回弹），
 * 淡入淡出走 effects（临界阻尼无过冲）—— 并自动叠加全局动画速度档（速度=0 时 [Motion]
 * 退化为瞬切，等价 reduce-motion）。
 *
 * 所有 LazyColumn 的 item 根复用此扩展：改一处即全局生效（token 化）。仅当 item 带稳定 key
 * 时才会触发动画。
 */
@Composable
fun LazyItemScope.flikkyItemAnimation(): Modifier =
    Modifier.animateItem(
        fadeInSpec = Motion.effects(),
        placementSpec = Motion.spatial(),
        fadeOutSpec = Motion.effects(),
    )

/*
 * 这里曾有一个 `StreamedListItem`：流式追加时给每行排一个封顶的阶梯延迟，
 * 用 `AnimatedVisibility(visible = shown)`（shown 初值 false）实现入场。
 *
 * 它是错的，而且一个错法产出三个症状（v1.20.0 装机验收）：
 *   1. `visible = false` 时 AnimatedVisibility 不组合内容，item 高度为 0。
 *      lazy 布局靠累加 item 高度判断「视口填满了没有」，零高推不动它，于是继续
 *      往下组合；等入场落地拿到真实高度，布局又变，循环重来 —— 表现为
 *      「条目不出现，必须下拉到底部才再冒出 1~2 行」。
 *   2. `remember` 活在 item 的组合里，而 lazy 布局在 item 离开视口时销毁它的组合，
 *      滚回来是全新组合、初值又是 false —— 动效反复重播。
 *   3. 零高导致超量向前组合，每行还带一个 LaunchedEffect，切 tab 时全部销毁 —— 卡顿。
 *
 * 结论：**会回收的列表里不做逐行入场**。节奏交给流式批次之间的间隔，
 * 增删与重排交给上面的 [flikkyItemAnimation]（`animateItem` 由 lazy 布局按 key
 * 自己跟踪，不受回收影响）。浏览器端仍然做逐行阶梯，因为 DOM 节点不回收
 * （见 `panels.css` 的 `.fk-files-list > .fk-item`）—— 这是两端刻意的不对称。
 *
 * 守卫见 `test/.../ui/LazyItemHeightConventionTest.kt`。
 */
