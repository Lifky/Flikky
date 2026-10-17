/**
 * 会话里的其他设备（v1.23 多设备）：哪条消息是我发的、别的是谁发的、要不要在气泡上方标名字。
 *
 * 纯逻辑、不碰 DOM；app.js 负责把它接到气泡渲染上。名册来自服务端 `peers` 事件，
 * 只有名字、头像与在线状态（凭据、UA 不在里面）。规则与手机端 ui/components/SenderLabel.kt 一致。
 */
(function (global) {
    'use strict';

    const ME = 'me';
    const PHONE = 'phone';
    const MODES = ['AUTO', 'ALWAYS', 'OFF'];

    /**
     * 气泡的发送者：ME / PHONE / 对方的 deviceId。
     * 没有发送设备的旧消息、或还不知道自己是哪台时，浏览器发的照旧算自己的（v1.23 之前的行为）。
     */
    function senderKey(origin, senderDeviceId, myDeviceId) {
        if (origin !== 'BROWSER') return PHONE;
        if (!senderDeviceId || !myDeviceId || senderDeviceId === myDeviceId) return ME;
        return String(senderDeviceId);
    }

    function normalizeMode(mode) {
        return MODES.indexOf(mode) >= 0 ? mode : 'AUTO';
    }

    /** 「自动」= 本会话出现过 2 台以上对端设备时才标名字：一对一时名字只是噪音。 */
    function namesEnabled(mode, peerCount) {
        switch (normalizeMode(mode)) {
            case 'ALWAYS': return true;
            case 'OFF': return false;
            default: return peerCount >= 2;
        }
    }

    /**
     * 每条要不要标名字：只标对方一侧；同一发送者连续几条只标第一条（群聊惯例）；
     * 时间分隔条之后重新开始一段。[breaks][i] 为真表示第 i 条前面有分隔条。
     */
    function labelPlan(senders, breaks) {
        return senders.map((sender, i) => {
            if (sender === ME) return false;
            return i === 0 || senders[i - 1] !== sender || !!(breaks && breaks[i]);
        });
    }

    function parseRoster(payload) {
        const map = new Map();
        const devices = payload && Array.isArray(payload.devices) ? payload.devices : [];
        devices.forEach((d) => {
            if (!d || typeof d.deviceId !== 'string' || !d.deviceId) return;
            map.set(d.deviceId, {
                deviceId: d.deviceId,
                name: typeof d.name === 'string' ? d.name : '',
                defaultLabel: typeof d.defaultLabel === 'string' ? d.defaultLabel : null,
                defaultOrdinal: Number.isInteger(d.defaultOrdinal) ? d.defaultOrdinal : 1,
                avatarKey: typeof d.avatarKey === 'string' ? d.avatarKey : '',
                online: d.online === true,
                present: d.present !== false,
            });
        });
        return map;
    }

    /** 与手机端 PeerDevice.displayName 同一规则：自定义名优先，否则「标签 + 编号」。 */
    function displayName(entry, fallback) {
        if (!entry) return fallback;
        if (entry.name) return entry.name;
        const label = entry.defaultLabel || fallback;
        return entry.defaultOrdinal > 1 ? label + ' ' + entry.defaultOrdinal : label;
    }

    global.flikkyPeers = { ME, PHONE, senderKey, normalizeMode, namesEnabled, labelPlan, parseRoster, displayName };
})(globalThis);
