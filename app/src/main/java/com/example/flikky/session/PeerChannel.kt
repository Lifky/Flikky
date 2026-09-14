package com.example.flikky.session

/**
 * State of one resource channel exposed to the peer.
 *
 * Availability and peer access are independent axes. Keeping [Unavailable] distinct from [Off]
 * lets the UI explain a missing prerequisite without showing a switch that cannot take effect.
 */
enum class PeerChannelState {
    Unavailable,
    Off,
    On,
}

fun peerChannelState(available: Boolean, peerEnabled: Boolean): PeerChannelState =
    when {
        !available -> PeerChannelState.Unavailable
        peerEnabled -> PeerChannelState.On
        else -> PeerChannelState.Off
    }

/** Returns only channels whose content is currently visible, preserving the caller's order. */
fun visibleChannelLabels(
    channels: List<Pair<String, PeerChannelState>>,
): List<String> = channels.filter { it.second == PeerChannelState.On }.map { it.first }
