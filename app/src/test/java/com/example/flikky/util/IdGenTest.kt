package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdGenTest {
    @Test
    fun `newPin is 6 digits zero-padded`() {
        repeat(1000) {
            val pin = IdGen.newPin()
            assertEquals(6, pin.length)
            assertTrue("digits only: $pin", pin.all { it.isDigit() })
        }
    }

    @Test
    fun `newToken is 22 chars base64url without padding`() {
        val t = IdGen.newToken()
        assertEquals(22, t.length)
        assertTrue("base64url chars: $t", t.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun `newToken returns distinct values`() {
        val a = IdGen.newToken()
        val b = IdGen.newToken()
        assertNotEquals(a, b)
    }

    @Test
    fun `newMessageId is monotonic within process`() {
        val a = IdGen.newMessageId()
        val b = IdGen.newMessageId()
        val c = IdGen.newMessageId()
        assertTrue(a < b)
        assertTrue(b < c)
    }
}
