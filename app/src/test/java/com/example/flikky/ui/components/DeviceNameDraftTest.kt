package com.example.flikky.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNameDraftTest {
    @Test fun `keeping the default name stores nothing`() {
        assertEquals("", normalizeDeviceNameDraft("Chrome · Windows", "Chrome · Windows"))
        assertEquals("", normalizeDeviceNameDraft("  Chrome · Windows ", "Chrome · Windows"))
    }

    @Test fun `a custom name is trimmed`() {
        assertEquals("张三的笔记本", normalizeDeviceNameDraft(" 张三的笔记本 ", "Chrome · Windows"))
    }

    // v1.23 审查 Important #1：预填的默认名可能超过上限（旧数据、两位数编号）。
    // 只拒绝「变长且超长」的输入，否则删字也被拒，输入框看起来像卡死了。
    @Test fun `an over-length prefill can still be shortened`() {
        val long = "Samsung Internet · Android 2"
        assertTrue(acceptsDeviceNameEdit(long, long.dropLast(1)))
        assertTrue(acceptsDeviceNameEdit("a".repeat(19), "a".repeat(20)))
        assertFalse(acceptsDeviceNameEdit("a".repeat(20), "a".repeat(21)))
        assertFalse(acceptsDeviceNameEdit(long, long + "x"))
    }
}
