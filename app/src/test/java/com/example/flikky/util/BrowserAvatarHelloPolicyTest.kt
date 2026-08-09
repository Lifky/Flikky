package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserAvatarHelloPolicyTest {

    @Test
    fun `explicit pick always adopts even when a key is stored`() {
        assertEquals(
            BrowserAvatarHelloDecision.Adopt,
            BrowserAvatarHelloPolicy.decide(explicit = true, storedKey = "icon:star"),
        )
    }

    @Test
    fun `announce with no stored key adopts as upgrade migration`() {
        assertEquals(
            BrowserAvatarHelloDecision.Adopt,
            BrowserAvatarHelloPolicy.decide(explicit = false, storedKey = null),
        )
    }

    @Test
    fun `announce with stored key pushes the stored key back`() {
        assertEquals(
            BrowserAvatarHelloDecision.PushBack("icon:star"),
            BrowserAvatarHelloPolicy.decide(explicit = false, storedKey = "icon:star"),
        )
    }
}
