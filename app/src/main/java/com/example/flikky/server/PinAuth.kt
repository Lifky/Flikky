package com.example.flikky.server

class PinAuth(
    private val nowMs: () -> Long,
    pinSupplier: () -> String,
    private val tokenSupplier: () -> String,
) {
    sealed class Result {
        data class Ok(val token: String) : Result()
        object Wrong : Result()
        object Locked : Result()
        object PinAlreadyUsed : Result()
        object Terminated : Result()
    }

    private var pin: String? = pinSupplier()
    private var issuedToken: String? = null
    private var wrongTotal: Int = 0
    private var wrongInWindow: Int = 0
    private var lockUntilMs: Long = 0L
    private var terminated: Boolean = false

    @Synchronized
    fun currentPin(): String? = pin

    @Synchronized
    fun tryConsume(attempt: String): Result {
        if (terminated) return Result.Terminated

        val isLocked = nowMs() < lockUntilMs

        // Any non-correct attempt (including attempts while locked) counts toward total wrong tally.
        // Correct-pin attempts while locked are treated as wrong (user is rate-limited).
        val currentPin = pin
        val isCorrectPin = currentPin != null && attempt == currentPin

        if (isLocked || !isCorrectPin) {
            // Count as a wrong attempt unless the pin was already consumed (null) and we're not locked
            if (isLocked || currentPin != null) {
                wrongTotal += 1
                if (wrongTotal >= TERMINATE_THRESHOLD) {
                    terminated = true
                    return Result.Terminated
                }
            }
            if (isLocked) return Result.Locked
            if (currentPin == null) return Result.PinAlreadyUsed
            // Wrong attempt while not locked
            wrongInWindow += 1
            if (wrongInWindow >= LOCK_THRESHOLD) {
                lockUntilMs = nowMs() + LOCK_DURATION_MS
                wrongInWindow = 0
                return Result.Locked
            }
            return Result.Wrong
        }

        // Correct pin, not locked
        val token = tokenSupplier()
        issuedToken = token
        pin = null
        return Result.Ok(token)
    }

    @Synchronized
    fun validateToken(token: String): Boolean = issuedToken != null && issuedToken == token

    companion object {
        const val LOCK_THRESHOLD = 3
        const val TERMINATE_THRESHOLD = 5
        const val LOCK_DURATION_MS = 30_000L
    }
}
