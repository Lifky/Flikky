package com.example.flikky.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceNameDraftTest {
    @Test fun `keeping the default name stores nothing`() {
        assertEquals("", normalizeDeviceNameDraft("Chrome · Windows", "Chrome · Windows"))
        assertEquals("", normalizeDeviceNameDraft("  Chrome · Windows ", "Chrome · Windows"))
    }

    @Test fun `a custom name is trimmed`() {
        assertEquals("张三的笔记本", normalizeDeviceNameDraft(" 张三的笔记本 ", "Chrome · Windows"))
    }
}
