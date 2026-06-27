package com.example.flikky.ui.components

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
