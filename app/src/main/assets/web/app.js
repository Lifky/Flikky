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
        a.textContent = `${msg.name} (${formatSize(msg.sizeBytes)})`;
        div.appendChild(a);
        list.appendChild(div);
        list.scrollTop = list.scrollHeight;
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

    function onWsEvent(ev) {
        const payloadId = ev.payload && ev.payload.id;
        const key = `${ev.type}:${payloadId}`;
        if (payloadId != null && seen.has(key)) return;
        if (ev.type === 'text_added') {
            seen.add(key);
            renderText(ev.payload, ev.payload.origin === 'BROWSER');
        } else if (ev.type === 'file_added') {
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
        for (const f of data.files) { seen.add(`file_added:${f.id}`); renderFile(f, f.origin === 'BROWSER'); }
    }

    function openWs() {
        const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(`${proto}//${location.host}/ws`);
        ws.onopen = () => { conn.textContent = '已连接'; };
        ws.onclose = () => { conn.textContent = '已断开'; setTimeout(openWs, 1500); };
        ws.onmessage = (e) => {
            try { onWsEvent(JSON.parse(e.data)); } catch (_) {}
        };
    }

    async function sendText() {
        const text = input.value.trim();
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

    async function sendFile(file) {
        const form = new FormData();
        form.append('file', file, file.name);
        await fetch('/api/files', { method: 'POST', body: form });
    }

    sendBtn.addEventListener('click', sendText);
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendText(); }
    });
    fileBtn.addEventListener('click', () => filePicker.click());
    filePicker.addEventListener('change', async () => {
        for (const f of filePicker.files) { await sendFile(f); }
        filePicker.value = '';
    });

    loadHistory().then(openWs);
})();
