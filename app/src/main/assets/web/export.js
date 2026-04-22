(function () {
    const summaryEl = document.getElementById('summary');
    const listEl = document.getElementById('session-list');
    const btn = document.getElementById('download-btn');
    const snackbar = document.getElementById('snackbar');
    const hintEl = document.getElementById('export-hint');

    function formatSize(b) {
        if (b == null || Number.isNaN(b)) return '0 B';
        if (b >= 1024 * 1024 * 1024) return (b / 1073741824).toFixed(2) + ' GB';
        if (b >= 1024 * 1024) return (b / 1048576).toFixed(1) + ' MB';
        if (b >= 1024) return (b / 1024).toFixed(1) + ' KB';
        return b + ' B';
    }

    function toast(msg) {
        if (!snackbar) return;
        snackbar.textContent = msg;
        snackbar.open = true;
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
            toast('网络错误，请检查连接');
            return;
        }
        if (resp.status === 401) {
            window.location.href = '/?next=/export';
            return;
        }
        if (resp.status === 409) {
            setSummaryText('导出会话已失效');
            disableDownloadWith('不可下载');
            toast('导出会话已失效，请在手机上重新发起');
            return;
        }
        if (!resp.ok) {
            setSummaryText(`加载失败 (${resp.status})`);
            toast(`加载失败 (${resp.status})`);
            return;
        }
        let info;
        try {
            info = await resp.json();
        } catch (_) {
            setSummaryText('响应解析失败');
            toast('响应解析失败');
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

    btn.addEventListener('click', () => {
        if (btn.disabled) return;
        disableDownloadWith('下载中…');
        triggerDownload();
        toast('下载已开始，可在浏览器下载管理器查看进度');
        if (hintEl) {
            hintEl.textContent = '下载已开始；完成后可关闭此页面。手机端服务会在传输结束后自动停止。';
        }
    });

    loadInfo();
})();
