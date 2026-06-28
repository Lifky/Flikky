package com.example.flikky.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import com.example.flikky.ui.theme.Motion

/** 底栏同级目的地（彼此无空间关系 → MD3 fade-through）。其余为父子详情（→ MD3 shared-axis X）。 */
internal val TOP_LEVEL_ROUTES = setOf("transfer", "favorites", "settings")

/** 纯逻辑：判断一个 route 是否为底栏同级目的地。单测见 `NavRouteTest`。 */
internal fun isTopLevelRoute(route: String?): Boolean = route in TOP_LEVEL_ROUTES

/** NavHost 的四个转场 lambda（enter/exit/popEnter/popExit），按 route 关系选 fade-through 或 shared-axis。 */
class FlikkyNavTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
)

/**
 * 构造 [FlikkyNavTransitions]。spec 在 composition 内从官方 [Motion] 读取（NavHost 的转场 lambda
 * 非 @Composable，故先取后闭包捕获），自动叠加全局动画速度档（速度=0 时退化为瞬切）。
 *
 * - **同级**（底栏三页互切）：fade-through —— 出场淡出、进场淡入 + 轻微 0.92→1 放大。
 * - **父子**（进/出详情页）：shared-axis X —— 前进时新页自右滑入旧页向左滑出，后退方向相反，均带淡入淡出。
 */
@Composable
fun flikkyNavTransitions(): FlikkyNavTransitions {
    val offsetSpec = Motion.spatial<IntOffset>()
    val fadeSpec = Motion.effects<Float>()
    val scaleSpec = Motion.spatial<Float>()
    val slide = with(LocalDensity.current) { 30.dp.roundToPx() }

    val fadeThroughEnter = fadeIn(fadeSpec) + scaleIn(animationSpec = scaleSpec, initialScale = 0.92f)
    val fadeThroughExit = fadeOut(fadeSpec)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.bothTopLevel(): Boolean =
        isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)

    return FlikkyNavTransitions(
        enter = {
            if (bothTopLevel()) fadeThroughEnter
            else slideInHorizontally(offsetSpec) { slide } + fadeIn(fadeSpec)
        },
        exit = {
            if (bothTopLevel()) fadeThroughExit
            else slideOutHorizontally(offsetSpec) { -slide } + fadeOut(fadeSpec)
        },
        popEnter = {
            if (bothTopLevel()) fadeThroughEnter
            else slideInHorizontally(offsetSpec) { -slide } + fadeIn(fadeSpec)
        },
        popExit = {
            if (bothTopLevel()) fadeThroughExit
            else slideOutHorizontally(offsetSpec) { slide } + fadeOut(fadeSpec)
        },
    )
}
