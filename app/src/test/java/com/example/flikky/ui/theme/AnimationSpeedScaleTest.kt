package com.example.flikky.ui.theme

import com.example.flikky.data.settings.AnimationSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 纯逻辑单测：[effectiveMotionScale] 把「用户动画速度档」与「系统 animatorDurationScale」合成为
 * 全局速度倍率。语义：相乘（系统=0 → 强制 0 守住 reduce-motion；系统非零 → 叠加用户档），钳到 [0, 5]。
 */
class AnimationSpeedScaleTest {

    @Test
    fun `standard speed under normal system scale is 1x`() {
        assertEquals(1f, effectiveMotionScale(AnimationSpeed.STANDARD, 1f), 0.0001f)
    }

    @Test
    fun `off speed disables motion regardless of system`() {
        assertEquals(0f, effectiveMotionScale(AnimationSpeed.OFF, 1f), 0.0001f)
    }

    @Test
    fun `slow speed scales duration up`() {
        assertEquals(1.5f, effectiveMotionScale(AnimationSpeed.SLOW, 1f), 0.0001f)
    }

    @Test
    fun `fast speed scales duration down`() {
        assertEquals(0.7f, effectiveMotionScale(AnimationSpeed.FAST, 1f), 0.0001f)
    }

    @Test
    fun `system reduce-motion forces off even when user picked standard`() {
        assertEquals(0f, effectiveMotionScale(AnimationSpeed.STANDARD, 0f), 0.0001f)
    }

    @Test
    fun `system and user scales compound`() {
        assertEquals(3f, effectiveMotionScale(AnimationSpeed.SLOW, 2f), 0.0001f)
    }

    @Test
    fun `effective scale is clamped to a sane ceiling`() {
        // 1.5 * 5 = 7.5 -> clamped to 5
        assertEquals(5f, effectiveMotionScale(AnimationSpeed.SLOW, 5f), 0.0001f)
    }

    @Test
    fun `negative system scale coerces to off`() {
        assertEquals(0f, effectiveMotionScale(AnimationSpeed.STANDARD, -1f), 0.0001f)
    }
}
