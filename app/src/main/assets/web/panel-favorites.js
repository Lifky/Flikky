/* ============================================================================
 * 收藏面板：功能柱的第二个面板，形状照抄 panel-settings.js——
 * window.flikkyPanels.favorites.mount(root) 是唯一入口，脚本末尾自己 mount 一次。
 *
 * D8（与设置面板的刻意分歧）：这里的骨架（搜索框、chips 容器、toolbar）是写在
 * app.html 里的静态 HTML，不像设置面板整块由 JS 建——契约钉死了 8 个 id，
 * 且搜索框/工具条这类「壳」本来就不随数据变化。本文件只动态建「行」和 chips
 * 的内容；静态标签上的 data-i18n* 交给 i18n.js 的 applyStaticTranslations
 * 换语言，本文件的 render 只管重画行。
 *
 * D4（正是 Task 5 踩过的坑——commit 5d98a92）：本文件在 </body> 前同步执行，
 * 严格早于 app.js 的 fetchPeerInfo() 的 await 返回。所以：
 *   1) mount 后立刻自己请求 /api/favorites，不等 body 上的 favoriteEnabled 落地——
 *      功能关着就是一个 404，而 404 直接就是「功能未开启」这个终态，不用等谁通知。
 *   2) 额外拿 MutationObserver 盯 document.body 的 data-favorite-enabled——
 *      手机在会话中途从「设置」切换收藏开关会通过 settings_changed 走
 *      app.js 的 applyPeerAppearance 发布到这个属性，此时必须重新拉取一次，
 *      而不是假装什么都没变。
 *   3) 首次可见状态永远是「加载中」，绝不是错误或关闭态——那两种状态只能来自
 *      一次真实的响应。
 *
 * D1：渲染只由三处触发——i18n.onChange（真实 i18n.js 订阅时立刻同步调用一次，
 * 这一次就是初次渲染，不要在 mount 里再手动调一次 render，否则重复渲染）、
 * MutationObserver 的回调、以及 load() 自己在请求落地后调用。用户交互
 * （搜索、点 chip、选中/清除行）也会直接调 render——这些不是「初次渲染的重复」，
 * 是数据/选中状态真的变了之后的正常重渲染。
 *
 * D7：不用任何 DOM 选择器 API 反查状态——选中集是模块内的 Set<id>，行元素是
 * Map<id, rowEl>，两者都活在 JS 里，不反查 DOM。
 * ==========================================================================*/

