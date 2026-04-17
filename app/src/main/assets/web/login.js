(function () {
    const inputs = document.querySelectorAll('#pin-input input');
    const err = document.getElementById('err');
    const form = document.getElementById('pin-form');
    const btn = document.getElementById('submit-btn');

    inputs.forEach((el, i) => {
        el.addEventListener('input', () => {
            el.value = el.value.replace(/\D/g, '').slice(0, 1);
            if (el.value && i < inputs.length - 1) inputs[i + 1].focus();
        });
        el.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && !el.value && i > 0) inputs[i - 1].focus();
        });
    });

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const pin = Array.from(inputs).map(i => i.value).join('');
        if (pin.length !== 6) { err.textContent = '请输入 6 位 PIN'; return; }
        btn.disabled = true; err.textContent = '';
        try {
            const resp = await fetch('/api/auth', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pin }),
            });
            const data = await resp.json();
            if (resp.ok && data.ok) {
                window.location.href = '/app';
                return;
            }
            if (data.error === 'locked') err.textContent = '尝试过多，请 30 秒后再试';
            else if (data.error === 'terminated') err.textContent = '错误次数过多，服务已终止';
            else if (data.error === 'pin_consumed') err.textContent = 'PIN 已被使用，请重启手机服务';
            else err.textContent = 'PIN 错误';
        } catch (e) {
            err.textContent = '网络错误';
        } finally {
            btn.disabled = false;
            inputs.forEach(i => i.value = '');
            inputs[0].focus();
        }
    });

    inputs[0].focus();
})();
