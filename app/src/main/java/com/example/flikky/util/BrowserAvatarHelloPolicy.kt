package com.example.flikky.util

/**
 * Decision for an incoming browser `client_hello` avatar announcement.
 * Single source of truth is the phone's persisted key (DataStore):
 * - explicit picks from the browser always win - they sync to the phone immediately;
 * - a connect-time announce only wins when the phone has never persisted a key
 *   (upgrade migration from the browser's localStorage);
 * - otherwise the phone's stored key is pushed back to the browser.
 */
sealed interface BrowserAvatarHelloDecision {
    /** Persist the announced key and update the in-memory session avatar. */
    data object Adopt : BrowserAvatarHelloDecision

    /** Ignore the announcement; broadcast [authoritativeKey] back to the browser. */
    data class PushBack(val authoritativeKey: String) : BrowserAvatarHelloDecision
}

object BrowserAvatarHelloPolicy {
    fun decide(explicit: Boolean, storedKey: String?): BrowserAvatarHelloDecision =
        if (explicit || storedKey == null) BrowserAvatarHelloDecision.Adopt
        else BrowserAvatarHelloDecision.PushBack(storedKey)
}
