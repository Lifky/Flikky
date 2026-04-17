package com.example.flikky.server

import android.content.Context
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
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
import com.example.flikky.server.routes.fileRoutes
import com.example.flikky.server.routes.messageRoutes
import com.example.flikky.server.routes.wsRoutes
import kotlinx.serialization.json.Json

class KtorServer(
    private val context: Context,
    private val host: String,
    private val startPort: Int = 8080,
    private val endPort: Int = 8099,
    private val pinAuth: PinAuth,
    private val session: SessionState,
    private val stats: TransferStats,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var engine: EmbeddedServer<*, *>? = null
    var boundPort: Int = -1
        private set

    internal val wsHub = com.example.flikky.server.routes.WsHub()
    internal val fileStore = AndroidFileStore(context)

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
                    intercept(ApplicationCallPipeline.Plugins) {
                        call.response.headers.append("Content-Security-Policy",
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'")
                        call.response.headers.append("X-Content-Type-Options", "nosniff")
                        call.response.headers.append("Referrer-Policy", "no-referrer")
                        call.response.headers.append("X-Frame-Options", "DENY")
                        call.response.headers.append("Cross-Origin-Opener-Policy", "same-origin")
                        call.response.headers.append("Cross-Origin-Resource-Policy", "same-origin")
                    }
                    routing {
                        authRoutes(pinAuth, readAsset = { path ->
                            context.assets.open(path).use { it.readBytes() }
                        })
                        messageRoutes(
                            session = session,
                            pinAuth = pinAuth,
                            broadcastEvent = { type, payload -> wsHub.broadcast(type, payload) },
                            nowMs = nowMs,
                        )
                        fileRoutes(
                            session = session,
                            pinAuth = pinAuth,
                            store = fileStore,
                            stats = stats,
                            broadcastEvent = { type, payload -> wsHub.broadcast(type, payload) },
                            nowMs = nowMs,
                        )
                        wsRoutes(pinAuth, session, wsHub)
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

class AndroidFileStore(private val context: android.content.Context) :
    com.example.flikky.server.routes.FileStore {
    private val pending = java.util.concurrent.ConcurrentHashMap<String, com.example.flikky.server.routes.PushedFile>()

    override fun fileDir(): java.io.File =
        java.io.File(context.filesDir, "transfer").apply { mkdirs() }

    override fun registerPushFromPhone(
        fileId: String,
        name: String,
        size: Long,
        mime: String,
        input: () -> java.io.InputStream,
    ) {
        pending[fileId] = com.example.flikky.server.routes.PushedFile(name, size, mime, input())
    }

    override fun takePushedFile(fileId: String): com.example.flikky.server.routes.PushedFile? =
        pending.remove(fileId)
}
