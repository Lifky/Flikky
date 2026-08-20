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
 * 收藏行 id 解析出的落盘文件 + 下载展示用文件名。不携带 mime —— 这个 endpoint 永远按
 * octet-stream 下发（见下方 §4.2 注释），加 mime 字段没有消费者，属于重新引入已删掉的面。
 */
data class FavoriteFileHandle(val file: File, val fileName: String)

/**
 * v1.19.0: 浏览器端收藏 tab 的只读接口。
 *
 * 只接 lambda，不认识 Room / Android —— 数据读取与实体映射都在 service/ 完成。
 * 两个 endpoint 都走 cookie 统一鉴权；「进 tab 才拉取」，不做 WS 增量推送（YAGNI，见 spec §4.2）。
 *
 * [enabled] 是 favoriteBetaEnabled 功能开关的只读探针（beta 关闭是默认态）：鉴权检查之后再判断，
 * 未鉴权始终先拿 401；鉴权通过但开关关闭时统一回 404（不是 403——不向未授权方暴露"这个功能存在但被禁"）。
 */
fun Route.favoriteRoutes(
    authGate: AuthGate,
    listProvider: suspend () -> FavoritesResponseDto,
    fileResolver: suspend (favoriteId: Long) -> FavoriteFileHandle?,
    enabled: suspend () -> Boolean = { true },
) {
    fun authed(call: ApplicationCall): Boolean =
        authGate.isAuthorized(call.request.cookies[AUTH_COOKIE])

    get("/api/favorites") {
        if (!authed(call)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        if (!enabled()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respond(listProvider())
    }

    get("/api/favorites/{id}/file") {
        if (!authed(call)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        if (!enabled()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        // 入参只允许纯数字行 id。真实路径由 service 层用 FavoriteFileStore.resolve 得出，
        // 用户输入不参与路径拼接 —— 结构上没有 ../ 穿越面。
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val handle = fileResolver(id)
        if (handle == null || !handle.file.isFile) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        // 收藏面板只提供“下载”，没有预览/lightbox（spec §4.2），所以这里刻意不提供 inline：
        // 调用方不能选择渲染的 Content-Type，永远按 attachment + octet-stream 下发。
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, handle.fileName)
                .toString(),
        )
        call.response.header(HttpHeaders.ContentLength, handle.file.length().toString())
        call.respondOutputStream(
            contentType = ContentType.Application.OctetStream,
            status = HttpStatusCode.OK,
        ) {
            // 收藏下载不计入 TransferStats：这是设计裁决（收藏是本机存量数据，不是这次会话
            // 的传输量），不是遗漏——不要在这里补 stats 记录。
            handle.file.inputStream().use { input ->
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
