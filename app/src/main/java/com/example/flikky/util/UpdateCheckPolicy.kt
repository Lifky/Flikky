package com.example.flikky.util

/** Pure decision rules for the auto update check throttle and prompt policy. */
object UpdateCheckPolicy {
    const val AUTO_CHECK_INTERVAL_MS: Long = 24 * 60 * 60 * 1000L

    /** True when enabled and the last successful check is 24h+ old, or the clock moved back. */
    fun shouldAutoCheck(enabled: Boolean, lastCheckAtMs: Long, nowMs: Long): Boolean {
        if (!enabled) return false
        if (lastCheckAtMs > nowMs) return true
        return nowMs - lastCheckAtMs >= AUTO_CHECK_INTERVAL_MS
    }

    /** True when this remote version has not been auto-prompted before. */
    fun shouldAutoPrompt(remoteTag: String, lastPromptedTag: String?): Boolean =
        remoteTag != lastPromptedTag
}
