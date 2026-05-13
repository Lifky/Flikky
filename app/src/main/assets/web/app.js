(function () {
    const list = document.getElementById('list');
    const input = document.getElementById('text-input');
    const sendBtn = document.getElementById('send-btn');
    const fileBtn = document.getElementById('file-btn');
    const filePicker = document.getElementById('file-picker');
    const conn = document.getElementById('conn');
    const uptimeEl = document.getElementById('uptime');
    const countEl = document.getElementById('count');
    const rateEl = document.getElementById('rate');

    const seen = new Set();

    // 浏览器自己的 client id 一直随生命周期。所有出站请求带上 X-Client-Id，
    // 服务端 broadcast 的 file_added / text_added payload 会回传 senderId，
    // 自己发的事件 dedup 跳过避免双气泡。
    const myClientId = (window.crypto && crypto.randomUUID)
        ? crypto.randomUUID()
        : 'cid-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);

    // WS 在线状态：影响发送按钮和上传按钮是否可点。
    let wsConnected = false;
    function setSendEnabled(enabled) {
        sendBtn.disabled = !enabled;
        fileBtn.disabled = !enabled;
    }

    function formatSize(b) {
        if (b >= 1024 * 1024) return (b / 1048576).toFixed(1) + ' MB';
        if (b >= 1024) return (b / 1024).toFixed(1) + ' KB';
        return b + ' B';
    }
    function formatRate(b) { return formatSize(b) + '/s'; }
    function formatUptime(s) {
        const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
        const pad = (n) => String(n).padStart(2, '0');
        return h > 0 ? `${pad(h)}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`;
    }

    function renderText(msg, mine) {
        const div = document.createElement('div');
        div.className = 'bubble ' + (mine ? 'me' : 'them');
        div.textContent = msg.content;
        list.appendChild(div);
        list.scrollTop = list.scrollHeight;
    }

    function renderFile(msg, mine) {
        const div = document.createElement('div');
        div.className = 'file-bubble ' + (mine ? 'me' : 'them');
        const a = document.createElement('a');
        a.href = `/api/files/${msg.fileId}`;
        a.download = msg.name;
        a.textContent = msg.name;
        const size = document.createElement('span');
        size.className = 'size';
        size.textContent = formatSize(msg.sizeBytes);
        div.appendChild(a);
        div.appendChild(size);
        list.appendChild(div);
        list.scrollTop = list.scrollHeight;
        return div;
    }

    function renderUploadingBubble(opts) {
        const div = document.createElement('div');
        div.className = 'file-bubble me uploading';
        div.dataset.localId = opts.localId;

        const a = document.createElement('a');
        a.textContent = opts.name;
        a.setAttribute('aria-disabled', 'true');

        const size = document.createElement('span');
        size.className = 'size';
        size.textContent = formatSize(opts.total);

        const bar = document.createElement('div');
        bar.className = 'progress-bar';
        const fill = document.createElement('div');
        fill.className = 'progress-fill';
        bar.appendChild(fill);

        const pct = document.createElement('span');
        pct.className = 'progress-pct';
        pct.textContent = '0%';

        div.appendChild(a);
        div.appendChild(size);
        div.appendChild(bar);
        div.appendChild(pct);

        list.appendChild(div);
        list.scrollTop = list.scrollHeight;
        return div;
    }

    function updateBubbleProgress(bubble, loaded, total) {
        if (!total || total <= 0) return;
        const ratio = Math.min(1, loaded / total);
        const fill = bubble.querySelector('.progress-fill');
        const pct = bubble.querySelector('.progress-pct');
        if (fill) fill.style.width = (ratio * 100).toFixed(1) + '%';
        if (pct) pct.textContent = Math.floor(ratio * 100) + '%';
    }

    function markBubbleCompleted(bubble, dto) {
        bubble.dataset.fileId = dto.fileId;
        bubble.classList.remove('uploading');

        const bar = bubble.querySelector('.progress-bar');
        if (bar) bar.remove();
        const pct = bubble.querySelector('.progress-pct');
        if (pct) pct.remove();

        const a = bubble.querySelector('a');
        if (a) {
            a.href = `/api/files/${dto.fileId}`;
            a.download = dto.name;
            a.removeAttribute('aria-disabled');
            a.textContent = dto.name;
        }
        const size = bubble.querySelector('.size');
        if (size) size.textContent = formatSize(dto.sizeBytes);
    }

    function markBubbleFailed(bubble, file, status) {
        bubble.classList.remove('uploading');
        bubble.classList.add('failed');

        const bar = bubble.querySelector('.progress-bar');
        if (bar) bar.remove();
        const pct = bubble.querySelector('.progress-pct');
        if (pct) pct.remove();

        // 失败提示去掉下划线超链接形式，改为整个气泡可点击重试。
        const hint = document.createElement('span');
        hint.className = 'retry-hint';
        hint.textContent = '上传失败，点击重试';
        bubble.appendChild(hint);
        bubble.style.cursor = 'pointer';
        bubble.title = '点击重试';
        bubble.addEventListener('click', function retryHandler() {
            bubble.removeEventListener('click', retryHandler);
            bubble.remove();
            sendFile(file);
        });

        if (window.flikky && window.flikky.showError) {
            const suffix = status ? ` (${status})` : '';
            window.flikky.showError(`上传失败：${file.name}${suffix}`);
        }
    }

    function onWsEvent(ev) {
        const payloadId = ev.payload && ev.payload.id;
        const key = `${ev.type}:${payloadId}`;
        if (payloadId != null && seen.has(key)) return;
        // 自己发的广播跳过——已经在 XHR/fetch onload 时本地渲染过。
        if (ev.payload && ev.payload.senderId && ev.payload.senderId === myClientId) {
            seen.add(key);
            return;
        }
        if (ev.type === 'text_added') {
            seen.add(key);
            renderText(ev.payload, ev.payload.origin === 'BROWSER');
        } else if (ev.type === 'file_added') {
            const fileId = ev.payload && ev.payload.fileId;
            if (fileId && list.querySelector(`[data-file-id="${fileId}"]`)) {
                seen.add(key);
                return;
            }
            seen.add(key);
            renderFile(ev.payload, ev.payload.origin === 'BROWSER');
        } else if (ev.type === 'status') {
            uptimeEl.textContent = formatUptime(ev.payload.uptime || 0);
            countEl.textContent = ev.payload.fileCount;
            rateEl.textContent = formatRate(ev.payload.bytesPerSecond);
        }
    }

    async function loadHistory() {
        const r = await fetch('/api/messages');
        if (!r.ok) return;
        const data = await r.json();
        for (const t of data.texts) {
            const key = `text_added:${t.id}`;
            if (seen.has(key)) continue;
            seen.add(key);
            renderText(t, t.origin === 'BROWSER');
        }
        for (const f of data.files) {
            const key = `file_added:${f.id}`;
            if (seen.has(key)) continue;
            seen.add(key);
            const bubble = renderFile(f, f.origin === 'BROWSER');
            if (bubble) bubble.dataset.fileId = f.fileId;
        }
    }

    function setConn(text) { conn.textContent = text; }

    function openWs() {
        const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(`${proto}//${location.host}/ws`);
        ws.onopen = () => {
            wsConnected = true;
            setConn('已连接');
            setSendEnabled(true);
            // 重连后追平断开期间手机端发的消息 — 通过 seen 集合 dedup 防止重复渲染。
            loadHistory().catch(() => {});
        };
        ws.onclose = () => {
            wsConnected = false;
            setConn('已断开');
            setSendEnabled(false);
            setTimeout(openWs, 1500);
        };
        ws.onmessage = (e) => {
            try { onWsEvent(JSON.parse(e.data)); } catch (_) {}
        };
    }

    async function sendText() {
        const text = (input.value || '').trim();
        if (!text) return;
        if (!wsConnected) {
            if (window.flikky && window.flikky.showError) {
                window.flikky.showError('连接已断开，请稍候');
            }
            return;
        }
        sendBtn.disabled = true;
        try {
            const r = await fetch('/api/messages', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Client-Id': myClientId },
                body: JSON.stringify({ text }),
            });
            if (r.ok) {
                try {
                    const dto = await r.json();
                    // 本地立即渲染自己的消息，避免等服务端 WS 广播来回。
                    // WS 后续到达的同 id 事件会被 seen 集合 dedup。
                    if (dto && typeof dto.id === 'number') {
                        seen.add(`text_added:${dto.id}`);
                        renderText(dto, true);
                    }
                } catch (_) { /* ignore parse errors */ }
                input.value = '';
            }
        } catch (e) {
            if (window.flikky && window.flikky.showError) {
                window.flikky.showError('发送失败');
            }
        } finally {
            sendBtn.disabled = !wsConnected;
            input.focus();
        }
    }

    function sendFile(file) {
        const localId = `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
        const bubble = renderUploadingBubble({ localId, name: file.name, total: file.size });
        const form = new FormData();
        form.append('file', file, file.name);

        const xhr = new XMLHttpRequest();
        // 大文件 + 慢 Wi-Fi 也要给足时间；30 分钟覆盖 100 MB 以上正常上传场景。
        xhr.timeout = 30 * 60 * 1000;
        xhr.upload.onprogress = (e) => {
            if (e.lengthComputable) updateBubbleProgress(bubble, e.loaded, e.total);
        };
        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    const dto = JSON.parse(xhr.responseText);
                    markBubbleCompleted(bubble, dto);
                } catch (_) {
                    markBubbleFailed(bubble, file);
                }
            } else {
                markBubbleFailed(bubble, file, xhr.status);
            }
        };
        xhr.onerror = () => markBubbleFailed(bubble, file);
        xhr.ontimeout = () => markBubbleFailed(bubble, file, 'timeout');
        // 网络断开 / WS 已 close 时 abort，立即变红失败而不是卡进度条。
        xhr.upload.onerror = () => markBubbleFailed(bubble, file);
        xhr.upload.onabort = () => markBubbleFailed(bubble, file);
        xhr.open('POST', '/api/files');
        xhr.setRequestHeader('X-Client-Id', myClientId);
        xhr.send(form);
    }

    sendBtn.addEventListener('click', sendText);
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendText(); }
    });
    fileBtn.addEventListener('click', () => filePicker.click());
    filePicker.addEventListener('change', () => {
        for (const f of filePicker.files) sendFile(f);
        filePicker.value = '';
    });

    // 初始禁用，等 WS 连上后启用。
    setSendEnabled(false);
    loadHistory().then(openWs);
})();
