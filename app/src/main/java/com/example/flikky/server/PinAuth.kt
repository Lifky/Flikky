package com.example.flikky.server

/** 只核对 PIN：单次使用、3 次锁 30 秒、5 次终止。token 只经手不存，凭据由 DeviceRegistry 保管哈希。 */
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
    private var wrongTotal: Int = 0
    private var wrongInWindow: Int = 0
    private var lockUntilMs: Long = 0L
    private var terminated: Boolean = false

    @Synchronized
    fun currentPin(): String? = pin

    /** 换了绑定 IP（D75）：换一个新的一次性 PIN，并清零防暴力破解计数。旧 Cookie 由 DeviceRegistry.revokeAll() 作废。 */
    @Synchronized
    fun renewPin(newPin: String) {
        pin = newPin
        wrongTotal = 0
        wrongInWindow = 0
        lockUntilMs = 0L
        terminated = false
    }

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
        pin = null
        return Result.Ok(token)
    }

    /**
     * 断开设备后换一个新 PIN（spec §4.4）。**只换 PIN**：防暴力破解计数全部保留 ——
     * 同一个 IP 意味着攻击者还在原位，清零等于送他一轮新的尝试（D75）。换 IP 用 [renewPin]。
     */
    @Synchronized
    fun issueNextPin(newPin: String) {
        pin = newPin
    }

    /** PIN 已被用掉、且再没有设备持有凭据时才需要新 PIN（spec §4.2 的不变式）。 */
    @Synchronized
    fun needsNextPin(hasCredentials: Boolean): Boolean = pin == null && !hasCredentials

    companion object {
        const val LOCK_THRESHOLD = 3
        const val TERMINATE_THRESHOLD = 5
        const val LOCK_DURATION_MS = 30_000L
    }
}
