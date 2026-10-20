package com.example.flikky.server

import com.example.flikky.server.routes.AUTH_COOKIE
import com.example.flikky.session.PeerDevice
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import java.io.FilterOutputStream
import java.io.OutputStream

/** WebSocket 的路径：它不走统一取消，见 [installRevocationCancel]。 */
private const val WS_PATH = "/ws"

/**
 * 手机断开设备即时生效（v1.24，D82）。每个请求只在开始时鉴权一次；没有这道挂钩，断开之后它正在进行的
 * 上传、文件 / ZIP / 列表流会照常跑完。这里把带有效设备凭据的请求登记到 [watch]，设备被撤销时取消本次调用的协程：
 * 上传走路由自己的失败清理，下载流的连接被掐断。
 *
 * 阻塞写的响应（`respondOutputStream`）不响应协程取消 —— 实测取消之后照样写到底 —— 所以 `server/` 里一律改用
 * [respondRevocableOutputStream]，守卫见 `RevocationCancelTest`。
 *
 * `/ws` 不走这里：`WsHub.closeDevice` 要先推 `device_revoked` 再以 4403 关闭，浏览器据此显示「已被断开」；
 * 直接取消协程，那一帧就发不出去了。
 *
 * 没有凭据或凭据无效的请求原样放行，由各路由自己拒绝。
 */
internal fun Application.installRevocationCancel(
    deviceFor: (token: String?) -> PeerDevice?,
    watch: (deviceId: String, onRevoked: () -> Unit) -> AutoCloseable?,
) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path() == WS_PATH) return@intercept
        val device = deviceFor(call.request.cookies[AUTH_COOKIE]) ?: return@intercept
        val job = coroutineContext.job
        // 期间被撤销 → 不登记；路由里再查凭据时会拒绝。
        val handle = watch(device.deviceId) { job.cancel(CancellationException("device revoked")) }
            ?: return@intercept
        try {
            proceed()
        } finally {
            handle.close()
        }
    }
}

/**
 * `respondOutputStream` 的可撤销版本（D82）：每次写之前看一眼本次调用是否已被取消（设备被断开），
 * 是就抛出，引擎随即掐断连接。`server/` 里的流式响应一律用它。
 */
suspend fun ApplicationCall.respondRevocableOutputStream(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    contentLength: Long? = null,
    producer: suspend OutputStream.() -> Unit,
) {
    val job = currentCoroutineContext().job
    respondOutputStream(contentType, status, contentLength) {
        RevocableOutputStream(this, job).producer()
    }
}

private class RevocableOutputStream(out: OutputStream, private val job: Job) : FilterOutputStream(out) {
    private fun check() {
        if (!job.isActive) throw CancellationException("device revoked")
    }

    override fun write(b: Int) {
        check()
        out.write(b)
    }

    // FilterOutputStream 默认逐字节转发；直接整块写给下游。
    override fun write(b: ByteArray, off: Int, len: Int) {
        check()
        out.write(b, off, len)
    }

    override fun flush() {
        check()
        out.flush()
    }
}
