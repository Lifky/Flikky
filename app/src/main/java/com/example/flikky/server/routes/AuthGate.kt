package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth

class AuthGate(
    val required: Boolean,
    private val pinAuth: PinAuth,
) {
    fun authenticate(pin: String): PinAuth.Result = pinAuth.tryConsume(pin)

    fun isAuthorized(token: String?): Boolean =
        !required || (token != null && pinAuth.validateToken(token))
}
