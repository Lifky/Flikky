package com.example.flikky.util

import com.example.flikky.data.settings.DEVICE_NAME_MAX
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceNamePolicyTest {
    @Test fun `the limit is the same one the phone name uses`() {
        assertEquals(DEVICE_NAME_MAX, DeviceNamePolicy.MAX_LENGTH)
    }

    @Test fun `control characters and surrounding blanks are removed`() {
        assertEquals("我的电脑", DeviceNamePolicy.normalize("  我的\u0000电脑\n "))
        assertEquals("", DeviceNamePolicy.normalize(null))
        assertEquals("", DeviceNamePolicy.normalize("   "))
    }

    @Test fun `long names are cut at twenty utf16 units`() {
        assertEquals("a".repeat(20), DeviceNamePolicy.normalize("a".repeat(25)))
    }

    @Test fun `a cut never splits an emoji in half`() {
        assertEquals("a".repeat(19), DeviceNamePolicy.normalize("a".repeat(19) + "😀"))
        assertEquals("a".repeat(18) + "😀", DeviceNamePolicy.normalize("a".repeat(18) + "😀"))
    }
}
