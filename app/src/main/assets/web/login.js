(function () {
    const form = document.getElementById('pin-form');
    const pinField = document.getElementById('pin-input');
    const btn = document.getElementById('submit-btn');

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

    function showError(msg) {
        pinField.error = true;
        pinField.helper = msg;
    }
    function clearError() {
        pinField.error = false;
        pinField.helper = '';
    }

    pinField.addEventListener('input', () => {
        const cleaned = (pinField.value || '').replace(/\D/g, '').slice(0, 6);
        if (cleaned !== pinField.value) pinField.value = cleaned;
        if (pinField.error) clearError();
    });

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const pin = (pinField.value || '').trim();
        if (pin.length !== 6) { showError('请输入 6 位 PIN'); return; }
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
            if (err === 'locked') showError('尝试过多，请 30 秒后再试');
            else if (err === 'terminated') showError('错误次数过多，服务已终止');
            else if (err === 'pin_consumed') showError('PIN 已被使用，请重启手机服务');
            else showError('PIN 错误');
        } catch (_) {
            showError('网络错误');
        } finally {
            btn.disabled = false;
            pinField.value = '';
            pinField.focus();
        }
    });

    setTimeout(() => pinField.focus(), 0);
})();
