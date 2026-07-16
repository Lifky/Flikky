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
    const i18n = window.flikkyI18n;
    const t = (key, values) => i18n.t(key, values);
    const countText = (key, count) => i18n.count(key, count);
    let lastStatus = null;

    const ua = navigator.userAgent || '';
    if (/Android|iPhone|iPad|iPod/i.test(ua)) {
        document.body.classList.add('mobile-ua');
    }

    // Material Symbols are self-hosted; FILL uses the official variable-font axis.
    function symbolName(name) {
        return name === 'person' ? 'account_circle' : name;
    }

    function materialSymbolEl(name, filled, slot) {
        const icon = document.createElement('span');
        icon.className = 'material-symbols-outlined';
        if (slot) icon.setAttribute('slot', slot);
        icon.style.fontVariationSettings =
            "'FILL' " + (filled ? 1 : 0) + ", 'wght' 400, 'GRAD' 0, 'opsz' 24";
        icon.textContent = symbolName(name);
        return icon;
    }


    // ── M9b: Avatar constants ─────────────────────────────────────────────
    /*
    // Order matches PRESET_AVATARS in Avatar.kt (indices 0-11).
    const AVATAR_BG = [
        '#FF7043', // 0 Person      deep orange
        '#F4B400', // 1 Star        amber
        '#E91E63', // 2 Favorite    pink
        '#43A047', // 3 Home        green
        '#1E88E5', // 4 Email       blue
        '#00ACC1', // 5 Call        cyan
        '#7E57C2', // 6 Phone       purple
        '#EF6C00', // 7 ShoppingCart orange
        '#039BE5', // 8 ThumbUp     light blue
        '#D81B60', // 9 Place       raspberry
        '#6D4C41', // 10 Notifications brown
        '#546E7A', // 11 Settings   blue-grey
    ];
    const AVATAR_EMOJI = [
        '👤', // Person
        '⭐', // Star
        '❤️', // Favorite
        '🏠', // Home
        '✉️', // Email
        '📞', // Call
        '☎️', // Phone
        '🛒', // ShoppingCart
        '👍', // ThumbUp
        '📍', // Place
        '🔔', // Notifications
        '⚙️', // Settings
    ];

    // Own avatar: loaded from localStorage, default 0.
    let myAvatarId = Math.max(0, Math.min(11, Number(localStorage.getItem('flikky_avatar') ?? 0)));
    // Peer (phone) avatar — set after peer-info fetch.
    let phoneAvatarId = 0;

    // Build an avatar circle element (does not attach to DOM).
    function makeAvatarEl(avatarId) {
        const idx = Math.max(0, Math.min(11, avatarId));
        const el = document.createElement('div');
        el.className = 'avatar-circle';
        el.style.backgroundColor = AVATAR_BG[idx];
        el.textContent = AVATAR_EMOJI[idx];
        return el;
    }

    */
    const AVATAR_DEFAULT_BROWSER = 'icon:desktop_windows';
    const AVATAR_DEFAULT_PHONE = 'icon:smartphone';
    const AVATAR_LEGACY_KEYS = [
        'icon:person',
        'icon:star',
        'icon:person',
        'icon:person',
        'icon:person',
        'icon:person',
        'icon:smartphone',
        'icon:person',
        'icon:person',
        'icon:person',
        'icon:person',
        'icon:settings',
    ];
    const AVATAR_PRESETS = [
        AVATAR_DEFAULT_BROWSER,
        AVATAR_DEFAULT_PHONE,
        'icon:person',
        'icon:star:outline',
        'icon:face',
        'icon:palette',
        'icon:image:outline',
        'icon:settings:outline',
    ];

    function legacyAvatarKey(id, fallback) {
        const idx = Number(id);
        return Number.isInteger(idx) && idx >= 0 && idx < AVATAR_LEGACY_KEYS.length
            ? AVATAR_LEGACY_KEYS[idx]
            : fallback;
    }

    function normalizeAvatarKey(raw, fallback) {
        const key = (typeof raw === 'string' ? raw.trim() : '');
        if (key.startsWith('icon:') && key.slice(5).trim()) {
            const parts = key.slice(5).split(':');
            const name = (parts[0] || '').trim();
            const style = (parts[1] || '').trim();
            if (!name) return fallback;
            if (style === 'filled' || style === 'outline') return 'icon:' + name + ':' + style;
            return 'icon:' + name;
        }
        if (key.startsWith('char:') && key.slice(5).trim()) return 'char:' + Array.from(key.slice(5).trim())[0];
        return fallback;
    }

    function defaultIconFilled(name) {
        return name === 'star' || name === 'settings';
    }

    function avatarIconSpec(normalized) {
        const parts = normalized.slice(5).split(':');
        const name = (parts[0] || '').trim();
        const style = (parts[1] || '').trim();
        const filled = style === 'filled' ? true : style === 'outline' ? false : defaultIconFilled(name);
        return { name, filled };
    }

    function clearAvatar(el) {
        while (el.firstChild) el.removeChild(el.firstChild);
        el.removeAttribute('icon');
        el.textContent = '';
    }

    function renderAvatar(el, key) {
        if (!el) return;
        const normalized = normalizeAvatarKey(key, AVATAR_DEFAULT_BROWSER);
        clearAvatar(el);
        el.classList.add('avatar-circle');
        if (normalized.startsWith('char:')) {
            el.textContent = normalized.slice(5);
            return;
        }
        const spec = avatarIconSpec(normalized);
        const icon = materialSymbolEl(spec.name, spec.filled);
        icon.classList.add('avatar-symbol');
        el.appendChild(icon);
    }

    function makeAvatarEl(avatarKey) {
        const el = document.createElement('mdui-avatar');
        renderAvatar(el, avatarKey);
        return el;
    }

    function readMyAvatarKey() {
        const stored = localStorage.getItem('flikky_avatar_key');
        if (stored) return normalizeAvatarKey(stored, AVATAR_DEFAULT_BROWSER);
        const legacy = localStorage.getItem('flikky_avatar');
        return legacy === null
            ? AVATAR_DEFAULT_BROWSER
            : legacyAvatarKey(legacy, AVATAR_DEFAULT_BROWSER);
    }

    let myAvatarKey = readMyAvatarKey();
    let phoneAvatarKey = AVATAR_DEFAULT_PHONE;
    let avatarPickerFilled = avatarIconSpec(normalizeAvatarKey(myAvatarKey, AVATAR_DEFAULT_BROWSER)).filled;
    let avatarGrouping = 'FIRST';
    let recallEnabled = false;

    // Track last rendered origin for consecutive same-origin suppression.
    // 'PHONE' | 'BROWSER' | null
    let lastBubbleOrigin = null;

    // Wrap a bubble div in a bubble-row that includes the appropriate avatar
    // or spacer. `origin` is 'PHONE' | 'BROWSER'.
    // Returns the row element (appended to list).
    function appendBubbleRow(bubbleEl, origin) {
        const mine = origin === 'BROWSER';
        const isContinuation = origin === lastBubbleOrigin;
        lastBubbleOrigin = origin;

        const row = document.createElement('div');
        row.className = 'bubble-row ' + (mine ? 'me' : 'them');

        if (isContinuation) {
            // Same origin in a row: show spacer instead of avatar.
            const spacer = document.createElement('div');
            spacer.className = 'avatar-spacer';
            row.appendChild(spacer);
        } else {
            const avatarEl = makeAvatarEl(mine ? myAvatarKey : phoneAvatarKey);
            row.appendChild(avatarEl);
        }
        row.appendChild(bubbleEl);

        list.appendChild(row);
        reflowMessageAvatars();
        list.scrollTop = list.scrollHeight;
        return row;
    }

    function rowOrigin(row) {
        if (!row) return null;
        if (row.classList.contains('me')) return 'BROWSER';
        if (row.classList.contains('them')) return 'PHONE';
        return null;
    }

    function makeAvatarSpacer() {
        const spacer = document.createElement('div');
        spacer.className = 'avatar-spacer';
        return spacer;
    }

    function setRowAvatarMarker(row, origin, showAvatar) {
        const marker = showAvatar
            ? makeAvatarEl(origin === 'BROWSER' ? myAvatarKey : phoneAvatarKey)
            : makeAvatarSpacer();
        const current = row.children[0];
        if (current && (current.classList.contains('avatar-circle') || current.classList.contains('avatar-spacer'))) {
            row.insertBefore(marker, current);
            current.remove();
        } else {
            row.insertBefore(marker, row.firstChild);
        }
    }

    function normalizeAvatarGrouping(value) {
        return value === 'LAST' || value === 'EACH' ? value : 'FIRST';
    }

    function shouldShowAvatarForRow(origin, previousOrigin, nextOrigin) {
        switch (avatarGrouping) {
            case 'LAST':
                return origin !== nextOrigin;
            case 'EACH':
                return true;
            case 'FIRST':
            default:
                return origin !== previousOrigin;
        }
    }

    function reflowMessageAvatars() {
        const rows = Array.from(list.querySelectorAll('.bubble-row'));
        let previousOrigin = null;
        rows.forEach((row, index) => {
            const origin = rowOrigin(row);
            if (!origin) return;
            const nextOrigin = rowOrigin(rows[index + 1]);
            setRowAvatarMarker(row, origin, shouldShowAvatarForRow(origin, previousOrigin, nextOrigin));
            previousOrigin = origin;
        });
        lastBubbleOrigin = previousOrigin;
    }

    // Update the header avatar and name for the peer.
    function renderPeerHeader(deviceName, avatarKey) {
        const peerAvatarEl = document.getElementById('peer-avatar');
        const peerNameEl = document.getElementById('peer-name');
        renderAvatar(peerAvatarEl, avatarKey);
        if (peerNameEl) {
            peerNameEl.textContent = t('app.peer_from', { device: deviceName });
        }
    }

    // Render the my-avatar button in the header.
    function renderMyAvatarBtn() {
        const btn = document.getElementById('my-avatar-btn');
        if (!btn) return;
        renderAvatar(btn, myAvatarKey);
    }

    function refreshBrowserMessageAvatars() {
        list.querySelectorAll('.bubble-row.me > .avatar-circle').forEach((avatarEl) => {
            renderAvatar(avatarEl, myAvatarKey);
        });
    }

    // Convert an ARGB Long string (e.g. "4294944066") to a CSS color string.
    // ARGB: bits 31-24 = alpha, 23-16 = R, 15-8 = G, 7-0 = B.
    function argbLongToCss(str) {
        const n = Number(str);
        if (!Number.isFinite(n)) return null;
        // Use unsigned 32-bit arithmetic.
        const argb = n >>> 0;
        const r = (argb >>> 16) & 0xFF;
        const g = (argb >>> 8) & 0xFF;
        const b = argb & 0xFF;
        const a = ((argb >>> 24) & 0xFF) / 255;
        return 'rgba(' + r + ',' + g + ',' + b + ',' + a.toFixed(3) + ')';
    }

    let currentBackgroundMode = 'DEFAULT';
    let currentDeviceName = t('app.phone');
    let connectionWatermarkState = 'connected';

    function defaultWatermarkText() {
        const status = connectionWatermarkState === 'disconnected'
            ? t('app.disconnected')
            : t('app.connected');
        return t('app.watermark', { status, device: currentDeviceName });
    }

    function refreshDefaultWatermark() {
        if (currentBackgroundMode === 'DEFAULT') {
            setWatermark(defaultWatermarkText());
        }
    }

    function setConnectionWatermarkState(state) {
        connectionWatermarkState = state;
        refreshDefaultWatermark();
    }

    // Apply conversation background to the list element based on peer-info.
    function applyBackground(mode, value, deviceName) {
        const normalizedMode = ['BLANK', 'SOLID', 'GRADIENT', 'DEFAULT'].includes(mode) ? mode : 'DEFAULT';
        currentBackgroundMode = normalizedMode;
        currentDeviceName = (typeof deviceName === 'string' && deviceName.length > 0) ? deviceName : currentDeviceName;
        switch (normalizedMode) {
            case 'BLANK':
                list.style.background = '';
                removeWatermark();
                break;
            case 'SOLID': {
                const css = argbLongToCss(value);
                if (css) list.style.background = css;
                removeWatermark();
                break;
            }
            case 'GRADIENT': {
                const grad = gradientCss(value);
                if (grad) list.style.background = grad;
                removeWatermark();
                break;
            }
            case 'DEFAULT':
            default:
                list.style.background = '';
                setWatermark(defaultWatermarkText());
                break;
        }
    }

    function gradientCss(name) {
        switch (name) {
            case 'sunset': return 'linear-gradient(135deg, #FF7043, #FF4081)';
            case 'forest': return 'linear-gradient(135deg, #2E7D32, #81C784)';
            case 'ocean':  return 'linear-gradient(135deg, #1565C0, #4FC3F7)';
            default:       return null;
        }
    }

    function setWatermark(text) {
        removeWatermark();
        const wm = document.createElement('div');
        wm.className = 'chat-list-watermark';
        wm.id = 'chat-watermark';
        wm.textContent = text;
        list.appendChild(wm);
    }

    function removeWatermark() {
        const wm = document.getElementById('chat-watermark');
        if (wm) wm.remove();
    }

    // Phase 3 双端对齐：跟随手机当前主题——深浅 + 主题色相（seed）。
    // mdui 与手机端 MDC 同用 Material Color Utilities，同一 seed 产同一色相，双端观感对齐；
    // seed 为空（手机用动态色/Material You，浏览器拿不到壁纸）时清回 mdui 默认配色，仅跟深浅。
    let lastThemeKey = null;
    function applyTheme(seed, dark) {
        const key = (dark ? 'd' : 'l') + '|' + (seed || '');
        if (key === lastThemeKey) return;   // peer-info 可能多次拉取，避免反复重建调色板
        lastThemeKey = key;
        if (!window.mdui) return;
        try {
            if (typeof mdui.setTheme === 'function') mdui.setTheme(dark ? 'dark' : 'light');
            if (typeof mdui.setColorScheme === 'function') {
                if (typeof seed === 'string' && /^#[0-9a-fA-F]{6}$/.test(seed)) {
                    mdui.setColorScheme(seed);
                } else if (typeof mdui.removeColorScheme === 'function') {
                    mdui.removeColorScheme();
                }
            }
        } catch (_) {
            // mdui 缺失或 API 漂移时主题不致命，静默——不阻断传输。
        }
    }

    // 气泡圆角双端联动：手机在设置里拖 slider → peer-info 推 bubbleCornerRadius(dp) →
    // 覆写 tokens.css 的 --flikky-bubble-radius，气泡两端圆角一致。这是「一个设计决策
    // 一处改动两端生效」的活样例。钳制到 App 侧的 8..28dp，非法值回落默认 18。
    let lastBubbleRadius = null;
    function applyBubbleRadius(dp) {
        const n = Number(dp);
        const clamped = Number.isFinite(n) ? Math.max(8, Math.min(28, Math.round(n))) : 18;
        if (clamped === lastBubbleRadius) return;
        lastBubbleRadius = clamped;
        document.documentElement.style.setProperty('--flikky-bubble-radius', clamped + 'px');
    }

    function resolvePhoneAvatarKey(data) {
        if (Object.prototype.hasOwnProperty.call(data, 'phoneAvatarKey')) {
            return normalizeAvatarKey(data.phoneAvatarKey, phoneAvatarKey);
        }
        if (Object.prototype.hasOwnProperty.call(data, 'phoneAvatarId')) {
            const legacyId = Number(data.phoneAvatarId);
            return Number.isInteger(legacyId) && legacyId !== 0
                ? legacyAvatarKey(legacyId, phoneAvatarKey)
                : phoneAvatarKey;
        }
        return phoneAvatarKey;
    }

    function applyPeerAppearance(data, fallbackName) {
        const name = (data.deviceName && typeof data.deviceName === 'string') ? data.deviceName : fallbackName;
        if (Object.prototype.hasOwnProperty.call(data, 'recallEnabled')) {
            recallEnabled = data.recallEnabled === true;
            if (!recallEnabled) closeRecallMenu();
        }
        phoneAvatarKey = resolvePhoneAvatarKey(data);
        avatarGrouping = normalizeAvatarGrouping(data.avatarGrouping);
        renderPeerHeader(name, phoneAvatarKey);
        applyBackground(data.backgroundMode || 'DEFAULT', data.backgroundValue || '', name);
        applyTheme(typeof data.themeSeed === 'string' ? data.themeSeed : null, !!data.themeDark);
        applyBubbleRadius(data.bubbleCornerRadius);
        reflowMessageAvatars();
    }

    // Fetch peer info and apply.
    async function fetchPeerInfo() {
        try {
            const r = await fetch('/api/peer-info');
            if (!r.ok) return;
            const data = await r.json();
            applyPeerAppearance(data, t('app.phone'));
        } catch (_) {
            // Fail silently — do not block transfers.
        }
    }

    // ── M9b: Avatar picker ────────────────────────────────────────────────
    function buildAvatarPickerGrid() {
        const grid = document.getElementById('avatar-picker-grid');
        if (!grid || grid.childElementCount > 0) return; // build once
        const dialog = grid.parentElement;
        if (dialog && !document.getElementById('avatar-fill-row')) {
            const row = document.createElement('label');
            row.id = 'avatar-fill-row';
            row.className = 'avatar-fill-row';
            const label = document.createElement('span');
            label.id = 'avatar-fill-label';
            label.textContent = t('app.filled_icons');
            const fillSwitch = document.createElement('mdui-switch');
            fillSwitch.id = 'avatar-fill-switch';
            fillSwitch.checked = avatarPickerFilled;
            fillSwitch.addEventListener('change', () => {
                avatarPickerFilled = !!fillSwitch.checked;
                updatePickerSelection();
            });
            row.appendChild(label);
            row.appendChild(fillSwitch);
            dialog.insertBefore(row, grid);
        }
        for (const key of AVATAR_PRESETS) {
            const cell = document.createElement('mdui-avatar');
            cell.setAttribute('role', 'button');
            cell.setAttribute('tabindex', '0');
            cell.dataset.avatarKey = key;
            cell.addEventListener('click', () => selectAvatar(displayAvatarKey(key)));
            cell.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); selectAvatar(displayAvatarKey(key)); }
            });
            grid.appendChild(cell);
        }
        const charCell = document.createElement('button');
        charCell.className = 'avatar-char-cell';
        charCell.type = 'button';
        charCell.textContent = 'A';
        charCell.addEventListener('click', () => {
            const value = window.prompt(t('app.avatar_character'), 'A') || '';
            const first = Array.from(value.trim())[0];
            if (first) selectAvatar('char:' + first);
        });
        grid.appendChild(charCell);
        updatePickerSelection();
    }

    function displayAvatarKey(key) {
        const normalized = normalizeAvatarKey(key, AVATAR_DEFAULT_BROWSER);
        if (!normalized.startsWith('icon:')) return normalized;
        const spec = avatarIconSpec(normalized);
        return 'icon:' + spec.name + ':' + (avatarPickerFilled ? 'filled' : 'outline');
    }

    function sameAvatarVisual(a, b) {
        const left = normalizeAvatarKey(a, AVATAR_DEFAULT_BROWSER);
        const right = normalizeAvatarKey(b, AVATAR_DEFAULT_BROWSER);
        if (left.startsWith('char:') || right.startsWith('char:')) return left === right;
        const l = avatarIconSpec(left);
        const r = avatarIconSpec(right);
        return l.name === r.name && l.filled === r.filled;
    }

    function openAvatarPicker() {
        avatarPickerFilled = avatarIconSpec(normalizeAvatarKey(myAvatarKey, AVATAR_DEFAULT_BROWSER)).filled;
        buildAvatarPickerGrid();
        const fillSwitch = document.getElementById('avatar-fill-switch');
        if (fillSwitch) fillSwitch.checked = avatarPickerFilled;
        updatePickerSelection();
        const picker = document.getElementById('avatar-picker');
        if (picker) picker.open = true;   // mdui-dialog: overlay + Esc close are built in.
    }

    function closeAvatarPicker() {
        const picker = document.getElementById('avatar-picker');
        if (picker) picker.open = false;
    }

    function updatePickerSelection() {
        const grid = document.getElementById('avatar-picker-grid');
        if (!grid) return;
        for (const cell of grid.children) {
            if (!cell.dataset.avatarKey) continue;
            const key = displayAvatarKey(cell.dataset.avatarKey);
            renderAvatar(cell, key);
            const selected = sameAvatarVisual(key, myAvatarKey);
            cell.setAttribute('aria-selected', selected ? 'true' : 'false');
        }
    }

    function selectAvatar(key) {
        myAvatarKey = normalizeAvatarKey(key, AVATAR_DEFAULT_BROWSER);
        localStorage.setItem('flikky_avatar_key', myAvatarKey);
        localStorage.removeItem('flikky_avatar');
        renderMyAvatarBtn();
        refreshBrowserMessageAvatars();
        updatePickerSelection();
        closeAvatarPicker();
        // Re-send client_hello so the phone updates its view of our avatar.
        sendClientHello();
    }

    // ── M9b: client_hello ─────────────────────────────────────────────────
    function sendClientHello() {
        if (currentWs && currentWs.readyState === 1) {
            try {
                currentWs.send(JSON.stringify({ type: 'client_hello', avatarKey: myAvatarKey }));
            } catch (_) {}
        }
    }

    // Init header avatar on load.
    renderMyAvatarBtn();

    // Attach picker open/close handlers after DOM is ready.
    const myAvatarBtnEl = document.getElementById('my-avatar-btn');
    if (myAvatarBtnEl) {
        myAvatarBtnEl.addEventListener('click', openAvatarPicker);
    }
    // mdui-dialog handles overlay-click and Escape dismissal itself (close-on-overlay-click
    // / close-on-esc), so no manual backdrop or keydown listeners are needed.

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
        appendBubbleRow(div, mine ? 'BROWSER' : 'PHONE');
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
        // 文件图标（与 App 文件气泡同款 Material Symbols description）。
        div.appendChild(materialSymbolEl('description', false));
        div.appendChild(a);
        div.appendChild(size);
        appendBubbleRow(div, mine ? 'BROWSER' : 'PHONE');
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
            if (!recallEnabled) return;
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
        if (!recallEnabled) return;
        closeRecallMenu();
        // 外层仍是 fixed 定位到指针坐标的手写容器——mdui-dropdown 的 anchor 模型不支持
        // 任意屏幕坐标 + 动态气泡长按触发，硬换会倒退。内部换成官方 mdui-menu / menu-item，
        // 带与 App 撤回同款的 undo 图标，拿到视觉一致又保留稳定的坐标/长按逻辑。
        const menu = document.createElement('div');
        menu.className = 'recall-menu';
        menu.id = 'recall-menu';
        // 避免菜单溢出屏幕右/下边缘——简单偏移即可。
        menu.style.left = Math.min(x, window.innerWidth - 120) + 'px';
        menu.style.top = Math.min(y, window.innerHeight - 60) + 'px';
        const mduiMenu = document.createElement('mdui-menu');
        const item = document.createElement('mdui-menu-item');
        item.appendChild(materialSymbolEl('undo', false, 'icon'));
        item.appendChild(document.createTextNode(t('app.recall')));
        item.addEventListener('click', (e) => {
            e.stopPropagation();
            closeRecallMenu();
            confirmRecallMessage(messageId);
        });
        mduiMenu.appendChild(item);
        menu.appendChild(mduiMenu);
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
        if (!recallEnabled) return;
        const dialog = document.getElementById('recall-confirm-dialog');
        if (!dialog) {
            // 回退：mdui 没加载，直接走 native confirm。
            if (window.confirm(`${t('app.recall_title')} ${t('app.recall_body')}`)) {
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
        if (!recallEnabled) return;
        try {
            const r = await fetch(`/api/messages/${messageId}`, {
                method: 'DELETE',
                headers: { 'X-Client-Id': myClientId },
            });
            if (r.ok || r.status === 404) {
                // 200 刚撤；404 已删（idempotent）。本地节点直接消失 + snackbar 提示。
                removeMessageNode(messageId);
                if (window.flikky && window.flikky.showInfo) {
                    window.flikky.showInfo(t('app.message_recalled'));
                }
            } else if (r.status === 403) {
                let error = null;
                try { error = await r.json(); } catch (_) {}
                const message = error && error.error === 'recall_disabled'
                    ? t('app.recall_not_enabled')
                    : t('app.recall_own_only');
                if (window.flikky && window.flikky.showError) window.flikky.showError(message);
            } else {
                if (window.flikky && window.flikky.showError) {
                    window.flikky.showError(t('app.recall_failed'));
                }
            }
        } catch (_) {
            if (window.flikky && window.flikky.showError) {
                window.flikky.showError(t('app.recall_network_failed'));
            }
        }
    }

    // v1.3 D26 修订：撤回 = 消息节点完全消失（不留占位符）。被两条路径调用：
    //  - 本浏览器调 DELETE 成功 → 移除节点 + showInfo "消息已撤回"
    //  - 收到 message_recalled WS event（对端撤回）→ 移除节点 + showInfo "对方撤回了一条消息"
    // 调用方负责 snackbar 文案；本函数只管 DOM 清理，且幂等。
    // M9b: bubble is now inside a .bubble-row → remove the row (parent) to
    // leave no ghost spacers. Falls back to removing the node itself if it is
    // a direct list child (future-proofing).
    function removeMessageNode(messageId) {
        const node = list.querySelector(`[data-message-id="${messageId}"]`);
        if (!node) return;
        const row = node.closest('.bubble-row');
        if (row && row.parentNode === list) {
            row.remove();
        } else {
            node.remove();
        }
        reflowMessageAvatars();
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

        appendBubbleRow(div, mine ? 'BROWSER' : 'PHONE');
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

        appendBubbleRow(div, 'BROWSER');
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
        hint.dataset.flikkyI18n = 'app.upload_failed_retry';
        hint.textContent = t('app.upload_failed_retry');
        bubble.appendChild(hint);
        bubble.style.cursor = 'pointer';
        bubble.dataset.flikkyI18nTitle = 'app.retry';
        bubble.title = t('app.retry');
        bubble.addEventListener('click', function retryHandler() {
            bubble.removeEventListener('click', retryHandler);
            // M9b: bubble is inside a .bubble-row — remove the whole row.
            const row = bubble.closest('.bubble-row');
            if (row && row.parentNode === list) {
                row.remove();
            } else {
                bubble.remove();
            }
            reflowMessageAvatars();
            sendFile(file);
        });

        if (window.flikky && window.flikky.showError) {
            const suffix = status ? ` (${status})` : '';
            window.flikky.showError(t('app.upload_failed_file', {
                file: file.name,
                suffix,
            }));
        }
    }

    function markBubbleFailedNoRetry(bubble, key) {
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
        failHint.dataset.flikkyI18n = key;
        failHint.textContent = t(key);
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
            setConnectionWatermarkState('disconnected');
            showBanner('terminated', 'app.service_stopped');
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
                    window.flikky.showInfo(t('app.peer_recalled'));
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
                    markBubbleFailedNoRetry(bubble, 'app.transfer_failed');
                }
            }
        } else if (ev.type === 'status') {
            lastStatus = ev.payload;
            renderStatus();
        } else if (ev.type === 'peer_avatar_changed') {
            if (ev.payload && typeof ev.payload.avatarKey === 'string') {
                myAvatarKey = normalizeAvatarKey(ev.payload.avatarKey, AVATAR_DEFAULT_BROWSER);
                localStorage.setItem('flikky_avatar_key', myAvatarKey);
                localStorage.removeItem('flikky_avatar');
                renderMyAvatarBtn();
                refreshBrowserMessageAvatars();
                updatePickerSelection();
            }
        } else if (ev.type === 'settings_changed') {
            applyPeerAppearance(ev.payload || {}, t('app.phone'));
        }
    }

    async function loadHistory() {
        lastBubbleOrigin = null;
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

    let currentConnKey = 'app.connecting';
    function setConn(key) {
        currentConnKey = key;
        conn.textContent = t(key);
    }

    function renderStatus() {
        if (!lastStatus) return;
        uptimeEl.textContent = formatUptime(lastStatus.uptime || 0);
        countEl.textContent = countText('app.files', Number(lastStatus.fileCount) || 0);
        rateEl.textContent = formatRate(lastStatus.bytesPerSecond || 0);
    }

    // 断网卡片：醒目位置展示连接状态。
    const banner = document.getElementById('conn-banner');
    let bannerState = null;
    function showBanner(kind, key, values) {
        if (!banner) return;
        bannerState = { kind, key, values };
        banner.textContent = t(key, values);
        banner.dataset.kind = kind;   // 'disconnected' | 'restored'
        banner.hidden = false;
    }
    function hideBanner() {
        if (!banner) return;
        bannerState = null;
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
        setConnectionWatermarkState('disconnected');
        setSendEnabled(false);
        setConn('app.disconnected');
        if (hadConnected) showBanner('disconnected', 'app.reconnecting');
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
            showBanner('terminated', 'app.service_maybe_closed');
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
            serverStopped = false;
            setConnectionWatermarkState('connected');
            setConn('app.connected');
            setSendEnabled(true);
            lastFrameAt = Date.now();
            reconnectAttempts = 0;   // 成功一次就清零计数
            startHeartbeat();
            // M9b: announce our avatar to the phone; fetch phone's info.
            sendClientHello();
            fetchPeerInfo().catch(() => {});
            // 重连后追平断开期间手机端发的消息（seen 集合 dedup 防重复渲染）。
            loadHistory().catch(() => {});
            if (hadConnected) {
                // 仅在"曾经连过又重连"时弹"已恢复"卡片，避免首次连上也弹。
                showBanner('restored', 'app.reconnected');
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
            setConnectionWatermarkState('disconnected');
            setConn('app.disconnected');
            setSendEnabled(false);
            stopHeartbeat();
            if (serverStopped) {
                showBanner('terminated', 'app.service_stopped');
                return;
            }
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                showBanner('terminated', 'app.service_maybe_closed');
                return;
            }
            reconnectAttempts++;
            if (hadConnected) {
                showBanner('disconnected', 'app.reconnecting_attempt', {
                    attempt: reconnectAttempts,
                    max: MAX_RECONNECT_ATTEMPTS,
                });
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
                window.flikky.showError(t('app.wait_reconnect'));
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
                window.flikky.showError(t('app.send_failed'));
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
                    if (pct) {
                        pct.dataset.flikkyI18n = 'app.processing';
                        pct.textContent = t('app.processing');
                    }
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
        xhr.onabort = () => { removeFromActive(); markBubbleFailedNoRetry(bubble, 'app.send_failed'); };
        xhr.upload.onerror = () => { removeFromActive(); markBubbleFailed(bubble, file); };
        xhr.upload.onabort = () => { removeFromActive(); markBubbleFailedNoRetry(bubble, 'app.send_failed'); };
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

    i18n.onChange(() => {
        conn.textContent = t(currentConnKey);
        if (lastStatus) renderStatus();
        else countEl.textContent = countText('app.files', 0);
        renderPeerHeader(currentDeviceName, phoneAvatarKey);
        refreshDefaultWatermark();
        const fillLabel = document.getElementById('avatar-fill-label');
        if (fillLabel) fillLabel.textContent = t('app.filled_icons');
        document.querySelectorAll('[data-flikky-i18n]').forEach((element) => {
            element.textContent = t(element.dataset.flikkyI18n);
        });
        document.querySelectorAll('[data-flikky-i18n-title]').forEach((element) => {
            element.title = t(element.dataset.flikkyI18nTitle);
        });
        if (bannerState && banner) {
            banner.textContent = t(bannerState.key, bannerState.values);
        }
        closeRecallMenu();
        fetchPeerInfo().catch(() => {});
    });

    // 初始禁用，等 WS 连上后启用。
    setSendEnabled(false);
    loadHistory().then(openWs);
})();
