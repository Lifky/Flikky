package com.example.flikky.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respondText
import com.example.flikky.session.LocalNameStatus
import com.example.flikky.session.shownNumber
import com.example.flikky.util.LocalHostName
import java.net.URI

/** 会改状态的方法。GET/HEAD/OPTIONS 只读，不在此列（WebSocket 握手单独算，见下）。 */
private val STATE_CHANGING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")

/**
 * D79：请求是否同源。只对状态修改请求（含 WebSocket 握手）做判定，其余一律放行。
 *
 * - 有 `Origin`：必须与 `scheme://Host` 完全相同（主机名不分大小写）。`Origin: null`
 *   （沙箱 iframe、no-referrer 表单都会发，攻击页可以主动做到）一律拒绝。
 * - 没有 `Origin` 但有 `Referer`：取它的源来比。
 * - 两者都没有：不是浏览器发的（受支持的浏览器对这些请求都会带 Origin）。CSRF 需要
 *   受害者的浏览器替攻击者发请求，所以放行不开口子 —— 鉴权仍由各路由自己做。
 *
 * 服务端只听明文 HTTP、只绑具体私有 IPv4（红线），`scheme` 由调用方传入，不猜。
 */
internal fun isSameOriginRequest(
    method: String,
    isWebSocketUpgrade: Boolean,
    origin: String?,
    referer: String?,
    scheme: String,
    host: String?,
): Boolean {
    val guarded = isWebSocketUpgrade || method.uppercase() in STATE_CHANGING_METHODS
    if (!guarded) return true
    val claimed = when {
        origin != null -> origin
        referer != null -> originOf(referer) ?: return false
        else -> return true
    }
    if (host.isNullOrBlank()) return false
    return claimed.equals("$scheme://$host", ignoreCase = true)
}

/** `http://h:p/path?q` → `http://h:p`；读不出来的当作外源（返回 null）。 */
private fun originOf(url: String): String? = runCatching {
    val u = URI(url)
    val scheme = u.scheme ?: return null
    val authority = u.rawAuthority ?: return null
    "$scheme://$authority"
}.getOrNull()

/**
 * 本机真正对外的名字：绑定的 IP 与卡片上显示的 `.local` 名，都带实际端口。
 * 别的名字（包括 DNS 重绑定到本机 IP 的攻击者域名）一律不认。
 */
internal fun servedHosts(host: String, port: Int, localName: LocalNameStatus): Set<String> =
    setOfNotNull("$host:$port", localName.shownNumber()?.let { "${LocalHostName.fqdn(it)}:$port" })

/** Host 头是否是 [served] 之一。主机名不分大小写，允许末尾的根点（`flikky3.local.:8080`）。 */
internal fun isServedHost(hostHeader: String?, served: Set<String>): Boolean {
    if (hostHeader.isNullOrBlank()) return false
    val colon = hostHeader.lastIndexOf(':')
    if (colon <= 0) return false
    val name = hostHeader.substring(0, colon).trimEnd('.').lowercase()
    val normalized = "$name:${hostHeader.substring(colon + 1)}"
    return served.any { it.equals(normalized, ignoreCase = true) }
}

/**
 * 装在所有路由之前，两道检查，任一不过就 403，路由里的任何副作用都不会发生：
 * 1. **Host 白名单**（所有请求）：只认 [allowedHosts]。挡 DNS 重绑定 —— 攻击页把自己的域名重绑到手机 IP 时，
 *    Host 与 Origin 都是攻击者域名，同源比对会成立；PIN 关闭时鉴权又放行一切（D79 审查修订）。
 *    每次请求现取：局域网名称在服务起来之后才探测完成。
 * 2. **同源**（状态修改请求与 WS 握手）：见 [isSameOriginRequest]。CLAUDE.md 安全红线「状态修改请求必须校验同源」的落点。
 */
fun Application.installSameOriginGuard(allowedHosts: () -> Set<String>) {
    intercept(ApplicationCallPipeline.Plugins) {
        val request = call.request
        val host = request.header(HttpHeaders.Host)
        if (!isServedHost(host, allowedHosts())) {
            // 直接写文本，不经 ContentNegotiation：守卫不依赖插件的安装顺序。
            call.respondText("""{"error":"unknown_host"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
            finish()
            return@intercept
        }
        val upgrade = request.header(HttpHeaders.Upgrade)?.equals("websocket", ignoreCase = true) == true
        val ok = isSameOriginRequest(
            method = request.httpMethod.value,
            isWebSocketUpgrade = upgrade,
            origin = request.header(HttpHeaders.Origin),
            referer = request.header(HttpHeaders.Referrer),
            scheme = "http",
            host = host,
        )
        if (!ok) {
            call.respondText("""{"error":"cross_origin"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
            finish()
        }
    }
}
