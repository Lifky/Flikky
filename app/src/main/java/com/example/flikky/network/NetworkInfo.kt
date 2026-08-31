package com.example.flikky.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

class NetworkInfo(private val context: Context) {

    /**
     * 开服要绑的地址。
     *
     * 先走已连接 Wi-Fi（原有唯一路径，**逐字节未改**），取不到再回落到热点扫描。
     * 顺序是刻意的：修热点场景不该有机会弄坏主场景。
     */
    fun currentWifiIpv4(): String? = connectedWifiIpv4() ?: hotspotIpv4()

    /** 原有实现原样搬进来，一行未动。 */
    private fun connectedWifiIpv4(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = cm.allNetworks
        for (net in networks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val linkProps = cm.getLinkProperties(net) ?: continue
            val addr: LinkAddress? = linkProps.linkAddresses.firstOrNull { it.address is Inet4Address }
            if (addr != null) return (addr.address as Inet4Address).hostAddress
        }
        return null
    }

    /**
     * 手机自己当热点时，AP 接口不是「已连接的网络」，[ConnectivityManager] 枚举不到它，
     * 只能自己扫 [NetworkInterface]。
     *
     * 蜂窝与 VPN 的接口名从 [ConnectivityManager] 取（权威判定），判断全部交给
     * [HotspotIpPolicy]——**本函数只取数据，一个判断都不做**。这样「该绑哪个」是纯函数，
     * 能在 `test/` 穷举；而这里剩下的只有「怎么取数据」，出错方式是抛异常而不是选错。
     *
     * `NetworkInterface` 的每个属性都可能抛 [SocketException]（接口在枚举过程中消失），
     * 逐个兜住并按「最保守」取值：取不到就当它 down / 是回环，宁可少一个候选也不要绑错。
     */
    private fun hotspotIpv4(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val excluded = cm.allNetworks.mapNotNull { net ->
            val caps = cm.getNetworkCapabilities(net) ?: return@mapNotNull null
            val cellularOrVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            if (cellularOrVpn) cm.getLinkProperties(net)?.interfaceName else null
        }.toSet()

        val ifaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: SocketException) {
            emptyList()
        }
        val candidates = buildList {
            for (iface in ifaces) {
                val up = try { iface.isUp } catch (e: SocketException) { false }
                val loopback = try { iface.isLoopback } catch (e: SocketException) { true }
                val virtual = try { iface.isVirtual } catch (e: SocketException) { true }
                val name = iface.name ?: continue
                for (addr in iface.inetAddresses) {
                    if (addr !is Inet4Address) continue
                    val host = addr.hostAddress ?: continue
                    add(IpCandidate(name, host, up, loopback, virtual))
                }
            }
        }
        return HotspotIpPolicy.pick(candidates, excluded)
    }
}
