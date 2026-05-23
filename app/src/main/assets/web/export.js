(function () {
    const summaryEl = document.getElementById('summary');
    const listEl = document.getElementById('session-list');
    const btn = document.getElementById('download-btn');
    const hintEl = document.getElementById('export-hint');
    const bannerEl = document.getElementById('export-banner');

    // snackbar.js may or may not be loaded (defensive — page works without it).
    const showError = (msg) => {
        if (window.flikky && window.flikky.showError) window.flikky.showError(msg);
    };
    const showInfo = (msg) => {
        if (window.flikky && window.flikky.showInfo) window.flikky.showInfo(msg);
    };

    function formatSize(b) {
        if (b == null || Number.isNaN(b)) return '0 B';
        if (b >= 1024 * 1024 * 1024) return (b / 1073741824).toFixed(2) + ' GB';
        if (b >= 1024 * 1024) return (b / 1048576).toFixed(1) + ' MB';
        if (b >= 1024) return (b / 1024).toFixed(1) + ' KB';
        return b + ' B';
    }

    function setSummaryText(text) {
        summaryEl.textContent = text;
    }

    function renderSummary(info) {
        const parts = [
            `${info.sessionCount} 个会话`,
            `${info.messageCount} 条消息`,
            `${info.fileCount} 个文件`,
            `约 ${formatSize(info.totalBytes)}`,
        ];
        summaryEl.textContent = parts.join(' · ');
    }

    function clearChildren(node) {
        while (node.firstChild) node.removeChild(node.firstChild);
    }

    function renderSessions(sessions) {
        clearChildren(listEl);
        if (!sessions || sessions.length === 0) {
            const empty = document.createElement('mdui-list-item');
            empty.setAttribute('headline', '（无会话）');
            listEl.appendChild(empty);
            return;
        }
        for (const s of sessions) {
            const item = document.createElement('mdui-list-item');
            // mdui-list-item supports setting headline/description via attrs.
            // Use setAttribute (which internally uses string assignment) rather
            // than innerHTML-based rendering; attribute values are not parsed as HTML.
            item.setAttribute('headline', s.name || `会话 #${s.id}`);
            const desc = `${s.messageCount} 条消息 · ${s.fileCount} 个文件 · ${formatSize(s.totalBytes)}`;
            item.setAttribute('description', desc);
            item.setAttribute('rounded', '');
            listEl.appendChild(item);
        }
    }

    function enableDownload(totalBytes) {
        btn.disabled = false;
        btn.textContent = `下载全部 (${formatSize(totalBytes)})`;
    }

    function disableDownloadWith(text) {
        btn.disabled = true;
        btn.textContent = text;
    }

    async function loadInfo() {
        let resp;
        try {
            resp = await fetch('/api/export/info', { credentials: 'same-origin' });
        } catch (_) {
            setSummaryText('网络错误，无法读取导出信息');
            showError('网络错误，请检查连接');
            return;
        }
        if (resp.status === 401) {
            window.location.href = '/?next=/export';
            return;
        }
        if (resp.status === 409) {
            setSummaryText('导出会话已失效');
            disableDownloadWith('不可下载');
            showError('导出会话已失效，请在手机上重新发起');
            return;
        }
        if (!resp.ok) {
            setSummaryText(`加载失败 (${resp.status})`);
            showError(`加载失败 (${resp.status})`);
            return;
        }
        let info;
        try {
            info = await resp.json();
        } catch (_) {
            setSummaryText('响应解析失败');
            showError('响应解析失败');
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

    function showExportBanner(text) {
        if (!bannerEl) return;
        bannerEl.textContent = text; // 安全红线：禁止 innerHTML
        bannerEl.hidden = false;
    }

    function hideExportBanner() {
        if (!bannerEl) return;
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
                showExportBanner('与手机连接已断开，请检查网络');
                setDownloadEnabled(false);
                showError('与手机的连接已断开');
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
                showExportBanner('与手机连接已断开，请检查网络');
                setDownloadEnabled(false);
                showError('与手机的连接已断开');
            }
            return;
        }
        // 成功路径：若之前是断开态，触发恢复 UX；否则只清零计数器。
        if (healthDisconnected) {
            healthDisconnected = false;
            healthFailCount = 0;
            hideExportBanner();
            setDownloadEnabled(true);
            showInfo('连接已恢复');
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
        disableDownloadWith('下载中…');
        triggerDownload();
        showInfo('下载已开始，可在浏览器下载管理器查看进度');
        if (hintEl) {
            hintEl.textContent = '下载已开始；完成后可关闭此页面。手机端服务会在传输结束后自动停止。';
        }
    });

    // v1.3 test2 修订：export 页也连 WS，让断网感知从 3 秒 fetch 探测
    // 变为 WS onclose 立即响应（~2 秒，frame 超时与 /app 页一致）。
    // WS 不做业务通信（不监听 text_added 等），仅用于链路存活检测。
    let exportWs = null;
    const EXPORT_WS_FRAME_TIMEOUT = 2000;
    let exportWsLastFrame = 0;
    let exportWsHeartbeat = null;
    function openExportWs() {
        if (exportWs && (exportWs.readyState === 0 || exportWs.readyState === 1)) return;
        const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(`${proto}//${location.host}/ws`);
        exportWs = ws;
        ws.onopen = () => {
            exportWsLastFrame = Date.now();
            exportWsHeartbeat = setInterval(() => {
                if (ws.readyState === 1 && Date.now() - exportWsLastFrame > EXPORT_WS_FRAME_TIMEOUT) {
                    try { ws.close(); } catch (_) {}
                }
            }, 1000);
            if (healthDisconnected) {
                healthDisconnected = false;
                healthFailCount = 0;
                hideExportBanner();
                setDownloadEnabled(true);
                showInfo('连接已恢复');
            }
        };
        ws.onmessage = () => { exportWsLastFrame = Date.now(); };
        ws.onclose = () => {
            if (exportWsHeartbeat) { clearInterval(exportWsHeartbeat); exportWsHeartbeat = null; }
            if (!healthDisconnected) {
                healthDisconnected = true;
                healthFailCount = 99;
                showExportBanner('与手机连接已断开，请检查网络');
                setDownloadEnabled(false);
                showError('与手机的连接已断开');
            }
            // 3 秒后尝试重连（复用 health probe 的恢复路径）
            setTimeout(openExportWs, 3000);
        };
        ws.onerror = () => {};
    }

    window.addEventListener('beforeunload', () => {
        stopHealthProbe();
        if (exportWs) try { exportWs.close(); } catch (_) {}
    });

    loadInfo();
    startHealthProbe();
    openExportWs();
})();
