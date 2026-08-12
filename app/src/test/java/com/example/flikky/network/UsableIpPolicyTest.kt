package com.example.flikky.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 能不能开服，取决于有没有一个电脑真的连得上的地址。
 * 没连 Wi-Fi 时是 null；系统在刚断开/正在协商时还会给出 169.254.x 自配置地址——
 * 那个地址能绑上 socket，但同网段的电脑访问不到，等于开出一个假的服务。
 */
class UsableIpPolicyTest {

    @Test
    fun ordinaryPrivateAddressesAreUsable() {
        assertTrue(UsableIpPolicy.isUsable("192.168.1.7"))
        assertTrue(UsableIpPolicy.isUsable("10.0.0.42"))
        assertTrue(UsableIpPolicy.isUsable("172.20.10.3"))
    }

    @Test
    fun noWifiMeansNoAddress() {
        assertFalse(UsableIpPolicy.isUsable(null))
        assertFalse(UsableIpPolicy.isUsable(""))
        assertFalse(UsableIpPolicy.isUsable("   "))
    }

    @Test
    fun linkLocalAutoconfigurationIsNotReachableByThePeer() {
        assertFalse(UsableIpPolicy.isUsable("169.254.13.201"))
    }

    @Test
    fun wildcardAndLoopbackAreNotServeable() {
        assertFalse(UsableIpPolicy.isUsable("0.0.0.0"))
        assertFalse(UsableIpPolicy.isUsable("127.0.0.1"))
    }
}
