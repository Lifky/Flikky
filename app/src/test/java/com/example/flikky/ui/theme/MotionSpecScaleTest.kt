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
}
