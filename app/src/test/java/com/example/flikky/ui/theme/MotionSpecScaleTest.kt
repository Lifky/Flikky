package com.example.flikky.ui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯逻辑单测：[scaleMotionSpec] 把官方 MotionScheme 返回的 spec 按全局速度倍率缩放，
 * 并在速度=0（关闭 / reduce-motion）时退化为瞬时 snap。语义：scale>1 更慢、scale<1 更快。
 */
class MotionSpecScaleTest {

    @Test
    fun `scale of 1 returns the base spec unchanged`() {
        val base = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
        assertSame(base, scaleMotionSpec(base, 1f))
    }

    @Test
    fun `scale of 0 collapses to an instant snap for reduce-motion`() {
        val base = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
        assertTrue(scaleMotionSpec(base, 0f) is SnapSpec<*>)
    }

    @Test
    fun `negative scale also collapses to snap`() {
        val base = spring<Float>(stiffness = 380f)
        assertTrue(scaleMotionSpec(base, -1f) is SnapSpec<*>)
    }

    @Test
    fun `slower scale lowers spring stiffness proportionally and keeps damping`() {
        val base = spring<Float>(dampingRatio = 0.8f, stiffness = 400f)
        val scaled = scaleMotionSpec(base, 2f) as SpringSpec<Float>
        assertEquals(200f, scaled.stiffness, 0.001f)
        assertEquals(0.8f, scaled.dampingRatio, 0.001f)
    }

    @Test
    fun `faster scale raises spring stiffness proportionally`() {
        val base = spring<Float>(stiffness = 400f)
        val scaled = scaleMotionSpec(base, 0.5f) as SpringSpec<Float>
        assertEquals(800f, scaled.stiffness, 0.001f)
    }

    @Test
    fun `tween duration scales with the speed factor and preserves easing`() {
        val base = tween<Float>(durationMillis = 300)
        val scaled = scaleMotionSpec(base, 2f) as TweenSpec<Float>
        assertEquals(600, scaled.durationMillis)
        assertSame(base.easing, scaled.easing)
    }
    // ── criticallyDamped：收起到零专用（2026-09-10 装机反馈）───────────────────

    @Test
    fun `criticallyDamped removes the bounce and keeps the official stiffness`() {
        // 官方 spatial 系列刻意欠阻尼（expressive 的 fastSpatial 实测阻尼比 0.6）。
        // 收起到 0 时那个回弹会让间隔高度闪一下，所以这一档要临界阻尼。
        // **刚度必须保留** —— 换刚度就等于换了时长手感，那不是本次要改的东西。
        val bouncy = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
        val damped = criticallyDamped(bouncy) as SpringSpec<Float>
        assertEquals(1f, damped.dampingRatio, 0f)
        assertEquals(800f, damped.stiffness, 0f)
    }

    @Test
    fun `criticallyDamped leaves a tween alone`() {
        // tween 本来就不回弹，改它没有意义 —— 与 scaleMotionSpec 同一条保守原则。
        val base = tween<Float>(durationMillis = 300)
        assertSame(base, criticallyDamped(base))
    }

    @Test
    fun `a critically damped spec still scales with the global speed setting`() {
        // 两个变换要能叠：去回弹之后仍然过 scaleMotionSpec，
        // 否则「动画速度」设置对收起动画会静默失效。
        val scaled = scaleMotionSpec(
            criticallyDamped(spring<Float>(dampingRatio = 0.6f, stiffness = 800f)),
            2f,
        ) as SpringSpec<Float>
        assertEquals("去回弹后刚度还要能被速度倍率缩放", 400f, scaled.stiffness, 0f)
        assertEquals("缩放不该把阻尼改回去", 1f, scaled.dampingRatio, 0f)
    }
}
