package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.session.PeerDevice

/**
 * 不关心鉴权的路由测试用：任何请求（含没有 Cookie 的）都解析为同一台设备。
 * 生产代码里没有这种闸门 —— v1.23 起关 PIN 也必须持有受控会话（B54），这些测试原来借的是那个漏洞。
 */
fun allowAllAuthGate(
    device: PeerDevice = PeerDevice(
        deviceId = "test-device",
        avatarKey = "icon:desktop_windows",
        connectedAt = 0L,
        visible = true,
    ),
): AuthGate = object : AuthGate {
    override val required = false
    override fun authenticate(pin: String, userAgent: String?): AuthAttempt =
        AuthAttempt.Granted(DeviceRegistry.Issued("test-token", device))
    override fun issueOpen(userAgent: String?): DeviceRegistry.Issued = DeviceRegistry.Issued("test-token", device)
    override fun deviceFor(token: String?): PeerDevice = device
}
