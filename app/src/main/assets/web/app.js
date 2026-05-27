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
        div.dataset.messageId = msg.id;
        div.dataset.kind = 'text';
        list.appendChild(div);
        list.scrollTop = list.scrollHeight;
        if (mine) attachRecallHandler(div, msg.id);
        return div;
    }

    function renderFile(msg, mine) {
        const div = document.createElement('div');
        div.className = 'file-bubble ' + (mine ? 'me' : 'them');
        div.dataset.messageId = msg.id;
        div.dataset.kind = 'file';
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
        if (mine) attachRecallHandler(div, msg.id);
        return div;
    }

    // v1.3 B4 撤回 UI：长按自己气泡 → 弹一个简单 floating menu → 撤回。
    // 不用 mdui-menu 因为它的 anchor 模型对动态创建的 bubble 节点不友好；
    // 自己的 div 用 pointer 坐标固定定位最直接。
    function attachRecallHandler(bubble, messageId) {
        let longPressTimer = null;
        let pressX = 0, pressY = 0;
        bubble.addEventListener('pointerdown', (e) => {
            // 鼠标只接受左键；触摸 / 笔不区分。
            if (e.pointerType === 'mouse' && e.button !== 0) return;
            // 文件气泡里的 <a> 点击下载，不要被长按拦截——但移动设备上 <a>
            // 同样能触发 pointerdown，所以我们改在 pointerup 前看是否到 500ms。
            pressX = e.clientX; pressY = e.clientY;
            if (longPressTimer) clearTimeout(longPressTimer);
            longPressTimer = setTimeout(() => {
                longPressTimer = null;
                showRecallMenu(messageId, e.clientX, e.clientY);
            }, 500);
        });
        const cancel = () => {
            if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
        };
        bubble.addEventListener('pointerup', cancel);
        bubble.addEventListener('pointerleave', cancel);
        bubble.addEventListener('pointercancel', cancel);
        bubble.addEventListener('pointermove', (e) => {
            // 拖动超过 10px 视为非长按。
            if (Math.abs(e.clientX - pressX) > 10 || Math.abs(e.clientY - pressY) > 10) cancel();
        });
    }

    function showRecallMenu(messageId, x, y) {
        closeRecallMenu();
        const menu = document.createElement('div');
        menu.className = 'recall-menu';
        menu.id = 'recall-menu';
        // 避免菜单溢出屏幕右/下边缘——简单偏移即可。
        menu.style.left = Math.min(x, window.innerWidth - 120) + 'px';
        menu.style.top = Math.min(y, window.innerHeight - 60) + 'px';
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = '撤回';
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            closeRecallMenu();
            confirmRecallMessage(messageId);
        });
        menu.appendChild(btn);
        document.body.appendChild(menu);
        // 下一帧再装外部点击关闭，避免本次 pointerdown / click 立刻关掉自己。
        setTimeout(() => {
            document.addEventListener('pointerdown', dismissRecallMenu, { capture: true });
        }, 0);
    }

    function dismissRecallMenu(e) {
        const menu = document.getElementById('recall-menu');
        if (menu && !menu.contains(e.target)) closeRecallMenu();
    }

    function closeRecallMenu() {
        document.removeEventListener('pointerdown', dismissRecallMenu, { capture: true });
        const m = document.getElementById('recall-menu');
        if (m) m.remove();
    }

    /**
     * v1.3 D26 修订：撤回前必须二次确认（与手机端 AlertDialog 对齐）。
     * 用 mdui-dialog 弹一个简单的确认窗。点击"撤回"才真正调 DELETE。
     */
    function confirmRecallMessage(messageId) {
        const dialog = document.getElementById('recall-confirm-dialog');
        if (!dialog) {
            // 回退：mdui 没加载，直接走 native confirm。
            if (window.confirm('撤回这条消息？两端都会消失，不可恢复。')) {
                doRecallMessage(messageId);
            }
            return;
        }
        const okBtn = dialog.querySelector('[data-action="confirm"]');
        const cancelBtn = dialog.querySelector('[data-action="cancel"]');
        const onOk = () => { cleanup(); doRecallMessage(messageId); };
        const onCancel = () => { cleanup(); };
        function cleanup() {
            dialog.open = false;
            okBtn.removeEventListener('click', onOk);
            cancelBtn.removeEventListener('click', onCancel);
        }
        okBtn.addEventListener('click', onOk);
        cancelBtn.addEventListener('click', onCancel);
        dialog.open = true;
    }

    async function doRecallMessage(messageId) {
        try {
            const r = await fetch(`/api/messages/${messageId}`, {
                method: 'DELETE',
                headers: { 'X-Client-Id': myClientId },
            });
            if (r.ok || r.status === 404) {
                // 200 刚撤；404 已删（idempotent）。本地节点直接消失 + snackbar 提示。
                removeMessageNode(messageId);
                if (window.flikky && window.flikky.showInfo) window.flikky.showInfo('消息已撤回');
            } else if (r.status === 403) {
                if (window.flikky && window.flikky.showError) window.flikky.showError('只能撤回自己发的消息');
            } else {
                if (window.flikky && window.flikky.showError) window.flikky.showError('撤回失败');
            }
        } catch (_) {
            if (window.flikky && window.flikky.showError) window.flikky.showError('撤回失败：网络错误');
        }
    }

    // v1.3 D26 修订：撤回 = 消息节点完全消失（不留占位符）。被两条路径调用：
    //  - 本浏览器调 DELETE 成功 → 移除节点 + showInfo "消息已撤回"
    //  - 收到 message_recalled WS event（对端撤回）→ 移除节点 + showInfo "对方撤回了一条消息"
    // 调用方负责 snackbar 文案；本函数只管 DOM 清理，且幂等。
    function removeMessageNode(messageId) {
        const node = list.querySelector(`[data-message-id="${messageId}"]`);
        if (node) node.remove();
    }

    function renderTransferringBubble(msg) {
        const mine = msg.origin === 'BROWSER';
        const div = document.createElement('div');
        div.className = 'file-bubble ' + (mine ? 'me' : 'them') + ' transferring';
        div.dataset.messageId = msg.id;
        div.dataset.kind = 'file';
        if (msg.fileId) div.dataset.fileId = msg.fileId;

        const a = document.createElement('a');
        a.textContent = msg.name;
        a.setAttribute('aria-disabled', 'true');

        const size = document.createElement('span');
        size.className = 'size';
        size.textContent = formatSize(msg.sizeBytes);

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
        // v1.3 修订：上传完成后设 data-message-id 并绑长按撤回。
        // renderUploadingBubble 时没有 server-side id（还没上传），完成后 dto
        // 带了 id。没有 data-message-id 的节点 removeMessageNode 找不到它。
        if (dto.id != null) {
            bubble.dataset.messageId = dto.id;
            bubble.dataset.kind = 'file';
            attachRecallHandler(bubble, dto.id);
        }
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
        // 幂等：XHR 的 onerror / upload.onerror / upload.onabort / ontimeout 在
        // 网络异常时可能连续触发，旧版会重复添加 retry-hint 显示两行"上传失败"。
        if (bubble.classList.contains('failed')) return;
        bubble.classList.remove('uploading');
        bubble.classList.add('failed');

        const bar = bubble.querySelector('.progress-bar');
        if (bar) bar.remove();
        const pct = bubble.querySelector('.progress-pct');
        if (pct) pct.remove();

        // 失败提示：整个气泡可点击重试，不用下划线超链接。
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

    function markBubbleFailedNoRetry(bubble, text) {
        if (!bubble) return;
        bubble.classList.remove('uploading', 'transferring');
        bubble.classList.add('failed');
        const bar = bubble.querySelector('.progress-bar');
        if (bar) bar.remove();
        const pct = bubble.querySelector('.progress-pct');
        if (pct) pct.remove();
        const hint = bubble.querySelector('.retry-hint');
        if (hint) hint.remove();
        const failHint = document.createElement('span');
        failHint.className = 'retry-hint';
        failHint.textContent = text;
        bubble.appendChild(failHint);
        bubble.style.cursor = 'default';
    }

    function onWsEvent(ev) {
        // v1.3 应用层 pong：服务端 echo {"type":"pong","id":N}，无 payload 包裹。
        if (ev.type === 'pong') {
            handlePong(ev.id);
            return;
        }
        // 服务端主动停止 — 抢在 ws.onclose 之前标记，让重连流程跳过这个 WS。
        if (ev.type === 'server_stopped') {
            serverStopped = true;
            showBanner('terminated', '服务已停止，连接已断开');
            setSendEnabled(false);
            return;
        }
        const payloadId = ev.payload && ev.payload.id;
        const key = `${ev.type}:${payloadId}`;
        if (payloadId != null && seen.has(key)) return;
        // 自己发的广播跳过——已经在 XHR/fetch onload 时本地渲染过。
        if (ev.payload && ev.payload.senderId && ev.payload.senderId === myClientId) {
            seen.add(key);
            return;
        }
        if (ev.type === 'message_recalled') {
            // v1.3 D26 修订：服务端广播。本端 DELETE 成功路径已经自己 remove 节点了，
            // 这里覆盖"对端撤回"分支。removeMessageNode 幂等。
            // 文案区分：本端是"消息已撤回"（doRecallMessage 里发），对端是
            // "对方撤回了一条消息"。判断：如果该 messageId 节点还在，说明本端
            // 没主动撤过 → 是对端撤回。
            const p = ev.payload || {};
            if (typeof p.messageId === 'number') {
                const stillThere = list.querySelector(`[data-message-id="${p.messageId}"]`);
                removeMessageNode(p.messageId);
                if (stillThere && window.flikky && window.flikky.showInfo) {
                    window.flikky.showInfo('对方撤回了一条消息');
                }
            }
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
            if (ev.payload.status === 'IN_PROGRESS') {
                renderTransferringBubble(ev.payload);
            } else {
                renderFile(ev.payload, ev.payload.origin === 'BROWSER');
            }
        } else if (ev.type === 'file_progress') {
            const p = ev.payload;
            if (p && typeof p.messageId === 'number') {
                const bubble = list.querySelector(`[data-message-id="${p.messageId}"]`);
                if (bubble) updateBubbleProgress(bubble, p.bytesTransferred, p.totalBytes);
            }
        } else if (ev.type === 'file_ready') {
            const p = ev.payload;
            if (p && typeof p.messageId === 'number') {
                const bubble = list.querySelector(`[data-message-id="${p.messageId}"]`);
                if (bubble) {
                    markBubbleCompleted(bubble, p);
                }
            }
        } else if (ev.type === 'file_removed') {
            const p = ev.payload;
            if (p && typeof p.messageId === 'number') {
                const bubble = list.querySelector(`[data-message-id="${p.messageId}"]`);
                if (bubble) {
                    markBubbleFailedNoRetry(bubble, '传输失败');
                }
            }
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
        // 服务端 v1.2 起新增 `ordered`（按 timestamp 升序的混合列表）。优先用它，
        // 否则回退到 texts+files 各自顺序——但回退路径会丢失跨 kind 的时间顺序，
        // 仅做兼容。
        if (Array.isArray(data.ordered) && data.ordered.length) {
            for (const m of data.ordered) {
                if (m.kind === 'text') {
                    const key = `text_added:${m.id}`;
                    if (seen.has(key)) continue;
                    seen.add(key);
                    renderText(m, m.origin === 'BROWSER');
                } else if (m.kind === 'file') {
                    const key = `file_added:${m.id}`;
                    if (seen.has(key)) continue;
                    seen.add(key);
                    if (m.status === 'IN_PROGRESS') {
                        renderTransferringBubble(m);
                    } else {
                        const bubble = renderFile(m, m.origin === 'BROWSER');
                        if (bubble) bubble.dataset.fileId = m.fileId;
                    }
                }
            }
            return;
        }
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

    // 断网卡片：醒目位置展示连接状态。
    const banner = document.getElementById('conn-banner');
    function showBanner(kind, text) {
        if (!banner) return;
        banner.textContent = text;
        banner.dataset.kind = kind;   // 'disconnected' | 'restored'
        banner.hidden = false;
    }
    function hideBanner() {
        if (!banner) return;
        banner.hidden = true;
    }

    // WS 重入保护：openWs 在已 connecting/connected 时不再开新连接。
    // 防止 onclose → setTimeout 与外部 retry 的多个时间线让多个 WebSocket 并存
    // → 每个广播帧被多次 dispatch → 多个相同消息泡。
    let currentWs = null;
    let reconnectTimer = null;
    let hadConnected = false;
    // 服务端发了 server_stopped event 表示是用户主动停止（而非网络断开）。
    // 这种情况下浏览器不该尝试重连 —— 下次启动的服务是新会话，新 PIN，
    // 旧 URL 反正连不上。区分这两种语义是 user 在 test3 之后的核心反馈。
    let serverStopped = false;
    let activeUploads = [];
    // 重连尝试上限。万一 server_stopped event 由于 race 没及时到达浏览器，
    // 这一层兜底确保不会进入无限重连。9 秒内连不上 → 判定服务已不可达。
    let reconnectAttempts = 0;
    const MAX_RECONNECT_ATTEMPTS = 6;

    // v1.3 B5 应用层 ping/pong：替换 v1.2 的"4 秒 frame 超时被动 close"。
    // v1.2 依赖服务端 1Hz status 广播作为心跳——但那是隐式约定，业务流量频率变了
    // 就会误判。改为浏览器主动发 ping，服务端回 pong：语义明确、不依赖业务流量。
    //
    // 触发模式：空闲触发——连续 3 秒没收到任何 frame 才发 ping，省电省带宽。
    // pong 超时 2 秒；连续 2 次 pong 失败 → 强制 close → 触发重连。
    // v1.3 test2 修订：加回 frame 超时检测（2 秒）与 ping/pong 并存。
    // status broadcast 1Hz → 2 秒没收到任何 frame 就可判定链路断。
    // 用户反馈 7 秒感知太慢，要求"立即"——2 秒是 1Hz 广播 2 个周期的
    // 容忍，既避免单帧抖动误判又足够快。
    const FRAME_TIMEOUT_MS = 2000;
    const HEARTBEAT_IDLE_MS = 3000;
    const HEARTBEAT_PING_TIMEOUT_MS = 2000;
    const HEARTBEAT_MAX_FAILS = 2;
    const HEARTBEAT_TICK_MS = 1000;
    let lastFrameAt = 0;
    let heartbeatTimer = null;
    let pingSeq = 0;
    let pendingPings = new Map();   // seq -> sentAtMs
    let pingFailCount = 0;
    function resetHeartbeatCounters() {
        pingSeq = 0;
        pendingPings = new Map();
        pingFailCount = 0;
    }
    /**
     * 立即切到"已断开"状态：disable 按钮 + banner + 丢弃旧 WS + 启动重连。
     * heartbeat 超时和 ping 失败两条路径共用，确保逻辑一致。
     *
     * 关键：`currentWs = null` 让旧 WS 的 onclose 变 noop（`currentWs !== ws → return`）。
     * 新 openWs 创建的 WS 如果也失败，它的 onclose 里 `currentWs === ws` 为 true →
     * 走正常重连流程，循环直到 MAX_RECONNECT_ATTEMPTS 或连上。
     */
    function enterDisconnected() {
        wsConnected = false;
        setSendEnabled(false);
        setConn('已断开');
        if (hadConnected) showBanner('disconnected', '连接已断开，正在尝试重连…');
        stopHeartbeat();
        activeUploads.forEach(xhr => { try { xhr.abort(); } catch(_) {} });
        activeUploads = [];
        const old = currentWs;
        currentWs = null;    // 让旧 WS onclose 变 noop
        try { old?.close(); } catch (_) {}
        reconnectAttempts++;
        if (!serverStopped && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectTimer = setTimeout(() => { reconnectTimer = null; openWs(); }, 1500);
        } else if (!serverStopped) {
            showBanner('terminated', '连接已断开，服务可能已关闭');
        }
    }

    function startHeartbeat() {
        stopHeartbeat();
        resetHeartbeatCounters();
        heartbeatTimer = setInterval(() => {
            if (!currentWs || currentWs.readyState !== 1) return;
            const now = Date.now();
            // Frame 超时快速检测：status broadcast 每秒来一次，2 秒没来 = 链路断。
            // 关键：立即 disable 按钮 + 显示 banner，不等 ws.onclose。
            // WiFi 断开后 ws.close() 要等 TCP 超时才触发 onclose（几十秒），
            // 那时候用户早就失去耐心了。先改 UI 状态再关 socket。
            if (now - lastFrameAt > FRAME_TIMEOUT_MS) {
                enterDisconnected();
                return;
            }
            // 空闲足够久就主动 ping（覆盖 status broadcast 停了但链路没断的场景）
            if (now - lastFrameAt >= HEARTBEAT_IDLE_MS && pendingPings.size === 0) {
                const seq = ++pingSeq;
                pendingPings.set(seq, now);
                try {
                    currentWs.send(JSON.stringify({ type: 'ping', id: seq }));
                } catch (_) { /* 发不出 → 下面超时分支自己处理 */ }
            }
            // 检查 pending ping 超时
            for (const [seq, sentAt] of pendingPings) {
                if (now - sentAt > HEARTBEAT_PING_TIMEOUT_MS) {
                    pendingPings.delete(seq);
                    pingFailCount++;
                    if (pingFailCount >= HEARTBEAT_MAX_FAILS) {
                        enterDisconnected();
                        return;
                    }
                }
            }
        }, HEARTBEAT_TICK_MS);
    }
    function stopHeartbeat() {
        if (heartbeatTimer != null) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
    }
    function handlePong(id) {
        pendingPings.delete(id);
        pingFailCount = 0;
    }

    function openWs() {
        if (currentWs && (currentWs.readyState === 0 || currentWs.readyState === 1)) return;
        if (reconnectTimer != null) { clearTimeout(reconnectTimer); reconnectTimer = null; }
        const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(`${proto}//${location.host}/ws`);
        currentWs = ws;
        ws.onopen = () => {
            wsConnected = true;
            setConn('已连接');
            setSendEnabled(true);
            lastFrameAt = Date.now();
            reconnectAttempts = 0;   // 成功一次就清零计数
            startHeartbeat();
            // 重连后追平断开期间手机端发的消息（seen 集合 dedup 防重复渲染）。
            loadHistory().catch(() => {});
            if (hadConnected) {
                // 仅在"曾经连过又重连"时弹"已恢复"卡片，避免首次连上也弹。
                showBanner('restored', '已重新连接');
                setTimeout(() => { if (banner && banner.dataset.kind === 'restored') hideBanner(); }, 3000);
            } else {
                hideBanner();
            }
            hadConnected = true;
        };
        ws.onclose = () => {
            // enterDisconnected 把 currentWs 设为 null → 旧 WS 到这里 noop。
            // 只有「新 WS 连接失败」或「server 正常 close」才走到下面。
            if (currentWs !== ws) return;
            currentWs = null;
            wsConnected = false;
            setConn('已断开');
            setSendEnabled(false);
            stopHeartbeat();
            if (serverStopped) {
                showBanner('terminated', '服务已停止，连接已断开');
                return;
            }
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                showBanner('terminated', '连接已断开，服务可能已关闭');
                return;
            }
            reconnectAttempts++;
            if (hadConnected) {
                showBanner('disconnected', `连接已断开，正在尝试重连…（${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS}）`);
            }
            reconnectTimer = setTimeout(() => { reconnectTimer = null; openWs(); }, 1500);
        };
        ws.onerror = () => {
            // 不在 onerror 里做重试 — 让 onclose 兜底，避免双重 timer。
        };
        ws.onmessage = (e) => {
            lastFrameAt = Date.now();
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
        activeUploads.push(xhr);
        xhr.timeout = 30 * 60 * 1000;
        xhr.upload.onprogress = (e) => {
            if (e.lengthComputable) {
                updateBubbleProgress(bubble, e.loaded, e.total);
                if (e.loaded >= e.total) {
                    const pct = bubble.querySelector('.progress-pct');
                    if (pct) pct.textContent = '处理中...';
                }
            }
        };
        function removeFromActive() {
            activeUploads = activeUploads.filter(x => x !== xhr);
        }
        xhr.onload = () => {
            removeFromActive();
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
        xhr.onerror = () => { removeFromActive(); markBubbleFailed(bubble, file); };
        xhr.ontimeout = () => { removeFromActive(); markBubbleFailed(bubble, file, 'timeout'); };
        xhr.onabort = () => { removeFromActive(); markBubbleFailedNoRetry(bubble, '发送失败'); };
        xhr.upload.onerror = () => { removeFromActive(); markBubbleFailed(bubble, file); };
        xhr.upload.onabort = () => { removeFromActive(); markBubbleFailedNoRetry(bubble, '发送失败'); };
        xhr.open('POST', '/api/files');
        xhr.setRequestHeader('X-Client-Id', myClientId);
        xhr.setRequestHeader('X-File-Size', String(file.size));
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
