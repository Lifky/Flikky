(function () {
    const translations = {
        'zh-CN': {
            'common.cancel': '取消',
            'common.confirm': '确定',
            'common.got_it': '我知道了',

            'login.title': 'Flikky 登录',
            // v1.19.0 起登录页只有一行说明文字（原来的 <h2>「输入 PIN 码」连同
            // login.heading 一起删了：logo + 这句话已经说清楚要做什么）。
            'login.description': '请在手机屏幕上查看 6 位 PIN 码。',
            'login.pin_group': 'PIN 码',
            'login.submit': '进入',
            'login.invalid_pin': '请输入 6 位 PIN',
            'login.locked': '尝试过多，请 30 秒后再试',
            'login.terminated': '错误次数过多，服务已终止',
            'login.pin_consumed': 'PIN 已被使用，请重启手机服务',
            'login.wrong_pin': 'PIN 错误',
            'login.network_error': '网络错误',

            'app.nav.chat': '会话',
            'app.nav.files': '文件',
            'app.nav.album': '相册',
            'app.album.title': '相册',
            'app.album.loading': '正在读取相册…',
            'app.album.empty': '暂无照片或视频',
            'app.album.refresh': '刷新',
            'app.album.retry': '重试',
            'app.album.truncated': '相册列表可能不完整，请重试',
            'app.album.loadFailed': '相册加载失败，请重试',
            'app.album.today': '今天',
            'app.album.yesterday': '昨天',
            'app.album.monthDay': '{month}月{day}日',
            'app.album.yearMonthDay': '{year}年{month}月{day}日',
            'app.album.total': '共 {count} 项',
            'app.album.select': '选择',
            'app.album.selected': '已选 {count} 项',
            'app.album.clear': '清除选择',
            'app.album.saveSelected': '下载所选',
            'app.album.viewTimeline': '时间',
            'app.album.viewBuckets': '相册簿',
            'app.album.unknownBucket': '未知相册',
            'app.album.bucketCount': '{count} 项',
            'app.files.title': '文件',
            'app.files.loading': '正在读取…',
            'app.files.empty': '这个文件夹是空的',
            'app.files.root': '内部存储',
            'app.files.breadcrumb': '路径',
            'app.files.breadcrumbMore': '展开中间层级',
            'app.files.itemCount': '{count} 项',
            'app.files.download': '下载 {name}',
            'app.files.restrictedRow': '系统限制，不可访问',
            'app.files.needPermission': '需要在手机上授权',
            'app.files.needPermissionHow': '打开手机上的 Flikky，在设置里点击「去授权」，完成后这里会自动恢复。',
            'app.files.restricted': '系统限制，不可访问',
            'app.files.restrictedWhy': 'Android 锁定了这个目录，任何应用都读不到它的内容。',
            'app.files.badPath': '路径无效，已回到上一个位置',
            'app.files.unavailable': '文件浏览暂不可用',
            'app.files.unavailableWhy': '请在手机上打开「允许浏览器浏览手机存储」。',
            'app.files.selected': '已选 {count} 项',
            'app.files.clear': '清除选择',
            'app.files.saveSelected': '保存所选',
            'app.files.truncated': '列表可能不完整，请刷新重试',
            'app.files.elsewhere': '其他目录 {count} 项',
            'app.files.refresh': '刷新',
            'app.files.search': '搜索这个文件夹',
            'app.files.searchEmpty': '没有匹配的文件',
            'app.files.sort': '排序',
            'app.files.sortName': '按名称',
            'app.files.sortTime': '按修改时间',
            'app.files.sortSize': '按大小',
            'app.files.selectAll': '全选',
            'app.files.deselect': '取消全选',
            'app.files.total': '共 {count} 项',
            'app.files.loadingCount': '正在载入… 已 {count} 项',
            'app.files.gone': '这个位置已经不存在了',
            'app.files.offline': '连接中断，显示的是最后一次读取的内容',
            'app.nav.favorites': '收藏',
            'app.nav.settings': '设置',
            'app.avatar.change': '更换头像',
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
            'app.copy': '复制',
            'app.download': '下载',
            'app.preview': '预览',
            'app.copied': '已复制',
            'app.copy_failed': '复制失败',
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
            'app.drop_hint': '松开以发送文件',
            'app.drop_folder_unsupported': '不支持发送文件夹',
            'app.send_failed': '发送失败',
            'app.processing': '处理中…',
            'app.close_preview': '关闭预览',
            'app.save_all': '保存所有文件',
            'app.save_all_each': '逐个保存 ({count})',
            'app.save_all_zip': '打包为 ZIP',

            'app.settings.collapse': '收起功能栏',
            'app.settings.title': '设置',
            'app.settings.layout': '布局',
            'app.settings.railSide': '侧边栏靠右',
            'app.settings.railSideSub': '导航栏换到右侧，适配惯用手，仅宽屏生效',
            'app.settings.paneSwap': '会话栏与功能栏对调',
            'app.settings.paneSwapSub': '只换中间两栏的位置，不影响导航栏，仅宽屏生效',
            'app.settings.resetSplit': '重置栏宽',
            'app.settings.resetSplitSub': '恢复默认 58 : 42',
            'app.settings.fromPhone': '来自手机的设置',
            'app.settings.language': '语言',
            'app.settings.languageZh': '简体中文',
            'app.settings.languageEn': 'English',
            'app.settings.theme': '主题与外观',
            'app.settings.themeSub': '主题色、深色模式、气泡圆角由手机端控制',
            'app.settings.timestamps': '会话时间戳',
            'app.settings.timestampsSubOn': '已开启 · 在手机端「设置 › 会话外观」修改',
            'app.settings.timestampsSubOff': '已关闭 · 在手机端「设置 › 会话外观」修改',
            'app.settings.about': '关于',
            'app.settings.browserClient': '浏览器端 · v{version}',
            'app.settings.browserClientNoVersion': '浏览器端',
            'app.settings.github': 'GitHub 仓库',

            'app.favorites.search': '搜索收藏',
            'app.favorites.sort': '排序',
            'app.favorites.sortName': '按名称',
            'app.favorites.sortTime': '按收藏时间',
            'app.favorites.sortSize': '按大小',
            'app.favorites.allGroups': '全部',
            'app.favorites.ungrouped': '未分组',
            'app.favorites.copy': '复制',
            'app.favorites.save': '保存',
            'app.favorites.copied': '已复制',
            'app.favorites.selected': '已选 {count} 项',
            'app.favorites.saveSelected': '保存所选',
            'app.favorites.clear': '清除选择',
            'app.favorites.empty': '暂无收藏',
            'app.favorites.loadFailed': '加载失败',
            'app.favorites.retry': '重试',
            'app.favorites.refresh': '刷新',
            'app.favorites.disabled': '收藏功能未开启',
            'app.favorites.disabledHint': '在手机端「设置 › 收藏」开启',
            'app.favorites.noMatches': '没有匹配的收藏',

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
            'login.description': 'Find the 6-digit PIN on your phone.',
            'login.pin_group': 'PIN',
            'login.submit': 'Continue',
            'login.invalid_pin': 'Enter the 6-digit PIN',
            'login.locked': 'Too many attempts. Try again in 30 seconds.',
            'login.terminated': 'Too many incorrect attempts. The service has stopped.',
            'login.pin_consumed': 'This PIN has already been used. Restart the service on your phone.',
            'login.wrong_pin': 'Incorrect PIN',
            'login.network_error': 'Network error',

            'app.nav.chat': 'Chat',
            'app.nav.files': 'Files',
            'app.nav.album': 'Album',
            'app.album.title': 'Album',
            'app.album.loading': 'Loading album…',
            'app.album.empty': 'No photos or videos',
            'app.album.refresh': 'Refresh',
            'app.album.retry': 'Retry',
            'app.album.truncated': 'The album listing may be incomplete. Try again.',
            'app.album.loadFailed': 'Couldn\'t load the album. Try again.',
            'app.album.today': 'Today',
            'app.album.yesterday': 'Yesterday',
            'app.album.monthDay': '{month}/{day}',
            'app.album.yearMonthDay': '{year}/{month}/{day}',
            'app.album.total': '{count} items',
            'app.album.select': 'Select',
            'app.album.selected': '{count} selected',
            'app.album.clear': 'Clear selection',
            'app.album.saveSelected': 'Download selected',
            'app.album.viewTimeline': 'Timeline',
            'app.album.viewBuckets': 'Albums',
            'app.album.unknownBucket': 'Unknown album',
            'app.album.bucketCount': '{count} items',
            'app.files.title': 'Files',
            'app.files.loading': 'Loading…',
            'app.files.empty': 'This folder is empty',
            'app.files.root': 'Internal storage',
            'app.files.breadcrumb': 'Path',
            'app.files.breadcrumbMore': 'Show the levels in between',
            'app.files.itemCount': '{count} items',
            'app.files.download': 'Download {name}',
            'app.files.restrictedRow': 'Restricted by the system',
            'app.files.needPermission': 'Permission needed on the phone',
            'app.files.needPermissionHow': 'Open Flikky on the phone, tap "Grant access" in Settings, and this panel will recover on its own.',
            'app.files.restricted': 'Restricted by the system',
            'app.files.restrictedWhy': 'Android locks this folder — no app can read what is inside it.',
            'app.files.badPath': 'Invalid path — returned to the previous location',
            'app.files.unavailable': 'File browsing is unavailable',
            'app.files.unavailableWhy': 'Turn on "Let the browser browse phone storage" on the phone.',
            'app.files.selected': '{count} selected',
            'app.files.clear': 'Clear selection',
            'app.files.saveSelected': 'Save selected',
            'app.files.truncated': 'The listing may be incomplete — refresh to retry',
            'app.files.elsewhere': '{count} elsewhere',
            'app.files.refresh': 'Refresh',
            'app.files.search': 'Search this folder',
            'app.files.searchEmpty': 'No matching files',
            'app.files.sort': 'Sort',
            'app.files.sortName': 'By name',
            'app.files.sortTime': 'By date modified',
            'app.files.sortSize': 'By size',
            'app.files.selectAll': 'Select all',
            'app.files.deselect': 'Deselect all',
            'app.files.total': '{count} items',
            'app.files.loadingCount': 'Loading… {count} so far',
            'app.files.gone': 'That location no longer exists',
            'app.files.offline': 'Connection lost — showing the last listing that loaded',
            'app.nav.favorites': 'Favorites',
            'app.nav.settings': 'Settings',
            'app.avatar.change': 'Change avatar',
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
            'app.copy': 'Copy',
            'app.download': 'Download',
            'app.preview': 'Preview',
            'app.copied': 'Copied',
            'app.copy_failed': 'Copy failed',
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
            'app.drop_hint': 'Release to send files',
            'app.drop_folder_unsupported': 'Folders cannot be sent',
            'app.send_failed': 'Send failed',
            'app.processing': 'Processing…',
            'app.close_preview': 'Close preview',
            'app.save_all': 'Save all files',
            'app.save_all_each': 'Save each ({count})',
            'app.save_all_zip': 'Download as ZIP',

            'app.settings.collapse': 'Collapse panel',
            'app.settings.title': 'Settings',
            'app.settings.layout': 'Layout',
            'app.settings.railSide': 'Sidebar on the right',
            'app.settings.railSideSub': 'Moves the nav rail to your dominant hand. Wide screens only.',
            'app.settings.paneSwap': 'Swap chat and panel columns',
            'app.settings.paneSwapSub': 'Swaps only the two middle columns; the nav rail is unaffected. Wide screens only.',
            'app.settings.resetSplit': 'Reset column width',
            'app.settings.resetSplitSub': 'Restore the default 58 : 42',
            'app.settings.fromPhone': 'From your phone',
            'app.settings.language': 'Language',
            'app.settings.languageZh': '简体中文',
            'app.settings.languageEn': 'English',
            'app.settings.theme': 'Theme & appearance',
            'app.settings.themeSub': 'Color, dark mode, and bubble shape are controlled from the phone',
            'app.settings.timestamps': 'Message timestamps',
            'app.settings.timestampsSubOn': 'On · change it on the phone under Settings › Chat appearance',
            'app.settings.timestampsSubOff': 'Off · change it on the phone under Settings › Chat appearance',
            'app.settings.about': 'About',
            'app.settings.browserClient': 'Browser client · v{version}',
            'app.settings.browserClientNoVersion': 'Browser client',
            'app.settings.github': 'GitHub repository',

            'app.favorites.search': 'Search favorites',
            'app.favorites.sort': 'Sort',
            'app.favorites.sortName': 'By name',
            'app.favorites.sortTime': 'By date saved',
            'app.favorites.sortSize': 'By size',
            'app.favorites.allGroups': 'All',
            'app.favorites.ungrouped': 'Ungrouped',
            'app.favorites.copy': 'Copy',
            'app.favorites.save': 'Save',
            'app.favorites.copied': 'Copied',
            'app.favorites.selected': '{count} selected',
            'app.favorites.saveSelected': 'Save selected',
            'app.favorites.clear': 'Clear selection',
            'app.favorites.empty': 'No favorites yet',
            'app.favorites.loadFailed': 'Couldn\'t load favorites',
            'app.favorites.retry': 'Retry',
            'app.favorites.refresh': 'Refresh',
            'app.favorites.disabled': 'Favorites is not enabled',
            'app.favorites.disabledHint': 'Turn it on your phone under Settings › Favorites',
            'app.favorites.noMatches': 'No favorites match',

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

    let connected = true; // The PIN page has no WebSocket; app/export take ownership on load.
    let refreshTimer = null;
    let activeRequest = null;

    function pause() {
        if (refreshTimer !== null) clearTimeout(refreshTimer);
        refreshTimer = null;
        if (activeRequest) activeRequest.controller.abort();
        activeRequest = null;
    }

    function refresh() {
        if (!connected || document.hidden) return Promise.resolve();
        if (activeRequest) return activeRequest.promise;
        if (refreshTimer !== null) clearTimeout(refreshTimer);
        refreshTimer = null;
        const request = { controller: new AbortController(), promise: null };
        activeRequest = request;
        request.promise = (async () => {
            let succeeded = false;
            try {
                const response = await fetch('/api/web-theme', {
                    cache: 'no-store', signal: request.controller.signal,
                });
                if (!response.ok) return;
                const appearance = await response.json();
                if (activeRequest !== request) return;
                setLanguage(appearance.languageTag);
                succeeded = true;
                return appearance;
            } catch (_) {
                // An unreachable phone must not leave an endless background retry loop.
            } finally {
                if (activeRequest === request) {
                    activeRequest = null;
                    if (succeeded && connected && !document.hidden) {
                        refreshTimer = setTimeout(refresh, 30000);
                    }
                }
            }
        })();
        return request.promise;
    }

    function setConnected(value) {
        connected = !!value;
        if (connected) refresh();
        else pause();
    }

    window.flikkyI18n = {
        t,
        count,
        setLanguage,
        onChange,
        refresh,
        setConnected,
        get language() { return currentLanguage; },
    };

    setLanguage('zh-CN');
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) pause();
        else refresh();
    });
    refresh();
})();
