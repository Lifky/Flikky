(function () {
    const summaryEl = document.getElementById('summary');
    const listEl = document.getElementById('session-list');
    const sessionsCard = document.getElementById('sessions-card');
    const btn = document.getElementById('download-btn');
    const hintEl = document.getElementById('export-hint');
    const bannerEl = document.getElementById('export-banner');
    const i18n = window.flikkyI18n;
    const t = (key, values) => i18n.t(key, values);
    const countText = (key, count) => i18n.count(key, count);
    let latestInfo = null;
    let summaryState = { key: 'export.loading' };
    let downloadState = { type: 'key', key: 'export.preparing' };
    let hintKey = 'export.hint';
    let bannerState = null;

    // snackbar.js may or may not be loaded (defensive — page works without it).
    const showError = (msg) => {
        if (window.flikky && window.flikky.showError) window.flikky.showError(msg);
    };
    const showInfo = (msg) => {
        if (window.flikky && window.flikky.showInfo) window.flikky.showInfo(msg);
    };

    let lastThemeKey = null;
    function applyTheme(seed, dark) {
        const key = (dark ? 'd' : 'l') + '|' + (seed || '');
        if (key === lastThemeKey) return;
        lastThemeKey = key;
        const mduiApi = window.mdui;
        if (!mduiApi) return;
        try {
            if (typeof mduiApi.setTheme === 'function') mduiApi.setTheme(dark ? 'dark' : 'light');
            if (typeof seed === 'string' && /^#[0-9a-fA-F]{6}$/.test(seed)) {
                if (typeof mduiApi.setColorScheme === 'function') mduiApi.setColorScheme(seed);
            } else if (typeof mduiApi.removeColorScheme === 'function') {
                mduiApi.removeColorScheme();
            }
        } catch (_) {
            // Theme sync is best-effort; export must remain usable if mdui changes.
        }
    }

    async function fetchExportTheme() {
        let resp;
        try {
            resp = await fetch('/api/peer-info', {
                cache: 'no-store',
                credentials: 'same-origin',
            });
        } catch (_) {
            return;
        }
        if (resp.status === 401) {
            window.location.href = '/?next=/export';
            return;
        }
        if (!resp.ok) return;
        try {
            const data = await resp.json();
            applyTheme(data.themeSeed, !!data.themeDark);
        } catch (_) {
            // Ignore malformed appearance data; the export content still matters more.
        }
    }

    function formatSize(b) {
        if (b == null || Number.isNaN(b)) return '0 B';
        if (b >= 1024 * 1024 * 1024) return (b / 1073741824).toFixed(2) + ' GB';
        if (b >= 1024 * 1024) return (b / 1048576).toFixed(1) + ' MB';
        if (b >= 1024) return (b / 1024).toFixed(1) + ' KB';
        return b + ' B';
    }

    function setSummary(key, values) {
        latestInfo = null;
        summaryState = { key, values };
        summaryEl.textContent = t(key, values);
    }

    function renderSummary(info) {
        latestInfo = info;
        summaryState = null;
        const parts = [];
        if (info.sessionCount > 0) {
            parts.push(countText('export.sessions', info.sessionCount));
            parts.push(countText('export.messages', info.messageCount));
        }
        if (info.favoriteCount > 0) parts.push(countText('export.favorites', info.favoriteCount));
        if (info.settingsIncluded) parts.push(t('export.settings'));
        parts.push(countText('export.files', (info.fileCount || 0) + (info.favoriteFileCount || 0)));
        parts.push(t('export.approx_size', { size: formatSize(info.totalBytes) }));
        summaryEl.textContent = parts.join(' · ');
    }

    function clearChildren(node) {
        while (node.firstChild) node.removeChild(node.firstChild);
    }

    function renderSessions(sessions) {
        clearChildren(listEl);
        if (!sessions || sessions.length === 0) {
            if (sessionsCard) sessionsCard.hidden = true;
            return;
        }
        if (sessionsCard) sessionsCard.hidden = false;
        for (const s of sessions) {
            const item = document.createElement('mdui-list-item');
            // mdui-list-item supports setting headline/description via attrs.
            // Use setAttribute (which internally uses string assignment) rather
            // than innerHTML-based rendering; attribute values are not parsed as HTML.
            item.setAttribute('headline', s.name || t('export.session_fallback', { id: s.id }));
            const desc = t('export.session_description', {
                messages: countText('export.messages', s.messageCount),
                files: countText('export.files', s.fileCount),
                size: formatSize(s.totalBytes),
            });
            item.setAttribute('description', desc);
            item.setAttribute('rounded', '');
            listEl.appendChild(item);
        }
    }

    function enableDownload(totalBytes) {
        downloadState = { type: 'archive', totalBytes };
        btn.disabled = false;
        btn.textContent = t('export.download_archive', { size: formatSize(totalBytes) });
    }

    function disableDownloadWith(key, values) {
        downloadState = { type: 'key', key, values };
        btn.disabled = true;
        btn.textContent = t(key, values);
    }

    async function loadInfo() {
        let resp;
        try {
            resp = await fetch('/api/export/info', { credentials: 'same-origin' });
        } catch (_) {
            setSummary('export.network_info_failed');
            showError(t('export.network_check'));
            return;
        }
        if (resp.status === 401) {
            window.location.href = '/?next=/export';
            return;
        }
        if (resp.status === 409) {
            setSummary('export.expired');
            disableDownloadWith('export.unavailable');
            showError(t('export.expired_action'));
            return;
        }
        if (!resp.ok) {
            setSummary('export.load_failed', { status: resp.status });
            showError(t('export.load_failed', { status: resp.status }));
            return;
        }
        let info;
        try {
            info = await resp.json();
        } catch (_) {
            setSummary('export.parse_failed');
            showError(t('export.parse_failed'));
            return;
        }
        renderSummary(info);
        renderSessions(info.sessions || []);
        enableDownload(info.totalBytes || 0);
    }

    function triggerDownload() {
        // Native browser download via anchor click. This lets the browser stream
        // the response straight to disk, avoiding a fetch→blob roundtrip that
        // would buffer the whole zip in RAM — important for multi-GB exports.
        const a = document.createElement('a');
        a.href = '/api/export/zip';
        a.setAttribute('download', '');
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }

    // v1.3 B9 (T26): export-page health probe.
    //
    // Why: the export browser tab has no WebSocket — it relies on a single REST
    // call (loadInfo) and then sits idle until the user clicks 下载. If the
    // phone disappears (wifi loss, service stopped, screen-off doze) between
    // info-load and click, the download click silently 404s. The probe surfaces
    // that loss before the user wastes a click.
    //
    // Design knobs:
    //  - 5s interval matches the spec (§3.4) and is gentle enough not to burn
    //    phone radio while user is just reading the summary.
    //  - 连续 2 次失败才算断开 — single transient blip (e.g. handoff between
    //    APs) shouldn't flash a scary banner.
    //  - 下载启动后探测继续跑，但不再回写按钮文字。原生 GET 流由浏览器接管，
    //    我们拿不到完成事件，按钮停在 '下载中…' 直到用户主动刷新（既有行为）。
    const HEALTH_INTERVAL_MS = 3000;
    let healthFailCount = 0;
    let healthDisconnected = false;
    let healthTimer = null;
    let downloadStarted = false;

    function showExportBanner(key, values) {
        if (!bannerEl) return;
        bannerState = { key, values };
        bannerEl.textContent = t(key, values);
        bannerEl.hidden = false;
    }

    function hideExportBanner() {
        if (!bannerEl) return;
        bannerState = null;
        bannerEl.hidden = true;
    }

    function setDownloadEnabled(enabled) {
        // 下载已经点过的话，按钮文字是 '下载中…' / disabled，浏览器在跑流，
        // 这里不应该回写它的状态——即便短暂连不上，下载本体也未必失败。
        if (downloadStarted) return;
        if (!btn) return;
        btn.disabled = !enabled;
    }

    async function checkHealth() {
        let r;
        try {
            r = await fetch('/api/export/info', {
                cache: 'no-store',
                credentials: 'same-origin',
            });
        } catch (_) {
            // 真正的网络层失败（断网、DNS、TCP reset）才算"连接断开"。
            healthFailCount++;
            if (healthFailCount >= 1 && !healthDisconnected) {
                healthDisconnected = true;
                showExportBanner('export.disconnected');
                setDownloadEnabled(false);
                showError(t('export.disconnected_short'));
            }
            return;
        }
        // 401 / 409 是服务端"正常"应答（认证过期 / 导出失效），不是网络问题。
        // 这两种状态由 loadInfo 的初次调用负责处理，探测层退出即可。
        if (r.status === 401 || r.status === 409) {
            stopHealthProbe();
            return;
        }
        if (!r.ok) {
            healthFailCount++;
            if (healthFailCount >= 1 && !healthDisconnected) {
                healthDisconnected = true;
                showExportBanner('export.disconnected');
                setDownloadEnabled(false);
                showError(t('export.disconnected_short'));
            }
            return;
        }
        // 成功路径：若之前是断开态，触发恢复 UX；否则只清零计数器。
        if (healthDisconnected) {
            healthDisconnected = false;
            healthFailCount = 0;
            hideExportBanner();
            setDownloadEnabled(true);
            showInfo(t('export.connection_restored'));
        } else {
            healthFailCount = 0;
        }
    }

    function startHealthProbe() {
        if (healthTimer != null) return;
        healthTimer = setInterval(checkHealth, HEALTH_INTERVAL_MS);
    }

    function stopHealthProbe() {
        if (healthTimer != null) {
            clearInterval(healthTimer);
            healthTimer = null;
        }
    }

    btn.addEventListener('click', () => {
        if (btn.disabled) return;
        downloadStarted = true;
        disableDownloadWith('export.downloading');
        triggerDownload();
        showInfo(t('export.download_started'));
        if (hintEl) {
            hintKey = 'export.download_started_hint';
            hintEl.textContent = t(hintKey);
        }
        // 下载开始后关闭链路检测——zip 走独立 HTTP GET 流，WS 断不断不影响它。
        // server 传完 zip 后会 stopSelf()，WS 自然关掉；如果我们还在检测，
        // WS onclose 会触发「连接已断开 + 重连循环」—— 那是正常的服务结束不是网络断。
        stopHealthProbe();
        stopExportWsPing();
        if (exportWs) { try { exportWs.close(); } catch (_) {} exportWs = null; }
    });

    // v1.3 test2 修订：export 页也连 WS，让断网感知从 3 秒 fetch 探测
    // 变为 WS onclose 立即响应（~2 秒，frame 超时与 /app 页一致）。
    // WS 不做业务通信（不监听 text_added 等），仅用于链路存活检测。
    // Export WS：纯链路检测用。export mode 的 server 不推 status broadcast
    // （statusBroadcastJob 只在 Transfer mode 启动），所以不能用 frame 超时。
    // 改用主动 ping/pong：每 3 秒发 ping，pong 超时 2 秒 → 判定断开。
    let exportWs = null;
    let exportWsPingTimer = null;
    let exportWsPendingPing = false;
    let exportWsPingTimeout = null;
    let exportServerStopped = false;
    const EXPORT_WS_PING_INTERVAL = 3000;
    const EXPORT_WS_PONG_TIMEOUT = 2000;

    function showCancelDialog() {
        const dialog = document.getElementById('export-cancel-dialog');
        if (!dialog) return;
        const okBtn = dialog.querySelector('[data-action="ok"]');
        if (okBtn) {
            const handler = () => { dialog.open = false; okBtn.removeEventListener('click', handler); };
            okBtn.addEventListener('click', handler);
        }
        dialog.open = true;
    }

    function markExportDisconnected() {
        if (healthDisconnected) return;
        healthDisconnected = true;
        healthFailCount = 99;
        showExportBanner('export.disconnected');
        setDownloadEnabled(false);
        showError(t('export.disconnected_short'));
    }

    function stopExportWsPing() {
        if (exportWsPingTimer) { clearInterval(exportWsPingTimer); exportWsPingTimer = null; }
        if (exportWsPingTimeout) { clearTimeout(exportWsPingTimeout); exportWsPingTimeout = null; }
        exportWsPendingPing = false;
    }

    function enterExportDisconnected() {
        stopExportWsPing();
        markExportDisconnected();
        const old = exportWs;
        exportWs = null;
        try { old?.close(); } catch (_) {}
        if (!exportServerStopped && !downloadStarted) {
            setTimeout(openExportWs, 3000);
        }
    }

    function openExportWs() {
        if (exportWs && (exportWs.readyState === 0 || exportWs.readyState === 1)) return;
        const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(`${proto}//${location.host}/ws`);
        exportWs = ws;
        ws.onopen = () => {
            // 启动 ping 定时器
            exportWsPingTimer = setInterval(() => {
                if (ws.readyState !== 1) return;
                exportWsPendingPing = true;
                try { ws.send(JSON.stringify({ type: 'ping', id: Date.now() })); } catch (_) {}
                exportWsPingTimeout = setTimeout(() => {
                    if (exportWsPendingPing) enterExportDisconnected();
                }, EXPORT_WS_PONG_TIMEOUT);
            }, EXPORT_WS_PING_INTERVAL);
            if (healthDisconnected) {
                healthDisconnected = false;
                healthFailCount = 0;
                hideExportBanner();
                setDownloadEnabled(true);
                showInfo(t('export.connection_restored'));
            }
        };
        ws.onmessage = (e) => {
            exportWsPendingPing = false;
            if (exportWsPingTimeout) { clearTimeout(exportWsPingTimeout); exportWsPingTimeout = null; }
            // server_stopped = 用户在手机端取消导出 / zip 传完后 server stopSelf。
            // 这是正常结束不是网络断，不应重连。
            try {
                const msg = JSON.parse(e.data);
                if (msg && msg.type === 'server_stopped') {
                    exportServerStopped = true;
                    stopExportWsPing();
                    stopHealthProbe();
                    exportWs = null;
                    try { ws.close(); } catch (_) {}
                    disableDownloadWith('export.cancelled');
                    hideExportBanner();
                    showCancelDialog();
                    return;
                }
            } catch (_) {}
        };
        ws.onclose = () => {
            if (exportWs !== ws) return;
            exportWs = null;
            stopExportWsPing();
            if (exportServerStopped || downloadStarted) return;
            markExportDisconnected();
            setTimeout(openExportWs, 3000);
        };
        ws.onerror = () => {};
    }

    window.addEventListener('beforeunload', () => {
        stopHealthProbe();
        if (exportWs) try { exportWs.close(); } catch (_) {}
    });

    i18n.onChange(() => {
        if (summaryState) {
            summaryEl.textContent = t(summaryState.key, summaryState.values);
        } else if (latestInfo) {
            renderSummary(latestInfo);
            renderSessions(latestInfo.sessions || []);
        }
        if (downloadState.type === 'archive') {
            btn.textContent = t('export.download_archive', {
                size: formatSize(downloadState.totalBytes),
            });
        } else {
            btn.textContent = t(downloadState.key, downloadState.values);
        }
        if (hintEl) hintEl.textContent = t(hintKey);
        if (bannerState && bannerEl) {
            bannerEl.textContent = t(bannerState.key, bannerState.values);
        }
    });

    fetchExportTheme();
    loadInfo();
    startHealthProbe();
    openExportWs();
})();
