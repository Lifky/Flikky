package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.JoinRequests
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

    /**
     * 会话里已有设备时，新设备要走「请求加入」、由手机允许（v1.23 多设备）。开 PIN 与关 PIN 同一条规则；
     * 没有设备时照旧（输 PIN / 直接进）。没启用请求加入（导出模式、测试）时恒为 false。
     */
    val joinRequired: Boolean get() = false

    /** 手机允许了加入请求：签发一台立即可见的设备（与经 PIN 签发同等，不占 pending 额度）。 */
    fun issueJoined(userAgent: String?): DeviceRegistry.Issued =
        throw UnsupportedOperationException("join requests are not enabled")
}

sealed interface AuthAttempt {
    data class Granted(val issued: DeviceRegistry.Issued) : AuthAttempt
    /** [result] 只会是 Wrong / Locked / PinAlreadyUsed / Terminated。 */
    data class Denied(val result: PinAuth.Result) : AuthAttempt
}

fun AuthGate(
    required: Boolean,
    pinAuth: PinAuth,
    registry: DeviceRegistry = DeviceRegistry(),
    joinRequests: JoinRequests? = null,
): AuthGate = DeviceAuthGate(required, pinAuth, registry, joinRequests != null)

private class DeviceAuthGate(
    override val required: Boolean,
    private val pinAuth: PinAuth,
    private val registry: DeviceRegistry,
    private val joinEnabled: Boolean,
) : AuthGate {
    override val joinRequired: Boolean get() = joinEnabled && registry.hasVisibleDevices()

    override fun issueJoined(userAgent: String?): DeviceRegistry.Issued = registry.issue(userAgent, viaPin = true)

    override fun authenticate(pin: String, userAgent: String?): AuthAttempt =
        when (val r = pinAuth.tryConsume(pin)) {
            is PinAuth.Result.Ok -> AuthAttempt.Granted(registry.issue(userAgent, viaPin = true, token = r.token))
            else -> AuthAttempt.Denied(r)
        }

    override fun issueOpen(userAgent: String?): DeviceRegistry.Issued? =
        if (required) null else registry.issue(userAgent, viaPin = false)

    override fun deviceFor(token: String?): PeerDevice? = registry.resolve(token)
}
