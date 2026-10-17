/**
 * 本机（这台浏览器）的设备身份：名字、头像「本机自选」标记，以及服务端推送的规范化（spec §5、§7）。
 *
 * 纯逻辑、不碰 DOM：app.js 与 panel-settings.js 都从这里取。
 * localStorage 里只放非敏感的展示信息（名字、头像）；凭据在 HttpOnly Cookie 里，JS 读不到（红线）。
 * 名字规则与手机端 DeviceNamePolicy 一致：去控制字符、去首尾空白、按 UTF-16 计 20、不劈开代理对。
 */
(function (global) {
    'use strict';

    const NAME_MAX = 20;
    const KEY_NAME = 'flikky_device_name';
    const KEY_AVATAR = 'flikky_avatar_key';
    const KEY_EXPLICIT = 'flikky_avatar_explicit';
    const CLOSE_UNAUTHORIZED = 4401;
    const CLOSE_REVOKED = 4403;

    // 隐私模式下 localStorage 可能整个抛异常：身份信息丢了只是回到默认名，不能让页面坏掉。
    function safeGet(storage, key) { try { return storage.getItem(key); } catch (_) { return null; } }
    function safeSet(storage, key, value) { try { storage.setItem(key, value); } catch (_) { /* 忽略 */ } }
    function safeRemove(storage, key) { try { storage.removeItem(key); } catch (_) { /* 忽略 */ } }

    function clampName(raw) {
        let s = String(raw == null ? '' : raw).replace(/[\u0000-\u001f\u007f-\u009f]/g, '').trim();
        if (s.length > NAME_MAX) {
            let end = NAME_MAX;
            const code = s.charCodeAt(end - 1);
            if (code >= 0xd800 && code <= 0xdbff) end -= 1;
            s = s.slice(0, end).trim();
        }
        return s;
    }

    function storedName(storage) { return clampName(safeGet(storage, KEY_NAME) || ''); }
    function avatarExplicit(storage) { return safeGet(storage, KEY_EXPLICIT) === '1'; }

    function helloFrame(storage, avatarKey) {
        return {
            type: 'client_hello',
            avatarKey: avatarKey,
            avatarExplicit: avatarExplicit(storage),
            deviceName: storedName(storage),
        };
    }

    /** `device_updated` / PUT 响应 → 写回本地并返回规范化的本机记录；payload 不合法返回 null、什么都不写。 */
    function applyUpdate(storage, payload) {
        if (!payload || typeof payload.deviceId !== 'string') return null;
        const self = {
            deviceId: payload.deviceId,
            name: clampName(payload.name),
            defaultLabel: typeof payload.defaultLabel === 'string' ? payload.defaultLabel : null,
            defaultOrdinal: Number.isInteger(payload.defaultOrdinal) && payload.defaultOrdinal > 1 ? payload.defaultOrdinal : 1,
            avatarKey: typeof payload.avatarKey === 'string' ? payload.avatarKey : null,
            avatarExplicit: payload.avatarExplicit === true,
        };
        if (self.name) safeSet(storage, KEY_NAME, self.name);
        else safeRemove(storage, KEY_NAME);
        if (self.avatarKey) safeSet(storage, KEY_AVATAR, self.avatarKey);
        // 服务端是「是不是自选」的权威：它说不是（比如上报的 key 不合法被换成了默认值），本地就去掉标记。
        if (self.avatarExplicit) safeSet(storage, KEY_EXPLICIT, '1');
        else safeRemove(storage, KEY_EXPLICIT);
        return self;
    }

    /**
     * 改名对话框确认时要存的名字。先拿未截断的输入和默认名比：预填的默认名可能超过 20 字，
     * 截断后再比会把「没改直接确定」误当成一个新名字存下来。空串 = 沿用默认名（spec §5.2）。
     */
    function nameToSave(rawInput, fallback) {
        const raw = String(rawInput == null ? '' : rawInput).trim();
        if (raw === fallback) return '';
        const clamped = clampName(raw);
        return clamped === fallback ? '' : clamped;
    }

    function markAvatarExplicit(storage, avatarKey) {
        safeSet(storage, KEY_AVATAR, avatarKey);
        safeSet(storage, KEY_EXPLICIT, '1');
    }

    /** `Chrome · Windows` → `Chrome`：默认名只含浏览器名，与手机端 PeerDevice.shortLabel 一致。 */
    function shortLabel(label) {
        return typeof label === 'string' && label ? label.split(' · ')[0] : null;
    }

    function defaultName(self, browserWord) {
        const label = (self && shortLabel(self.defaultLabel)) || browserWord;
        const ordinal = self && Number.isInteger(self.defaultOrdinal) ? self.defaultOrdinal : 1;
        return ordinal > 1 ? label + ' ' + ordinal : label;
    }

    function displayName(self, browserWord) {
        return (self && self.name) || defaultName(self, browserWord);
    }

    function closeKind(code) {
        if (code === CLOSE_REVOKED) return 'revoked';
        if (code === CLOSE_UNAUTHORIZED) return 'unauthorized';
        return null;
    }

    global.flikkyDeviceSelf = {
        NAME_MAX, CLOSE_UNAUTHORIZED, CLOSE_REVOKED,
        clampName, storedName, avatarExplicit, helloFrame, applyUpdate,
        nameToSave, markAvatarExplicit, shortLabel, defaultName, displayName, closeKind,
    };
})(globalThis);
