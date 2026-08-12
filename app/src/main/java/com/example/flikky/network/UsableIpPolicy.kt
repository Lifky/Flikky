package com.example.flikky.network

/**
 * 一个地址能不能拿来开服。
 *
 * 判断的是"电脑能不能连上"，不是"socket 能不能绑上"：`169.254.x` 自配置地址（Wi-Fi 刚断开或
 * 正在协商时系统会给）绑得上但同网段的电脑访问不到，`0.0.0.0` 与回环同理，而且 `0.0.0.0`
 * 违反"不监听 0.0.0.0"的安全红线。
 */
object UsableIpPolicy {

    fun isUsable(ip: String?): Boolean {
        val value = ip?.trim().orEmpty()
        if (value.isEmpty()) return false
        if (value == "0.0.0.0") return false
        if (value.startsWith("127.")) return false
        if (value.startsWith("169.254.")) return false
        return true
    }
}
