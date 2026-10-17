package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.AuthRequest
import com.example.flikky.server.dto.AuthResponse
import com.example.flikky.server.dto.WebThemeDto
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.io.FileNotFoundException

const val AUTH_COOKIE = "flikky_token"

/** 会话 Cookie：HttpOnly + SameSite=Strict + Path=/，不设过期 —— 会话结束即失效（spec §4.1）。 */
internal fun ApplicationCall.appendAuthCookie(token: String) {
    response.cookies.append(
        Cookie(
            name = AUTH_COOKIE,
            value = token,
            encoding = CookieEncoding.RAW,
            path = "/",
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        )
    )
}

fun Route.authRoutes(
    authGate: AuthGate,
    readAsset: (String) -> ByteArray,
    /** Where the browser should land after a successful PIN — depends on ServiceMode. */
    redirectAfterLogin: () -> String = { "/app" },
    /** Public, non-sensitive theme snapshot used by the pre-auth PIN page. */
    publicThemeProvider: () -> WebThemeDto = { WebThemeDto() },
) {
    get("/") {
        // 已持有有效凭据（第二个标签页、刷新、重新输入地址）：直接去落地页。
        // 否则 PIN 模式会把人留在一个 PIN 早已用掉的登录页上。
        if (authGate.isAuthorized(call.request.cookies[AUTH_COOKIE])) {
            call.respondRedirect(redirectAfterLogin())
            return@get
        }
        // 会话里已有设备：不管开没开 PIN，都进入口页「请求加入」，由手机允许（v1.23 多设备）。
        // 关 PIN 时不能再自动签发 —— 那等于局域网里谁来都直接进。
        if (authGate.joinRequired) {
            call.respondBytes(readAsset("web/login.html"), ContentType.Text.Html)
            return@get
        }
        if (!authGate.required) {
            // B54：关 PIN 也签发受控会话，之后照常按设备校验（spec §4.3）。
            authGate.issueOpen(call.request.headers[HttpHeaders.UserAgent])?.let { call.appendAuthCookie(it.token) }
            call.respondRedirect(redirectAfterLogin())
            return@get
        }
        val bytes = readAsset("web/login.html")
        call.respondBytes(bytes, ContentType.Text.Html)
    }

    post("/api/auth") {
        if (!authGate.required) {
            call.respond(AuthResponse(ok = true, redirectTo = redirectAfterLogin()))
            return@post
        }
        val req = call.receive<AuthRequest>()
        when (val attempt = authGate.authenticate(req.pin, call.request.headers[HttpHeaders.UserAgent])) {
            is AuthAttempt.Granted -> {
                call.appendAuthCookie(attempt.issued.token)
                call.respond(AuthResponse(ok = true, redirectTo = redirectAfterLogin()))
            }
            is AuthAttempt.Denied -> when (attempt.result) {
                PinAuth.Result.Wrong -> call.respond(HttpStatusCode.Unauthorized, AuthResponse(false, "wrong_pin"))
                PinAuth.Result.Locked -> call.respond(HttpStatusCode.TooManyRequests, AuthResponse(false, "locked", 30))
                PinAuth.Result.PinAlreadyUsed -> call.respond(HttpStatusCode.Gone, AuthResponse(false, "pin_consumed"))
                PinAuth.Result.Terminated -> call.respond(HttpStatusCode.Forbidden, AuthResponse(false, "terminated"))
                is PinAuth.Result.Ok -> call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }

    get("/api/web-theme") {
        call.respond(publicThemeProvider())
    }

    get("/app") {
        val token = call.request.cookies[AUTH_COOKIE]
        if (!authGate.isAuthorized(token)) {
            call.respondRedirect("/")
            return@get
        }
        // 模式判断必须排在鉴权之后：需要 PIN 时，未授权请求应当回登录页，
        // 而不是被转到 /export 再吃一个裸 401。
        //
        // authRoutes 在两种 ServiceMode 下都会注册，而这条路由原先无条件发会话页。
        // 于是「刚结束一次传输 → 手机端改为导出 → 浏览器停在 /app 直接刷新」会拿到
        // 一个完整的会话页，它随即去连一个导出模式下并不存在的 WebSocket ——
        // 表现为无限断开重连，且面板里没有任何有效数据。用户必须手工把地址删回
        // 端口号才能走到导出页。
        val landing = redirectAfterLogin()
        if (landing != "/app") {
            call.respondRedirect(landing)
            return@get
        }
        val bytes = readAsset("web/app.html")
        call.respondBytes(bytes, ContentType.Text.Html)
    }

    // Android asset 不在 classpath 中，Ktor 的 staticResources 找不到。
    // 这里用显式路由 + readAsset 读 app/src/main/assets/web/ 下的文件。
    get("/static/{path...}") {
        val parts = call.parameters.getAll("path").orEmpty()
        if (parts.isEmpty()) { call.respond(HttpStatusCode.NotFound); return@get }
        val rel = parts.joinToString("/")
        if (rel.contains("..")) { call.respond(HttpStatusCode.Forbidden); return@get }
        try {
            val bytes = readAsset("web/$rel")
            call.respondBytes(bytes, contentTypeFor(rel))
        } catch (_: FileNotFoundException) {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun contentTypeFor(path: String): ContentType = when {
    path.endsWith(".css", ignoreCase = true) -> ContentType.Text.CSS
    path.endsWith(".js", ignoreCase = true) -> ContentType.Application.JavaScript
    path.endsWith(".html", ignoreCase = true) -> ContentType.Text.Html
    path.endsWith(".json", ignoreCase = true) -> ContentType.Application.Json
    path.endsWith(".svg", ignoreCase = true) -> ContentType("image", "svg+xml")
    path.endsWith(".png", ignoreCase = true) -> ContentType.Image.PNG
    path.endsWith(".ico", ignoreCase = true) -> ContentType("image", "x-icon")
    else -> ContentType.Application.OctetStream
}
