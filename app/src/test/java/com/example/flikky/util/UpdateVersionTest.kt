package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun parse_accepts_v_prefix_and_plain() {
        assertEquals(listOf(1, 17, 0), UpdateVersion.parse("v1.17.0"))
        assertEquals(listOf(1, 16, 2), UpdateVersion.parse("1.16.2"))
        assertEquals(listOf(2, 0, 0), UpdateVersion.parse(" V2.0.0 "))
    }

    @Test
    fun parse_rejects_malformed() {
        assertEquals(null, UpdateVersion.parse(null))
        assertEquals(null, UpdateVersion.parse(""))
        assertEquals(null, UpdateVersion.parse("v1.17"))
        assertEquals(null, UpdateVersion.parse("1.17.0.1"))
        assertEquals(null, UpdateVersion.parse("v1.17.beta"))
        assertEquals(null, UpdateVersion.parse("latest"))
    }

    @Test
    fun isNewer_true_only_when_remote_greater() {
        assertTrue(UpdateVersion.isNewer("v1.17.0", "1.16.0"))
        assertTrue(UpdateVersion.isNewer("v1.16.1", "1.16.0"))
        assertTrue(UpdateVersion.isNewer("v2.0.0", "1.99.99"))
        assertFalse(UpdateVersion.isNewer("v1.16.0", "1.16.0"))
        assertFalse(UpdateVersion.isNewer("v1.16.0", "1.17.0"))
    }

    @Test
    fun isNewer_false_when_either_side_malformed() {
        assertFalse(UpdateVersion.isNewer(null, "1.16.0"))
        assertFalse(UpdateVersion.isNewer("nightly", "1.16.0"))
        assertFalse(UpdateVersion.isNewer("v1.17.0", null))
        assertFalse(UpdateVersion.isNewer("v1.17.0", "dev"))
    }
}
