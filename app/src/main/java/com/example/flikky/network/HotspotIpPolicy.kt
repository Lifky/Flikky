package com.example.flikky.network

/** 一个接口上的一个 IPv4 候选。由 [NetworkInfo] 从 `NetworkInterface` 枚举出来。 */
data class IpCandidate(
    val interfaceName: String,
    val ip: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isVirtual: Boolean,
)

/**
 * 手机自己当热点时该绑哪个地址。
 *
 * 安全红线写的是「普通 Wi-Fi **或手机系统热点**的具体私有 IPv4」，但热点场景一直没实现：
 * 手机当热点时 AP 接口不是「已连接的网络」，`ConnectivityManager` 枚举不到它，
 * 于是 `currentWifiIpv4()` 返回 null，服务起不来。
 *
 * ## 排除集为什么必须来自 ConnectivityManager
 *
 * 调用方收集 `TRANSPORT_CELLULAR` / `TRANSPORT_VPN` 网络的 `linkProperties.interfaceName`
 * 传进来，本对象只做判断。不靠接口名猜，两个理由：
 * 1. 接口名白名单跨厂商不可靠（`ap0` / `swlan0` / `softap0` / `wlan1` 各家不同，还随版本变）。
 * 2. 退一步用「私有 IPv4」兜也不成立——**运营商 CGNAT 就发 10.x**，蜂窝接口照样命中，
 *    直接违反「不绑定蜂窝/VPN」这条红线。
 *
 * 排除是**逐接口**的：插着 VPN 时不能整体放弃，否则「开了 VPN 热点就连不上」，
 * 而用户根本不知道这两件事有关系。
 *
 * ## 优先级
 *
 * 192.168 > 172.16–31 > 10，同段按接口名再按地址字典序。**必须完全确定**——
 * 不确定的选择会表现为「有时能连有时不能」，最难排查的那一类。
 */
object HotspotIpPolicy {

    fun pick(candidates: List<IpCandidate>, excludedInterfaces: Set<String>): String? =
        candidates
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .filterNot { it.interfaceName in excludedInterfaces }
            .filter { UsableIpPolicy.isUsable(it.ip) }
            .mapNotNull { candidate ->
                privateClassOf(candidate.ip)?.let {
                    Triple(it, candidate.interfaceName, candidate.ip)
                }
            }
            // 三级定序：段优先级 → 接口名 → 地址。少了最后一级时，同一接口上的两个地址
            // 会随枚举顺序变化，症状是重启后 URL 变了。
            .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
            .firstOrNull()
            ?.third

    /**
     * RFC1918 段序号（小的优先），非私有返回 null。
     *
     * 逐段解析八位组而不是 `startsWith` 前缀比较：`startsWith("10.")` 会把 `100.64.x`
     * （CGNAT 段）判成 10/8 的近亲——虽然带上点号后不会，但 `192.168` 少写一个点
     * 就会收下 `192.1689.x` 这类畸形串。解析成数字后这类误判整类消失。
     */
    private fun privateClassOf(ip: String): Int? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        val (a, b) = octets
        return when {
            a == 192 && b == 168 -> 0
            a == 172 && b in 16..31 -> 1
            a == 10 -> 2
            else -> null
        }
    }
}
