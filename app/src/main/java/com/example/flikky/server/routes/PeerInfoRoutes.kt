package com.example.flikky.server.routes

import com.example.flikky.server.dto.PeerInfoDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * M9: GET /api/peer-info — returns the phone's appearance settings so the browser can
 * display them (device name, phone avatar, background). Cookie-gated like all other API routes.
 */
fun Route.peerInfoRoutes(authGate: AuthGate, provider: () -> PeerInfoDto) {
    get("/api/peer-info") {
        val token = call.request.cookies[AUTH_COOKIE]
        if (!authGate.isAuthorized(token)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        call.respond(provider())
    }
}
