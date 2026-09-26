package com.example.flikky.session

import com.example.flikky.util.LocalHostName

/** 本次会话的局域网名称状态（spec §4.2）。 */
sealed interface LocalNameStatus {
    data object Disabled : LocalNameStatus
    data class Probing(val number: Int) : LocalNameStatus
    data class Owned(val number: Int) : LocalNameStatus
    /** 设定的编号被占用，本次会话用了 [actual]。设置里仍是 [wanted]。 */
    data class Renamed(val wanted: Int, val actual: Int) : LocalNameStatus
    data object Unavailable : LocalNameStatus
}

data class PortNotice(val requested: Int, val actual: Int)

/** 连接卡片要显示的全部地址信息。IP 永远是主地址 —— 最稳定，二维码编的也是它（D63）。 */
data class ConnectionAddresses(
    val primaryUrl: String,
    val localUrl: String?,
    val localName: LocalNameStatus,
    val portNotice: PortNotice?,
)

fun connectionAddresses(
    ip: String,
    boundPort: Int,
    requestedPort: Int,
    localName: LocalNameStatus,
): ConnectionAddresses {
    val number = when (localName) {
        is LocalNameStatus.Probing -> localName.number
        is LocalNameStatus.Owned -> localName.number
        is LocalNameStatus.Renamed -> localName.actual
        LocalNameStatus.Disabled, LocalNameStatus.Unavailable -> null
    }
    return ConnectionAddresses(
        primaryUrl = LocalHostName.url(ip, boundPort),
        localUrl = number?.let { LocalHostName.url(LocalHostName.fqdn(it), boundPort) },
        localName = localName,
        portNotice = PortNotice(requestedPort, boundPort)
            .takeIf { requestedPort > 0 && boundPort > 0 && requestedPort != boundPort },
    )
}
