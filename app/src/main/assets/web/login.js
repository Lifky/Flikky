(function () {
    const form = document.getElementById('pin-form');
    const pinField = document.getElementById('pin-input');
    const btn = document.getElementById('submit-btn');
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

    function showError(key, values) {
        currentError = { key, values };
        pinField.error = true;
        pinField.helper = t(key, values);
    }
    function clearError() {
        currentError = null;
        pinField.error = false;
        pinField.helper = '';
    }

    i18n.onChange(() => {
        if (currentError) pinField.helper = t(currentError.key, currentError.values);
    });

    pinField.addEventListener('input', () => {
        const cleaned = (pinField.value || '').replace(/\D/g, '').slice(0, 6);
        if (cleaned !== pinField.value) pinField.value = cleaned;
        if (pinField.error) clearError();
    });

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
            btn.disabled = false;
            pinField.value = '';
            pinField.focus();
        }
    });

    setTimeout(() => pinField.focus(), 0);
})();
