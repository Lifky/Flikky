package com.example.flikky.server.routes

import com.example.flikky.server.dto.FavoritesResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File

/**
 * v1.19.0: 浏览器端收藏 tab 的只读接口。
 *
 * 只接 lambda，不认识 Room / Android —— 数据读取与实体映射都在 service/ 完成。
 * 两个 endpoint 都走 cookie 统一鉴权；「进 tab 才拉取」，不做 WS 增量推送（YAGNI，见 spec §4.2）。
 */
fun Route.favoriteRoutes(
    authGate: AuthGate,
    listProvider: suspend () -> FavoritesResponseDto,
    fileResolver: suspend (favoriteId: Long) -> File?,
) {
    fun authed(call: ApplicationCall): Boolean =
        authGate.isAuthorized(call.request.cookies[AUTH_COOKIE])

    get("/api/favorites") {
        if (!authed(call)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        call.respond(listProvider())
    }
}
