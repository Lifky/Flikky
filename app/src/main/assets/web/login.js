(function () {
    const form = document.getElementById('pin-form');
    const pinField = document.getElementById('pin-input');
    const btn = document.getElementById('submit-btn');

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
                window.location.href = '/app';
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
