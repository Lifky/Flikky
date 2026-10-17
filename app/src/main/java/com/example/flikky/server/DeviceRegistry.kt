package com.example.flikky.server

import com.example.flikky.session.PeerDevice
import com.example.flikky.session.shortLabel
import com.example.flikky.util.IdGen
import com.example.flikky.util.PeerAvatarPolicy
import com.example.flikky.util.UserAgentLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * 本次服务的对端设备与其凭据（spec §3、§4）。
 *
 * 只存 `SHA-256(token) → deviceId`：token 明文只出现在 [issue] 的返回值里一次，用来种 Cookie（红线）。
 * token 是高熵随机值，不需要慢哈希；按哈希查表也不泄露可利用的时序，所以不另做常量时间比较。
 *
 * 跨 Wi-Fi rebind 存活（TransferService 字段），不持有任何 KtorServer 成员（rebind 引用规范）。
 */
class DeviceRegistry(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val tokenSupplier: () -> String = IdGen::newToken,
    private val idSupplier: () -> String = IdGen::newDeviceId,
    /** 「浏览器默认头像」，每次签发现取（TransferService 传设置里的值）。 */
    private val defaultAvatar: () -> String = { PeerAvatarPolicy.DEFAULT_BROWSER },
    private val maxPending: Int = MAX_PENDING,
) {
    data class Issued(val token: String, val device: PeerDevice)

    private val deviceIdByHash = HashMap<String, String>()
    private val hashByDeviceId = HashMap<String, String>()
    /** 插入序 = 签发序。 */
    private val byId = LinkedHashMap<String, PeerDevice>()
    /** 已采纳过第一次 client_hello 的设备（spec §5.2：之后以服务端为准）。 */
    private val announced = HashSet<String>()

    private val _devices = MutableStateFlow<List<PeerDevice>>(emptyList())
    /** 只含 visible 设备，按 connectedAt 升序。 */
    val devices: StateFlow<List<PeerDevice>> = _devices.asStateFlow()

    /**
     * 签发一台设备。[viaPin] 为真（输对了 PIN）时签发即 visible、不占 pending 额度：
     * 若也等 WebSocket，用户输完 PIN 就关页面会留下一台「看不见也断不开」的设备，PIN 又已用掉（spec §4.3）。
     */
    @Synchronized
    fun issue(userAgent: String?, viaPin: Boolean, token: String = tokenSupplier()): Issued {
        if (!viaPin) evictPendingOverflow()
        val id = idSupplier()
        var device = PeerDevice(
            deviceId = id,
            defaultLabel = UserAgentLabel.from(userAgent),
            avatarKey = defaultAvatar(),
            connectedAt = nowMs(),
        )
        if (viaPin) device = makeVisible(device)
        val hash = hashOf(token)
        deviceIdByHash[hash] = id
        hashByDeviceId[id] = hash
        byId[id] = device
        publish()
        return Issued(token, device)
    }

    @Synchronized
    fun resolve(token: String?): PeerDevice? = token?.let { deviceIdByHash[hashOf(it)] }?.let { byId[it] }

    @Synchronized
    fun device(deviceId: String): PeerDevice? = byId[deviceId]

    /** 一条 WebSocket 接入；关 PIN 签发的设备在这里第一次变为 visible。 */
    @Synchronized
    fun attach(deviceId: String): PeerDevice? {
        val current = byId[deviceId] ?: return null
        val base = if (current.visible) current else makeVisible(current)
        return store(base.copy(connections = base.connections + 1))
    }

    @Synchronized
    fun detach(deviceId: String): PeerDevice? {
        val current = byId[deviceId] ?: return null
        return store(current.copy(connections = (current.connections - 1).coerceAtLeast(0)))
    }

    @Synchronized
    fun isAnnounced(deviceId: String): Boolean = deviceId in announced

    /** 采纳第一次连接上报；之后的上报原样返回当前记录，不改任何字段。 */
    @Synchronized
    fun applyHello(deviceId: String, name: String?, avatarKey: String, avatarExplicit: Boolean): PeerDevice? {
        val current = byId[deviceId] ?: return null
        if (!announced.add(deviceId)) return current
        return store(
            current.copy(
                name = name ?: current.name,
                avatarKey = avatarKey,
                avatarExplicit = avatarExplicit,
            ),
        )
    }

    /** 改名 / 换头像（手机设备面板或浏览器 `PUT /api/devices/self`）。null 参数表示不改该字段。 */
    @Synchronized
    fun update(
        deviceId: String,
        name: String? = null,
        avatarKey: String? = null,
        avatarExplicit: Boolean? = null,
    ): PeerDevice? {
        val current = byId[deviceId] ?: return null
        return store(
            current.copy(
                name = name ?: current.name,
                avatarKey = avatarKey ?: current.avatarKey,
                avatarExplicit = avatarExplicit ?: current.avatarExplicit,
            ),
        )
    }

    /** 手机改了「浏览器默认头像」：没自选过头像的设备跟着变。返回变了的 visible 设备（只推给它们）。 */
    @Synchronized
    fun applyDefaultAvatar(key: String): List<PeerDevice> {
        val changed = byId.values
            .filter { !it.avatarExplicit && it.avatarKey != key }
            .map { it.copy(avatarKey = key) }
        if (changed.isEmpty()) return emptyList()
        changed.forEach { byId[it.deviceId] = it }
        publish()
        return changed.filter { it.visible }
    }

    @Synchronized
    fun revoke(deviceId: String): Boolean {
        if (!removeQuietly(deviceId)) return false
        publish()
        return true
    }

    @Synchronized
    fun revokeAll() {
        if (byId.isEmpty()) return
        byId.clear()
        deviceIdByHash.clear()
        hashByDeviceId.clear()
        announced.clear()
        publish()
    }

    /** 会话里有没有「真的进来了」的设备：有就要手机允许新设备加入（JoinRequests）。pending 不算。 */
    @Synchronized
    fun hasVisibleDevices(): Boolean = byId.values.any { it.visible }

    /** 还有没有任何设备持有凭据（含 pending）。PIN 换发的判据，见 PinAuth.needsNextPin。 */
    @Synchronized
    fun hasCredentials(): Boolean = byId.isNotEmpty()

    private fun makeVisible(device: PeerDevice): PeerDevice {
        // 取同名里最大编号 + 1，而不是计数：撤销过中间那台时计数会撞号。按默认名（只含浏览器）比，
        // 不按完整标签 —— 否则 Windows 与 Mac 上的 Edge 都叫「Edge」。
        val maxOrdinal = byId.values
            .filter { it.visible && it.deviceId != device.deviceId && it.shortLabel() == device.shortLabel() }
            .maxOfOrNull { it.defaultOrdinal } ?: 0
        return device.copy(visible = true, defaultOrdinal = maxOrdinal + 1)
    }

    private fun evictPendingOverflow() {
        val pending = byId.values.filter { !it.visible }
        val excess = pending.size - maxPending + 1
        if (excess <= 0) return
        pending.take(excess).forEach { removeQuietly(it.deviceId) }
    }

    private fun removeQuietly(deviceId: String): Boolean {
        byId.remove(deviceId) ?: return false
        hashByDeviceId.remove(deviceId)?.let { deviceIdByHash.remove(it) }
        announced.remove(deviceId)
        return true
    }

    private fun store(device: PeerDevice): PeerDevice {
        byId[device.deviceId] = device
        publish()
        return device
    }

    private fun publish() {
        _devices.value = byId.values.filter { it.visible }.sortedBy { it.connectedAt }
    }

    companion object {
        /** 关 PIN 时从未连上 WebSocket 的签发记录上限（spec §4.3）。 */
        const val MAX_PENDING = 16

        fun hashOf(token: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
