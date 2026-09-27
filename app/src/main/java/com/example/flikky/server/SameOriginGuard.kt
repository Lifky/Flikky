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
 * 装在所有路由之前：跨源的状态修改请求在进路由前就被 403 挡掉，路由里的任何副作用都不会发生。
 * CLAUDE.md 安全红线「状态修改请求必须校验同源」的落点；此前只有 `SameSite=Strict` Cookie 这一道。
 */
fun Application.installSameOriginGuard() {
    intercept(ApplicationCallPipeline.Plugins) {
        val request = call.request
        val upgrade = request.header(HttpHeaders.Upgrade)?.equals("websocket", ignoreCase = true) == true
        val ok = isSameOriginRequest(
            method = request.httpMethod.value,
            isWebSocketUpgrade = upgrade,
            origin = request.header(HttpHeaders.Origin),
            referer = request.header(HttpHeaders.Referrer),
            scheme = "http",
            host = request.header(HttpHeaders.Host),
        )
        if (!ok) {
            // 直接写文本，不经 ContentNegotiation：守卫不依赖插件的安装顺序。
            call.respondText("""{"error":"cross_origin"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
            finish()
        }
    }
}
