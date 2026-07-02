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

    // ── Icons: Material Symbols, paths copied verbatim from the App's ic_*.xml
    // (viewBox 0 -960 960 960) so both ends render the exact same glyph. Inlined as
    // SVG (not the CDN font) to stay offline and within CSP default-src 'self'. ──
    const ICON_PATHS = {
        description: 'M320-240h320v-80H320v80Zm0-160h320v-80H320v80ZM240-80q-33 0-56.5-23.5T160-160v-640q0-33 23.5-56.5T240-880h320l240 240v480q0 33-23.5 56.5T720-80H240Zm280-520v-200H240v640h480v-440H520ZM240-800v200-200 640-640Z',
        desktop_windows: 'M320-120v-80h80v-80H160q-33 0-56.5-23.5T80-360v-400q0-33 23.5-56.5T160-840h640q33 0 56.5 23.5T880-760v400q0 33-23.5 56.5T800-280H560v80h80v80H320ZM160-360h640v-400H160v400Zm0 0v-400 400Z',
        desktop_windows_filled: { viewBox: '0 0 24 24', path: 'M20 18c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2H0v2h24v-2h-4zM4 4h16v12H4V4z' },
        smartphone: 'M280-40q-33 0-56.5-23.5T200-120v-720q0-33 23.5-56.5T280-920h400q33 0 56.5 23.5T760-840v124q18 7 29 22t11 34v80q0 19-11 34t-29 22v404q0 33-23.5 56.5T680-40H280Zm0-80h400v-720H280v720Zm0 0v-720 720Zm228.5-611.5Q520-743 520-760t-11.5-28.5Q497-800 480-800t-28.5 11.5Q440-777 440-760t11.5 28.5Q463-720 480-720t28.5-11.5Z',
        smartphone_filled: { viewBox: '0 0 24 24', path: 'M17 1.01 7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z' },
        person: 'M234-276q51-39 114-61.5T480-360q69 0 132 22.5T726-276q35-41 54.5-93T800-480q0-133-93.5-226.5T480-800q-133 0-226.5 93.5T160-480q0 59 19.5 111t54.5 93Zm146.5-204.5Q340-521 340-580t40.5-99.5Q421-720 480-720t99.5 40.5Q620-639 620-580t-40.5 99.5Q539-440 480-440t-99.5-40.5ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm100-95.5q47-15.5 86-44.5-39-29-86-44.5T480-280q-53 0-100 15.5T294-220q39 29 86 44.5T480-160q53 0 100-15.5ZM523-537q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm-43-43Zm0 360Z',
        person_filled: { viewBox: '0 0 24 24', path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM12 5c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zM12 19.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z' },
        settings: 'm370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm112-260q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Z',
        settings_outline: 'm370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm70-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z',
        star: 'm233-120 65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Z',
        star_outline: 'm354-287 126-76 126 77-33-144 111-96-146-13-58-136-58 135-146 13 111 97-33 143ZM233-120l65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Zm247-350Z',
        face: 'M324.5-404.5Q310-419 310-440t14.5-35.5Q339-490 360-490t35.5 14.5Q410-461 410-440t-14.5 35.5Q381-390 360-390t-35.5-14.5Zm240 0Q550-419 550-440t14.5-35.5Q579-490 600-490t35.5 14.5Q650-461 650-440t-14.5 35.5Q621-390 600-390t-35.5-14.5ZM480-160q134 0 227-93t93-227q0-24-3-46.5T786-570q-21 5-42 7.5t-44 2.5q-91 0-172-39T390-708q-32 78-91.5 135.5T160-486v6q0 134 93 227t227 93Zm0 80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm-54-715q42 70 114 112.5T700-640q14 0 27-1.5t27-3.5q-42-70-114-112.5T480-800q-14 0-27 1.5t-27 3.5ZM177-581q51-29 89-75t57-103q-51 29-89 75t-57 103Zm249-214Zm-103 36Z',
        face_filled: { viewBox: '0 0 24 24', path: 'M9 11.75c-.69 0-1.25-.56-1.25-1.25S8.31 9.25 9 9.25s1.25.56 1.25 1.25S9.69 11.75 9 11.75zM15 11.75c-.69 0-1.25-.56-1.25-1.25S14.31 9.25 15 9.25s1.25.56 1.25 1.25S15.69 11.75 15 11.75zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM12 20c-4.41 0-8-3.59-8-8 0-.29.02-.58.05-.86 2.36-1.05 4.23-2.98 5.21-5.37C11.07 8.33 14.05 10 17.42 10c.78 0 1.53-.09 2.25-.26.21.71.33 1.47.33 2.26 0 4.41-3.59 8-8 8z' },
        palette: 'M480-80q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480q0-83 32.5-156t88-127Q256-817 330-848.5T488-880q80 0 151 27.5t124.5 76q53.5 48.5 85 115T880-518q0 115-70 176.5T640-280h-74q-9 0-12.5 5t-3.5 11q0 12 15 34.5t15 51.5q0 50-27.5 74T480-80Zm0-400Zm-177 23q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm120-160q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm200 0q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17Zm120 160q17-17 17-43t-17-43q-17-17-43-17t-43 17q-17 17-17 43t17 43q17 17 43 17t43-17ZM480-160q9 0 14.5-5t5.5-13q0-14-15-33t-15-57q0-42 29-67t71-25h70q66 0 113-38.5T800-518q0-121-92.5-201.5T488-800q-136 0-232 93t-96 227q0 133 93.5 226.5T480-160Z',
        palette_filled: { viewBox: '0 0 24 24', path: 'M12 3C7.03 3 3 6.58 3 11c0 3.31 2.69 6 6 6h1.5c.83 0 1.5.67 1.5 1.5S12.67 20 13.5 20H15c3.31 0 6-2.69 6-6 0-6.08-4.93-11-9-11zM6.5 11C5.67 11 5 10.33 5 9.5S5.67 8 6.5 8 8 8.67 8 9.5 7.33 11 6.5 11zM9.5 7C8.67 7 8 6.33 8 5.5S8.67 4 9.5 4 11 4.67 11 5.5 10.33 7 9.5 7zM14.5 7C13.67 7 13 6.33 13 5.5S13.67 4 14.5 4 16 4.67 16 5.5 15.33 7 14.5 7zM17.5 11c-.83 0-1.5-.67-1.5-1.5S16.67 8 17.5 8 19 8.67 19 9.5 18.33 11 17.5 11z' },
        image: 'M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm0-80h560v-560H200v560Zm40-80h480L570-480 450-320l-90-120-120 160Zm-40 80v-560 560Z',
        image_filled: { viewBox: '0 0 24 24', path: 'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 11.5l2.5 3.01L14.5 10l4.5 6H5l3.5-4.5z' },
        undo: 'M280-200v-80h284q63 0 109.5-40T720-420q0-60-46.5-100T564-560H312l104 104-56 56-200-200 200-200 56 56-104 104h252q97 0 166.5 63T800-420q0 94-69.5 157T564-200H280Z',
    };
    // Returns an <mdui-icon> element wrapping the inline SVG for the given name.
    function svgIcon(name, slot) {
        const icon = document.createElement('mdui-icon');
        if (slot) icon.setAttribute('slot', slot);
        const iconDef = ICON_PATHS[name];
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', typeof iconDef === 'object' ? iconDef.viewBox : '0 -960 960 960');
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', typeof iconDef === 'object' ? iconDef.path : (iconDef || ''));
        svg.appendChild(path);
        icon.appendChild(svg);
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
        const pathName = spec.filled && ICON_PATHS[spec.name + '_filled']
            ? spec.name + '_filled'
            : (!spec.filled && ICON_PATHS[spec.name + '_outline'] ? spec.name + '_outline' : spec.name);
        if (ICON_PATHS[pathName]) {
            el.appendChild(svgIcon(pathName));
        } else {
            el.setAttribute('icon', spec.name);
        }
    }

    function makeAvatarEl(avatarKey) {
        const el = document.createElement('mdui-avatar');
        renderAvatar(el, avatarKey);
        return el;
    }

    function readMyAvatarKey() {
        const stored = localStorage.getItem('flikky_avatar_key');
        if (stored) return normalizeAvatarKey(stored, AVATAR_DEFAULT_BROWSER);
        return legacyAvatarKey(Number(localStorage.getItem('flikky_avatar')), AVATAR_DEFAULT_BROWSER);
    }

    let myAvatarKey = readMyAvatarKey();
    let phoneAvatarKey = AVATAR_DEFAULT_PHONE;
    let avatarPickerFilled = avatarIconSpec(normalizeAvatarKey(myAvatarKey, AVATAR_DEFAULT_BROWSER)).filled;

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
        list.scrollTop = list.scrollHeight;
        return row;
    }

    // Update the header avatar and name for the peer.
    function renderPeerHeader(deviceName, avatarKey) {
        const peerAvatarEl = document.getElementById('peer-avatar');
        const peerNameEl = document.getElementById('peer-name');
        renderAvatar(peerAvatarEl, avatarKey);
        if (peerNameEl) {
            peerNameEl.textContent = '来自 ' + deviceName;
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

    // Apply conversation background to the list element based on peer-info.
    function applyBackground(mode, value, deviceName) {
        switch (mode) {
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
                setWatermark('已连接 · ' + deviceName);
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

    // Fetch peer info and apply.
    async function fetchPeerInfo() {
        try {
            const r = await fetch('/api/peer-info');
            if (!r.ok) return;
            const data = await r.json();
            const name = (data.deviceName && typeof data.deviceName === 'string') ? data.deviceName : '手机';
            phoneAvatarKey = normalizeAvatarKey(
                data.phoneAvatarKey,
                legacyAvatarKey(Number(data.phoneAvatarId), AVATAR_DEFAULT_PHONE),
            );
            renderPeerHeader(name, phoneAvatarKey);
            applyBackground(data.backgroundMode || 'DEFAULT', data.backgroundValue || '', name);
            applyTheme(typeof data.themeSeed === 'string' ? data.themeSeed : null, !!data.themeDark);
            applyBubbleRadius(data.bubbleCornerRadius);
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
            label.textContent = '填充图标';
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
            const value = window.prompt('Avatar character', 'A') || '';
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
        div.appendChild(svgIcon('description'));
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
        item.appendChild(svgIcon('undo', 'icon'));
        item.appendChild(document.createTextNode('撤回'));
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
        hint.textContent = '上传失败，点击重试';
        bubble.appendChild(hint);
        bubble.style.cursor = 'pointer';
        bubble.title = '点击重试';
        bubble.addEventListener('click', function retryHandler() {
            bubble.removeEventListener('click', retryHandler);
            // M9b: bubble is inside a .bubble-row — remove the whole row.
            const row = bubble.closest('.bubble-row');
            if (row && row.parentNode === list) {
                row.remove();
            } else {
                bubble.remove();
            }
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
        } else if (ev.type === 'peer_avatar_changed') {
            if (ev.payload && typeof ev.payload.avatarKey === 'string') {
                myAvatarKey = normalizeAvatarKey(ev.payload.avatarKey, AVATAR_DEFAULT_BROWSER);
                localStorage.setItem('flikky_avatar_key', myAvatarKey);
                localStorage.removeItem('flikky_avatar');
                renderMyAvatarBtn();
                refreshBrowserMessageAvatars();
                updatePickerSelection();
            }
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
            // M9b: announce our avatar to the phone; fetch phone's info.
            sendClientHello();
            fetchPeerInfo().catch(() => {});
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
