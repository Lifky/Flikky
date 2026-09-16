/* ============================================================================
 * 相册面板：浏览手机 MediaStore 提供的照片与视频。
 *
 * enabled 默认 false，只由 app.js 在 peer-info 到达后写入，避免开关未知时撞 404。
 * 列表按 NDJSON 增量消费；requestSeq 使旧响应无法覆盖当前状态。日期头和每组三个
 * 媒体项都是一个逻辑行，DOM 只保留视口附近的窗口。格子尺寸与间距由 CSS 决定，
 * JS 不读取布局尺寸；上下 spacer 只按逻辑行数估算滚动范围。
 * 所有文本都通过 textContent 写入，图标使用 Material Symbols 并对辅助技术隐藏。
 * ==========================================================================*/
(function () {
    'use strict';

    const t = (key, values) => (window.flikkyI18n ? window.flikkyI18n.t(key, values) : key);
    const ALBUM_COLUMNS = 3;
    const VIRTUAL_OVERSCAN = 6;
    const VIRTUAL_ROW_PITCH = 132;
    const LF = String.fromCharCode(10);

    let enabled = false;
    let requestSeq = 0;
    let root = null;
    let bodyEl = null;
    let rowsHost = null;
    let renderedFirst = -1;
    let renderedTo = -1;
    let items = [];
    let logicalRows = [];
    let reportedTotal = 0;
    let complete = false;
    let loading = false;
    let retryVisible = false;

    function icon(name) {
        const el = document.createElement('span');
        el.className = 'material-symbols-outlined';
        el.dataset.icon = name;
        el.setAttribute('aria-hidden', 'true');
        return el;
    }

    function clearBody() {
        if (!bodyEl) return;
        byVisibleImages(bodyEl).forEach((img) => img.removeAttribute('src'));
        bodyEl.textContent = '';
        rowsHost = null;
        renderedFirst = -1;
        renderedTo = -1;
    }

    function byVisibleImages(host) {
        const found = [];
        const visit = (node) => {
            if (!node) return;
            if (node.tagName === 'IMG') found.push(node);
            Array.prototype.forEach.call(node.children || [], visit);
        };
        visit(host);
        return found;
    }

    function buildShell(container) {
        container.textContent = '';

        const header = document.createElement('header');
        header.className = 'fk-panel-head';
        const title = document.createElement('h1');
        title.className = 'fk-panel-title';
        title.textContent = t('app.album.title');
        header.appendChild(title);

        const refresh = document.createElement('button');
        refresh.type = 'button';
        refresh.className = 'fk-icon-btn';
        refresh.setAttribute('aria-label', t('app.album.refresh'));
        refresh.appendChild(icon('refresh'));
        refresh.addEventListener('click', () => load());
        header.appendChild(refresh);

        const collapse = document.createElement('button');
        collapse.type = 'button';
        collapse.className = 'fk-icon-btn fk-panel-collapse';
        collapse.setAttribute('aria-label', t('app.settings.collapse'));
        collapse.appendChild(icon('close'));
        header.appendChild(collapse);
        container.appendChild(header);

        bodyEl = document.createElement('div');
        bodyEl.className = 'fk-panel-body flikky-scroll';
        bodyEl.addEventListener('scroll', syncVirtual);
        container.appendChild(bodyEl);
    }

    function renderNotice(iconName, textKey) {
        clearBody();
        const notice = document.createElement('div');
        notice.className = 'fk-empty';
        notice.appendChild(icon(iconName));
        const text = document.createElement('p');
        text.textContent = t(textKey);
        notice.appendChild(text);
        bodyEl.appendChild(notice);
    }

    function dateKey(date) {
        return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
    }

    function dateLabel(takenAtMs, nowMs) {
        const now = new Date(nowMs);
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const yesterday = new Date(today.getTime());
        yesterday.setDate(yesterday.getDate() - 1);
        const date = new Date(takenAtMs);
        if (date >= today) return t('app.album.today');
        if (dateKey(date) === dateKey(yesterday)) return t('app.album.yesterday');
        if (date.getFullYear() === today.getFullYear()) {
            return t('app.album.monthDay', { month: date.getMonth() + 1, day: date.getDate() });
        }
        return t('app.album.yearMonthDay', {
            year: date.getFullYear(),
            month: date.getMonth() + 1,
            day: date.getDate(),
        });
    }

    function rebuildLogicalRows() {
        const sorted = items.slice().sort((a, b) => Number(b.takenAtMs || 0) - Number(a.takenAtMs || 0));
        const sections = [];
        const today = new Date();
        const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        sorted.forEach((item) => {
            const rawDate = new Date(Number(item.takenAtMs || 0));
            const bucketDate = rawDate >= todayStart ? todayStart : rawDate;
            const key = dateKey(bucketDate);
            let section = sections.length ? sections[sections.length - 1] : null;
            if (!section || section.key !== key) {
                section = { key, label: dateLabel(Number(item.takenAtMs || 0), Date.now()), items: [] };
                sections.push(section);
            }
            section.items.push(item);
        });

        const next = [];
        sections.forEach((section) => {
            next.push({ kind: 'date', label: section.label });
            for (let at = 0; at < section.items.length; at += ALBUM_COLUMNS) {
                next.push({ kind: 'media', items: section.items.slice(at, at + ALBUM_COLUMNS) });
            }
        });
        logicalRows = next;
    }

    function formatDuration(durationMs) {
        const totalSeconds = Math.max(0, Math.floor(Number(durationMs || 0) / 1000));
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = String(totalSeconds % 60).padStart(2, '0');
        return `${minutes}:${seconds}`;
    }

    function thumbUrl(id) {
        return '/api/album/thumb?id=' + encodeURIComponent(id);
    }

    function fileUrl(id, inline) {
        return '/api/album/file?id=' + encodeURIComponent(id) + (inline ? '&inline=1' : '');
    }

    function createTile(item) {
        const tile = document.createElement('button');
        tile.type = 'button';
        tile.className = 'fk-album-tile';
        tile.setAttribute('aria-label', item.name || item.id);

        const image = document.createElement('img');
        image.alt = '';
        image.loading = 'lazy';
        image.src = thumbUrl(item.id);
        tile.appendChild(image);

        const video = String(item.mime || '').toLowerCase().startsWith('video/');
        if (video) {
            const badge = document.createElement('span');
            badge.className = 'fk-album-video';
            badge.appendChild(icon('play_circle'));
            const duration = document.createElement('span');
            duration.textContent = formatDuration(item.durationMs);
            badge.appendChild(duration);
            tile.appendChild(badge);
        }
        tile.addEventListener('click', () => {
            if (!window.flikky || typeof window.flikky.openLightbox !== 'function') return;
            window.flikky.openLightbox({
                kind: video ? 'video' : 'image',
                thumbnailUrl: thumbUrl(item.id),
                fullUrl: fileUrl(item.id, true),
            });
        });
        return tile;
    }

    function createRow(row, index) {
        let element;
        if (row.kind === 'date') {
            element = document.createElement('div');
            element.className = 'fk-album-date';
            element.textContent = row.label;
        } else {
            element = document.createElement('div');
            element.className = 'fk-album-grid';
            row.items.forEach((item) => element.appendChild(createTile(item)));
        }
        element.dataset.rowIndex = String(index);
        return element;
    }

    function spacer(rows) {
        if (rows <= 0) return null;
        const el = document.createElement('div');
        el.className = 'fk-album-vspace';
        el.style.setProperty('--flikky-album-spacer-rows', rows);
        el.setAttribute('aria-hidden', 'true');
        return el;
    }

    function syncVirtual() {
        if (!rowsHost || !bodyEl) return;
        const viewport = bodyEl.clientHeight || VIRTUAL_ROW_PITCH * 6;
        const first = Math.max(0, Math.floor((bodyEl.scrollTop || 0) / VIRTUAL_ROW_PITCH) - VIRTUAL_OVERSCAN);
        const visible = Math.ceil(viewport / VIRTUAL_ROW_PITCH) + VIRTUAL_OVERSCAN * 2;
        const to = Math.min(logicalRows.length, first + visible);
        if (first === renderedFirst && to === renderedTo) return;

        byVisibleImages(rowsHost).forEach((img) => img.removeAttribute('src'));
        rowsHost.textContent = '';

        const top = spacer(first);
        if (top) rowsHost.appendChild(top);
        for (let index = first; index < to; index += 1) {
            rowsHost.appendChild(createRow(logicalRows[index], index));
        }
        const bottom = spacer(logicalRows.length - to);
        if (bottom) rowsHost.appendChild(bottom);
        renderedFirst = first;
        renderedTo = to;
    }

    function appendFooter() {
        const footer = document.createElement('p');
        footer.className = 'fk-album-footer';
        footer.textContent = t('app.album.total', {
            count: complete ? items.length : Math.max(reportedTotal, items.length),
        });
        bodyEl.appendChild(footer);
    }

    function appendRetry() {
        const retry = document.createElement('button');
        retry.type = 'button';
        retry.className = 'fk-album-retry';
        retry.textContent = t('app.album.retry');
        retry.addEventListener('click', () => load());
        bodyEl.appendChild(retry);
    }

    function render() {
        if (!bodyEl) return;
        if (!items.length) {
            renderNotice(loading ? 'progress_activity' : 'photo_library',
                loading ? 'app.album.loading' : 'app.album.empty');
            if (retryVisible) appendRetry();
            return;
        }
        clearBody();
        rowsHost = document.createElement('div');
        rowsHost.className = 'fk-album-rows';
        bodyEl.appendChild(rowsHost);
        syncVirtual();
        appendFooter();
        if (retryVisible) appendRetry();
    }

    function notifyError(key) {
        if (window.flikky && typeof window.flikky.showError === 'function') {
            window.flikky.showError(t(key));
        }
    }

    async function load() {
        if (!enabled) return;
        const seq = ++requestSeq;
        items = [];
        logicalRows = [];
        reportedTotal = 0;
        complete = false;
        loading = true;
        retryVisible = false;
        if (bodyEl) bodyEl.scrollTop = 0;
        render();

        try {
            const response = await fetch('/api/album/list?stream=1', { credentials: 'same-origin' });
            if (seq !== requestSeq) return;
            if (!response.ok) {
                loading = false;
                retryVisible = true;
                notifyError('app.album.loadFailed');
                render();
                return;
            }

            const reader = response.body && typeof response.body.getReader === 'function'
                ? response.body.getReader()
                : null;
            const decoder = reader ? new TextDecoder() : null;
            let buffered = '';
            let sawDone = false;
            let streamEnded = false;
            while (!streamEnded) {
                const step = reader
                    ? await reader.read()
                    : { value: null, done: true };
                if (seq !== requestSeq) {
                    if (reader && typeof reader.cancel === 'function') reader.cancel();
                    return;
                }
                if (step.value) buffered += decoder.decode(step.value, { stream: true });
                if (!reader && typeof response.text === 'function') {
                    buffered += await response.text();
                    if (seq !== requestSeq) return;
                }
                for (;;) {
                    const newline = buffered.indexOf(LF);
                    if (newline < 0) break;
                    const line = buffered.slice(0, newline).trim();
                    buffered = buffered.slice(newline + 1);
                    if (!line) continue;
                    let value;
                    try { value = JSON.parse(line); } catch (e) { continue; }
                    if (value.done === true) { sawDone = true; continue; }
                    if (typeof value.total === 'number' && typeof value.id !== 'string') {
                        reportedTotal = Math.max(0, value.total);
                        continue;
                    }
                    if (typeof value.id === 'string') items.push(value);
                }
                if (items.length) {
                    rebuildLogicalRows();
                    render();
                }
                streamEnded = step.done || !reader;
            }
            if (seq !== requestSeq) return;
            loading = false;
            complete = sawDone;
            retryVisible = !sawDone;
            rebuildLogicalRows();
            render();
            if (!sawDone) notifyError('app.album.truncated');
        } catch (e) {
            if (seq !== requestSeq) return;
            loading = false;
            retryVisible = true;
            notifyError('app.album.loadFailed');
            render();
        }
    }

    function setEnabled(next) {
        const on = !!next;
        if (on === enabled) return;
        enabled = on;
        if (!enabled) {
            requestSeq += 1;
            items = [];
            logicalRows = [];
            reportedTotal = 0;
            complete = false;
            loading = false;
            retryVisible = false;
            render();
            return;
        }
        load();
    }

    function mount(container) {
        if (!container) return;
        root = container;
        buildShell(root);
        render();
        if (enabled) load();
        if (window.flikkyI18n) {
            window.flikkyI18n.onChange(() => {
                buildShell(root);
                rebuildLogicalRows();
                render();
            });
        }
    }

    window.flikkyPanels = window.flikkyPanels || {};
    window.flikkyPanels.album = {
        mount: mount,
        setEnabled: setEnabled,
        render: render,
    };
    mount(document.getElementById('view-album'));
})();
