package com.example.flikky.server.routes

import com.example.flikky.server.dto.StorageErrorDto
import com.example.flikky.server.dto.StorageListDto
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 存储访问的结果，四态与 spec 4.4 的错误表 1:1 对应。
 *
 * 不用 `T?` 加异常：null 无法区分「路径非法」「不存在」「系统限制」，而这三者在前端要给
 * 不同的引导文案。把区分做成类型，调用方就不可能忘记处理其中一种。
 */
sealed interface StorageResult<out T> {
    data class Ok<T>(val value: T) : StorageResult<T>
    data object InvalidPath : StorageResult<Nothing>
    data object NotFound : StorageResult<Nothing>
    data object Restricted : StorageResult<Nothing>
}

/** 下载一个文件所需的全部信息。没有消费者的字段就是负担，故只有这三个。 */
data class StorageFileHandle(val file: File, val fileName: String, val mime: String)

/**
 * 共享存储的只读视图。
 *
 * 只认相对路径与自有 DTO，**不认 `Context`、不认 `Uri`** —— 红线「不把 Android Context
 * 穿透到 server/ 包」。实现在 `data/SharedStorageBrowser`，由 `di/ServiceLocator` 装配。
 */
interface StorageBrowser {
    fun list(relative: String): StorageResult<StorageListDto>
    fun open(relative: String): StorageResult<StorageFileHandle>
}

/**
 * v1.20.0 浏览器端「文件」tab 的只读接口。
 *
 * 判断顺序是本函数的核心，**不可调整**：
 *   1. 鉴权（401）—— 恒在最前。排在开关之后，未授权方就能从 404 推出开关状态。
 *   2. 主开关（404）—— 照 [favoriteRoutes] 先例：功能关闭 = 该 endpoint 视同不存在。
 *   3. 系统权限（403 + code）—— 前端据此渲染「请在手机上完成存储授权」而非报错。
 *   4. 路径与业务（400 / 403 / 404 / 200）。
 *
 * [browser] 是 `() -> StorageBrowser?` 而非 `StorageBrowser`：跨 Wi-Fi rebind 时 KtorServer
 * 整组被替换，直接持有实例会变成死引用（CLAUDE.md 的 rebind 引用规范，本项目踩过三次）。
 *
 * **刻意不提供 `?inline=1`**：存储面板首版只有下载、没有预览/lightbox（spec 5.3），
 * 所以 inline 没有任何消费者，而它会把「用什么 Content-Type 渲染」的选择权交给调用方，
 * 等于白送一个同源 XSS 面。与 [favoriteRoutes] 同一裁决。
 */
fun Route.storageRoutes(
    authGate: AuthGate,
    enabled: suspend () -> Boolean,
    hasPermission: () -> Boolean,
    browser: () -> StorageBrowser?,
) {
    suspend fun ApplicationCall.passesGate(): Boolean {
        if (!authGate.isAuthorized(request.cookies[AUTH_COOKIE])) {
            respond(HttpStatusCode.Unauthorized)
            return false
        }
        if (!enabled()) {
            respond(HttpStatusCode.NotFound)
            return false
        }
        if (!hasPermission()) {
            respond(HttpStatusCode.Forbidden, StorageErrorDto("storage_permission_required"))
            return false
        }
        return true
    }

    suspend fun ApplicationCall.respondFailure(result: StorageResult<*>) = when (result) {
        // 400 而不是 404：前端要能区分「路径非法」（退回根目录）与「不存在」（重拉当前目录）。
        StorageResult.InvalidPath -> respond(HttpStatusCode.BadRequest)
        StorageResult.Restricted ->
            respond(HttpStatusCode.Forbidden, StorageErrorDto("storage_restricted"))
        else -> respond(HttpStatusCode.NotFound)
    }

    get("/api/storage/list") {
        if (!call.passesGate()) return@get
        val b = browser() ?: run { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
        // 列举是阻塞 I/O（listFiles + 每条目 3 次 stat + 每个子目录一次 readdir 算项数）。
        // 大目录里这是几百毫秒到几秒；留在请求协程的默认调度器上会占住事件循环线程，
        // 拖慢同一时刻的其它请求（消息、文件流）。
        val listed = withContext(Dispatchers.IO) {
            b.list(call.request.queryParameters["path"].orEmpty())
        }
        when (val result = listed) {
            is StorageResult.Ok -> call.respond(result.value)
            else -> call.respondFailure(result)
        }
    }

    get("/api/storage/file") {
        if (!call.passesGate()) return@get
        val b = browser() ?: run { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
        when (val result = b.open(call.request.queryParameters["path"].orEmpty())) {
            is StorageResult.Ok -> call.respondStorageFile(result.value)
            else -> call.respondFailure(result)
        }
    }
}

/**
 * 下载一个存储文件。header 与 64KB 泵**逐行照 [favoriteRoutes] 的下载那半**，
 * 不新写一份——两份下载实现迟早会在 header 细节上分叉。
 *
 * 与那边一致：永远 attachment + octet-stream，调用方不能选择渲染类型。
 * 文件名沿用项目既有的 `ContentDisposition.withParameter` 写法（本项目三处下载路由
 * 都是这一套；只在这一处改成 RFC 5987 反而会制造不一致）。
 */
private suspend fun ApplicationCall.respondStorageFile(handle: StorageFileHandle) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment
            .withParameter(ContentDisposition.Parameters.FileName, handle.fileName)
            .toString(),
    )
    response.header(HttpHeaders.ContentLength, handle.file.length().toString())
    respondOutputStream(contentType = ContentType.Application.OctetStream, status = HttpStatusCode.OK) {
        handle.file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                write(buf, 0, n)
            }
            flush()
        }
    }
}
