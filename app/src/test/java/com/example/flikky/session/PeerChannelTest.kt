package com.example.flikky.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the independent prerequisite and peer-gate axes from spec section 1. */
class PeerChannelTest {

    @Test
    fun `no prerequisite means unavailable regardless of the peer gate`() {
        assertEquals(
            PeerChannelState.Unavailable,
            peerChannelState(available = false, peerEnabled = false),
        )
        assertEquals(
            PeerChannelState.Unavailable,
            peerChannelState(available = false, peerEnabled = true),
        )
    }

    @Test
    fun `prerequisite met maps the peer gate straight through`() {
        assertEquals(
            PeerChannelState.Off,
            peerChannelState(available = true, peerEnabled = false),
        )
        assertEquals(
            PeerChannelState.On,
            peerChannelState(available = true, peerEnabled = true),
        )
    }

    @Test
    fun `only channels that are actually on get named in the header`() {
        val labels = visibleChannelLabels(
            listOf(
                "Files" to PeerChannelState.On,
                "Favourites" to PeerChannelState.Off,
                "Gallery" to PeerChannelState.Unavailable,
            ),
        )
        assertEquals(listOf("Files"), labels)
    }

    @Test
    fun `nothing on yields an empty list rather than a placeholder`() {
        val labels = visibleChannelLabels(
            listOf("Files" to PeerChannelState.Off, "Favourites" to PeerChannelState.Off),
        )
        assertEquals(emptyList<String>(), labels)
    }

    @Test
    fun `label order follows the input order, not the state`() {
        val labels = visibleChannelLabels(
            listOf(
                "Files" to PeerChannelState.On,
                "Favourites" to PeerChannelState.On,
                "Gallery" to PeerChannelState.On,
            ),
        )
        assertEquals(listOf("Files", "Favourites", "Gallery"), labels)
    }
}
