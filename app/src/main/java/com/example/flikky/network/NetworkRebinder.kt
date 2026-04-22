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
    private var currentIp: String? = null

    /** Feed one link event; returns the action the service should take. */
    fun onLink(info: LinkInfo): RebindIntent {
        val prev = currentIp
        val next = info.ipv4
        return when {
            next == null && prev == null -> RebindIntent.StayPut
            next == null && prev != null -> {
                currentIp = null
                RebindIntent.Lost
            }
            next != null && prev == null -> {
                currentIp = next
                RebindIntent.Rebind(next)
            }
            next != null && prev != null && next != prev -> {
                currentIp = next
                RebindIntent.Rebind(next)
            }
            else -> RebindIntent.StayPut
        }
    }

    /** Current known IP, or null if we believe there is no IPv4. For tests/diagnostics. */
    fun snapshot(): String? = currentIp

    /**
     * Seed the rebinder with the IP Ktor was actually bound to. Call this
     * exactly once per bind so the very next callback (which typically echoes
     * the already-bound IP) returns StayPut instead of a bogus Rebind.
     */
    fun prime(ip: String) {
        currentIp = ip
    }
}
