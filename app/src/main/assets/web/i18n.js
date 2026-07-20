(function () {
    const translations = {
        'zh-CN': {
            'common.cancel': '取消',
            'common.confirm': '确定',
            'common.got_it': '我知道了',

            'login.title': 'Flikky 登录',
            'login.heading': '输入 PIN 码',
            'login.description': '请在手机屏幕上查看 6 位 PIN 码。',
            'login.submit': '进入',
            'login.invalid_pin': '请输入 6 位 PIN',
            'login.locked': '尝试过多，请 30 秒后再试',
            'login.terminated': '错误次数过多，服务已终止',
            'login.pin_consumed': 'PIN 已被使用，请重启手机服务',
            'login.wrong_pin': 'PIN 错误',
            'login.network_error': '网络错误',

            'app.peer_info': '对方信息',
            'app.peer_avatar': '对方头像',
            'app.connecting': '连接中…',
            'app.files': { one: '{count} 个文件', other: '{count} 个文件' },
            'app.my_avatar': '我的头像，点击更换',
            'app.choose_avatar': '选择头像',
            'app.attach': '附件',
            'app.message': '消息',
            'app.message_placeholder': '输入消息',
            'app.send': '发送',
            'app.recall_title': '撤回这条消息？',
            'app.recall_body': '撤回后两端都会消失，不可恢复。',
            'app.recall': '撤回',
            'app.peer_from': '来自 {device}',
            'app.phone': '手机',
            'app.connected': '已连接',
            'app.disconnected': '已断开',
            'app.watermark': '{status} · {device}',
            'app.filled_icons': '填充图标',
            'app.avatar_character': '头像字符',
            'app.message_recalled': '消息已撤回',
            'app.recall_not_enabled': '消息撤回未开启',
            'app.recall_own_only': '只能撤回自己发的消息',
            'app.recall_failed': '撤回失败',
            'app.recall_network_failed': '撤回失败：网络错误',
            'app.peer_recalled': '对方撤回了一条消息',
            'app.upload_failed_retry': '上传失败，点击重试',
            'app.retry': '点击重试',
            'app.upload_failed_file': '上传失败：{file}{suffix}',
            'app.transfer_failed': '传输失败',
            'app.service_stopped': '服务已停止，连接已断开',
            'app.reconnecting': '连接已断开，正在尝试重连…',
            'app.service_maybe_closed': '连接已断开，服务可能已关闭',
            'app.reconnected': '已重新连接',
            'app.reconnecting_attempt': '连接已断开，正在尝试重连…（{attempt}/{max}）',
            'app.wait_reconnect': '连接已断开，请稍候',
            'app.send_failed': '发送失败',
            'app.processing': '处理中…',

            'export.title': 'Flikky 导出',
            'export.pending': '即将下载',
            'export.loading': '读取中…',
            'export.session_list': '会话清单',
            'export.preparing': '准备中…',
            'export.hint': '提示：下载由浏览器处理，开始后可保持此页面开启直至保存完成。',
            'export.cancelled_title': '导出已取消',
            'export.cancelled_body': '手机端已取消本次导出，如需重新导出请回到手机端操作。',
            'export.sessions': { one: '{count} 个会话', other: '{count} 个会话' },
            'export.messages': { one: '{count} 条消息', other: '{count} 条消息' },
            'export.favorites': { one: '{count} 条收藏', other: '{count} 条收藏' },
            'export.files': { one: '{count} 个文件', other: '{count} 个文件' },
            'export.settings': '设置',
            'export.approx_size': '约 {size}',
            'export.session_fallback': '会话 #{id}',
            'export.session_description': '{messages} · {files} · {size}',
            'export.download_archive': '下载归档 ({size})',
            'export.network_info_failed': '网络错误，无法读取导出信息',
            'export.network_check': '网络错误，请检查连接',
            'export.expired': '导出会话已失效',
            'export.unavailable': '不可下载',
            'export.expired_action': '导出会话已失效，请在手机上重新发起',
            'export.load_failed': '加载失败 ({status})',
            'export.parse_failed': '响应解析失败',
            'export.disconnected': '与手机连接已断开，请检查网络',
            'export.disconnected_short': '与手机的连接已断开',
            'export.connection_restored': '连接已恢复',
            'export.downloading': '下载中…',
            'export.download_started': '下载已开始，可在浏览器下载管理器查看进度',
            'export.download_started_hint': '下载已开始；完成后可关闭此页面。手机端服务会在传输结束后自动停止。',
            'export.cancelled': '已取消',
        },
        en: {
            'common.cancel': 'Cancel',
            'common.confirm': 'Confirm',
            'common.got_it': 'Got it',

            'login.title': 'Flikky sign in',
            'login.heading': 'Enter PIN',
            'login.description': 'Find the 6-digit PIN on your phone.',
            'login.submit': 'Continue',
            'login.invalid_pin': 'Enter the 6-digit PIN',
            'login.locked': 'Too many attempts. Try again in 30 seconds.',
            'login.terminated': 'Too many incorrect attempts. The service has stopped.',
            'login.pin_consumed': 'This PIN has already been used. Restart the service on your phone.',
            'login.wrong_pin': 'Incorrect PIN',
            'login.network_error': 'Network error',

            'app.peer_info': 'Peer information',
            'app.peer_avatar': 'Peer avatar',
            'app.connecting': 'Connecting…',
            'app.files': { one: '{count} file', other: '{count} files' },
            'app.my_avatar': 'My avatar, click to change',
            'app.choose_avatar': 'Choose avatar',
            'app.attach': 'Attach',
            'app.message': 'Message',
            'app.message_placeholder': 'Enter a message',
            'app.send': 'Send',
            'app.recall_title': 'Recall this message?',
            'app.recall_body': 'It will disappear on both devices and can\'t be recovered.',
            'app.recall': 'Recall',
            'app.peer_from': 'From {device}',
            'app.phone': 'Phone',
            'app.connected': 'Connected',
            'app.disconnected': 'Disconnected',
            'app.watermark': '{status} · {device}',
            'app.filled_icons': 'Filled icons',
            'app.avatar_character': 'Avatar character',
            'app.message_recalled': 'Message recalled',
            'app.recall_not_enabled': 'Message recall is disabled',
            'app.recall_own_only': 'You can only recall messages you sent',
            'app.recall_failed': 'Couldn\'t recall message',
            'app.recall_network_failed': 'Couldn\'t recall message: network error',
            'app.peer_recalled': 'The other device recalled a message',
            'app.upload_failed_retry': 'Upload failed. Click to retry.',
            'app.retry': 'Click to retry',
            'app.upload_failed_file': 'Upload failed: {file}{suffix}',
            'app.transfer_failed': 'Transfer failed',
            'app.service_stopped': 'The service stopped and the connection closed',
            'app.reconnecting': 'Connection lost. Reconnecting…',
            'app.service_maybe_closed': 'Connection lost. The service may have stopped.',
            'app.reconnected': 'Reconnected',
            'app.reconnecting_attempt': 'Connection lost. Reconnecting… ({attempt}/{max})',
            'app.wait_reconnect': 'Connection lost. Please wait.',
            'app.send_failed': 'Send failed',
            'app.processing': 'Processing…',

            'export.title': 'Flikky export',
            'export.pending': 'Ready to download',
            'export.loading': 'Loading…',
            'export.session_list': 'Sessions',
            'export.preparing': 'Preparing…',
            'export.hint': 'Your browser handles the download. Keep this page open until the file has been saved.',
            'export.cancelled_title': 'Export cancelled',
            'export.cancelled_body': 'The export was cancelled on your phone. Start a new export there if needed.',
            'export.sessions': { one: '{count} session', other: '{count} sessions' },
            'export.messages': { one: '{count} message', other: '{count} messages' },
            'export.favorites': { one: '{count} favorite', other: '{count} favorites' },
            'export.files': { one: '{count} file', other: '{count} files' },
            'export.settings': 'Settings',
            'export.approx_size': 'About {size}',
            'export.session_fallback': 'Session #{id}',
            'export.session_description': '{messages} · {files} · {size}',
            'export.download_archive': 'Download archive ({size})',
            'export.network_info_failed': 'Network error. Export information is unavailable.',
            'export.network_check': 'Network error. Check the connection.',
            'export.expired': 'This export session has expired',
            'export.unavailable': 'Unavailable',
            'export.expired_action': 'This export session has expired. Start it again on your phone.',
            'export.load_failed': 'Loading failed ({status})',
            'export.parse_failed': 'Couldn\'t read the server response',
            'export.disconnected': 'Connection to the phone was lost. Check the network.',
            'export.disconnected_short': 'Connection to the phone was lost',
            'export.connection_restored': 'Connection restored',
            'export.downloading': 'Downloading…',
            'export.download_started': 'Download started. Check your browser’s download manager for progress.',
            'export.download_started_hint': 'Download started. You can close this page after it finishes. The phone service will stop automatically.',
            'export.cancelled': 'Cancelled',
        },
    };

    let currentLanguage = null;
    const listeners = new Set();

    function normalizeLanguageTag(languageTag) {
        const language = String(languageTag || '').trim().split('-')[0].toLowerCase();
        if (language === 'en') return 'en';
        if (language === 'zh') return 'zh-CN';
        return 'zh-CN';
    }

    function interpolate(template, values) {
        return String(template).replace(/\{([a-zA-Z0-9_]+)\}/g, (_, key) =>
            Object.prototype.hasOwnProperty.call(values || {}, key) ? String(values[key]) : `{${key}}`
        );
    }

    function entryFor(key) {
        return translations[currentLanguage][key] ?? translations['zh-CN'][key] ?? key;
    }

    function t(key, values) {
        const entry = entryFor(key);
        const template = typeof entry === 'object' ? entry.other : entry;
        return interpolate(template, values);
    }

    function count(key, value, values) {
        const entry = entryFor(key);
        const template = typeof entry === 'object'
            ? (value === 1 ? entry.one : entry.other)
            : entry;
        return interpolate(template, Object.assign({ count: value }, values || {}));
    }

    function applyStaticTranslations() {
        document.querySelectorAll('[data-i18n]').forEach((element) => {
            element.textContent = t(element.getAttribute('data-i18n'));
        });
        const bindings = [
            ['data-i18n-title', 'title'],
            ['data-i18n-label', 'label'],
            ['data-i18n-placeholder', 'placeholder'],
            ['data-i18n-aria-label', 'aria-label'],
            ['data-i18n-headline', 'headline'],
        ];
        bindings.forEach(([dataAttribute, targetAttribute]) => {
            document.querySelectorAll(`[${dataAttribute}]`).forEach((element) => {
                element.setAttribute(targetAttribute, t(element.getAttribute(dataAttribute)));
            });
        });
    }

    function setLanguage(languageTag) {
        const normalized = normalizeLanguageTag(languageTag);
        const changed = normalized !== currentLanguage;
        currentLanguage = normalized;
        document.documentElement.setAttribute('lang', normalized);
        if (changed) {
            applyStaticTranslations();
            listeners.forEach((listener) => listener(normalized));
        }
        return normalized;
    }

    function onChange(listener) {
        listeners.add(listener);
        listener(currentLanguage);
        return () => listeners.delete(listener);
    }

    async function refresh() {
        try {
            const response = await fetch('/api/web-theme', { cache: 'no-store' });
            if (!response.ok) return;
            const appearance = await response.json();
            setLanguage(appearance.languageTag);
        } catch (_) {
            // Keep the last known language while the phone server is temporarily unavailable.
        }
    }

    window.flikkyI18n = {
        t,
        count,
        setLanguage,
        onChange,
        refresh,
        get language() { return currentLanguage; },
    };

    setLanguage('zh-CN');
    refresh();
    setInterval(refresh, 1000);
})();
