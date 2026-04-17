package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.AuthRequest
import com.example.flikky.server.dto.AuthResponse
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

const val AUTH_COOKIE = "flikky_token"

fun Route.authRoutes(
    pinAuth: PinAuth,
    readAsset: (String) -> ByteArray,
) {
    get("/") {
        val bytes = readAsset("web/login.html")
        call.respondBytes(bytes, ContentType.Text.Html)
    }

    post("/api/auth") {
        val req = call.receive<AuthRequest>()
        when (val r = pinAuth.tryConsume(req.pin)) {
            is PinAuth.Result.Ok -> {
                call.response.cookies.append(
                    Cookie(
                        name = AUTH_COOKIE,
                        value = r.token,
                        encoding = CookieEncoding.RAW,
                        path = "/",
                        httpOnly = true,
                        extensions = mapOf("SameSite" to "Strict"),
                    )
                )
                call.respond(AuthResponse(ok = true))
            }
            PinAuth.Result.Wrong -> call.respond(HttpStatusCode.Unauthorized, AuthResponse(false, "wrong_pin"))
            PinAuth.Result.Locked -> call.respond(HttpStatusCode.TooManyRequests, AuthResponse(false, "locked", 30))
            PinAuth.Result.PinAlreadyUsed -> call.respond(HttpStatusCode.Gone, AuthResponse(false, "pin_consumed"))
            PinAuth.Result.Terminated -> call.respond(HttpStatusCode.Forbidden, AuthResponse(false, "terminated"))
        }
    }

    get("/app") {
        val token = call.request.cookies[AUTH_COOKIE]
        if (token == null || !pinAuth.validateToken(token)) {
            call.respondRedirect("/")
            return@get
        }
        val bytes = readAsset("web/app.html")
        call.respondBytes(bytes, ContentType.Text.Html)
    }

    staticResources("/static", "web")
}
