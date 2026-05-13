package com.example.flikky.network

/**
 * Minimal, platform-free snapshot of the network identity we care about.
 *
 * The Service bridges Android's [android.net.LinkProperties] callbacks into
 * this shape so the rebinder stays pure-JVM testable.
 */
data class LinkInfo(
    /** First non-loopback IPv4 address on the current default network, or null if none. */
    val ipv4: String?,
)

/**
 * What the Service should do in response to a link event. The rebinder never
 * talks to Ktor or Android directly — it only emits intents.
 */
sealed class RebindIntent {
    /** No change relative to the last link event. */
    object StayPut : RebindIntent()

    /** A new IPv4 arrived (or we changed IP). Service should rebind Ktor to [newIp]. */
    data class Rebind(val newIp: String) : RebindIntent()

    /** We used to have an IPv4 and now we don't. Service should flag the UI as "lost". */
    object Lost : RebindIntent()

    /**
     * After a [Lost] the SAME IPv4 came back (e.g. user toggled Wi-Fi without
     * changing networks). Ktor was never stopped, so the Service should NOT
     * restart it — just clear the "lost" UI flag. Critical for not blowing up
     * the live WebSocket sessions on a transient outage.
     */
    object Restored : RebindIntent()
}

/**
 * Pure-logic state machine that folds a stream of [LinkInfo] events into
 * [RebindIntent]s. One instance is owned by TransferService.
 *
 * Lifecycle contract:
 *  - After the first successful KtorServer.start() the service calls
 *    [prime] with the IP it bound to, so the first callback doesn't look
 *    like a spurious "Rebind".
 *  - Every [android.net.ConnectivityManager.NetworkCallback.onLinkPropertiesChanged]
 *    produces one [onLink] call. Duplicate IPs are collapsed to StayPut.
 */
class NetworkRebinder {
    /**
     * Last IPv4 we believe the OS surfaced to us. We keep this populated even
     * across a transient [Lost] so that "same IP comes back" can be told apart
     * from "different IP arrives after no-IP". [lost] is the only field that
     * tracks whether we are currently in the lost state.
     */
    private var currentIp: String? = null
    private var lost: Boolean = false

    /** Feed one link event; returns the action the service should take. */
    fun onLink(info: LinkInfo): RebindIntent {
        val prev = currentIp
        val next = info.ipv4
        return when {
            // No-op echoes.
            next == null && prev == null -> RebindIntent.StayPut
            // We had an IP and lost it. Keep currentIp populated so a same-IP
            // return is distinguishable from a fresh arrival.
            next == null && prev != null && !lost -> {
                lost = true
                RebindIntent.Lost
            }
            next == null && lost -> RebindIntent.StayPut
            // Fresh IPv4 arrived (cold start / very first IP we ever see).
            next != null && prev == null -> {
                currentIp = next
                lost = false
                RebindIntent.Rebind(next)
            }
            // Same IP came back after a Lost — Ktor was never stopped, just
            // clear the lost flag. No rebind, no Ktor restart, no wsHub reset.
            next != null && prev != null && next == prev && lost -> {
                lost = false
                RebindIntent.Restored
            }
            // Same IP and we never lost it: just an echo.
            next != null && prev != null && next == prev && !lost -> RebindIntent.StayPut
            // Genuine IP change — Wi-Fi network actually swapped.
            next != null && prev != null && next != prev -> {
                currentIp = next
                lost = false
                RebindIntent.Rebind(next)
            }
            else -> RebindIntent.StayPut
        }
    }

    /** Current known IP, or null if we never observed an IPv4. For tests/diagnostics. */
    fun snapshot(): String? = currentIp

    /**
     * Seed the rebinder with the IP Ktor was actually bound to. Call this
     * exactly once per bind so the very next callback (which typically echoes
     * the already-bound IP) returns StayPut instead of a bogus Rebind.
     */
    fun prime(ip: String) {
        currentIp = ip
        lost = false
    }
}
