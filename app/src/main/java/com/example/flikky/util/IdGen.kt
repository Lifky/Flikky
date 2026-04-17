package com.example.flikky.util

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

object IdGen {
    private val random = SecureRandom()
    private val messageCounter = AtomicLong(0L)

    fun newPin(): String {
        val n = random.nextInt(1_000_000)
        return n.toString().padStart(6, '0')
    }

    fun newToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun newMessageId(): Long = messageCounter.incrementAndGet()
}
