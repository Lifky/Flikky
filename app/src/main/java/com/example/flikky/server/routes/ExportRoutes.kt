package com.example.flikky.server.routes

import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.ZipExporter
import com.example.flikky.server.PinAuth
import com.example.flikky.session.SessionState
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable

/**
 * Routes serving the browser-facing export flow (v1.2 §2.2 / §3.1).
 *
 * - `GET /export`             →  HTML shell the browser lands on after PIN auth.
 * - `GET /api/export/zip`     →  streaming zip download. Only allowed while
 *                                [SessionState.exportMode] is [ExportMode.Armed].
 *
 * The server package intentionally does not depend on `data/`. Callers inject
 * the live snapshot via [exportedBy] and the file lookup via [fileResolver];
 * the typical wiring (in `KtorServer`) reads the snapshot straight from
 * `(SessionState.exportMode.value as? ExportMode.Armed)?.snapshot`.
 *
 * [onZipSent] fires once the zip response has finished streaming successfully.
 * `TransferService` uses it to trigger `stopSelf()` so the export window closes
 * cleanly.
 */
fun Route.exportRoutes(
    sessionState: SessionState,
    pinAuth: PinAuth,
    readAsset: (String) -> ByteArray,
    exportedBy: (sessionIds: List<Long>) -> ExportSnapshot,
    fileResolver: (sessionId: Long, fileId: String) -> java.io.File?,
    onZipSent: suspend () -> Unit,
    now: () -> Long,
) {
    fun authed(call: ApplicationCall): Boolean {
        val token = call.request.cookies[AUTH_COOKIE]
        return token != null && pinAuth.validateToken(token)
    }

    get("/export") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@get }
        val bytes = try {
            readAsset("web/export.html")
        } catch (_: FileNotFoundException) {
            STUB_EXPORT_HTML.toByteArray(Charsets.UTF_8)
        }
        call.respondBytes(bytes, ContentType.Text.Html)
    }

    get("/api/export/info") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@get }

        val armed = sessionState.exportMode.value as? ExportMode.Armed
        if (armed == null) {
            call.respond(HttpStatusCode.Conflict, mapOf("error" to "not_armed"))
            return@get
        }

        call.respond(buildInfoDto(armed.snapshot))
    }

    get("/api/export/zip") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@get }

        val armed = sessionState.exportMode.value as? ExportMode.Armed
        if (armed == null) {
            call.respond(HttpStatusCode.Conflict, mapOf("error" to "not_armed"))
            return@get
        }

        val snapshot = armed.snapshot
        val filename = buildFilename(now())

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter("filename", filename).toString(),
        )

        // Move Armed → Sending so the UI knows a transfer is in flight. totalBytes
        // is an estimate from file messages in the snapshot; -1L if unknown.
        val totalBytes = estimateTotalBytes(snapshot)
        runCatching { sessionState.updateExportProgress(bytesSent = 0L, totalBytes = totalBytes) }

        var streamedOk = false
        try {
            call.respondOutputStream(contentType = ContentType.Application.Zip, status = HttpStatusCode.OK) {
                ZipExporter.write(
                    out = this,
                    snapshot = snapshot,
                    fileResolver = fileResolver,
                    timeZone = TimeZone.getDefault(),
                )
            }
            streamedOk = true
        } catch (t: Throwable) {
            // Don't transition to Done on failure; reset so the UI doesn't lie about success.
            sessionState.clearExport()
            throw t
        }

        if (streamedOk) {
            runCatching { sessionState.markExportDone() }
            onZipSent()
        }
    }
}

private fun buildFilename(nowMs: Long): String {
    val fmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    return "flikky-export-${fmt.format(Date(nowMs))}.zip"
}

private fun estimateTotalBytes(snapshot: ExportSnapshot): Long {
    return snapshot.sessions.sumOf { s ->
        s.messages.filterIsInstance<com.example.flikky.export.MessageExport.File>()
            .sumOf { it.sizeBytes }
    }
}

// Minimal placeholder shipped until T8 delivers the real export page.
private val STUB_EXPORT_HTML: String =
    "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">" +
        "<title>Flikky Export</title></head><body>" +
        "<h1>Flikky Export</h1>" +
        "<p>占位页 — 正式界面将在 M3 T8 提供。</p>" +
        "<p><a href=\"/api/export/zip\">下载 zip</a></p>" +
        "</body></html>"

@Serializable
internal data class ExportInfoDto(
    val sessionCount: Int,
    val messageCount: Int,
    val fileCount: Int,
    val totalBytes: Long,
    val sessions: List<ExportInfoSessionDto>,
)

@Serializable
internal data class ExportInfoSessionDto(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val messageCount: Int,
    val fileCount: Int,
    val totalBytes: Long,
)

private fun buildInfoDto(snapshot: ExportSnapshot): ExportInfoDto {
    val sessionDtos = snapshot.sessions.map { s ->
        val fileMessages = s.messages.filterIsInstance<com.example.flikky.export.MessageExport.File>()
        ExportInfoSessionDto(
            id = s.id,
            name = s.name,
            startedAt = s.startedAt,
            endedAt = s.endedAt,
            messageCount = s.messages.size,
            fileCount = fileMessages.size,
            totalBytes = fileMessages.sumOf { it.sizeBytes },
        )
    }
    return ExportInfoDto(
        sessionCount = sessionDtos.size,
        messageCount = sessionDtos.sumOf { it.messageCount },
        fileCount = sessionDtos.sumOf { it.fileCount },
        totalBytes = sessionDtos.sumOf { it.totalBytes },
        sessions = sessionDtos,
    )
}
