package com.example.flikky.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.flikky.data.settings.AnimationSpeed

/** 全局速度倍率的安全上限（避免系统/用户叠加出过低刚度导致动画拖沓）。 */
const val MAX_MOTION_SCALE = 5f

/**
 * 把「用户动画速度档 [speed]」与「系统 animatorDurationScale [systemScale]」合成为全局速度倍率
 * （纯逻辑，单测见 `AnimationSpeedScaleTest`）。
 *
 * 相乘语义：系统把动画关掉（`systemScale<=0`）→ 结果 0（强制 reduce-motion，守住无障碍）；
 * 系统正常时叠加用户档（标准 1.0 / 慢 1.5 / 快 0.7 / 关 0）。钳到 `[0, MAX_MOTION_SCALE]`。
 */
fun effectiveMotionScale(speed: AnimationSpeed, systemScale: Float): Float =
    (speed.multiplier * systemScale).coerceIn(0f, MAX_MOTION_SCALE)

/**
 * 全局动画速度倍率（duration 语义：`1f`=标准，`>1` 更慢，`<1` 更快，`0f`=关闭 / reduce-motion）。
 *
 * 由 [com.example.flikky.ui.theme.FlikkyTheme] 依据「用户动画速度设置 × 系统 animatorDurationScale」
 * 提供；系统把动画关掉（animatorDurationScale==0）时为 `0f`，自动尊重无障碍 reduce-motion。
 */
val LocalMotionScale = staticCompositionLocalOf { 1f }

/**
 * MD3 motion tokens —— **官方优先**。
 *
 * spring 数值不再手抄常量，而是读官方 `MaterialTheme.motionScheme`（App 根由 [FlikkyTheme] 包
 * `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())` 注入），再经 [scaleMotionSpec]
 * 叠加 [LocalMotionScale] 速度层。这样官方组件与自定义动画共用同一套 expressive 手感，且无镜像漂移。
 *
 * 约定（新 UI 一律取这里的 spec，禁止再写内联 spring/tween）：
 *  - `spatial*()`：位移 / 尺寸 / 形变（官方 spatial spring，带轻微回弹）。
 *  - `effects*()`：透明度 / 颜色（官方 effects spring，临界阻尼不过冲）。
 *  - 进场用 `*` / `*Slow`、出场用 `*Fast`；大元素用 `*Slow`、小元素用 `*Fast`
 *    —— 体现 MD3「进比出慢、大比小慢」。
 *  - Duration / Easing 是 MD3 曲线系统（官方 MotionScheme 不含），供固定时长 tween 类转场
 *    （fade-through / shared-axis）使用。
 */
object Motion {
    // ---- Durations (ms)，MD3 curve system ----
    const val Short1 = 50
    const val Short2 = 100
    const val Short3 = 150
    const val Short4 = 200
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium3 = 350
    const val Medium4 = 400
    const val Long1 = 450
    const val Long2 = 500
    const val Long3 = 550
    const val Long4 = 600
    const val ExtraLong1 = 700
    const val ExtraLong2 = 800
    const val ExtraLong3 = 900
    const val ExtraLong4 = 1000

    /**
     * 成组入场的逐项错峰步进（ms）：主元素先动、次元素每项延后一步（MD3 stagger）。
     * 调用方乘以 [LocalMotionScale] 再用于 delay → 受全局速度档统辖（关闭=0 → 无错峰、整组同时出现）。
     */
    const val StaggerStepMillis = 40

    // ---- Easing（官方 cubic-bezier）----
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    // ---- 速度缩放后的 spring 访问器（读官方 MotionScheme + [LocalMotionScale]）----

    /** 位移/尺寸/形变，标准。进场首选。 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable @ReadOnlyComposable
    fun <T> spatial(): FiniteAnimationSpec<T> =
        scaleMotionSpec(MaterialTheme.motionScheme.defaultSpatialSpec(), LocalMotionScale.current)

    /** 位移/尺寸/形变，快。出场/小元素首选。 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable @ReadOnlyComposable
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        scaleMotionSpec(MaterialTheme.motionScheme.fastSpatialSpec(), LocalMotionScale.current)

    /** 位移/尺寸/形变，慢。大元素/hero 首选。 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable @ReadOnlyComposable
    fun <T> spatialSlow(): FiniteAnimationSpec<T> =
        scaleMotionSpec(MaterialTheme.motionScheme.slowSpatialSpec(), LocalMotionScale.current)

    /** 透明度/颜色，标准。 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable @ReadOnlyComposable
    fun <T> effects(): FiniteAnimationSpec<T> =
        scaleMotionSpec(MaterialTheme.motionScheme.defaultEffectsSpec(), LocalMotionScale.current)

    /** 透明度/颜色，快。 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable @ReadOnlyComposable
    fun <T> effectsFast(): FiniteAnimationSpec<T> =
        scaleMotionSpec(MaterialTheme.motionScheme.fastEffectsSpec(), LocalMotionScale.current)

    /** 透明度/颜色，慢。 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable @ReadOnlyComposable
    fun <T> effectsSlow(): FiniteAnimationSpec<T> =
        scaleMotionSpec(MaterialTheme.motionScheme.slowEffectsSpec(), LocalMotionScale.current)

    /**
     * 基于 duration token 的 tween（固定时长 cross-fade / shared-axis 等用），duration 受
     * [LocalMotionScale] 缩放；速度为 0 时退化 snap。
     */
    @Composable @ReadOnlyComposable
    fun <T> durationSpec(durationMillis: Int, easing: Easing = Standard): FiniteAnimationSpec<T> =
        scaleMotionSpec(tween(durationMillis = durationMillis, easing = easing), LocalMotionScale.current)
}

/**
 * 把官方 MotionScheme 返回的 spec 按全局速度倍率 [scale] 缩放（纯逻辑，单测见 `MotionSpecScaleTest`）。
 * 语义：`scale<=0`→瞬时 [snap]（关闭/reduce-motion）；`==1`→原样返回；`>1` 更慢、`<1` 更快。
 * spring 经「刚度 ÷ scale」缩放并保持阻尼手感；tween 经「时长 × scale」缩放并保留 easing。
 * 非 spring/tween 的 spec 无法安全缩放，原样返回（不破坏官方默认行为）。
 */
internal fun <T> scaleMotionSpec(base: FiniteAnimationSpec<T>, scale: Float): FiniteAnimationSpec<T> {
    if (scale <= 0f) return snap()
    if (scale == 1f) return base
    return when (base) {
        is SpringSpec<*> -> {
            @Suppress("UNCHECKED_CAST") val s = base as SpringSpec<T>
            spring(dampingRatio = s.dampingRatio, stiffness = s.stiffness / scale, visibilityThreshold = s.visibilityThreshold)
        }
        is TweenSpec<*> -> {
            @Suppress("UNCHECKED_CAST") val t = base as TweenSpec<T>
            tween(
                durationMillis = (t.durationMillis * scale).toInt().coerceAtLeast(1),
                delayMillis = t.delay,
                easing = t.easing,
            )
        }
        else -> base
    }
}
