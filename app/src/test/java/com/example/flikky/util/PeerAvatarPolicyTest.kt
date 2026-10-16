package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerAvatarPolicyTest {
    @Test fun `a browser's own pick wins and never rewrites the phone default`() {
        assertEquals(
            PeerAvatarPolicy.Decision("icon:star", explicit = true, persistAsDefault = false),
            PeerAvatarPolicy.onHello("icon:star", announcedExplicit = true, storedDefault = "icon:face"),
        )
    }

    @Test fun `an old local avatar seeds the phone default when the phone never stored one`() {
        assertEquals(
            PeerAvatarPolicy.Decision("icon:star", explicit = false, persistAsDefault = true),
            PeerAvatarPolicy.onHello("icon:star", announcedExplicit = false, storedDefault = null),
        )
    }

    @Test fun `otherwise the device follows the phone default`() {
        assertEquals(
            PeerAvatarPolicy.Decision("icon:face", explicit = false, persistAsDefault = false),
            PeerAvatarPolicy.onHello("icon:star", announcedExplicit = false, storedDefault = "icon:face"),
        )
        assertEquals(
            PeerAvatarPolicy.Decision(PeerAvatarPolicy.DEFAULT_BROWSER, explicit = false, persistAsDefault = false),
            PeerAvatarPolicy.onHello(null, announcedExplicit = false, storedDefault = null),
        )
    }

    @Test fun `a malformed announced key is treated as absent`() {
        assertEquals(
            PeerAvatarPolicy.Decision("icon:face", explicit = false, persistAsDefault = false),
            PeerAvatarPolicy.onHello("<script>", announcedExplicit = true, storedDefault = "icon:face"),
        )
    }

    @Test fun `only icon and char keys of sane length are acceptable`() {
        assertTrue(PeerAvatarPolicy.isAcceptableKey("icon:star"))
        assertTrue(PeerAvatarPolicy.isAcceptableKey("icon:star:filled"))
        assertTrue(PeerAvatarPolicy.isAcceptableKey("char:中"))
        assertFalse(PeerAvatarPolicy.isAcceptableKey(null))
        assertFalse(PeerAvatarPolicy.isAcceptableKey("star"))
        assertFalse(PeerAvatarPolicy.isAcceptableKey("icon:" + "a".repeat(60)))
        assertFalse(PeerAvatarPolicy.isAcceptableKey("icon:a\"b"))
        assertFalse(PeerAvatarPolicy.isAcceptableKey("icon:a\nb"))
    }
}
