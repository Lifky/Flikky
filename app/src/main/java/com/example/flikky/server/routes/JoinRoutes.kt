package com.example.flikky.server.routes

import com.example.flikky.server.JoinRequests
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

/** 请求凭证的 Cookie：HttpOnly（页面 JS 读不到）、SameSite=Strict、只发给 /api/join。 */
const val JOIN_COOKIE = "flikky_join"
private const val JOIN_PATH = "/api/join"

@Serializable
data class JoinStateDto(val joinRequired: Boolean)

@Serializable
data class JoinStatusDto(
    val status: String,
    val redirectTo: String? = null,
    /** 还在等手机时的比对码（D82）：页面显示它，用户与手机上的核对。其余状态为 null。 */
    val code: String? = null,
)

/**
 * 「请求加入」（v1.23 多设备，用户裁决）：会话里已有设备时，新设备在入口页请求加入，手机允许后进入。
 *
 * - `GET  /api/join/state`  入口页决定显示 PIN 还是「请求加入」。不含任何秘密，所以不需要凭据。
 * - `POST /api/join`        发起（同一页面重复点不会再排一个）。
 * - `POST /api/join/status` 轮询；手机允许后这一次当场签发会话凭据并种 Cookie。
 *
 * 等待中的应答都带比对码（D82）：刷新页面后再点一次，拿到的仍是同一个请求、同一个码。
 *
 * 两个 POST 都过 SameOriginGuard（D79），请求凭证只走 HttpOnly Cookie。
 */
fun Route.joinRoutes(
    authGate: AuthGate,
    joins: JoinRequests,
    redirectAfterLogin: () -> String = { "/app" },
) {
    get("$JOIN_PATH/state") {
        call.respond(JoinStateDto(joinRequired = authGate.joinRequired))
    }

    post(JOIN_PATH) {
        if (!authGate.joinRequired) {
            call.respond(HttpStatusCode.Conflict, JoinStatusDto("not_needed"))
            return@post
        }
        val existing = joins.status(call.request.cookies[JOIN_COOKIE])
        if (existing is JoinRequests.Status.Pending) {
            call.respond(HttpStatusCode.Accepted, JoinStatusDto("pending", code = existing.code))
            return@post
        }
        val created = joins.create(call.request.headers[HttpHeaders.UserAgent])
        if (created == null) {
            call.respond(HttpStatusCode.TooManyRequests, JoinStatusDto("busy"))
            return@post
        }
        call.setJoinCookie(created.secret)
        call.respond(HttpStatusCode.Accepted, JoinStatusDto("pending", code = created.request.code))
    }

    post("$JOIN_PATH/status") {
        val secret = call.request.cookies[JOIN_COOKIE]
        when (val status = joins.status(secret)) {
            is JoinRequests.Status.Pending -> call.respond(HttpStatusCode.Accepted, JoinStatusDto("pending", code = status.code))
            JoinRequests.Status.Approved -> {
                val claimed = joins.claim(secret)
                if (claimed == null) {
                    call.respond(HttpStatusCode.Gone, JoinStatusDto("unknown"))
                    return@post
                }
                call.appendAuthCookie(authGate.issueJoined(claimed.userAgent).token)
                call.setJoinCookie("", expire = true)
                call.respond(JoinStatusDto("approved", redirectTo = redirectAfterLogin()))
            }
            JoinRequests.Status.Denied -> {
                call.setJoinCookie("", expire = true)
                call.respond(HttpStatusCode.Forbidden, JoinStatusDto("denied"))
            }
            JoinRequests.Status.Expired -> call.respond(HttpStatusCode.Gone, JoinStatusDto("expired"))
            JoinRequests.Status.Unknown -> call.respond(HttpStatusCode.Gone, JoinStatusDto("unknown"))
        }
    }
}

private fun ApplicationCall.setJoinCookie(value: String, expire: Boolean = false) {
    response.cookies.append(
        Cookie(
            name = JOIN_COOKIE,
            value = value,
            encoding = CookieEncoding.RAW,
            path = JOIN_PATH,
            httpOnly = true,
            maxAge = if (expire) 0 else null,
            extensions = mapOf("SameSite" to "Strict"),
        )
    )
}
