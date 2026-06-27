@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.example.flikky.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.example.flikky.ui.theme.Motion

/**
 * [SharedTransitionLayout][androidx.compose.animation.SharedTransitionLayout] 的 scope 与当前 Nav
 * 目的地的 [AnimatedVisibilityScope]，经 CompositionLocal 下传 —— hero 的两端（会话卡深埋在
 * HomeScreen → LazyColumn → SessionRow，详情在 HistoryScreen）分处不同 composable 子树，靠 local
 * 取 scope 比穿透多层签名更干净。未包在 SharedTransitionLayout / 目的地内时为 null（静默降级）。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * hero 容器转场：会话卡 ↔ History 详情「整卡展开成全屏」。给同一 [key] 的两端各挂一个 sharedBounds，
 * bounds 走 spatial 弹簧（受全局动画速度档统辖），内容淡入淡出完成换装。
 *
 * 两端缺一时静默降级（返回原 Modifier 不动画）——例如进行中会话点开走的是 serving（无匹配 key），
 * 或某屏未提供 scope。调用方无需关心实验性 API：本扩展把 [ExperimentalSharedTransitionApi] 封在内部，
 * 签名只暴露稳定类型。
 */
@Composable
fun Modifier.flikkyContainerTransform(key: Any): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val avScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val boundsSpec = Motion.spatial<Rect>()
    val fadeSpec = Motion.effects<Float>()
    return with(sharedScope) {
        this@flikkyContainerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = avScope,
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
            boundsTransform = BoundsTransform { _, _ -> boundsSpec },
        )
    }
}
