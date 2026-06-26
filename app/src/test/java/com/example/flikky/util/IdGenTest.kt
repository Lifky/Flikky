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

    @Test
    fun `seedMessageCounter makes next id exceed persisted max`() {
        // Simulate a cold restart where the DB already holds messages up to id 500.
        // Without seeding, the process-volatile counter restarts at 0 and the next
        // newMessageId() would be 1 — colliding with an existing PRIMARY KEY.
        IdGen.seedMessageCounter(500L)
        assertTrue("next id must clear persisted max", IdGen.newMessageId() > 500L)
    }

    @Test
    fun `seedMessageCounter never lowers the counter`() {
        IdGen.seedMessageCounter(1_000L)
        val afterHigh = IdGen.newMessageId() // > 1000
        // A later, smaller seed (e.g. a stale read) must not rewind the counter.
        IdGen.seedMessageCounter(10L)
        assertTrue("counter must not regress", IdGen.newMessageId() > afterHigh)
    }
}
