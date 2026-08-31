/* ============================================================================
 * 文件面板：手机共享存储的远程浏览。形状照 panel-favorites.js——
 * window.flikkyPanels.files.mount(root) 是唯一入口，脚本末尾自己 mount 一次。
 *
 * 本任务（plan Task 6）只建骨架：面板壳 + 空态/加载态 + 对外的 render 入口。
 * 列表、面包屑、下载与错误态分别在 Task 7 / Task 8 填。
 *
 * 与收藏面板的两处刻意分歧：
 *   1. 骨架整块由 JS 建（像 panel-settings.js），不写进 app.html。收藏面板的静态
 *      骨架是因为搜索框/工具条那类「壳」不随数据变化；这里连面包屑都随路径变，
 *      静态化只会把契约摊到两个文件里。
 *   2. 入口的显隐不由本文件管——那是 app.js 的 applyStorageBrowsing（主开关驱动）。
 *      面板只管「被显示的时候画什么」。
 *
 * 所有文本一律 textContent（红线：禁 innerHTML）。图标一律 data-icon + aria-hidden，
 * 绝不 textContent 写字形——那会把连字放回 DOM 文本，手机长按又能选中。
 * ==========================================================================*/
(function () {
    'use strict';

    const t = (key, values) => (window.flikkyI18n ? window.flikkyI18n.t(key, values) : key);

    /** 当前相对路径。只存内存：不敏感，但也没有跨会话价值，且重连必须回根目录。 */
    let currentPath = '';

    /**
     * 主开关是否开启。**默认 false**：脚本加载时还没收到 peer-info，
     * 此刻任何请求都会在开关关闭时撞上 404，并被 handleFailure 报成「目录没了」
     * ——那正是装机验收 Screenshot_1 里那条 toast 的来路。
     */
    let enabled = false;

    /**
     * 是否已经成功加载过一次。
     *
     * handleFailure 用它区分「用户导航到的目录没了」与「面板还没成功过」：
     * 后者说「这个位置已经不存在了」毫无意义——用户还没导航过任何位置。
     */
    let hasLoadedOnce = false;

    /**
     * 请求序号。每次 load 自增，只有**最新**那个序号的响应允许落地。
     *
     * 服务端列举大目录要几百毫秒（listFiles + 每条 stat + 每个子目录一次 readdir 算项数）。
     * 期间用户可能已经点进别的目录，而 fetch 的完成顺序不保证与发起顺序一致——
     * 旧响应后到就会把用户已经离开的目录重新画上来。App 端用协程取消解决，
     * 浏览器端用序号：比 AbortController 更朴素，且**即使请求已发出也一定不会误画**。
     */
    let requestSeq = 0;

    /** 正在加载的目标路径。有值时列表区画进度条，用户点了立刻有反应。 */
    let loadingPath = null;

    /**
     * 已勾选的文件相对路径。**跨目录累积**，与 App 端同语义（上游 D5）。
     * 只装文件：目录行单击是「进入」，不参与勾选。
     */
    const selected = new Set();

    let root = null;
    let bodyEl = null;
    let toolbarEl = null;
    let countEl = null;

    function icon(name) {
        const el = document.createElement('span');
        el.className = 'material-symbols-outlined';
        el.dataset.icon = name;
        el.setAttribute('aria-hidden', 'true');
        return el;
    }

    function buildShell(container) {
        container.textContent = '';

        const head = document.createElement('header');
        head.className = 'fk-panel-head';

        const title = document.createElement('h1');
        title.className = 'fk-panel-title';
        title.textContent = t('app.files.title');
        head.appendChild(title);

        const collapse = document.createElement('button');
        collapse.type = 'button';
        collapse.className = 'fk-icon-btn fk-panel-collapse';
        collapse.setAttribute('aria-label', t('app.settings.collapse'));
        collapse.appendChild(icon('close'));
        head.appendChild(collapse);

        container.appendChild(head);

        bodyEl = document.createElement('div');
        bodyEl.className = 'fk-panel-body flikky-scroll';
        container.appendChild(bodyEl);

        // 批量下载工具条。**与收藏面板同一套外观与槽位顺序**（计数 → 清除 → 主操作，
        // 主操作在最右且用 --filled 变体），复用 .fk-toolbar / .fk-toolbar-count /
        // .fk-icon-btn --- 收藏那份是写在 app.html 里的静态骨架，这里随面板一起建，
        // 但类名与顺序逐个对齐，不新写 CSS。
        toolbarEl = document.createElement('div');
        toolbarEl.className = 'fk-toolbar';
        toolbarEl.hidden = true;

        countEl = document.createElement('span');
        countEl.className = 'fk-toolbar-count';
        toolbarEl.appendChild(countEl);

        const clear = document.createElement('button');
        clear.type = 'button';
        clear.className = 'fk-icon-btn';
        clear.setAttribute('aria-label', t('app.files.clear'));
        clear.appendChild(icon('close'));
        clear.addEventListener('click', () => {
            selected.clear();
            render(lastState);
        });
        toolbarEl.appendChild(clear);

        const save = document.createElement('button');
        save.type = 'button';
        save.className = 'fk-icon-btn fk-icon-btn--filled';
        save.setAttribute('aria-label', t('app.files.saveSelected'));
        save.appendChild(icon('download'));
        save.addEventListener('click', () => saveSelected());
        toolbarEl.appendChild(save);

        container.appendChild(toolbarEl);
    }

    /**
     * 逐个触发所选文件的下载，与收藏面板的批量保存同一做法
     * （浏览器首次会问「允许下载多个文件」，属预期）。
     *
     * 发完**不清空选择**：与收藏一致，用户可能想再存一遍到别的位置。
     * 名字从当前列表里取；取不到就用路径末段兜底（列表已翻页到别处时）。
     */
    function saveSelected() {
        const byPath = new Map();
        if (lastState && Array.isArray(lastState.entries)) {
            lastState.entries.forEach((e) => byPath.set(childPath(e), e.name));
        }
        selected.forEach((p) => {
            downloadOne(p, byPath.get(p) || p.split('/').pop());
        });
    }

    /** 按当前选择刷新工具条。选中数为 0 时整条隐藏。 */
    function syncToolbar() {
        if (!toolbarEl || !countEl) return;
        toolbarEl.hidden = selected.size === 0;
        // 数字直接拼在 JS 侧：面板一律只用 t(key) 这一种调用形态（与收藏同）。
        countEl.textContent = t('app.files.selected', { count: selected.size });
    }


    /** 中性提示（加载中 / 空文件夹）。错误与引导态在 Task 8 接。 */
    function renderNotice(host, key) {
        const p = document.createElement('p');
        p.className = 'fk-panel-notice';
        p.textContent = t(key);
        host.appendChild(p);
    }

    // ---- 面包屑 -------------------------------------------------------------

    /**
     * 从**左侧**折叠：首级恒显示、中间折叠为一个展开控件、末两级恒显示。
     * 从右侧折叠会藏掉「我在哪」，方向反了。
     *
     * 返回的每一项是 { name, path }；name 为 null 表示根（渲染成「内部存储」）。
     */
    function breadcrumbSegments(relativePath) {
        const parts = relativePath ? relativePath.split('/').filter(Boolean) : [];
        const all = [{ name: null, path: '' }].concat(
            parts.map((name, i) => ({ name: name, path: parts.slice(0, i + 1).join('/') })),
        );
        if (all.length <= 4) return { head: all, collapsed: [], tail: [] };
        return { head: [all[0]], collapsed: all.slice(1, all.length - 2), tail: all.slice(-2) };
    }

    function crumbLabel(seg) {
        return seg.name === null ? t('app.files.root') : seg.name;
    }

    function renderCrumb(list, seg, isCurrent) {
        const li = document.createElement('li');
        if (isCurrent) {
            // 已经在这一级了，不该是个可点的东西。aria-current 是导航语境的正确属性。
            const span = document.createElement('span');
            span.className = 'fk-crumb';
            span.setAttribute('aria-current', 'page');
            span.textContent = crumbLabel(seg);
            li.appendChild(span);
        } else {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'fk-crumb';
            btn.textContent = crumbLabel(seg);
            btn.addEventListener('click', () => navigate(seg.path));
            li.appendChild(btn);
            const sep = icon('chevron_right');
            sep.classList.add('fk-crumb-sep');
            li.appendChild(sep);
        }
        list.appendChild(li);
    }

    function renderBreadcrumb(host, relativePath) {
        const nav = document.createElement('nav');
        nav.className = 'fk-crumbs';
        nav.setAttribute('aria-label', t('app.files.breadcrumb'));
        const list = document.createElement('ol');
        nav.appendChild(list);

        const seg = breadcrumbSegments(relativePath);
        // 当前级 = 整条路径的最后一级，按 path 比对判定。
        // 曾写成 `flat.length === 1`，那只在根目录成立，多级路径下没有任何一级被标成当前。
        const shown = seg.head.concat(seg.tail);
        const currentPathOfCrumb = shown[shown.length - 1].path;
        const isCurrent = (x) => x.path === currentPathOfCrumb;
        seg.head.forEach((s) => renderCrumb(list, s, isCurrent(s)));
        if (seg.collapsed.length) {
            // 折叠控件本身是一级：点它回到被折叠区间的最后一级（最近的祖先）。
            const li = document.createElement('li');
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'fk-crumb fk-crumb--more';
            btn.textContent = '…';
            btn.setAttribute('aria-label', t('app.files.breadcrumbMore'));
            const nearest = seg.collapsed[seg.collapsed.length - 1];
            btn.addEventListener('click', () => navigate(nearest.path));
            li.appendChild(btn);
            const sep = icon('chevron_right');
            sep.classList.add('fk-crumb-sep');
            li.appendChild(sep);
            list.appendChild(li);
        }
        seg.tail.forEach((s) => renderCrumb(list, s, isCurrent(s)));
        host.appendChild(nav);
    }

    // ---- 列表 ---------------------------------------------------------------

    /** 大小走 app.js 导出的唯一格式化器；本文件不再造第四份（见文件头注释）。 */
    function formatSize(bytes) {
        const f = window.flikky && window.flikky.formatSize;
        return typeof f === 'function' ? f(bytes) : String(bytes);
    }

    function subtitleFor(entry) {
        if (entry.restricted) return t('app.files.restrictedRow');
        const when = entry.mtime ? new Date(entry.mtime).toLocaleString() : '';
        if (entry.isDir) {
            const n = typeof entry.childCount === 'number' ? entry.childCount : null;
            return n === null ? when : t('app.files.itemCount', { count: n });
        }
        const size = typeof entry.size === 'number' ? formatSize(entry.size) : '';
        return size && when ? size + ' · ' + when : (size || when);
    }

    /**
     * 行首视觉。**与收藏行同一个类** `.fk-item-lead`（secondary-container 底 +
     * 官方 cookie 异形容器），不是裸图标。
     *
     * 首版用的是 `fk-item-lead--plain`（无底色、无容器），于是文件面板成了全站唯一
     * 一处「行首是光秃秃一个图标」的列表，与收藏页并排一看就不是一套东西
     * （装机验收 Screenshot_3）。「复用」的标准是视觉零差异。
     *
     * 沙箱目录用 lock 图标但保留同一个容器：占位一致，headline 起点才不会左右跳。
     */
    function leadFor(entry) {
        const wrap = document.createElement('span');
        wrap.className = 'fk-item-lead';
        // 目录用 folder；文件的分类图标取 app.js 导出的唯一事实源，
        // 面板不许自带第二张 mime→图标映射表。
        const name = entry.restricted
            ? 'lock'
            : (entry.isDir
                ? 'folder'
                : ((window.flikky && window.flikky.fileSymbolName)
                    ? window.flikky.fileSymbolName(entry.mime)
                    : 'draft'));
        wrap.appendChild(icon(name));
        return wrap;
    }

    function downloadUrl(relativePath) {
        return '/api/storage/file?path=' + encodeURIComponent(relativePath);
    }

    function childPath(entry) {
        return currentPath ? currentPath + '/' + entry.name : entry.name;
    }

    /**
     * 一行。结构与 `panel-favorites.js` 的行**逐个槽位对齐**：
     * `.fk-item` > `.fk-item-lead` + `.fk-item-text`(title/sub) + `.fk-item-trail`。
     * 文件行的 trail 是 `.fk-check` + `.fk-icon-btn`，与收藏文件行完全一致。
     *
     * 文件行**单击整行 = 勾选**（与收藏同交互，也与 App 端一致），
     * 行尾下载按钮 `stopPropagation` 以免顺手勾上。目录行单击 = 进入，不参与勾选。
     */
    function renderRow(host, entry) {
        const p = childPath(entry);
        const isFile = !entry.isDir && !entry.restricted;
        const row = document.createElement('div');
        row.className = 'fk-item';
        if (entry.restricted) row.setAttribute('aria-disabled', 'true');
        // 多选语义用 aria-selected（列表行的正确属性；导航项才是 aria-current）。
        if (isFile) row.setAttribute('aria-selected', selected.has(p) ? 'true' : 'false');

        row.appendChild(leadFor(entry));

        const text = document.createElement('span');
        text.className = 'fk-item-text';
        const title = document.createElement('span');
        title.className = 'fk-item-title';
        title.textContent = entry.name;
        const sub = document.createElement('span');
        sub.className = 'fk-item-sub';
        sub.textContent = subtitleFor(entry);
        text.appendChild(title);
        text.appendChild(sub);
        row.appendChild(text);

        const trail = document.createElement('span');
        trail.className = 'fk-item-trail';
        // restricted 行既无 chevron 也无下载按钮：系统不给读，摆任何入口都是骗人。
        if (entry.restricted) {
            row.appendChild(trail);
            host.appendChild(row);
            return;
        }
        if (entry.isDir) {
            trail.appendChild(icon('chevron_right'));
            row.addEventListener('click', () => navigate(p));
        } else {
            const check = document.createElement('span');
            check.className = 'fk-check';
            check.appendChild(icon('check'));
            trail.appendChild(check);

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'fk-icon-btn';
            // 可访问名必须含文件名——读屏连续听到十个「下载」无法分辨。
            btn.setAttribute('aria-label', t('app.files.download', { name: entry.name }));
            btn.appendChild(icon('download'));
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                downloadOne(p, entry.name);
            });
            trail.appendChild(btn);

            row.addEventListener('click', () => {
                if (selected.has(p)) selected.delete(p); else selected.add(p);
                render(lastState);
            });
        }
        row.appendChild(trail);
        host.appendChild(row);
    }

    /** 单个下载。`<a download>` 直连鉴权同源流式路由，不走 Blob。 */
    function downloadOne(relativePath, name) {
        const a = document.createElement('a');
        a.href = downloadUrl(relativePath);
        a.setAttribute('download', name);
        a.click();
    }

    /**
     * 加载中：面包屑照 [target] 先画出来（点击的即时反馈），列表区换成进度条。
     *
     * 不留旧列表：把上一个目录的内容画在新目录的面包屑下面是在骗人。
     * 进度用 mdui 的 linear-progress（外壳、无障碍属性由库负责）。
     */
    function renderLoading(target) {
        if (!bodyEl) return;
        bodyEl.textContent = '';
        renderBreadcrumb(bodyEl, target || '');
        const bar = document.createElement('mdui-linear-progress');
        bar.className = 'fk-files-progress';
        bar.setAttribute('aria-label', t('app.files.loading'));
        bodyEl.appendChild(bar);
    }

    function render(state) {
        if (!bodyEl) return;
        bodyEl.textContent = '';
        if (!state || !Array.isArray(state.entries)) {
            renderNotice(bodyEl, 'app.files.loading');
            syncToolbar();
            return;
        }
        currentPath = typeof state.path === 'string' ? state.path : '';
        renderBreadcrumb(bodyEl, currentPath);
        if (state.entries.length === 0) {
            renderNotice(bodyEl, 'app.files.empty');
            syncToolbar();
            return;
        }
        const list = document.createElement('div');
        // .fk-group 是收藏面板用的那个连接列表组（组间距 + 首尾外圆角 + 按下挤压）。
        // 首版写的是 `.fk-list`，而那个类**在 panels.css 里根本不存在**——
        // 于是只剩 .fk-item 的内圆角，整块看起来是一片扁平灰板（Screenshot_3）。
        // 类名拼错不会报错、不会转红，只会静默退化，与 D31 记的「缺失的 CSS 自定义属性
        // 静默降级」同一形状。守卫见 panel-files.test.js 的「行样式复用收藏那一套」。
        list.className = 'fk-group fk-files-list';
        state.entries.forEach((entry) => renderRow(list, entry));
        bodyEl.appendChild(list);
        syncToolbar();
    }

    // ---- 引导态 -------------------------------------------------------------

    /**
     * 需要用户去别处操作的状态（缺权限、系统限制）。
     *
     * **不给「重试」按钮**：用户要做的事在手机上，给了按钮会让人以为点它能解决。
     * 也不弹 snackbar —— 这不是一次失败，是一个需要用户离开浏览器去处理的状态。
     */
    function renderGuidance(iconName, titleKey, bodyKey) {
        if (!bodyEl) return;
        bodyEl.textContent = '';
        const box = document.createElement('div');
        box.className = 'fk-guidance';
        const ic = icon(iconName);
        ic.classList.add('fk-guidance-icon');
        box.appendChild(ic);
        const h = document.createElement('p');
        h.className = 'fk-guidance-title';
        h.textContent = t(titleKey);
        box.appendChild(h);
        const b = document.createElement('p');
        b.className = 'fk-guidance-body';
        b.textContent = t(bodyKey);
        box.appendChild(b);
        bodyEl.appendChild(box);
    }

    function notifyError(text) {
        if (window.flikky && typeof window.flikky.showError === 'function') {
            window.flikky.showError(text);
        }
    }

    // ---- 数据 ---------------------------------------------------------------

    /** 最后一次成功的列表。失败时用它重绘，绝不把面板留成空白。 */
    let lastState = null;

    /**
     * 处理一次失败的加载。
     *
     * **本函数绝不发新请求。** plan 原案是「400 退回根目录并重拉 / 404 重拉当前目录」，
     * 那两条都会死循环：目录没了，重拉还是 404，再重拉……根目录本身 400 时同理。
     * 正确做法是「报错 + 用内存里最后一次成功的列表重绘」，新请求只由用户动作触发。
     */
    function handleFailure(status, code) {
        if (status === 403 && code === 'storage_permission_required') {
            renderGuidance('folder_off', 'app.files.needPermission', 'app.files.needPermissionHow');
            return;
        }
        if (status === 403 && code === 'storage_restricted') {
            renderGuidance('lock', 'app.files.restricted', 'app.files.restrictedWhy');
            return;
        }
        if (status === 400) {
            // 客户端记着一个非法路径：退回最后一次成功的位置，别停在非法路径上。
            notifyError(t('app.files.badPath'));
            currentPath = lastState && typeof lastState.path === 'string' ? lastState.path : '';
        } else {
            // 404 及其它：这一处没了，但你还在原来的位置。
            //
            // 只有**导航过之后**才说得通。面板还没成功加载过时说「这个位置已经不存在了」
            // 毫无意义——用户还没导航到任何位置（Screenshot_1 就是这个状态）。
            // 那种情况下按引导态处理，不弹 snackbar。
            if (!hasLoadedOnce) {
                renderGuidance('folder_off', 'app.files.unavailable', 'app.files.unavailableWhy');
                return;
            }
            notifyError(t('app.files.gone'));
        }
        render(lastState);
    }

    function navigate(relativePath) {
        load(typeof relativePath === 'string' ? relativePath : '');
    }

    async function load(relativePath) {
        // 开关关闭时一律不请求。这是缺陷 1b 的正面修法：服务端按 D33 返回 404
        // 且刻意不带 code（不暴露「功能存在但被关」），所以客户端分不清
        // 「功能被关」与「路径不存在」——那就别让它撞上。
        if (!enabled) return;
        const target = relativePath || '';
        const seq = ++requestSeq;
        // 立即前进：面包屑先动、列表区画进度。大目录里服务端要几百毫秒，
        // 没有这一步用户点了什么都不变，以为没点上（App 端同一个问题的浏览器版）。
        loadingPath = target;
        renderLoading(target);
        try {
            const r = await fetch(
                '/api/storage/list?path=' + encodeURIComponent(target),
                { credentials: 'same-origin' },
            );
            // 过期响应一律丢弃。这一条要在**任何**分支之前判——包括失败分支，
            // 否则一个旧目录的 404 会把用户已经打开的新目录报成「不存在了」。
            if (seq !== requestSeq) return;
            loadingPath = null;
            if (!r.ok) {
                let code = '';
                try {
                    const body = await r.json();
                    code = body && typeof body.code === 'string' ? body.code : '';
                } catch (e) { /* 非 JSON 正文（400 就没有正文），当作无 code */ }
                handleFailure(r.status, code);
                return;
            }
            lastState = await r.json();
            currentPath = typeof lastState.path === 'string' ? lastState.path : target;
            hasLoadedOnce = true;
            render(lastState);
        } catch (e) {
            if (seq !== requestSeq) return;
            loadingPath = null;
            // 断线：保留最后一次列表，不清空——清空会让用户以为文件都没了。
            notifyError(t('app.files.offline'));
            render(lastState);
        }
    }

    /**
     * 建壳，**不发请求**。
     *
     * 这个函数在脚本末尾被调用一次，也就是「页面加载完」那一刻——那时 peer-info
     * 还没到，主开关状态未知。在这里 load() 等于「不管开关一律请求一次」，
     * 开关关闭时就是一条 404 加一句「这个位置已经不存在了」。
     * 第一次请求交给 setEnabled(true) 发起。
     */
    function mount(container) {
        if (!container) return;
        root = container;
        buildShell(root);
        render(lastState);
        if (window.flikkyI18n) {
            window.flikkyI18n.onChange(() => {
                buildShell(root);
                render(lastState);
            });
        }
    }

    /**
     * 主开关的唯一入口，由 app.js 的 applyStorageBrowsing 调用。
     *
     * 开 → 若还没加载过就拉根目录（重复调用不会重复请求）。
     * 关 → 丢掉缓存的列表并回到根目录。不保留是刻意的：重新开启时可能已经换了手机、
     *      换了授权状态，把上一次的目录画出来会让用户以为那些文件还在。
     */
    function setEnabled(next) {
        const on = !!next;
        if (on === enabled) return;
        enabled = on;
        if (!enabled) {
            lastState = null;
            hasLoadedOnce = false;
            currentPath = '';
            selected.clear();
            render(lastState);
            return;
        }
        load(currentPath);
    }

    window.flikkyPanels = window.flikkyPanels || {};
    window.flikkyPanels.files = {
        mount: mount,
        render: render,
        navigate: navigate,
        setEnabled: setEnabled,
    };
    window.flikky = window.flikky || {};
    window.flikky.renderFilesPanel = render;
    window.flikky.navigateStorage = navigate;
    /** 断线重连后必须回根目录：服务端可能已换手机、换授权状态。 */
    window.flikky.resetStorageBrowser = function () { currentPath = ''; lastState = null; };

    const host = document.getElementById('view-files');
    if (host) mount(host);
})();
