package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import com.example.flikky.session.PeerDevice

/**
 * 所有受保护接口的统一入口：由 Cookie 解析出设备，解析不出即拒绝（spec §4.8）。
 * 关 PIN 时也一样 —— 区别只在于设备怎么来：[issueOpen] 自动签发，而不是输 PIN（B54）。
 */
interface AuthGate {
    val required: Boolean
    fun authenticate(pin: String, userAgent: String?): AuthAttempt
    /** 关 PIN：为没有有效 Cookie 的访问签发一台设备；开着 PIN 时返回 null。 */
    fun issueOpen(userAgent: String?): DeviceRegistry.Issued?
    fun deviceFor(token: String?): PeerDevice?
    fun isAuthorized(token: String?): Boolean = deviceFor(token) != null
}

sealed interface AuthAttempt {
    data class Granted(val issued: DeviceRegistry.Issued) : AuthAttempt
    /** [result] 只会是 Wrong / Locked / PinAlreadyUsed / Terminated。 */
    data class Denied(val result: PinAuth.Result) : AuthAttempt
}

fun AuthGate(required: Boolean, pinAuth: PinAuth, registry: DeviceRegistry = DeviceRegistry()): AuthGate =
    DeviceAuthGate(required, pinAuth, registry)

private class DeviceAuthGate(
    override val required: Boolean,
    private val pinAuth: PinAuth,
    private val registry: DeviceRegistry,
) : AuthGate {
    override fun authenticate(pin: String, userAgent: String?): AuthAttempt =
        when (val r = pinAuth.tryConsume(pin)) {
            is PinAuth.Result.Ok -> AuthAttempt.Granted(registry.issue(userAgent, viaPin = true, token = r.token))
            else -> AuthAttempt.Denied(r)
        }

    override fun issueOpen(userAgent: String?): DeviceRegistry.Issued? =
        if (required) null else registry.issue(userAgent, viaPin = false)

    override fun deviceFor(token: String?): PeerDevice? = registry.resolve(token)
}
