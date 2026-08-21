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

    function applyTheme(seed, dark) {
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
            applyTheme(data.themeSeed, !!data.themeDark);
        } catch (_) {
            // Keep the PIN page usable even if the theme endpoint is unavailable.
        }
    }

    fetchPublicTheme();

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
            else if (err === 'pin_consumed') showError('login.pin_consumed');
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
