package com.example.flikky.network

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `NetworkInfo` 的接线守卫。
 *
 * `HotspotIpPolicy` 的单测能穷举「给定候选与排除集该选谁」，但**排除集是不是真的传对了**
 * 只有接线处知道——而传错的后果是直接违反安全红线（把蜂窝 CGNAT 的 10.x 当热点地址绑上），
 * 且在任何一台没插 SIM 的测试机上都不会复现。
 *
 * 这一层用源码扫描而不是仪器测试：要用仪器测试验它，得同时具备热点开启 + 蜂窝在线 + VPN 连着
 * 三个条件，而那正是最难在 CI 或开发机上凑齐的组合。
 */
class NetworkInfoHotspotWiringTest {

    private fun source(): String {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            for (rel in listOf(
                "src/main/java/com/example/flikky/network/NetworkInfo.kt",
                "app/src/main/java/com/example/flikky/network/NetworkInfo.kt",
            )) {
                val f = File(dir, rel)
                if (f.isFile) return f.readText(Charsets.UTF_8)
            }
            dir = dir.parentFile
        }
        error("cannot locate NetworkInfo.kt from user.dir=" + System.getProperty("user.dir"))
    }

    private val src: String
        get() = source()
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""(?m)^\s*//.*$"""), "")
            .replace(Regex("""(?m)^import .*$"""), "")

    @Test
    fun `the connected wifi path is tried before the hotspot fallback`() {
        // 顺序反过来的后果：连着 Wi-Fi 时也走接口扫描，可能绑到别的接口上。
        // 修热点场景不该有机会弄坏主场景。
        val decl = Regex("""fun currentWifiIpv4\(\).*""").find(src)
        assertTrue("no currentWifiIpv4 declaration", decl != null)
        val body = decl!!.value
        val wifiAt = body.indexOf("connectedWifiIpv4()")
        val hotspotAt = body.indexOf("hotspotIpv4()")
        assertTrue("currentWifiIpv4 must consult connectedWifiIpv4: $body", wifiAt >= 0)
        assertTrue("currentWifiIpv4 must fall back to hotspotIpv4: $body", hotspotAt >= 0)
        assertTrue("the wifi path must come first: $body", wifiAt < hotspotAt)
        assertTrue("the fallback must be elvis, not unconditional: $body", body.contains("?:"))
    }

    @Test
    fun `the hotspot fallback excludes cellular and vpn interfaces`() {
        // 红线：不绑定蜂窝/VPN。传 emptySet() 会让策略照单全收——运营商 CGNAT 就发 10.x，
        // 私有地址这个条件排不掉它。这条断言就是那条红线在代码里的唯一守卫。
        val at = src.indexOf("private fun hotspotIpv4()")
        assertTrue("no hotspotIpv4", at > 0)
        val body = src.substring(at)
        assertTrue(
            "the excluded set must come from TRANSPORT_CELLULAR",
            body.contains("NetworkCapabilities.TRANSPORT_CELLULAR"),
        )
        assertTrue(
            "the excluded set must come from TRANSPORT_VPN",
            body.contains("NetworkCapabilities.TRANSPORT_VPN"),
        )
        assertTrue("interface names must come from linkProperties", body.contains("interfaceName"))
        // 必须真的传进策略，不是算完丢掉。
        val call = Regex("""HotspotIpPolicy\.pick\([^)]*\)""").find(body)
        assertTrue("no HotspotIpPolicy.pick call", call != null)
        assertFalse(
            "the exclusion set must be passed to the policy, not emptySet(): ${call!!.value}",
            call.value.contains("emptySet()"),
        )
        assertTrue(
            "pick must receive the collected exclusions: ${call.value}",
            call.value.contains("excluded"),
        )
    }

    @Test
    fun `the fallback makes no address decisions of its own`() {
        // 判断全部在 HotspotIpPolicy（可穷举）。在这里再判一遍就会分叉出
        // 「策略说不行、接线处放行」的状态，而那需要真机 + 热点 + 蜂窝才能复现。
        val at = src.indexOf("private fun hotspotIpv4()")
        val body = src.substring(at)
        for (banned in listOf("192.168", "172.", "startsWith(\"10.\"", "0.0.0.0", "169.254")) {
            assertFalse(
                "address judgement belongs in HotspotIpPolicy, found '$banned' in hotspotIpv4",
                body.contains(banned),
            )
        }
    }
}