(function () {
    'use strict';

    function t(key, values) {
        return window.flikkyI18n ? window.flikkyI18n.t(key, values) : key;
    }

    function icon(name) {
        const span = document.createElement('span');
        span.className = 'material-symbols-outlined';
        span.dataset.icon = name;
        span.setAttribute('aria-hidden', 'true');
        return span;
    }

    // ── 模块状态：数据 + UI 状态全部在这里，不从 DOM 属性反查（D7）。
    let viewRoot = null;
    let refreshBtn = null;
    let searchInput = null;
    let sortMenuEl = null;
    const SORT_STORAGE_KEY = 'flikky_sort_favorites';
    // 默认按收藏时间倒序 = 本版之前 DAO 的 createdAt DESC。
    let favSort = null;
    let chipsHost = null;
    let listHost = null;
    let toolbar = null;
    let countEl = null;
    let clearBtn = null;
    let saveSelectedBtn = null;

    let phase = 'loading';           // 'loading' | 'disabled' | 'error' | 'loaded'
    let groups = [];
    let items = [];
    const itemsById = new Map();
    let groupId = 'all';
    let query = '';
    const selected = new Set();      // 选中的收藏行 id
    const rowsById = new Map();      // id -> 当前渲染出的行元素

    function formatFileSize(bytes) {
        if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes < 0) return '';
        if (bytes >= 1024 * 1024) return (bytes / 1048576).toFixed(1) + ' MB';
        if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return bytes + ' B';
    }

    function subtitleFor(item) {
        const date = new Date(item.createdAt).toLocaleString();
        if (item.kind === 'FILE') {
            const size = formatFileSize(item.fileSize || 0);
            return size ? `${size} · ${date}` : date;
        }
        return date;
    }

    // 分类图标必须与手机端一致（App 侧是 FilesListBuilder.categoryOf →
    // FileCategory.iconResource()）。这里曾经自带一份简化映射，缺 audio 分支、
    // 也没有 svg 特例，于是 mp3 显示成文档、svg 显示成图片，两端对不上。
    // 现在统一取 app.js 发布的那一份 —— 会话页文件泡用的就是它。
    function fileIconFor(item) {
        const mime = item.mime || '';
        const name = item.fileName || '';
        // apk 与 zip 是收藏页特有的两个细分（会话页的文件泡没有这个需求），
        // 它们落在共享映射的 'draft' 兜底里，在这之前先判掉。
        if (mime === 'application/vnd.android.package-archive' || /\.apk$/i.test(name)) return 'android';
        if (mime.indexOf('zip') !== -1 || /\.zip$/i.test(name)) return 'folder_zip';
        if (window.flikky && typeof window.flikky.fileSymbolName === 'function') {
            return window.flikky.fileSymbolName(mime);
        }
        return 'draft';
    }

    /** filterFavorites 是纯函数、单独导出——不依赖 DOM，测试直接喂数组即可验证。 */
    /**
     * 排序内核（`sort.js`）。生产环境由 app.html 的 script 顺序保证它先加载；
     * 缺失时回落成「什么都不排」而不是抛 —— 排序是视图偏好，不该让面板打不开。
     * 与 panel-files.js 同一处理。
     */
    const sorter = window.flikkySort || {
        natural: function (k) { return { key: k, desc: false }; },
        tap: function (_s, k) { return { key: k, desc: false }; },
        compareName: function (a, b) { return a < b ? -1 : (a > b ? 1 : 0); },
        load: function (_k, fallback) { return fallback; },
        save: function () {},
    };

    /**
     * 排序菜单的定位层（`sort-menu.js`）。与 sorter 同样的防御：
     * 缺失时回落成「菜单打不开」而不是抛。
     */
    const menus = window.flikkySortMenu || {
        open: function () {}, close: function () {}, isOpen: function () { return false; },
    };

    function filterFavorites(itemsList, options) {
        const opts = options || {};
        const groupFilter = Object.prototype.hasOwnProperty.call(opts, 'groupId') ? opts.groupId : 'all';
        const rawQuery = typeof opts.query === 'string' ? opts.query : '';
        const q = rawQuery.trim().toLowerCase();
        return (itemsList || []).filter((item) => {
            // 坑 2：groupId === 'all' 必须在字符串化之前短路，否则 'all' 会被
            // String() 归一化成 "all" 再去跟 item.groupId 的字符串比，白白多算一遍
            // 且一旦哪天 groups 里出现名为 all 的分组 id 就会撞车。
            if (groupFilter !== 'all' && String(item.groupId) !== String(groupFilter)) return false;
            if (!q) return true;
            const haystack = item.kind === 'FILE' ? (item.fileName || '') : (item.text || '');
            return haystack.toLowerCase().indexOf(q) !== -1;
        });
    }

    /**
     * 收藏排序。与 App 端 `FavoritesListOrder` 是同一套语义的两份实现，
     * 由 `app/src/test/resources/sort-order.json` 的 `favorites` 段钉死。
     *
     * 名称键用**原始值**（`fileName || text || ''`）：回落到本地化占位串会让
     * 切换语言改变排序。按大小排时无文件的项两个方向都排末尾 ——
     * 「没有大小」不是「大小为 0」。末尾兜底恒**升序**，与文件浏览一致。
     *
     * 返回新数组，不改入参。
     */
    function sortFavorites(itemsList, spec) {
        const s = spec || sorter.natural('TIME');
        const nameOf = (it) => it.fileName || it.text || '';
        return (itemsList || []).slice().sort(function (a, b) {
            if (s.key === 'SIZE') {
                const ma = (a.fileSize === null || a.fileSize === undefined) ? 1 : 0;
                const mb = (b.fileSize === null || b.fileSize === undefined) ? 1 : 0;
                if (ma !== mb) return ma - mb;
            }
            let k;
            if (s.key === 'NAME') {
                k = sorter.compareName(nameOf(a), nameOf(b));
            } else if (s.key === 'TIME') {
                const ta = a.createdAt || 0;
                const tb = b.createdAt || 0;
                k = ta < tb ? -1 : (ta > tb ? 1 : 0);
            } else {
                const sa = a.fileSize || 0;
                const sb = b.fileSize || 0;
                k = sa < sb ? -1 : (sa > sb ? 1 : 0);
            }
            if (s.desc) k = -k;
            if (k !== 0) return k;
            return sorter.compareName(nameOf(a), nameOf(b));
        });
    }

    /**
     * 给当前键的那一项挂方向箭头，其余项摘掉。与 panel-files.js 同一画法：
     * 箭头既是选中指示也是方向指示，所以不再放对勾。
     *
     * 摘旧箭头走 `while (firstChild) removeChild`：那是项目共用的那套 DOM 操作
     * （见 mini-dom 的 KDoc）。本面板一律不按选择器回查 DOM —— 状态活在 JS 里，
     * 从 DOM 读回既慢又容易和渲染不同步（守卫见 panel-favorites.test.js）。
     * 连这段注释里也不能出现那个 API 名，否则守卫会把它当成一处调用。
     */
    function syncSortMenu() {
        if (!sortMenuEl || !favSort) return;
        Array.prototype.forEach.call(sortMenuEl.children, function (item) {
            const label = item.textContent;
            while (item.firstChild) item.removeChild(item.firstChild);
            item.textContent = label;
            if (item.getAttribute('value') !== favSort.key) return;
            const dir = document.createElement('span');
            dir.className = 'material-symbols-outlined fk-sort-dir';
            dir.setAttribute('data-icon', favSort.desc ? 'arrow_downward' : 'arrow_upward');
            dir.setAttribute('aria-hidden', 'true');
            dir.setAttribute('slot', 'end-icon');
            item.appendChild(dir);
        });
    }

    // ── 复制 / 下载 ─────────────────────────────────────────────────────────

    function notifyInfo(text) {
        if (window.flikky && typeof window.flikky.showInfo === 'function') window.flikky.showInfo(text);
    }
    function notifyError(text) {
        if (window.flikky && typeof window.flikky.showError === 'function') window.flikky.showError(text);
    }

    function execCommandCopy(text) {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        document.body.appendChild(textarea);
        if (typeof textarea.select === 'function') textarea.select();
        let ok = false;
        try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
        textarea.remove();
        return ok;
    }

    async function copyFavorite(item) {
        const text = item.text || '';
        let ok = false;
        // 不看 window.isSecureContext：本项目当前版本就是明文 HTTP（见 CLAUDE.md
        // 安全红线），只要 navigator.clipboard.writeText 存在就走它，走不通再兜底。
        if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
            try {
                await navigator.clipboard.writeText(text);
                ok = true;
            } catch (e) { ok = false; }
        }
        if (!ok) ok = execCommandCopy(text);
        if (ok) notifyInfo(t('app.favorites.copied'));
        else notifyError(t('app.copy_failed'));
    }

    function downloadFavorite(item) {
        const link = document.createElement('a');
        link.href = `/api/favorites/${item.id}/file`;
        link.download = item.fileName || '';
        document.body.appendChild(link);
        link.click();
        link.remove();
    }

    function saveSelected() {
        const ids = Array.from(selected);
        (async () => {
            for (const id of ids) {
                const item = itemsById.get(id);
                if (item && item.kind === 'FILE') {
                    downloadFavorite(item);
                    // D9：复刻 app.js 的 saveAllIndividually 节奏——一个个点，
                    // 别在同一 tick 里连点 N 次触发浏览器的下载节流。
                    await new Promise((resolve) => setTimeout(resolve, 350));
                }
            }
        })();
    }

    // ── 行 / chip 构建 ──────────────────────────────────────────────────────

    function buildRow(item) {
        const isFile = item.kind === 'FILE';
        const row = document.createElement('div');
        row.className = 'fk-item';
        row.setAttribute('data-fav-id', String(item.id));
        row.setAttribute('data-kind', isFile ? 'file' : 'text');
        row.setAttribute('aria-selected', selected.has(item.id) ? 'true' : 'false');
        if (isFile) {
            const leading = window.flikkyLeading;
            row.dataset.leadingType = leading && typeof leading.typeOf === 'function'
                ? leading.typeOf(item.mime).id
                : 'other';
        }

        const lead = document.createElement('span');
        lead.className = 'fk-item-lead';
        const leadIcon = isFile ? fileIconFor(item) : 'format_quote';
        lead.appendChild(icon(leadIcon));
        const leading = window.flikkyLeading;
        const mediaKind = window.flikky && typeof window.flikky.mediaKind === 'function'
            ? window.flikky.mediaKind(item.mime)
            : null;
        if (isFile && mediaKind && leading && typeof leading.attachThumbnail === 'function') {
            leading.attachThumbnail(lead, {
                path: String(item.id),
                url: `/api/favorites/${item.id}/thumb`,
                alt: item.fileName || '',
                onClick: function () {
                    const open = window.flikky && window.flikky.openLightbox;
                    if (typeof open !== 'function') return;
                    open({
                        kind: mediaKind,
                        fullUrl: `/api/favorites/${item.id}/file?inline=1`,
                        thumbnailUrl: `/api/favorites/${item.id}/thumb`,
                    });
                },
                onError: function () {
                    while (lead.firstChild) lead.removeChild(lead.firstChild);
                    lead.classList.remove('fk-item-lead--thumb');
                    lead.appendChild(icon(leadIcon));
                },
            });
        }
        row.appendChild(lead);

        const text = document.createElement('span');
        text.className = 'fk-item-text';
        const title = document.createElement('span');
        title.className = isFile ? 'fk-item-title' : 'fk-item-title fk-item-title--wrap';
        title.textContent = isFile ? (item.fileName || '') : (item.text || '');
        const sub = document.createElement('span');
        sub.className = 'fk-item-sub';
        sub.textContent = subtitleFor(item);
        text.appendChild(title);
        text.appendChild(sub);
        row.appendChild(text);

        const trail = document.createElement('span');
        trail.className = 'fk-item-trail';
        if (isFile) {
            const check = document.createElement('span');
            check.className = 'fk-check';
            check.appendChild(icon('check'));
            trail.appendChild(check);
        }
        const actionBtn = document.createElement('button');
        actionBtn.type = 'button';
        actionBtn.className = 'fk-icon-btn';
        actionBtn.setAttribute('data-role', isFile ? 'save' : 'copy');
        actionBtn.setAttribute('title', t(isFile ? 'app.favorites.save' : 'app.favorites.copy'));
        actionBtn.appendChild(icon(isFile ? 'download' : 'content_copy'));
        actionBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            if (isFile) downloadFavorite(item); else copyFavorite(item);
        });
        trail.appendChild(actionBtn);
        row.appendChild(trail);

        if (isFile) {
            row.addEventListener('click', () => {
                if (selected.has(item.id)) selected.delete(item.id); else selected.add(item.id);
                // **就地更新**，绝不调 render()：那会把每一行连同分组标题全部拆掉重建。
                // 文件面板已经因为这个被用户抓到过（「每次选中一个文件项，整个列表会闪一次」）；
                // 收藏这边一直没被察觉，只因为行原本没有入场动画，重绘看不太出来。
                // 现在行有了逐行入场，重绘会变成每次勾选闪一整屏。
                row.setAttribute('aria-selected', selected.has(item.id) ? 'true' : 'false');
                syncToolbar();
            });
        }

        rowsById.set(item.id, row);
        return row;
    }

    /**
     * 给行设批内阶梯序号 `--i`，并**封顶**。
     *
     * 与文件面板同一套（同一个 CSS 规则 `.fk-list-in > .fk-item`、同一个封顶值）。
     * 不封顶的话，收藏项多起来时末行要等好几秒才出现——文件面板那边的注释记着
     * 这个坑：正确答案是封顶，不是放弃逐行。
     */
    function stagger(row, i) {
        if (row && row.style) row.style.setProperty('--i', String(Math.min(i, STAGGER_CAP)));
        return row;
    }

    /** 批内阶梯封顶步数。与 panel-files.js 同值，两个面板观感一致。 */
    const STAGGER_CAP = 8;

    function sectionTitle(text) {
        const el = document.createElement('div');
        el.className = 'fk-section-title';
        el.textContent = text;
        return el;
    }

    function buildMessage(text, iconName) {
        const wrap = document.createElement('div');
        wrap.className = 'fk-empty';
        wrap.appendChild(icon(iconName || 'star_border'));
        const title = document.createElement('div');
        title.className = 'fk-empty-title';
        title.textContent = text;
        wrap.appendChild(title);
        return wrap;
    }

    // 官方 mdui-chip，filter 变体。自绘的那版还得自己补选中态配色、check 图标的
    // 出现、按压涟漪与键盘语义，而 mdui 现成的这些都做得更好 —— 这正是「库比自己写强
    // 的地方就用库」（决策 D30）。
    function buildChip(id, label) {
        const chip = document.createElement('mdui-chip');
        chip.setAttribute('variant', 'filter');
        chip.setAttribute('selectable', '');
        chip.setAttribute('data-group-id', id);
        if (String(groupId) === String(id)) chip.setAttribute('selected', '');
        chip.textContent = label;
        // 这是一组单选筛选，而 mdui 的 selectable 是各自独立开关：点已选中的那个会把它
        // 关掉，剩下一个都没选中的空状态。所以不直接采信组件的 selected，而是把点击
        // 一律当成「切到这一组」，再由 render() 重画出唯一的选中项。
        chip.addEventListener('change', () => {
            groupId = id;
            render();
        });
        return chip;
    }

    function buildChips() {
        while (chipsHost.firstChild) chipsHost.removeChild(chipsHost.firstChild);
        chipsHost.appendChild(buildChip('all', t('app.favorites.allGroups')));
        groups.forEach((g) => chipsHost.appendChild(buildChip(String(g.id), g.name)));
    }

    function renderList() {
        while (listHost.firstChild) listHost.removeChild(listHost.firstChild);
        rowsById.clear();

        if (items.length === 0) {
            listHost.appendChild(buildMessage(t('app.favorites.empty')));
            return;
        }

        const filtered = sortFavorites(filterFavorites(items, { groupId, query }), favSort);
        if (filtered.length === 0) {
            listHost.appendChild(buildMessage(t('app.favorites.noMatches')));
            return;
        }

        const byGroup = new Map();
        const ungrouped = [];
        filtered.forEach((item) => {
            const known = groups.some((g) => String(g.id) === String(item.groupId));
            if (known) {
                const key = String(item.groupId);
                if (!byGroup.has(key)) byGroup.set(key, []);
                byGroup.get(key).push(item);
            } else {
                // 坑 3：groupId 指向一个不存在的分组（读竞态的瞬时产物）——
                // 归到未分组，绝不丢弃、绝不因此抛错。
                ungrouped.push(item);
            }
        });

        groups.forEach((g) => {
            const groupItems = byGroup.get(String(g.id));
            if (!groupItems || groupItems.length === 0) return;
            listHost.appendChild(sectionTitle(g.name));
            const groupEl = document.createElement('div');
            groupEl.className = 'fk-group fk-list-in';
            groupItems.forEach((item, i) => stagger(groupEl.appendChild(buildRow(item)), i));
            listHost.appendChild(groupEl);
        });

        if (ungrouped.length > 0) {
            listHost.appendChild(sectionTitle(t('app.favorites.ungrouped')));
            const groupEl = document.createElement('div');
            groupEl.className = 'fk-group fk-list-in';
            ungrouped.forEach((item, i) => stagger(groupEl.appendChild(buildRow(item)), i));
            listHost.appendChild(groupEl);
        }
    }

    // ── 四态渲染 ─────────────────────────────────────────────────────────────

    function clearHosts() {
        while (chipsHost.firstChild) chipsHost.removeChild(chipsHost.firstChild);
        while (listHost.firstChild) listHost.removeChild(listHost.firstChild);
        rowsById.clear();
        toolbar.hidden = true;
    }

    function renderLoading() {
        clearHosts();
        listHost.appendChild(buildMessage(t('app.processing'), 'hourglass_top'));
    }

    // 404 是列表接口唯一的含义——功能未开启。重试没有意义，所以不给重试按钮，
    // 也不能跟「加载失败」共用同一句文案/同一个 .fk-error 容器（D5）。
    function renderDisabled() {
        clearHosts();
        const wrap = buildMessage(t('app.favorites.disabled'), 'star_border');
        const hint = document.createElement('div');
        hint.className = 'fk-item-sub';
        hint.textContent = t('app.favorites.disabledHint');
        wrap.appendChild(hint);
        listHost.appendChild(wrap);
    }

    // 除 404 外的任何非 2xx，或者 fetch 本身抛出（离线/超时）——都是可重试的错误。
    // .fk-error 是 panels.css 里原本缺失的类（brief 允许在真缺失时补一条），
    // 只加了一条：把这个空态图标的颜色换成错误色，跟「真的没有/关闭」区分开。
    function renderError() {
        clearHosts();
        const wrap = buildMessage(t('app.favorites.loadFailed'), 'error');
        wrap.classList.add('fk-error');
        const retryBtn = document.createElement('mdui-button');
        retryBtn.setAttribute('variant', 'text');
        retryBtn.setAttribute('data-role', 'retry');
        retryBtn.textContent = t('app.favorites.retry');
        retryBtn.addEventListener('click', () => { refresh(); });
        wrap.appendChild(retryBtn);
        listHost.appendChild(wrap);
    }

    /**
     * 按当前选择刷新工具条。勾选时只调它，不重绘列表。
     *
     * 数字不走 t() 的插值——手机端/浏览器端所有既有面板都只有 t(key) 这一种
     * 调用形态，没有 count()/values 插值那一套（那是 app.js 顶层聊天区独有的
     * {one, other} 复数形态）；数字直接拼在 JS 侧，翻译只负责后缀那几个字。
     */
    function syncToolbar() {
        if (!toolbar || !countEl) return;
        toolbar.hidden = selected.size === 0;
        countEl.textContent = t('app.favorites.selected', { count: selected.size });
    }

    /** 清空选择。同样就地更新，不重绘。 */
    function clearSelection() {
        selected.clear();
        rowsById.forEach((el) => {
            if (el.getAttribute('aria-selected') === null) return;
            el.setAttribute('aria-selected', 'false');
        });
        syncToolbar();
    }

    function renderLoaded() {
        toolbar.hidden = true; // renderList 会按 selected 重建行；下面按真实选中数收尾
        buildChips();
        renderList();
        syncToolbar();
    }

    function render() {
        if (!listHost) return;
        if (phase === 'disabled') { renderDisabled(); return; }
        if (phase === 'error') { renderError(); return; }
        if (phase === 'loading') { renderLoading(); return; }
        renderLoaded();
    }

    // ── 数据加载 ─────────────────────────────────────────────────────────────

    async function load() {
        phase = 'loading';
        try {
            const res = await fetch('/api/favorites', { credentials: 'same-origin' });
            if (res.status === 404) {
                phase = 'disabled';
                render();
                return;
            }
            if (!res.ok) {
                phase = 'error';
                render();
                return;
            }
            const data = await res.json();
            groups = Array.isArray(data.groups) ? data.groups : [];
            items = Array.isArray(data.items) ? data.items : [];
            itemsById.clear();
            items.forEach((item) => itemsById.set(item.id, item));
            selected.clear();
            phase = 'loaded';
            render();
        } catch (e) {
            phase = 'error';
            render();
        }
    }

    function refresh() {
        load();
    }

    // D4：手机在会话中途切换收藏开关时，app.js 的 applyPeerAppearance 会把结果
    // 发布到 body 的 data-favorite-enabled 上（settings_changed 事件）。这里不让
    // app.js 反过来知道面板存在，而是观察它已经发布出来的属性，与 panel-settings.js
    // 观察 data-app-version/data-timestamps 是同一手法。变化时重新拉取——
    // 属性本身只是提示，真相仍然是服务端那次 GET 的状态码。
    const PUBLISHED_ATTRS = ['data-favorite-enabled'];

    function observeFavoriteEnabled() {
        if (typeof MutationObserver !== 'function') return;
        new MutationObserver(() => { load(); }).observe(document.body, {
            attributes: true,
            attributeFilter: PUBLISHED_ATTRS,
        });
    }

    function mount(root) {
        // 幂等：文件末尾会对 #view-favorites 自挂载一次（跟 panel-settings.js
        // 同一手法），测试/调用方可能再显式调用一次 mount(同一个 root)——
        // 不加这道闩，第二次调用会把 fetch、事件监听、MutationObserver 全部
        // 重复注册一遍。
        if (!root || viewRoot === root) return;
        viewRoot = root;
        refreshBtn = document.getElementById('fav-refresh');
        searchInput = document.getElementById('fav-search');
        chipsHost = document.getElementById('fav-chips');
        listHost = document.getElementById('fav-list');
        toolbar = document.getElementById('fav-toolbar');
        countEl = document.getElementById('fav-count');
        clearBtn = document.getElementById('fav-clear');
        saveSelectedBtn = document.getElementById('fav-save-selected');
        if (!listHost || !chipsHost || !toolbar) return;

        // 排序：默认按收藏时间倒序（= 本版之前 DAO 的 createdAt DESC）。
        favSort = sorter.load(SORT_STORAGE_KEY, { key: 'TIME', desc: true });
        sortMenuEl = document.getElementById('fav-sort-menu');
        const sortTrigger = document.getElementById('fav-sort');
        if (sortTrigger && sortMenuEl) {
            // 菜单在 HTML 里带 hidden（不然首帧会平铺在头部）；交给定位层时撤掉。
            sortTrigger.addEventListener('click', function () {
                if (menus.isOpen()) { menus.close(); return; }
                sortMenuEl.hidden = false;
                menus.open(sortTrigger, sortMenuEl);
            });
        }
        if (sortMenuEl) {
            Array.prototype.forEach.call(sortMenuEl.children, function (item) {
                item.addEventListener('click', function () {
                    favSort = sorter.tap(favSort, item.getAttribute('value'));
                    sorter.save(SORT_STORAGE_KEY, favSort);
                    syncSortMenu();
                    render();
                    // mdui-dropdown 原本免费给的：点了就收起。
                    menus.close();
                });
            });
            syncSortMenu();
        }

        if (refreshBtn) refreshBtn.addEventListener('click', () => { refresh(); });
        if (searchInput) {
            searchInput.addEventListener('input', () => {
                query = searchInput.value;
                if (phase === 'loaded') render();
            });
        }
        if (clearBtn) clearBtn.addEventListener('click', () => { clearSelection(); });
        if (saveSelectedBtn) saveSelectedBtn.addEventListener('click', () => { saveSelected(); });

        observeFavoriteEnabled();

        if (window.flikkyI18n) {
            // onChange 订阅时会立即用当前语言调用一次监听器——这就是初次渲染
            // （画出 phase==='loading' 的初始态），不要在这之外再手动调一次
            // render，否则面板会渲染两遍（D1）。
            window.flikkyI18n.onChange(() => render());
        }
        load();
    }

    window.flikkyPanels = window.flikkyPanels || {};
    window.flikkyPanels.favorites = { mount, refresh, filterFavorites, sortFavorites };

    mount(document.getElementById('view-favorites'));
})();
