package com.example.flikky.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRecallPolicyTest {

    @Test
    fun `recall disabled blocks both own and peer messages`() {
        assertFalse(canRecallMessage(Origin.PHONE, Origin.PHONE, false, true))
        assertFalse(canRecallMessage(Origin.PHONE, Origin.BROWSER, false, true))
    }

    @Test
    fun `peer recall disabled allows only messages from the requester side`() {
        assertTrue(canRecallMessage(Origin.PHONE, Origin.PHONE, true, false))
        assertTrue(canRecallMessage(Origin.BROWSER, Origin.BROWSER, true, false))
        assertFalse(canRecallMessage(Origin.PHONE, Origin.BROWSER, true, false))
        assertFalse(canRecallMessage(Origin.BROWSER, Origin.PHONE, true, false))
    }

    @Test
    fun `peer recall enabled allows both message directions`() {
        assertTrue(canRecallMessage(Origin.PHONE, Origin.BROWSER, true, true))
        assertTrue(canRecallMessage(Origin.BROWSER, Origin.PHONE, true, true))
    }
}
