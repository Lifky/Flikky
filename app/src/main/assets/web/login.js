(function () {
    const form = document.getElementById('pin-form');
    const pinField = document.getElementById('pin-input');
    const pinGroup = document.getElementById('pin');
    const helperEl = document.getElementById('helper');
    const btn = document.getElementById('submit-btn');
    // 6 个格子只是显示层，真正持有值的是 #pin-input。querySelectorAll 在这里
    // 取一次就够——格子是静态标签，不会增删。
    const cells = Array.prototype.slice.call(
        (document.querySelectorAll && document.querySelectorAll('.fk-pin-cell')) || [],
    );
    const i18n = window.flikkyI18n;
    const t = (key, values) => i18n.t(key, values);
    let currentError = null;

    function applyTheme(seed, dark, amoled) {
        // AMOLED 只在深色下有意义。登录页只调用一次，没有缓存键要维护。
        // 在 mdui 短路之前写：纯黑底色由 tokens.css 的 [data-amoled="1"] 提供。
        document.documentElement.setAttribute('data-amoled', (!!dark && amoled === true) ? '1' : '0');
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
            // Theme sync is best-effort and must not block PIN auth.
        }
    }

    async function fetchPublicTheme() {
        try {
            const resp = await fetch('/api/web-theme');
            if (!resp.ok) return;
            const data = await resp.json();
            applyTheme(data.themeSeed, !!data.themeDark, data.amoled === true);
        } catch (_) {
            // Keep the PIN page usable even if the theme endpoint is unavailable.
        }
    }

    fetchPublicTheme();

    // ── v1.23 请求加入 ────────────────────────────────────────────────────
    // 会话里已有设备时，新设备不能再靠 PIN（PIN 已用掉）或直接进（关 PIN），要手机点「允许」。
    const joinLib = window.flikkyJoin;
    const joinPanel = document.getElementById('join-panel');
    const joinText = document.getElementById('join-text');
    const joinBtn = document.getElementById('join-btn');
    const joinCodeEl = document.getElementById('join-code');
    const loginSub = document.getElementById('login-sub');
    let joinState = 'idle';
    let joinCode = '';
    let pollTimer = null;

    function renderJoin() {
        if (!joinLib || !joinPanel) return;
        const v = joinLib.view(joinState);
        joinText.textContent = t(v.textKey);
        joinBtn.textContent = t(v.buttonKey);
        joinBtn.disabled = v.buttonDisabled;
        if (joinCodeEl) {
            joinCodeEl.hidden = !(v.showsCode && joinCode);
            joinCodeEl.textContent = joinCode;
            joinCodeEl.setAttribute('aria-label', t('login.join.code_label', { code: joinLib.spokenCode(joinCode) }));
        }
    }

    function showJoin() {
        if (!joinPanel) return;
        form.hidden = true;
        if (loginSub) loginSub.hidden = true;
        joinPanel.hidden = false;
        renderJoin();
    }

    function setJoinState(state) {
        joinState = state;
        renderJoin();
        if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }
        if (joinLib.view(state).polling) pollTimer = setTimeout(pollJoin, joinLib.POLL_MS);
    }

    async function handleJoinResponse(resp) {
        const body = await resp.json().catch(() => ({}));
        const next = joinLib.outcome(resp.status, body);
        if (next === 'approved') {
            window.location.href = body.redirectTo || '/app';
            return;
        }
        // 409：其实不需要加入了（比如手机断开了所有设备、换了新 PIN）——回到正常的进入方式。
        if (next === 'not_needed') { window.location.href = '/'; return; }
        // 等待中的应答都带码；网络抖动时保留上一次的码，请求结束（拒绝 / 过期）就清掉。
        if (next === 'waiting') joinCode = joinLib.codeFrom(body) || joinCode;
        else if (next !== 'error') joinCode = '';
        setJoinState(next);
    }

    async function pollJoin() {
        try {
            await handleJoinResponse(await fetch('/api/join/status', { method: 'POST' }));
        } catch (_) {
            setJoinState('error');
        }
    }

    if (joinBtn) {
        joinBtn.addEventListener('click', async () => {
            joinBtn.disabled = true;
            try {
                await handleJoinResponse(await fetch('/api/join', { method: 'POST' }));
            } catch (_) {
                setJoinState('error');
            }
        });
    }

    async function checkJoinState() {
        try {
            const resp = await fetch('/api/join/state');
            if (!resp.ok) return;
            const data = await resp.json();
            if (data && data.joinRequired === true) showJoin();
        } catch (_) {
            // 拿不到就按 PIN 页处理：提交 PIN 时服务端会说明 PIN 已被使用，再切过来。
        }
    }

    checkJoinState();

    // 把当前值画到 6 个格子上，并把「下一个待输入」的格子标成活跃。
    // 输满 6 位时没有待输入格 —— 活跃态全部清掉，视觉上落在「已完成」。
    function syncCells() {
        const value = pinField.value || '';
        for (let i = 0; i < cells.length; i += 1) {
            const ch = value.charAt(i);
            cells[i].textContent = ch;
            cells[i].dataset.filled = ch ? 'true' : 'false';
            if (i === value.length && value.length < cells.length) cells[i].dataset.active = 'true';
            else delete cells[i].dataset.active;
        }
        btn.disabled = value.length !== 6;
    }

    function showError(key, values) {
        currentError = { key, values };
        helperEl.textContent = t(key, values);
        pinGroup.dataset.error = 'true';
    }
    function clearError() {
        if (!currentError) return;
        currentError = null;
        helperEl.textContent = '';
        pinGroup.dataset.error = 'false';
    }

    i18n.onChange(() => {
        if (currentError) helperEl.textContent = t(currentError.key, currentError.values);
        if (joinPanel && !joinPanel.hidden) renderJoin();
    });

    pinField.addEventListener('input', () => {
        const cleaned = (pinField.value || '').replace(/\D/g, '').slice(0, 6);
        if (cleaned !== pinField.value) pinField.value = cleaned;
        clearError();
        syncCells();
    });

    // 格子不可聚焦（它们是 <div>），点在上面必须把焦点还给那个透明 input，
    // 否则用户点了「输入框」却打不出字。
    if (pinGroup && pinGroup.addEventListener) {
        pinGroup.addEventListener('click', () => pinField.focus());
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const pin = (pinField.value || '').trim();
        if (pin.length !== 6) { showError('login.invalid_pin'); return; }
        btn.disabled = true;
        clearError();
        try {
            const resp = await fetch('/api/auth', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pin }),
            });
            const data = await resp.json().catch(() => ({}));
            if (resp.ok && data.ok) {
                // 服务端按 ServiceMode 决定跳哪里：Transfer → /app，Export → /export。
                window.location.href = data.redirectTo || '/app';
                return;
            }
            const err = data.error;
            if (err === 'locked') showError('login.locked');
            else if (err === 'terminated') showError('login.terminated');
            else if (err === 'pin_consumed') showJoin();
            else showError('login.wrong_pin');
        } catch (_) {
            showError('login.network_error');
        } finally {
            pinField.value = '';
            // 清空后必须重画：syncCells 顺带把提交键恢复成 disabled。直接改
            // btn.disabled 会和「输满 6 位才可提交」这条规则分叉。
            syncCells();
            pinField.focus();
        }
    });

    syncCells();
    setTimeout(() => pinField.focus(), 0);
})();

['dragover', 'drop'].forEach((type) => {
    document.addEventListener(type, (event) => event.preventDefault());
});
