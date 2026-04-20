package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.FileMessageDto
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import com.example.flikky.util.IdGen
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.writeFully
import java.io.File
import java.util.UUID

interface FileStore {
    /** 目录路径：filesDir/sessions/$sessionId/files/，自动建目录。 */
    fun fileDir(sessionId: Long): File
}

fun Route.fileRoutes(
    session: SessionState,
    pinAuth: PinAuth,
    store: FileStore,
    stats: TransferStats,
    broadcastEvent: suspend (type: String, payload: String) -> Unit,
    nowMs: () -> Long,
) {
    fun authed(call: ApplicationCall): Boolean {
        val token = call.request.cookies[AUTH_COOKIE]
        return token != null && pinAuth.validateToken(token)
    }

    // TODO: Task 19 will update fileDir() call to pass sessionId
    post("/api/files") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@post }
        call.respond(HttpStatusCode.InternalServerError)
    }

    // TODO: Task 19 will update fileDir() and remove takePushedFile() references
    get("/api/files/{id}") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@get }
        val id = call.parameters["id"] ?: run { call.respond(HttpStatusCode.BadRequest); return@get }
        call.respond(HttpStatusCode.NotFound)
    }
}
