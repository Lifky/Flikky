package com.example.flikky.server

import android.content.Context
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import com.example.flikky.server.routes.authRoutes
import kotlinx.serialization.json.Json

class KtorServer(
    private val context: Context,
    private val host: String,
    private val startPort: Int = 8080,
    private val endPort: Int = 8099,
    private val pinAuth: PinAuth,
    private val session: SessionState,
    private val stats: TransferStats,
) {
    private var engine: EmbeddedServer<*, *>? = null
    var boundPort: Int = -1
        private set

    fun start(): Int {
        var lastError: Throwable? = null
        for (port in startPort..endPort) {
            try {
                val server = embeddedServer(CIO, host = host, port = port) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
                    }
                    install(WebSockets) {
                        pingPeriodMillis = 15_000L
                        timeoutMillis = 30_000L
                    }
                    install(StatusPages) {
                        exception<Throwable> { call, cause ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "error")))
                        }
                    }
                    routing {
                        authRoutes(pinAuth, readAsset = { path ->
                            context.assets.open(path).use { it.readBytes() }
                        })
                    }
                }
                server.start(wait = false)
                engine = server
                boundPort = port
                return port
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalStateException("No port available in $startPort..$endPort", lastError)
    }

    fun stop() {
        engine?.stop(1_000, 3_000)
        engine = null
        boundPort = -1
    }
}
