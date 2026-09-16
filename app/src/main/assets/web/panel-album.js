/* ============================================================================
 * 相册面板：浏览手机 MediaStore 提供的照片与视频。
 *
 * enabled 默认 false，只由 app.js 在 peer-info 到达后写入，避免开关未知时撞 404。
 * 列表按 NDJSON 增量消费；requestSeq 使旧响应无法覆盖当前状态。
 * 所有文本都通过 textContent 写入，图标使用 Material Symbols 并对辅助技术隐藏。
 *
 * ## 日期不在这里算（D65）
 *
 * 装机验收（2026-09-16，Screenshot_12 / 13）暴露过双端分组不一致：手机按自己的时区
 * 算日期、浏览器按**电脑**的时区算，两台设备时区不同，于是跨午夜的照片在两端掉进
 * 不同的分组 —— 分组数、每组张数、日期标签全不一样，看起来像相册把文件搞混了。
 * 现在每项自带手机算好的 `dateKey`，流首行带 `todayKey` / `yesterdayKey`，
 * 本文件**只比较字符串**，不碰 Date 的年月日方法（`panel-album.test.js` 有守卫）。
 *
 * ## 格子固定尺寸、按面板宽度换行
 *
 * 首版照搬了 App 端手机竖屏的「固定三列」，但面板宽度可变，于是缩略图跟着面板缩放
 * （装机反馈 Screenshot_8 / 9）。现在格子是固定的 TILE_PX，每行放几个由容器宽度
 * 算出来，宽度变化时重排而不是拉伸。
 *
 * ## 虚拟化只增量改动窗口
 *
 * 首版每次滚动都把窗口整块拆掉重建，代价是：滚轮被浏览器的滚动锚定打断、
 * 已经在视口里的缩略图重新发一次请求（装机时控制台 244 次请求 / 2.6MB）、
 * 并且肉眼可见地闪。现在窗口只做差集：留下的行**一个 DOM 节点都不动**。
 * ==========================================================================*/
