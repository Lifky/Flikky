package com.example.flikky.server

import com.example.flikky.util.IdGen
import com.example.flikky.util.UserAgentLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「请求加入」（v1.23 多设备，用户裁决）：会话里已有设备时，新设备要手机点允许才能进。
 *
 * - 请求凭证 [Created.secret] 256 位，只经 HttpOnly Cookie 交给发起请求的页面；这里只存它的 SHA-256。
 * - 手机允许后**不当场签发**会话凭据：请求方下次来查状态时 [claim]，由路由当场向 DeviceRegistry 签发 ——
 *   会话 token 明文从不在服务端停留。
 * - 一分钟没人理就过期；同时最多 [maxPending] 个，多了直接拒，免得一台设备刷请求把手机淹在弹窗里。
 *
 * 跨 Wi-Fi rebind 存活（TransferService 字段），不持有任何 KtorServer 成员（rebind 引用规范）。
 */
class JoinRequests(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val secretSupplier: () -> String = IdGen::newToken,
    private val idSupplier: () -> String = IdGen::newDeviceId,
    private val maxPending: Int = MAX_PENDING,
) {
    enum class State { PENDING, APPROVED, DENIED }

    /** 给手机界面看的一项：公开的 [id]、可读的设备标签。不含凭证。 */
    data class View(val id: String, val label: String?, val createdAt: Long)

    data class Created(val secret: String, val request: View)

    data class Claimed(val userAgent: String?)

    sealed class Status {
        object Pending : Status()
        object Approved : Status()
        object Denied : Status()
        object Expired : Status()
        object Unknown : Status()
    }

    private class Entry(val view: View, val userAgent: String?, var state: State)

    private val byHash = LinkedHashMap<String, Entry>()
    private val _pending = MutableStateFlow<List<View>>(emptyList())
    /** 待手机处理的请求，按到达先后。 */
    val pending: StateFlow<List<View>> = _pending.asStateFlow()

    @Synchronized
    fun create(userAgent: String?): Created? {
        dropExpired()
        if (byHash.values.count { it.state == State.PENDING } >= maxPending) return null
        val secret = secretSupplier()
        val view = View(idSupplier(), UserAgentLabel.from(userAgent), nowMs())
        byHash[DeviceRegistry.hashOf(secret)] = Entry(view, userAgent, State.PENDING)
        publish()
        return Created(secret, view)
    }

    @Synchronized
    fun status(secret: String?): Status {
        if (secret.isNullOrEmpty()) return Status.Unknown
        val hash = DeviceRegistry.hashOf(secret)
        val entry = byHash[hash] ?: return Status.Unknown
        if (isExpired(entry)) {
            byHash.remove(hash)
            publish()
            return Status.Expired
        }
        return when (entry.state) {
            State.PENDING -> Status.Pending
            State.APPROVED -> Status.Approved
            State.DENIED -> Status.Denied
        }
    }

    @Synchronized
    fun approve(requestId: String): Boolean = decide(requestId, State.APPROVED)

    @Synchronized
    fun deny(requestId: String): Boolean = decide(requestId, State.DENIED)

    /** 已允许的请求只能领一次：领完即删，再查就是 Unknown。 */
    @Synchronized
    fun claim(secret: String?): Claimed? {
        if (secret.isNullOrEmpty()) return null
        val hash = DeviceRegistry.hashOf(secret)
        val entry = byHash[hash] ?: return null
        if (entry.state != State.APPROVED || isExpired(entry)) return null
        byHash.remove(hash)
        publish()
        return Claimed(entry.userAgent)
    }

    @Synchronized
    fun clear() {
        if (byHash.isEmpty()) return
        byHash.clear()
        publish()
    }

    private fun decide(requestId: String, state: State): Boolean {
        dropExpired()
        val entry = byHash.values.firstOrNull { it.view.id == requestId && it.state == State.PENDING } ?: return false
        entry.state = state
        publish()
        return true
    }

    private fun isExpired(entry: Entry) = nowMs() - entry.view.createdAt > TTL_MS

    private fun dropExpired() {
        if (byHash.values.removeAll { isExpired(it) }) publish()
    }

    private fun publish() {
        _pending.value = byHash.values.filter { it.state == State.PENDING && !isExpired(it) }.map { it.view }
    }

    companion object {
        const val TTL_MS = 60_000L
        const val MAX_PENDING = 3
    }
}
