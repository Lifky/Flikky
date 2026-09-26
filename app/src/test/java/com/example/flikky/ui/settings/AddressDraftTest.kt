package com.example.flikky.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressDraftTest {
    @Test fun `valid values preview the full address`() {
        val d = AddressDraft(enabled = true, numberText = "37", portText = "8080")
        assertTrue(d.canSave)
        assertEquals("http://flikky37.local:8080", d.preview())
    }

    @Test fun `a bad number blocks saving while the name is on`() {
        val d = AddressDraft(enabled = true, numberText = "1000", portText = "8080")
        assertTrue(d.numberError); assertFalse(d.canSave); assertNull(d.preview())
    }

    @Test fun `a bad number does not matter while the name is off`() {
        val d = AddressDraft(enabled = false, numberText = "1000", portText = "8080")
        assertFalse(d.numberError); assertTrue(d.canSave); assertNull(d.preview())
    }

    @Test fun `a bad port always blocks saving`() {
        val d = AddressDraft(enabled = false, numberText = "37", portText = "80")
        assertTrue(d.portError); assertFalse(d.canSave)
    }

    @Test fun `an empty field is an error, not a silent default`() {
        assertTrue(AddressDraft(true, "", "8080").numberError)
        assertTrue(AddressDraft(true, "37", "").portError)
    }

    @Test fun `a port browsers refuse gets its own error`() {
        val d = AddressDraft(enabled = true, numberText = "37", portText = "6000")
        assertTrue(d.portError); assertTrue(d.portBlockedByBrowsers); assertFalse(d.canSave)
        assertFalse(AddressDraft(true, "37", "80").portBlockedByBrowsers)
    }
}