(function () {
    'use strict';

    const t = (key, values) => (window.flikkyI18n ? window.flikkyI18n.t(key, values) : key);

    /**
     * 格子边长与间距，**必须与 panels.css 的 `--flikky-album-tile` / gap 一致**。
     *
     * JS 需要这两个数才能算「每行放几个」与「滚动区间有多高」，而读取实际布局
     * （offsetHeight / BoxWithConstraints 那一类）在本项目已经错过两次
     * （见 panel-files.js 行距那段注释）。所以这里不测量，而是与 CSS 共用常量：
     * 一处改就两处都改，`panel-album.test.js` 有一条断言把它们钉在一起。
     */
    const TILE_PX = 116;
    const TILE_GAP_PX = 8;
    /** 日期头的固定高度，同样与 CSS 对齐。 */
    const DATE_ROW_PX = 44;
    const MEDIA_ROW_PX = TILE_PX + TILE_GAP_PX;
    const VIRTUAL_OVERSCAN = 4;
    const LF = String.fromCharCode(10);

    let enabled = false;
    let requestSeq = 0;
    let root = null;
    let bodyEl = null;
    let rowsHost = null;
    let topSpacer = null;
    let bottomSpacer = null;
    let toolbarEl = null;
    let countEl = null;
    let items = [];
    let logicalRows = [];
    /** logicalRows[i] 顶边距列表顶端的像素距离；长度为 rows+1，末项即总高。 */
    let rowOffsets = [0];
    let reportedTotal = 0;
    let complete = false;
    let loading = false;
    let retryVisible = false;
    let columns = 3;

    /** 服务端下发的日期键；空串表示还没收到首行。 */
    let todayKey = '';
    let yesterdayKey = '';

    /**
     * 相册簿列表。**null 表示在时间线视图**，非 null（含空表）表示在相册簿视图。
     *
     * 用 null 而不是另加一个布尔：两个字段能拼出「在相册簿视图但没有列表」
     * 这种说不通的状态，而这一个字段说不出那句话（App 端 AlbumUiState 同构）。
     */
    let buckets = null;
    /** 已进入的相册簿名；null 表示没进任何簿。空串是合法簿名（未知相册）。 */
    let openBucket = null;

    /** 当前挂载的行：逻辑行号 → DOM 元素。虚拟化靠它做差集，不重建。 */
    const mounted = new Map();

    /** 已勾选的项 id。跨分组累积，与文件面板同语义。 */
    const selected = new Set();

    /**
     * 缩略图取不到的项。
     *
     * 装机时同一个 404 的项被反复请求了十几次（控制台 244 请求 / 2.6MB）——
     * 因为窗口每次重建都重新设一次 src。记下失败过的 id，之后只画占位图标，
     * 不再发请求。刷新面板会清空它，用户仍有重试的路。
     */
    const failedThumbs = new Set();

    function icon(name) {
        const el = document.createElement('span');
        el.className = 'material-symbols-outlined';
        el.dataset.icon = name;
        el.setAttribute('aria-hidden', 'true');
        return el;
    }

    function resetVirtualState() {
        mounted.clear();
        rowsHost = null;
        topSpacer = null;
        bottomSpacer = null;
    }

    function clearBody() {
        if (!bodyEl) return;
        byVisibleImages(bodyEl).forEach((img) => img.removeAttribute('src'));
        bodyEl.textContent = '';
        resetVirtualState();
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

    /** 按容器宽度算每行放几个格子。宽度未知时回落到 1，永远不会是 0。 */
    function columnsFor(width) {
        if (!width || width <= 0) return columns;
        const usable = width + TILE_GAP_PX;
        return Math.max(1, Math.floor(usable / (TILE_PX + TILE_GAP_PX)));
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

        buildToolbar(container);
        observeWidth();
    }

    /**
     * 选择工具栏。
     *
     * 类名与按钮顺序**逐个对齐文件面板**（`.fk-toolbar` / `.fk-toolbar-count` /
     * `.fk-icon-btn`，主操作在最右且用 --filled 变体），不新写 CSS ——
     * 两个面板的多选条长得不一样会立刻被看出来。
     */
    function buildToolbar(container) {
        toolbarEl = document.createElement('div');
        toolbarEl.className = 'fk-toolbar';
        toolbarEl.hidden = true;

        countEl = document.createElement('span');
        countEl.className = 'fk-toolbar-count';
        toolbarEl.appendChild(countEl);

        const clear = document.createElement('button');
        clear.type = 'button';
        clear.className = 'fk-icon-btn';
        clear.setAttribute('aria-label', t('app.album.clear'));
        clear.appendChild(icon('close'));
        clear.addEventListener('click', () => {
            selected.clear();
            // 就地更新：清一次选择不该让整个网格闪。
            markSelection();
            syncToolbar();
        });
        toolbarEl.appendChild(clear);

        const save = document.createElement('button');
        save.type = 'button';
        save.className = 'fk-icon-btn fk-icon-btn--filled';
        save.setAttribute('aria-label', t('app.album.saveSelected'));
        save.appendChild(icon('download'));
        save.addEventListener('click', () => saveSelected());
        toolbarEl.appendChild(save);

        container.appendChild(toolbarEl);
    }

    /**
     * 面板宽度变化时重排列数。
     *
     * 用 ResizeObserver 而不是 window.resize：功能栏可以被拖宽/收起，
     * 那两种都不触发 window.resize。没有 ResizeObserver 的环境（测试用的假 DOM）
     * 退化为不重排，列数仍是首次算出的值 —— 不会崩。
     */
    function observeWidth() {
        if (typeof ResizeObserver !== 'function' || !bodyEl) return;
        const observer = new ResizeObserver(() => {
            const next = columnsFor(bodyEl.clientWidth);
            if (next === columns) return;
            columns = next;
            rebuildLogicalRows();
            // 列数变了，行的构成全变，必须整块重画一次；这是宽度变化才有的代价。
            render();
        });
        observer.observe(bodyEl);
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

    /**
     * 日期键 → 分组头文案。
     *
     * **只做字符串比较**：键是零填充的 `yyyy-MM-dd`，所以字典序等于时间序，
     * `key >= todayKey` 就是「今天或更晚」。这里刻意不构造 Date、不取
     * getFullYear/getMonth/getDate —— 那正是双端分组不一致的来源（D65）。
     */
    function dateLabelFor(key) {
        if (!key) return t('app.album.today');
        if (todayKey && key >= todayKey) return t('app.album.today');
        if (yesterdayKey && key === yesterdayKey) return t('app.album.yesterday');
        const parts = key.split('-');
        if (parts.length !== 3) return t('app.album.today');
        const year = Number(parts[0]);
        const month = Number(parts[1]);
        const day = Number(parts[2]);
        const currentYear = todayKey ? Number(todayKey.split('-')[0]) : year;
        if (year === currentYear) {
            return t('app.album.monthDay', { month: month, day: day });
        }
        return t('app.album.yearMonthDay', { year: year, month: month, day: day });
    }

    function rebuildLogicalRows() {
        const sorted = items.slice().sort((a, b) => Number(b.takenAtMs || 0) - Number(a.takenAtMs || 0));
        const next = [];
        let openKey = null;
        let pending = [];

        const flush = () => {
            for (let at = 0; at < pending.length; at += columns) {
                next.push({ kind: 'media', items: pending.slice(at, at + columns) });
            }
            pending = [];
        };

        sorted.forEach((item) => {
            // 超过今天的键折叠到今天：相机时间设错的照片会落在未来，不折叠的话
            // 每个未来日期各自成一组、而标签都是「今天」，用户会看到好几个「今天」。
            // Kotlin 侧的 AlbumTimeline.bucketKeyFor 是同一条规则。
            const raw = String(item.dateKey || '');
            const key = (todayKey && raw && raw > todayKey) ? todayKey : raw;
            if (key !== openKey) {
                flush();
                openKey = key;
                next.push({ kind: 'date', label: dateLabelFor(key), key: key });
            }
            pending.push(item);
        });
        flush();
        logicalRows = next;

        // 行高表：日期头与媒体行高度不同，所以不能用单一 pitch 反推位置
        // （首版用了一个硬编码的 132，于是滚动位置与真实内容越滚越偏）。
        rowOffsets = new Array(logicalRows.length + 1);
        rowOffsets[0] = 0;
        for (let i = 0; i < logicalRows.length; i += 1) {
            const height = logicalRows[i].kind === 'date' ? DATE_ROW_PX : MEDIA_ROW_PX;
            rowOffsets[i + 1] = rowOffsets[i] + height;
        }
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

    function isVideo(item) {
        return String(item.mime || '').toLowerCase().startsWith('video/');
    }

    function createTile(item) {
        const tile = document.createElement('div');
        tile.className = 'fk-album-tile';
        tile.dataset.itemId = item.id;
        if (selected.has(item.id)) tile.dataset.selected = '1';

        const open = document.createElement('button');
        open.type = 'button';
        open.className = 'fk-album-open';
        open.setAttribute('aria-label', item.name || item.id);
        // 相册项就在手机上；把缩略图拖出去会被会话页的全局 drop zone 当成
        // 「拖了个文件进来」并提示「松开以发送文件」，于是用户会把手机上的照片
        // 又发一遍给手机（装机反馈 2026-09-16）。这里把拖拽整条路关掉。
        open.draggable = false;
        open.addEventListener('dragstart', (event) => event.preventDefault());

        if (failedThumbs.has(item.id)) {
            // 失败过就不再请求：占位一个图标，避免同一个 404 被窗口反复重发。
            open.appendChild(icon('broken_image'));
        } else {
            const image = document.createElement('img');
            image.alt = '';
            image.loading = 'lazy';
            image.draggable = false;
            image.addEventListener('dragstart', (event) => event.preventDefault());
            image.addEventListener('error', () => {
                failedThumbs.add(item.id);
                image.removeAttribute('src');
                if (image.parentNode === open) open.replaceChild(icon('broken_image'), image);
            });
            image.src = thumbUrl(item.id);
            open.appendChild(image);
        }

        if (isVideo(item)) {
            const badge = document.createElement('span');
            badge.className = 'fk-album-video';
            badge.appendChild(icon('play_circle'));
            const duration = document.createElement('span');
            duration.textContent = formatDuration(item.durationMs);
            badge.appendChild(duration);
            open.appendChild(badge);
        }

        open.addEventListener('click', () => {
            // 已经在选择态时，点图是继续勾选 —— 与 App 端多选态同一手感。
            if (selected.size > 0) {
                toggleSelection(item.id);
                return;
            }
            if (!window.flikky || typeof window.flikky.openLightbox !== 'function') return;
            window.flikky.openLightbox({
                kind: isVideo(item) ? 'video' : 'image',
                thumbnailUrl: failedThumbs.has(item.id) ? null : thumbUrl(item.id),
                fullUrl: fileUrl(item.id, true),
            });
        });
        tile.appendChild(open);

        // 选择角标：鼠标没有长按，所以选择需要自己的入口（App 端那边是长按）。
        const pick = document.createElement('button');
        pick.type = 'button';
        pick.className = 'fk-album-pick';
        pick.setAttribute('aria-label', t('app.album.select'));
        pick.appendChild(icon('check_circle'));
        pick.addEventListener('click', (event) => {
            event.stopPropagation();
            toggleSelection(item.id);
        });
        tile.appendChild(pick);

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

    /** 二分找到第一个底边超过 y 的行 —— 行高不统一，所以不能用除法。 */
    function rowAt(y) {
        let low = 0;
        let high = logicalRows.length;
        while (low < high) {
            const mid = (low + high) >> 1;
            if (rowOffsets[mid + 1] <= y) low = mid + 1; else high = mid;
        }
        return Math.min(low, Math.max(0, logicalRows.length - 1));
    }

    /**
     * 把 DOM 里的窗口对齐到当前滚动位置。
     *
     * **只做差集**：窗口内已经挂着的行一个都不碰，于是滚动时视口里的缩略图
     * 既不重新请求也不闪。首版每次都 `textContent = ''` 重建整块，
     * 那既打断了滚轮（浏览器滚动锚定）又制造了请求风暴。
     */
    function syncVirtual() {
        if (!rowsHost || !bodyEl) return;
        const total = logicalRows.length;
        if (!total) return;
        const viewport = bodyEl.clientHeight || MEDIA_ROW_PX * 6;
        const scrollTop = bodyEl.scrollTop || 0;
        const firstVisible = rowAt(scrollTop);
        const first = Math.max(0, firstVisible - VIRTUAL_OVERSCAN);
        let to = first;
        while (to < total && rowOffsets[to] < scrollTop + viewport) to += 1;
        to = Math.min(total, to + VIRTUAL_OVERSCAN);

        // 1) 移出窗口的行：卸掉并把图片 src 摘掉，免得它还在后台加载。
        Array.from(mounted.keys()).forEach((index) => {
            if (index >= first && index < to) return;
            const element = mounted.get(index);
            byVisibleImages(element).forEach((img) => img.removeAttribute('src'));
            if (element.parentNode === rowsHost) rowsHost.removeChild(element);
            mounted.delete(index);
        });

        // 2) 新进窗口的行：插到正确位置，保持 DOM 顺序与逻辑顺序一致。
        for (let index = first; index < to; index += 1) {
            if (mounted.has(index)) continue;
            const element = createRow(logicalRows[index], index);
            let anchor = bottomSpacer;
            for (let probe = index + 1; probe < to; probe += 1) {
                if (mounted.has(probe)) { anchor = mounted.get(probe); break; }
            }
            rowsHost.insertBefore(element, anchor);
            mounted.set(index, element);
        }

        // 3) 两个 spacer 撑出窗口之外的滚动区间，高度用真实像素。
        topSpacer.style.height = rowOffsets[first] + 'px';
        bottomSpacer.style.height = (rowOffsets[total] - rowOffsets[to]) + 'px';
    }

    // ── 选择 ──────────────────────────────────────────────────────────────

    function toggleSelection(id) {
        if (selected.has(id)) selected.delete(id); else selected.add(id);
        markSelection();
        syncToolbar();
    }

    /** 只改受影响格子的 data-selected，**不重建 DOM** —— 勾一下不该让网格闪。 */
    function markSelection() {
        mounted.forEach((row) => {
            Array.prototype.forEach.call(row.children || [], (tile) => {
                const id = tile.dataset && tile.dataset.itemId;
                if (!id) return;
                if (selected.has(id)) tile.dataset.selected = '1';
                else delete tile.dataset.selected;
            });
        });
    }

    function syncToolbar() {
        if (!toolbarEl || !countEl) return;
        toolbarEl.hidden = selected.size === 0;
        countEl.textContent = t('app.album.selected', { count: selected.size });
    }

    function saveSelected() {
        const byId = new Map(items.map((item) => [item.id, item]));
        selected.forEach((id) => {
            const item = byId.get(id);
            const link = document.createElement('a');
            link.href = fileUrl(id, false);
            link.setAttribute('download', (item && item.name) || id);
            link.click();
        });
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

    // ── 相册簿视图 ────────────────────────────────────────────────────────

    /**
     * 视图切换行。进了某个簿之后换成返回入口 —— 两个入口同时在会让
     * 「我现在在哪」变得不明确，而这是个只有两层的导航，不值得面包屑。
     */
    function buildViewSwitch() {
        const bar = document.createElement('div');
        bar.className = 'fk-album-views';

        if (openBucket !== null) {
            const back = document.createElement('mdui-button');
            back.type = 'button';
            back.className = 'fk-album-view';
            back.setAttribute('variant', 'text');
            const backIcon = icon('arrow_back');
            backIcon.setAttribute('slot', 'icon');
            back.appendChild(backIcon);
            const label = document.createElement('span');
            label.textContent = openBucket || t('app.album.unknownBucket');
            back.appendChild(label);
            back.addEventListener('click', () => leaveBucket());
            bar.appendChild(back);
            return bar;
        }

        // 文案放进独立的 <span>，与全站动态图标同一条约定：按钮自身不持有文本节点，
        // 于是它里面若再放图标也不会出现「字形进了 DOM 文本」那类问题
        // （web-selection.test.js 的守卫盯着这条）。
        const make = (key, active, onClick) => {
            const button = document.createElement('mdui-button');
            button.type = 'button';
            button.className = 'fk-album-view';
            button.setAttribute('variant', active ? 'tonal' : 'outlined');
            button.setAttribute('aria-pressed', String(active));
            if (active) button.dataset.active = '1';
            const label = document.createElement('span');
            label.textContent = t(key);
            button.appendChild(label);
            button.addEventListener('click', onClick);
            return button;
        };
        bar.appendChild(make('app.album.viewTimeline', buckets === null, () => selectView(false)));
        bar.appendChild(make('app.album.viewBuckets', buckets !== null, () => selectView(true)));
        return bar;
    }

    function renderBuckets() {
        clearBody();
        bodyEl.appendChild(buildViewSwitch());
        if (!buckets.length) {
            const notice = document.createElement('div');
            notice.className = 'fk-empty';
            notice.appendChild(icon('photo_library'));
            const text = document.createElement('p');
            text.textContent = t(loading ? 'app.album.loading' : 'app.album.empty');
            notice.appendChild(text);
            bodyEl.appendChild(notice);
            return;
        }
        const grid = document.createElement('div');
        grid.className = 'fk-album-buckets';
        buckets.forEach((bucket) => {
            const card = document.createElement('button');
            card.type = 'button';
            card.className = 'fk-album-bucket';
            card.addEventListener('click', () => openBucketView(bucket.name));

            const cover = document.createElement('img');
            cover.alt = '';
            cover.loading = 'lazy';
            cover.draggable = false;
            cover.addEventListener('dragstart', (event) => event.preventDefault());
            if (bucket.coverId && !failedThumbs.has(bucket.coverId)) {
                cover.src = thumbUrl(bucket.coverId);
                cover.addEventListener('error', () => {
                    failedThumbs.add(bucket.coverId);
                    cover.removeAttribute('src');
                });
            }
            card.appendChild(cover);

            const name = document.createElement('span');
            name.className = 'fk-album-bucket-name';
            name.textContent = bucket.name || t('app.album.unknownBucket');
            card.appendChild(name);

            const count = document.createElement('span');
            count.className = 'fk-album-bucket-count';
            count.textContent = t('app.album.bucketCount', { count: bucket.count });
            card.appendChild(count);

            grid.appendChild(card);
        });
        bodyEl.appendChild(grid);
    }

    function selectView(showBuckets) {
        if (!showBuckets) {
            buckets = null;
            openBucket = null;
            load();
            return;
        }
        // 先给空表：它同时是「在相册簿视图」的标记，于是加载期间显示的是
        // 相册簿视图的空态，而不是上一次的时间线。
        buckets = [];
        openBucket = null;
        loadBuckets();
    }

    function openBucketView(name) {
        openBucket = name;
        load();
    }

    function leaveBucket() {
        openBucket = null;
        selectView(true);
    }

    async function loadBuckets() {
        if (!enabled) return;
        const seq = ++requestSeq;
        loading = true;
        selected.clear();
        render();
        try {
            const response = await fetch('/api/album/buckets', { credentials: 'same-origin' });
            if (seq !== requestSeq) return;
            if (!response.ok) {
                loading = false;
                notifyError('app.album.loadFailed');
                render();
                return;
            }
            const value = await response.json();
            if (seq !== requestSeq) return;
            buckets = Array.isArray(value) ? value : [];
            loading = false;
            render();
        } catch (e) {
            if (seq !== requestSeq) return;
            loading = false;
            notifyError('app.album.loadFailed');
            render();
        }
    }

    function render() {
        if (!bodyEl) return;
        if (buckets !== null && openBucket === null) {
            renderBuckets();
            syncToolbar();
            return;
        }
        if (!items.length) {
            renderNotice(loading ? 'progress_activity' : 'photo_library',
                loading ? 'app.album.loading' : 'app.album.empty');
            // 空态也要留着切换行，否则进了一个空相册簿就没有返回的路。
            if (openBucket !== null) bodyEl.insertBefore(buildViewSwitch(), bodyEl.children[0]);
            if (retryVisible) appendRetry();
            syncToolbar();
            return;
        }
        const keepScroll = bodyEl.scrollTop || 0;
        clearBody();
        bodyEl.appendChild(buildViewSwitch());
        columns = columnsFor(bodyEl.clientWidth);
        rowsHost = document.createElement('div');
        rowsHost.className = 'fk-album-rows';
        topSpacer = document.createElement('div');
        topSpacer.className = 'fk-album-vspace';
        topSpacer.setAttribute('aria-hidden', 'true');
        bottomSpacer = document.createElement('div');
        bottomSpacer.className = 'fk-album-vspace';
        bottomSpacer.setAttribute('aria-hidden', 'true');
        rowsHost.appendChild(topSpacer);
        rowsHost.appendChild(bottomSpacer);
        bodyEl.appendChild(rowsHost);
        // 流式追加与宽度重排都会走到这里；滚动位置要留住，否则每批到达都把用户弹回顶部。
        bodyEl.scrollTop = keepScroll;
        syncVirtual();
        appendFooter();
        if (retryVisible) appendRetry();
        syncToolbar();
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
        rowOffsets = [0];
        reportedTotal = 0;
        complete = false;
        loading = true;
        retryVisible = false;
        selected.clear();
        // 刷新是用户唯一的重试入口，所以清掉失败记录，让 404 过的项再试一次。
        failedThumbs.clear();
        if (bodyEl) bodyEl.scrollTop = 0;
        render();

        try {
            // 进了相册簿就只拉该簿；空串是合法簿名，所以判 null 而不是判空。
            const query = openBucket === null ? '' : '&bucket=' + encodeURIComponent(openBucket);
            const response = await fetch('/api/album/list?stream=1' + query, { credentials: 'same-origin' });
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
                        // 手机算好的今天/昨天：本文件据此渲染标签，自己不算日期（D65）。
                        if (typeof value.todayKey === 'string') todayKey = value.todayKey;
                        if (typeof value.yesterdayKey === 'string') yesterdayKey = value.yesterdayKey;
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
            rowOffsets = [0];
            reportedTotal = 0;
            complete = false;
            loading = false;
            retryVisible = false;
            selected.clear();
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
