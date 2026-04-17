package com.example.flikky.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import java.net.Inet4Address

class NetworkInfo(private val context: Context) {

    fun currentWifiIpv4(): String? {
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
}
