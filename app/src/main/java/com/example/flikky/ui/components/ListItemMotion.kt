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

/**
 * 流式追加时的逐行入场：淡入 + 轻微上移，同一批内按序号做**封顶**的阶梯延迟。
 *
 * ## 为什么不能只靠 [flikkyItemAnimation]
 *
 * 那个扩展管的是 `animateItem`——增删与重排。追加进来的行确实会走它的 `fadeInSpec`，
 * 但一批 24 行是**同时**追加的，于是整批一起淡入，观感是「一块一块地闪」而不是
 * 「一注流水」。这里补的就是批内的先后。
 *
 * ## 为什么阶梯必须封顶
 *
 * 上千行的目录里不封顶就会拖成一场幻灯片（末行要等几十秒）。
 * `panels.css` 曾因此**整个放弃**逐行阶梯、改成整组一次淡入；正确答案不是放弃，
 * 是封顶：批内前 [STAGGER_CAP_STEPS] 行依次落下，之后的行统一用封顶延迟。
 * 批与批之间的时间差由流式追加本身提供，两者叠起来就是持续向下生长。
 *
 * @param staggerIndex 本行在**当前这一批**里的序号，即 `index - state.lastBatchStart`。
 *   传全局 index 会让越靠后的行延迟越长，等于没封顶。
 */
@Composable
fun StreamedListItem(
    staggerIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionScale = LocalMotionScale.current
    var shown by remember { mutableStateOf(false) }
    // key 用 Unit：只在本行首次进入组合时排一次入场。用 staggerIndex 当 key 会让
    // 后续批次改变了 lastBatchStart 时已经显示的行重新入场（整列表反复闪）。
    LaunchedEffect(Unit) {
        val steps = staggerIndex.coerceIn(0, STAGGER_CAP_STEPS)
        val delayMs = (steps * Motion.StaggerStepMillis * motionScale).toLong()
        if (delayMs > 0L) delay(delayMs)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        // 只入场，不退场：行的移除由 animateItem 的 fadeOutSpec 负责，
        // 两套退场叠在一起会让删除动画走两遍。
        enter = fadeIn(Motion.effects()) +
            slideInVertically(Motion.spatial()) { it / 3 },
        exit = ExitTransition.None,
    ) {
        content()
    }
}

/**
 * 批内阶梯的封顶步数。8 × 40ms = 320ms，与一批的到达间隔同量级——
 * 再长就会和下一批的入场撞在一起，观感反而变乱。
 */
const val STAGGER_CAP_STEPS = 8
