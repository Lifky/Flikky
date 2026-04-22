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
        // No href yet — upload still in flight, no downloadable URL.

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
        // Stamp fileId so later WS file_added events are deduped.
        bubble.dataset.fileId = dto.fileId;
        bubble.classList.remove('uploading');

        // Drop progress bits.
        const bar = bubble.querySelector('.progress-bar');
        if (bar) bar.remove();
        const pct = bubble.querySelector('.progress-pct');
        if (pct) pct.remove();

        // Promote the name anchor to a real download link.
        const a = bubble.querySelector('a');
        if (a) {
            a.href = `/api/files/${dto.fileId}`;
            a.download = dto.name;
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

        const retry = document.createElement('a');
        retry.className = 'retry-btn';
        retry.href = '#';
        retry.textContent = '上传失败，点击重试';
        retry.addEventListener('click', (e) => {
            e.preventDefault();
            bubble.remove();
            sendFile(file);
        });
        bubble.appendChild(retry);

        if (window.flikky && window.flikky.showError) {
            const suffix = status ? ` (${status})` : '';
            window.flikky.showError(`上传失败：${file.name}${suffix}`);
        }
    }

    function onWsEvent(ev) {
        const payloadId = ev.payload && ev.payload.id;
        const key = `${ev.type}:${payloadId}`;
        if (payloadId != null && seen.has(key)) return;
        if (ev.type === 'text_added') {
            seen.add(key);
            renderText(ev.payload, ev.payload.origin === 'BROWSER');
        } else if (ev.type === 'file_added') {
            const fileId = ev.payload && ev.payload.fileId;
            if (fileId && list.querySelector(`[data-file-id="${fileId}"]`)) {
                // This client already mounted the bubble (local XHR completed).
                // Marking as seen guards against any late duplicate dispatch.
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
        for (const t of data.texts) { seen.add(`text_added:${t.id}`); renderText(t, t.origin === 'BROWSER'); }
        for (const f of data.files) {
            seen.add(`file_added:${f.id}`);
            const bubble = renderFile(f, f.origin === 'BROWSER');
            if (bubble) bubble.dataset.fileId = f.fileId;
        }
    }

    function setConn(text) { conn.textContent = text; }

    function openWs() {
        const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(`${proto}//${location.host}/ws`);
        ws.onopen = () => setConn('已连接');
        ws.onclose = () => { setConn('已断开'); setTimeout(openWs, 1500); };
        ws.onmessage = (e) => {
            try { onWsEvent(JSON.parse(e.data)); } catch (_) {}
        };
    }

    async function sendText() {
        const text = (input.value || '').trim();
        if (!text) return;
        sendBtn.disabled = true;
        try {
            const r = await fetch('/api/messages', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text }),
            });
            if (r.ok) input.value = '';
        } finally {
            sendBtn.disabled = false;
            input.focus();
        }
    }

    function sendFile(file) {
        const localId = `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
        const bubble = renderUploadingBubble({ localId, name: file.name, total: file.size });
        const form = new FormData();
        form.append('file', file, file.name);

        const xhr = new XMLHttpRequest();
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
        xhr.open('POST', '/api/files');
        xhr.send(form);
    }

    sendBtn.addEventListener('click', sendText);
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendText(); }
    });
    fileBtn.addEventListener('click', () => filePicker.click());
    filePicker.addEventListener('change', () => {
        // Kick each file off in parallel — each XHR drives its own bubble,
        // so serial await would only hide the per-file progress behind head-of-line blocking.
        for (const f of filePicker.files) sendFile(f);
        filePicker.value = '';
    });

    loadHistory().then(openWs);
})();
