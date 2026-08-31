package com.example.flikky.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotIpPolicyTest {

    private fun c(
        name: String,
        ip: String,
        isUp: Boolean = true,
        isLoopback: Boolean = false,
        isVirtual: Boolean = false,
    ) = IpCandidate(name, ip, isUp, isLoopback, isVirtual)

    @Test
    fun `a hotspot address on a plain interface is picked`() {
        assertEquals("192.168.43.1", HotspotIpPolicy.pick(listOf(c("ap0", "192.168.43.1")), emptySet()))
    }

    @Test
    fun `cellular and vpn interfaces are excluded by name from ConnectivityManager`() {
        // 运营商 CGNAT 就发 10.x，所以「私有地址」这个条件排不掉蜂窝——
        // 排除集必须来自 ConnectivityManager 的权威判定，不能靠接口名猜。
        val candidates = listOf(c("rmnet_data0", "10.180.4.7"), c("tun0", "10.8.0.2"))
        assertNull(HotspotIpPolicy.pick(candidates, setOf("rmnet_data0", "tun0")))
    }

    @Test
    fun `an excluded interface does not drag down a good one on another interface`() {
        // 排除是**逐接口**的。整体放弃会让「插着 VPN 时热点绑不上」，
        // 而用户根本不知道这两件事有关系。
        val candidates = listOf(c("tun0", "10.8.0.2"), c("ap0", "192.168.43.1"))
        assertEquals("192.168.43.1", HotspotIpPolicy.pick(candidates, setOf("tun0")))
    }

    @Test
    fun `down, loopback and virtual interfaces are skipped`() {
        assertNull(HotspotIpPolicy.pick(listOf(c("ap0", "192.168.43.1", isUp = false)), emptySet()))
        assertNull(HotspotIpPolicy.pick(listOf(c("lo", "127.0.0.1", isLoopback = true)), emptySet()))
        assertNull(HotspotIpPolicy.pick(listOf(c("p2p0", "192.168.49.1", isVirtual = true)), emptySet()))
    }

    @Test
    fun `non-private and unusable addresses are refused`() {
        assertNull(HotspotIpPolicy.pick(listOf(c("eth0", "8.8.8.8")), emptySet()))
        // 复用既有 UsableIpPolicy：169.254.x 绑得上但同网段电脑访问不到。
        assertNull(HotspotIpPolicy.pick(listOf(c("wlan0", "169.254.1.5")), emptySet()))
        assertNull(HotspotIpPolicy.pick(listOf(c("wlan0", "0.0.0.0")), emptySet()))
        // 边界：172 私有段止于 31。
        assertNull(HotspotIpPolicy.pick(listOf(c("eth0", "172.32.0.1")), emptySet()))
        assertEquals("172.31.0.1", HotspotIpPolicy.pick(listOf(c("eth0", "172.31.0.1")), emptySet()))
        // 下边界同样要钉：172.15 不是私有段。
        assertNull(HotspotIpPolicy.pick(listOf(c("eth0", "172.15.0.1")), emptySet()))
        assertEquals("172.16.0.1", HotspotIpPolicy.pick(listOf(c("eth0", "172.16.0.1")), emptySet()))
    }

    @Test
    fun `malformed addresses that merely look private are refused`() {
        // 逼红实测：第一版用的是 192.1.68.5 和 100.64.1.5，两者在 startsWith 实现下**也**会被拒
        // （前缀根本不匹配），所以那条断言分辨不出「前缀比较」与「逐段解析」——破坏 D 零条红。
        // 真正能分辨的是**畸形串**：下面三个在 startsWith("192.168") / startsWith("10") 下会被收下。
        assertNull("only three octets", HotspotIpPolicy.pick(listOf(c("eth0", "192.1689")), emptySet()))
        assertNull("five octets", HotspotIpPolicy.pick(listOf(c("eth0", "192.168.1.5.9")), emptySet()))
        assertNull("octet out of range", HotspotIpPolicy.pick(listOf(c("eth0", "192.168.1.999")), emptySet()))
        assertNull("non-numeric octet", HotspotIpPolicy.pick(listOf(c("eth0", "192.168.a.1")), emptySet()))
        // 顺带钉住真实的邻近段：100.64/10 是 CGNAT，不是 10/8。
        assertNull(HotspotIpPolicy.pick(listOf(c("eth0", "100.64.1.5")), emptySet()))
        assertNull(HotspotIpPolicy.pick(listOf(c("eth0", "192.1.68.5")), emptySet()))
    }

    @Test
    fun `the usable-address gate stays in place even though the private check subsumes it`() {
        // 逼红实测：拿掉 `.filter { UsableIpPolicy.isUsable(it.ip) }` 零条红——
        // 0.0.0.0 / 127.x / 169.254.x 全都不是 RFC1918 段，privateClassOf 已经把它们判掉了。
        // 那一层是**有意的纵深防御**（红线：不监听 0.0.0.0），不是遗漏；
        // 它保的是「将来有人放宽 privateClassOf 时外层闸门还在」。
        // 既然没有输入能让它单独转红，就用源码断言钉住它不被当作死代码删掉。
        val src = HotspotIpPolicySource.read()
        assertTrue(
            "the UsableIpPolicy gate must stay as defence in depth for the 0.0.0.0 red line",
            src.contains("UsableIpPolicy.isUsable("),
        )
    }

    @Test
    fun `192_168 wins over 172_16 and 10, deterministically`() {
        val candidates = listOf(
            c("if10", "10.0.0.5"),
            c("if172", "172.20.1.5"),
            c("if192", "192.168.1.5"),
        )
        // 这条路径只在「没有已连接 Wi-Fi」时走到，而 Android 热点用的就是 192.168.x。
        // 不是通用偏好，是场景偏好。
        assertEquals("192.168.1.5", HotspotIpPolicy.pick(candidates, emptySet()))
        assertEquals(
            "172.20.1.5",
            HotspotIpPolicy.pick(candidates.filterNot { it.ip.startsWith("192.") }, emptySet()),
        )
        assertEquals(
            "10.0.0.5",
            HotspotIpPolicy.pick(candidates.filter { it.ip.startsWith("10.") }, emptySet()),
        )
    }

    @Test
    fun `same class ties break by interface name so the answer is stable`() {
        val candidates = listOf(c("swlan0", "192.168.5.1"), c("ap0", "192.168.9.1"))
        // 不确定的选择会表现为「有时能连有时不能」，最难排查的那一类。
        assertEquals("192.168.9.1", HotspotIpPolicy.pick(candidates, emptySet()))
        assertEquals("192.168.9.1", HotspotIpPolicy.pick(candidates.reversed(), emptySet()))
    }

    @Test
    fun `two addresses on the same interface also resolve deterministically`() {
        // 一个接口可以有多个 IPv4。同接口同段时按地址定序，否则枚举顺序一变结果就变。
        val candidates = listOf(c("ap0", "192.168.9.1"), c("ap0", "192.168.5.1"))
        val first = HotspotIpPolicy.pick(candidates, emptySet())
        assertEquals(first, HotspotIpPolicy.pick(candidates.reversed(), emptySet()))
    }

    @Test
    fun `no candidates yields null`() {
        assertNull(HotspotIpPolicy.pick(emptyList(), emptySet()))
    }
}

/** 只为上面那条纵深防御断言读源码，不值得单开一个测试类。 */
private object HotspotIpPolicySource {
    fun read(): String {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            for (rel in listOf(
                "src/main/java/com/example/flikky/network/HotspotIpPolicy.kt",
                "app/src/main/java/com/example/flikky/network/HotspotIpPolicy.kt",
            )) {
                val f = java.io.File(dir, rel)
                if (f.isFile) {
                    return f.readText(Charsets.UTF_8)
                        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                        .replace(Regex("""(?m)^\s*//.*$"""), "")
                }
            }
            dir = dir.parentFile
        }
        error("cannot locate HotspotIpPolicy.kt")
    }
}
