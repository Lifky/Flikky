package com.example.flikky.server.routes

import com.example.flikky.server.dto.FavoritesResponseDto
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
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

    get("/api/favorites/{id}/file") {
        if (!authed(call)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        // 入参只允许纯数字行 id。真实路径由 service 层用 FavoriteFileStore.resolve 得出，
        // 用户输入不参与路径拼接 —— 结构上没有 ../ 穿越面。
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val file = fileResolver(id)
        if (file == null || !file.isFile) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val mime = call.request.queryParameters["mime"]
        val inline = call.request.queryParameters["inline"] == "1" &&
            mime != null && isMediaMime(mime)

        call.response.header(
            HttpHeaders.ContentDisposition,
            (if (inline) ContentDisposition.Inline else ContentDisposition.Attachment)
                .withParameter(ContentDisposition.Parameters.FileName, file.name)
                .toString(),
        )
        call.respondOutputStream(
            contentType = if (inline) ContentType.parse(mime!!) else ContentType.Application.OctetStream,
            status = HttpStatusCode.OK,
        ) {
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)   // 与 FileRoutes 同一 64KB 泵
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    write(buf, 0, n)
                }
                flush()
            }
        }
    }
}
