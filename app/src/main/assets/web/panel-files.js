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

    /**
     * 路径 → 行元素。勾选时只改这一行的 `aria-selected`，**不重建 DOM**。
     *
     * 装机验收：「每次选中一个文件项，整个文件列表会闪一次」——因为行的 click
     * 回调调的是 `render(lastState)`，而 render 第一件事就是 `bodyEl.textContent = ''`：
     * 面包屑和每一行全部拆掉重建，`.fk-files-list` 的入场动画也跟着重跑。
     * 勾选一下重建整份 DOM，那就是那一闪。
     */
    const rowElements = new Map();

    /** 当前的 `.fk-group` 列表容器。流式追加往它尾部塞行，不重建。 */
    let listEl = null;

    /** 进度条元素。流式期间一直在，但不挡住已经到达的行。 */
    let progressEl = null;

    /** 上一次建壳时的路径，只用来判断进/退方向。 */
    let shownPath = '';

    /** 建壳时算好、等第一批条目到达才盖上去的方向（'enter' / 'exit'）。 */
    let pendingDir = null;

    /**
     * 当前目录是否**已完整到达**（收到了 done 行）。
     *
     * 全选按钮的门禁：流未结束时「全部」没有确定含义，一个能点的按钮会让用户以为
     * 选中了整个目录，而实际只选中了已到达的那部分（2026-09-02 用户裁决）。
     * 也是页脚在「正在载入 N 项」与「共 N 项」之间切换的依据。
     */
    let listingComplete = false;

    let selectAllBtn = null;
    let selectAllIcon = null;
    let footerEl = null;

    /** 分帧写 aria-selected 的游标；新的一次写入会作废上一次未跑完的。 */
    let markSeq = 0;

    // ── 虚拟化（窗口渲染）─────────────────────────────────────────────────
    //
    // 实测每行 11 个元素节点：10000 行就是 11 万个元素，样式重算与布局每次都要
    // 走一遍 —— 装机验收「加载大目录内存非常高、明显卡顿」就是这个。
    // 所以只渲染视口附近那几十行，DOM 恒定在几百个节点，与目录大小无关。
    //
    // ## 行留在正常流里
    //
    // 第一版把行绝对定位在算出来的 `top` 上。那要求 JS **知道行距** ——
    // 而行距取决于字体、主题、缩放与内容，算错一次就直接毁掉行距
    // （装机验收连报三轮「挨得太近了」，两次测量各错一次）。
    //
    // 现在行留在流里，行距完全由 CSS 的 `gap` 负责 —— 与收藏面板同一个 token、
    // 同一条声明。于是「与收藏面板视觉零差异」是**结构保证**的，不再取决于
    // 我是否算对。滚动区间由上下两个 spacer 撑出来，行距测量只用在那里：
    // 算错也只是滚动条略不准，行本身永远是对的。**同一个测量，
    // 爆炸半径从「毁掉布局」降到「滚动条精度」。**

    /** 视口上下各多渲染几行，滚动时不至于露白。 */
    const VIRTUAL_OVERSCAN = 6;

    /** 量不到布局信息时的兜底行距。只影响滚动区间。 */
    const ROW_PITCH_FALLBACK = 64;

    /** 一行到下一行的距离。从**真正排好的两行**量，每次 sync 自我校正。 */
    let rowPitch = 0;

    /** 当前渲染出来的窗口 [winFrom, winTo)，以及当时的总数。 */
    let winFrom = 0;
    let winTo = 0;
    let winTotal = -1;

    /** 数据下标 → 行元素 / 行路径。复用靠这两张表，不靠推断 DOM 顺序。 */
    const rendered = new Map();
    const renderedPath = new Map();

    /** 撑滚动区间的两个占位块。窗口贴边时对应的那个不存在。 */
    let topSpacer = null;
    let bottomSpacer = null;

    /** 是否已经给滚动容器挂上监听。listEl 会重建，但 bodyEl 不会。 */
    let scrollBound = false;

    /**
     * 目录缓存：`path -> { entries }`。**回退不重新加载。**
     *
     * `Map` 保持插入序，命中时删了再插，于是它天然就是一条 LRU 链，
     * 淘汰只需取 `keys().next()`。
     *
     * ## 只缓存完整的列表
     *
     * 被截断的流（休眠 / Wi-Fi 切换 / 服务端读到一半失败）绝不入缓存。
     * 否则「秒回」会永远回一份残缺的列表，而用户再也见不到完整的那份。
     *
     * ## 刻意不做自动刷新
     *
     * 用户在子目录待久了，父目录可能已经变了。这里**不**自动重取（那会把「秒回」
     * 变回「每次都等」），而是给一个手动刷新按钮 —— 用户裁决 2026-09-03。
     */
    const dirCache = new Map();

    /** `path -> scrollTop`。与列表内容分开存：位置恢复是 UI 层的事。 */
    const scrollMemory = new Map();

    /**
     * 安全边界：两个上限都要卡。
     *
     * 只卡目录数不行 —— 32 个各含一万条的目录一样会撑爆内存；
     * 只卡总条目数也不行 —— 会让极多的小目录把 Map 撑得很长。
     */
    const CACHE_MAX_DIRS = 32;
    const CACHE_MAX_ENTRIES = 20000;

    let cachedEntryCount = 0;

    function cacheDrop(path) {
        const hit = dirCache.get(path);
        if (!hit) return;
        cachedEntryCount -= hit.entries.length;
        dirCache.delete(path);
    }

    function cacheStore(path, entries) {
        cacheDrop(path);
        // 单个目录就超过总上限时不缓存它 —— 存进去会把其他所有目录挤光，
        // 而它自己下次也一定被淘汰，白占一轮。
        if (entries.length > CACHE_MAX_ENTRIES) return;
        dirCache.set(path, { entries: entries.slice() });
        cachedEntryCount += entries.length;
        while (
            dirCache.size > CACHE_MAX_DIRS
            || cachedEntryCount > CACHE_MAX_ENTRIES
        ) {
            const oldest = dirCache.keys().next();
            if (oldest.done || oldest.value === path) break;
            cacheDrop(oldest.value);
        }
    }

    function cacheClear() {
        dirCache.clear();
        scrollMemory.clear();
        cachedEntryCount = 0;
    }

    /** 记下当前显示目录的滚动位置，供回退时恢复。 */
    function rememberScroll() {
        if (!bodyEl || typeof shownPath !== 'string') return;
        // 只在真的画过内容之后记。刚建壳、还没有行的时候 scrollTop 必然是 0，
        // 记下去会把用户在这个目录的真实位置覆盖成 0。
        if (!listEl || listEl.children.length === 0) return;
        scrollMemory.set(shownPath, bodyEl.scrollTop || 0);
    }

    /** 相对路径的层级深度。根为 0。 */
    function depthOf(p) {
        if (!p) return 0;
        return p.split('/').filter(Boolean).length;
    }

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

        // 全选放在**面板头部**，不放选择工具栏里。
        //
        // 工具栏只在 `selected.size > 0` 时出现（与收藏面板一致，那个 parity 是刻意的），
        // 把全选放进去就变成「必须先手动选中一个，才能点全选」——那让这个功能少了一半。
        // 全选是**列表级**动作，头部才是它的位置；取消全选是**选择级**动作，
        // 留在工具栏里（没有选择时它无从谈起）。
        //
        // **只在列表完整到达后可点**：流未结束时「全部」没有确定含义，
        // 一个能点的按钮会让用户以为选中了整个目录（2026-09-02 用户裁决）。
        // 排序下拉。用 mdui 官方组件：D30 定的归属是「需要 scrim / 焦点陷阱 /
        // Esc / ARIA 语义的用 mdui」，下拉菜单四样全占，属于「自己写容易写错」。
        // 排在全选**之前**：排序是「怎么看」，全选是「选什么」。
        const sortDropdown = document.createElement('mdui-dropdown');
        sortDropdown.setAttribute('placement', 'bottom-end');
        const sortTrigger = document.createElement('button');
        sortTrigger.type = 'button';
        sortTrigger.className = 'fk-icon-btn';
        sortTrigger.setAttribute('slot', 'trigger');
        sortTrigger.setAttribute('aria-label', t('app.files.sort'));
        sortTrigger.appendChild(icon('filter_list'));
        sortDropdown.appendChild(sortTrigger);
        sortMenuEl = document.createElement('mdui-menu');
        // 文案 key **显式写出**，不用「前缀 + key.toLowerCase()」拼出来 ——
        // 动态 key 让「两种语言的文案有没有齐」无法静态验证，而那正是
        // web-i18n 那条守卫存在的理由（拼接时它只看得见前缀，于是报缺失）。
        // 注：这段注释里也不能出现那种拼接的字面量，否则守卫会把它当成一个 key。
        const SORT_LABELS = {
            NAME: 'app.files.sortName',
            TIME: 'app.files.sortTime',
            SIZE: 'app.files.sortSize',
        };
        sorter.KEYS.forEach(function (key) {
            const item = document.createElement('mdui-menu-item');
            item.className = 'fk-sort-item';
            item.setAttribute('value', key);
            item.textContent = t(SORT_LABELS[key]);
            item.addEventListener('click', function () { pickSort(key); });
            sortMenuEl.appendChild(item);
        });
        sortDropdown.appendChild(sortMenuEl);
        head.appendChild(sortDropdown);
        syncSortMenu();

        selectAllBtn = document.createElement('button');
        selectAllBtn.type = 'button';
        selectAllBtn.className = 'fk-icon-btn';
        selectAllBtn.disabled = true;
        selectAllIcon = icon('select_all');
        selectAllBtn.appendChild(selectAllIcon);
        selectAllBtn.addEventListener('click', () => {
            // 真实浏览器不会给 disabled 按钮派发 click，所以这一条在生产里走不到。
            // 留着是防「将来有人改了显隐逻辑却忘了 disabled」，并且它是可测的
            // （测试直接 dispatch，绕过 disabled）。
            if (!listingComplete) return;
            if (allHereSelected()) deselectHere(); else selectAll();
        });
        head.appendChild(selectAllBtn);

        // 刷新。**与收藏面板那个逐字同形**（同 .fk-icon-btn、同 refresh 图标、
        // 同槽位——紧挨折叠按钮之前），所以视觉零差异。
        //
        // 目录缓存刻意不做自动刷新（用户在子目录待久了父目录可能已变，自动重取
        // 会把「秒回」变回「每次都等」），代价就是必须给用户一个手动的出口。
        // 它带 force：绕过缓存，真的去网络取。
        const refresh = document.createElement('button');
        refresh.type = 'button';
        refresh.className = 'fk-icon-btn';
        refresh.setAttribute('aria-label', t('app.files.refresh'));
        refresh.appendChild(icon('refresh'));
        refresh.addEventListener('click', () => { load(currentPath, true); });
        head.appendChild(refresh);

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

        // 工具栏这个按钮**换回 close 与它原来的语义**（用户裁决 2026-09-03）：
        // 清空整个选择集（含跨目录攒下的），把工具栏收起来。
        // 上一版把它改成了 deselect 图标，读起来变成了「取消全选」——
        // 而「取消全选」已经是头部那个两态按钮的另一面，重复且含义更窄。
        const clear = document.createElement('button');
        clear.type = 'button';
        clear.className = 'fk-icon-btn';
        clear.setAttribute('aria-label', t('app.files.clear'));
        clear.appendChild(icon('close'));
        clear.addEventListener('click', () => {
            selected.clear();
            // 就地更新：清除一次也不该让整个列表闪。
            markRows();
            syncToolbar();
            syncSelectAll();
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

    /**
     * 全选当前目录里的所有**文件**。
     *
     * 目录不进选择集合（单击目录是「进入」，它也不是能发送/下载的东西），
     * 沙箱条目同理——系统不给读，选中它只会在保存时被跳过。
     *
     * 上万行时的开销：写 Set 是 O(n) 次字符串插入（很快），改 DOM 是 O(n) 次
     * setAttribute。中间不读布局，所以不会触发逐次重排，实测量级是几毫秒。
     * 真正的墙是这批行本身就有上万个 DOM 节点（见 backlog B32：浏览器端无虚拟化）。
     */
    function selectAll() {
        if (!lastState || !Array.isArray(lastState.entries)) return;
        lastState.entries.forEach((e) => {
            if (e.isDir || e.restricted) return;
            selected.add(childPath(e));
        });
        markRows();
        syncToolbar();
        syncSelectAll();
    }

    /** 按当前选择刷新工具条。选中数为 0 时整条隐藏。 */
    function syncToolbar() {
        if (!toolbarEl || !countEl) return;
        toolbarEl.hidden = selected.size === 0;
        // 数字直接拼在 JS 侧：面板一律只用 t(key) 这一种调用形态（与收藏同）。
        // 选择集跨目录保留，所以这个数可能包含当前屏幕上看不到的行。有别处的就
        // 把它写出来 —— 否则用户既不知道那些在哪，也无从判断按下下载会下什么
        // （装机验收 2026-09-03）。判据与 App 端 StorageSelectionScope 同一条：
        // 只算**直接子项**，不是前缀比较（那会把 MusicVideos 也算进 Music，
        // 也分不清直接子项与更深的孙子项）。
        let here = 0;
        selected.forEach((p) => {
            const cut = p.lastIndexOf('/');
            const parent = cut < 0 ? '' : p.slice(0, cut);
            if (parent === currentPath) here += 1;
        });
        const elsewhere = selected.size - here;
        countEl.textContent = elsewhere > 0
            ? t('app.files.selected', { count: selected.size })
                + ' · ' + t('app.files.elsewhere', { count: elsewhere })
            : t('app.files.selected', { count: selected.size });
    }

    /** 当前目录里可被选中的条目（目录与沙箱条目都不算）。 */
    function selectableHere() {
        if (!lastState || !Array.isArray(lastState.entries)) return [];
        return lastState.entries.filter((e) => !e.isDir && !e.restricted);
    }

    /** 当前目录里可选的都已选中？空目录不算「全选」。 */
    function allHereSelected() {
        const here = selectableHere();
        if (here.length === 0) return false;
        return here.every((e) => selected.has(childPath(e)));
    }

    /** 只清**当前目录**的选中项。跨目录攒下的那些不动 —— 那是工具栏 close 的活。 */
    function deselectHere() {
        selectableHere().forEach((e) => selected.delete(childPath(e)));
        markRows();
        syncToolbar();
        syncSelectAll();
    }

    /**
     * 头部两态按钮的三件事：可用性、显隐、当前是哪一态。
     *
     * - **可用性**：列表未完整到达时置禁（流未结束时「全部」没有确定含义）。
     * - **显隐**：当前目录没有可选条目就整个藏起来 —— 一个点了什么都不会发生的
     *   按钮不该占位置（全是子目录的文件夹很常见）。
     * - **两态**：已全选时变成「取消全选」。合成一个按钮是用户裁决：
     *   两个并排的按钮里总有一个是无效操作。
     */
    function syncSelectAll() {
        if (!selectAllBtn) return;
        const here = selectableHere();
        selectAllBtn.hidden = here.length === 0;
        selectAllBtn.disabled = !listingComplete;
        const off = allHereSelected();
        if (selectAllIcon) selectAllIcon.dataset.icon = off ? 'deselect' : 'select_all';
        selectAllBtn.setAttribute(
            'aria-label',
            t(off ? 'app.files.deselect' : 'app.files.selectAll'),
        );
    }

    /**
     * 把 `selected` 刷到行的 `aria-selected` 上，**分帧写**。
     *
     * 为什么不能一把写完：上万行逐个 setAttribute 会触发一次覆盖上万元素的样式重算，
     * 全压在一帧里 —— 装机验收「点击全选会有卡顿」就是这个。逻辑上的选中是瞬时的
     * （Set 插入很快，计数立刻就对），慢的只是 DOM 标记，所以把它摊到几帧上。
     *
     * `markSeq` 让新的一次写入作废上一次未跑完的，否则连点两下会有两条链交替写。
     */
    function markRows() {
        const seq = ++markSeq;
        const all = [];
        rowElements.forEach((el, p) => {
            if (el.getAttribute('aria-selected') !== null) all.push([el, p]);
        });
        const CHUNK = 400;
        let at = 0;
        const step = () => {
            if (seq !== markSeq) return;
            const end = Math.min(at + CHUNK, all.length);
            for (let k = at; k < end; k += 1) {
                const pair = all[k];
                pair[0].setAttribute('aria-selected', selected.has(pair[1]) ? 'true' : 'false');
            }
            at = end;
            if (at < all.length) {
                if (typeof requestAnimationFrame === 'function') requestAnimationFrame(step);
                else step();
            }
        };
        step();
    }

    /**
     * 页脚：**终止标记**。
     *
     * 用户原话：「用户如何知道自己是否看到了全部」。流式加载下这是个真问题——
     * 列表停止生长与「加载完了」在屏幕上长得一样。加载中写「正在载入 N 项」，
     * 完成后写「共 N 项」，两者都给出确定的语义。
     */
    function syncFooter() {
        if (!bodyEl || !listEl) return;
        const n = allEntries().length;
        if (n === 0) {
            if (footerEl && footerEl.parentNode) footerEl.parentNode.removeChild(footerEl);
            footerEl = null;
            return;
        }
        if (!footerEl) {
            footerEl = document.createElement('p');
            footerEl.className = 'fk-files-footer';
            bodyEl.appendChild(footerEl);
        }
        footerEl.textContent = listingComplete
            ? t('app.files.total', { count: n })
            : t('app.files.loadingCount', { count: n });
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
    /**
     * @param index 这一行在**整份列表**里的下标。
     * @param total 整份列表的行数。
     *
     * 首尾外圆角由这两个值决定，**不是** DOM 位置。虚拟化之后 DOM 里只剩视口附近
     * 那几十行，`:first-child` 就不再是「列表第一行」—— 滚到中间时会有一行莫名其妙
     * 带上外圆角。收藏面板整份渲染，继续用结构选择器没问题。
     */
    function renderRow(host, entry, index, total) {
        const p = childPath(entry);
        const isFile = !entry.isDir && !entry.restricted;
        const row = document.createElement('div');
        row.className = 'fk-item';
        if (index === 0) row.classList.add('is-first');
        if (index === total - 1) row.classList.add('is-last');
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
                // 就地更新这一行 + 工具条。绝不调 render()——那会重建整个列表。
                row.setAttribute('aria-selected', selected.has(p) ? 'true' : 'false');
                syncToolbar();
                // 手选到最后一行时头部按钮要翻成「取消全选」，反之翻回来。
                syncSelectAll();
            });
        }
        row.appendChild(trail);
        rowElements.set(p, row);
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
     * 建壳：面包屑 + 进度条 + 空的列表容器。**只在导航时调一次**，
     * 之后所有条目都靠 [appendBatch] 追加。
     *
     * 这个分工是流式的前提，也顺带修掉了「勾选闪一下」——render 不再是唯一入口，
     * 于是勾选可以只改一行，而不是重建整份 DOM。
     */
    function renderShell(path) {
        if (!bodyEl) return;
        // 进/退方向按**层级深度**判，不按字符串长度。
        //
        // 用长度会被名字长短骗：从 DCIM 平移到 Music（都是第一层）算「进入」，
        // 平移到 A 却算「返回」——同一种操作因为名字短了就反向。逼红时发现的。
        // 也不用前缀比较：前缀在「进入 / 返回上一级」上是对的，但平移到同深度的
        // 兄弟目录会一律判成「返回」，而那更像是横向切换，按「进入」更自然。
        const dir = depthOf(path) >= depthOf(shownPath) ? 'enter' : 'exit';
        shownPath = path || '';
        bodyEl.textContent = '';
        rowElements.clear();
        // 新壳 = 还没有任何属于它的数据。清掉之后 syncVirtual 画不出任何行，
        // 直到头行到达把 viewEntries 接上 —— 那次 scroll 事件因此无害。
        setViewEntries([]);
        // 新目录一律从顶部开始。
        //
        // 摘空子节点**不保证**浏览器把 scrollTop 归零：只有新内容比当前滚动偏移
        // 还矮时它才被动夹回去。进入一个同样很高的目录时会停在半路，
        // 用户看到的是列表中段而不是开头。回退时的位置恢复在 renderFromCache 里,
        // 发生在内容铺好之后，所以这里归零不会把它覆盖掉。
        bodyEl.scrollTop = 0;
        // 页脚与 DOM 同生同死：忘了清会让引用指向已经摘掉的节点。
        footerEl = null;
        renderBreadcrumb(bodyEl, path || '');
        progressEl = document.createElement('mdui-linear-progress');
        progressEl.className = 'fk-files-progress';
        progressEl.setAttribute('aria-label', t('app.files.loading'));
        bodyEl.appendChild(progressEl);
        listEl = document.createElement('div');
        // 刻意**不带 .fk-group**：那条规则用 `:first-child` / `:last-child` 定外圆角，
        // 而窗口化之后第一个渲染出来的行往往不是列表首行。首尾由 renderRow 按数据
        // 下标打 `.is-first` / `.is-last`（收藏面板整份渲染，继续用 .fk-group）。
        listEl.className = 'fk-files-list fk-list-in';
        // 方向**先存着**，等第一批条目到达再盖到元素上（见 appendBatch）。
        // 在这里就盖等于让 224ms 的横移演给一个空盒子看：容器刚建好时列表是空的，
        // 第一批要等 fetch + 服务端扫描才到，动画早跑完了。
        pendingDir = dir;
        // 新目录 = 新窗口。listEl 是新建的，所以两张索引表与两个占位块
        // 都握着已经从文档里摘掉的东西 —— 一起复位。
        winFrom = 0;
        winTo = 0;
        winTotal = -1;
        rendered.clear();
        renderedPath.clear();
        topSpacer = null;
        bottomSpacer = null;
        bodyEl.appendChild(listEl);
        syncToolbar();
    }

    /** 进度条的显隐。流结束时收掉；空目录时补一句「这个文件夹是空的」。 */
    function setBusy(busy) {
        // 用类而不是 `hidden`：全局 reset 把 `[hidden]` 变成 display:none !important，
        // 那会让收回过渡完全不发生。收起态由 CSS 负责高度、外边距、透明度与
        // visibility（后者延到过渡结束才切，以便离开无障碍树）。
        if (progressEl) progressEl.classList.toggle('is-done', !busy);
        if (!busy && listEl && allEntries().length === 0) {
            renderNotice(bodyEl, 'app.files.empty');
        }
    }

    function render(state) {
        if (!bodyEl) return;
        bodyEl.textContent = '';
        // 索引与 DOM 同生同死：忘了清会让 rowElements 一直握着已经从文档里摘掉的
        // 元素（内存泄漏），而且清除按钮会去改一批看不见的行。
        rowElements.clear();
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
        list.className = 'fk-files-list fk-list-in';
        state.entries.forEach((entry, i) => renderRow(list, entry, i, state.entries.length));
        bodyEl.appendChild(list);
        listEl = list;
        progressEl = null;
        syncToolbar();
        syncFooter();
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
     * **正在显示的那个目录**的条目。虚拟化只读这一份。
     *
     * 与 `lastState` 不同：`lastState` 是「最后一次成功的列举」，加载期间它仍然是
     * **上一个目录**的 —— 400 的退回逻辑与选择集都需要它保持这个含义。
     * 虚拟化读了 lastState 就出了 2026-09-03 那个最严重的缺陷：`renderShell` 把
     * `scrollTop` 归零会触发一次 scroll 事件（异步，落在新壳就位之后），
     * 监听里调 syncVirtual，于是把**上一个目录**的行画进了新壳；随后子目录的批次
     * 又因为「这些下标已经渲染过了」而复用它们，再也不会被替换。
     *
     * 头行到达（或缓存命中）时指向那一份，`renderShell` 里清空。
     * 这是本版第三次同一个教训：**一份状态必须带着「我属于谁」。**
     */
    let viewEntries = [];

    /**
     * 过滤 + 排序之后的结果。**虚拟化只读这一份。**
     *
     * 不能把过滤排序放进 `allEntries()`：它被 `syncVirtual` 在**每个 scroll 事件**
     * 里调用，一万项每次滚动重排一遍，虚拟化的意义就没了。
     */
    let shownEntries = [];

    /**
     * 排序内核（`sort.js`）。生产环境由 `app.html` 的 script 顺序保证它先加载。
     *
     * 缺失时回落成「什么都不排、什么都不滤」而不是抛 —— 排序是**视图偏好**，
     * 它不该有能力让整个文件面板打不开。这也让面板保持可单独加载
     * （十几个既有测试只加载 panel-files.js，让它们逐个改 harness 是错的修法：
     * 那等于把一个真实的依赖问题摊派给测试）。
     */
    const sorter = window.flikkySort || {
        KEYS: [],
        natural: function (k) { return { key: k, desc: false }; },
        format: function (s) { return s.key + ':' + (s.desc ? 'desc' : 'asc'); },
        parse: function () { return null; },
        tap: function (s, k) { return { key: k, desc: false }; },
        sortEntries: function (e) { return e || []; },
        filterEntries: function (e) { return e || []; },
        load: function (_k, fallback) { return fallback; },
        save: function () {},
    };

    /** 当前排序。初值从 localStorage 读 —— 非敏感的纯展示状态，不触红线。 */
    const SORT_STORAGE_KEY = 'flikky_sort_files';
    let sortSpec = sorter.load(SORT_STORAGE_KEY, sorter.natural('NAME'));

    /** 当前关键词（Task 13 用；recomputeShown 需要它，所以在这里声明）。 */
    let query = '';

    /** 排序菜单的 DOM 引用，syncSortMenu 用它重打方向箭头。 */
    let sortMenuEl = null;

    /**
     * `viewEntries` 的**唯一写入口**。别的地方一律调它，不要直接赋值。
     *
     * 理由：`shownEntries` 是派生的，源变了必须跟着重算。分开写迟早有一处忘记，
     * 而症状是「屏幕上还是上一个目录的行」—— 静默、没有异常。
     * v1.20.0 连着六个缺陷都是这个形状（见 retrospective）。
     * 守卫见 `panel-files-sort.test.js` 的第一条。
     */
    function setViewEntries(list) {
        viewEntries = list || [];
        recomputeShown();
    }

    function recomputeShown() {
        shownEntries = sorter.sortEntries(
            sorter.filterEntries(viewEntries, query),
            sortSpec,
        );
    }

    /**
     * 切换排序。**本地重排，不重新请求** —— 条目已经全在内存里，
     * 回服务端要重扫一个可能上万项的目录（spec §6.2）。
     */
    function pickSort(key) {
        sortSpec = sorter.tap(sortSpec, key);
        sorter.save(SORT_STORAGE_KEY, sortSpec);
        // 目录缓存里存的是**旧顺序**，一并作废，否则返回上级会看到旧排序
        // （又一次「一份状态没跟着它的依据一起更新」）。
        cacheClear();
        applyViewChange();
        syncSortMenu();
    }

    /**
     * 顺序或过滤变了之后重建窗口。
     *
     * **绝不走 `renderShell`** —— 那是「换目录」的入口：它重建面包屑、重放方向
     * 横移、清空 viewEntries。这里路径没变，走它等于让列表无故横移一次还丢数据。
     *
     * 回顶放在**窗口复位之后、重建之前**：给一个已滚动过的容器赋 scrollTop 会
     * 触发一次**异步** scroll 事件，而监听里会调 syncVirtual。顺序错了就是
     * 2026-09-03 那个缺陷的翻版（见 renderShell 的注释）。
     *
     * **这个顺序目前没有测试守着**，逼红实测零条红：那次异步 scroll 事件本身
     * 会再调一次 syncVirtual 把窗口修正回来，所以先后顺序只影响**中间一帧**，
     * 最终状态相同 —— 而 mini-dom 只能观察最终状态。真实浏览器上表现为
     * 切换排序时闪一下列表中段。改动这几行时请自己盯住这一点。
     */
    function applyViewChange() {
        recomputeShown();
        dropAllRows();
        winFrom = 0;
        winTo = 0;
        winTotal = -1;
        if (bodyEl) bodyEl.scrollTop = 0;
        syncVirtual(-1);
        syncSelectAll();
        syncToolbar();
        syncFooter();
    }

    /**
     * 给当前键的那一项挂方向箭头，其余项摘掉。
     *
     * 箭头既是选中指示也是方向指示，一个元素说完两件事（Windows / Finder 同一
     * 画法，App 端 SortMenuAction 也是），所以不需要额外的对勾。
     *
     * 摘旧箭头用 `while (firstChild) removeChild` 而不是 `querySelector` ——
     * 后者不在项目共用的那套 DOM 操作里（见 mini-dom 的 KDoc：全项目统一用
     * removeChild 循环清空重建）。用它的话测试替身里旧箭头永远摘不掉，
     * 而真实浏览器里能过 —— 一个只在测试里可见的分叉。
     */
    function syncSortMenu() {
        if (!sortMenuEl) return;
        Array.prototype.forEach.call(sortMenuEl.children, function (item) {
            const isCurrent = item.getAttribute('value') === sortSpec.key;
            // 文本 + 可能的箭头：整个重建，语义最简单。
            const label = item.textContent;
            while (item.firstChild) item.removeChild(item.firstChild);
            item.textContent = label;
            if (!isCurrent) return;
            // 用项目自己的 icon()（Material Symbols 连字）而不是 mdui 的
            // end-icon 属性：后者走 mdui 自带的图标字体，而本项目打包的是
            // 完整的 Material Symbols，靠 content: attr(data-icon) 渲染。
            const dir = icon(sortSpec.desc ? 'arrow_downward' : 'arrow_upward');
            dir.classList.add('fk-sort-dir');
            dir.setAttribute('slot', 'end-icon');
            item.appendChild(dir);
        });
    }

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

    /**
     * 增量追加一批行到已有的列表容器里。
     *
     * 与 render 的分工：render 建壳（面包屑 + 空的列表容器），本函数只往容器尾部
     * 追加。首版没有这个分工，每来一次数据就 `bodyEl.textContent = ''` 重建整份
     * DOM——那既是「勾选闪一下」的来路，也让流式追加根本不可能。
     *
     * [batchStart] 是这一批在整份列表里的起始序号，用来给每行设 `--i`（批内阶梯）。
     */
    /**
     * 从缓存重建当前目录：不发请求，画完立刻把滚动位置放回去。
     *
     * 一次性把行全铺上（而不是分批），因为数据早就在内存里 —— 分批只会把渲染
     * 拆成多帧、总时长不变，还让滚动恢复没法一步到位：恢复 scrollTop 需要内容
     * 已经有完整高度，否则会被夹到当前可滚动范围里。
     */
    function renderFromCache(target, entries) {
        loadingPath = null;
        renderShell(target);
        // **必须在 appendBatch 之前**设好 currentPath 与 lastState。
        //
        // renderRow 是靠 childPath() 拼每一行的路径的，而它读的就是 currentPath。
        // 先建行、后设路径的话，从缓存恢复出来的每一行都带着**上一个目录**的前缀，
        // 点进去请求的是一个不存在的路径 —— 装机验收报的
        // 「返回后再进文件夹必定提示这个位置已经不存在了，面包屑错误，路径混乱」
        // 就是这一处的顺序写反了。
        currentPath = target;
        lastState = { path: target, entries: entries.slice() };
        setViewEntries(lastState.entries);
        if (entries.length > 0) appendBatch(entries, 0, true);
        listingComplete = true;
        hasLoadedOnce = true;
        setBusy(false);
        syncSelectAll();
        syncToolbar();
        syncFooter();
        // 内容已就位、高度已确定，这时候放回滚动位置才不会被夹。
        const at = scrollMemory.get(target);
        if (bodyEl && typeof at === 'number') bodyEl.scrollTop = at;
    }

    /** 全部条目。虚拟化只渲染其中一段，但高度、下标、首尾都按这一份算。 */
    function allEntries() {
        return shownEntries;
    }

    /**
     * 从**真正排好的两行**量行距，每次 sync 自我校正。
     *
     * 不用探针、不读 token、不解析 computed style —— 前两版分别栽在
     * 「探针从未进入正常流」和「把行高与行距分开量再相加」上。量真实相邻两行的
     * `offsetTop` 之差，得到的就是行距本身（已含 CSS 的 gap），
     * 无需知道它由什么构成。
     *
     * 这个值**只用来撑滚动区间**。量不到（没有布局引擎的环境）就退回
     * [ROW_PITCH_FALLBACK]，行距不受影响 —— 那是 CSS 的事。
     */
    function refreshPitch() {
        const a = rendered.get(winFrom);
        const b = rendered.get(winFrom + 1);
        if (!a || !b) return;
        if (typeof a.offsetTop !== 'number' || typeof b.offsetTop !== 'number') return;
        const d = b.offsetTop - a.offsetTop;
        if (d > 0) rowPitch = d;
    }

    /** 占位块。只在窗口没贴到那一边时存在。 */
    function spacer(px) {
        const el = document.createElement('div');
        el.className = 'fk-vspace';
        el.setAttribute('aria-hidden', 'true');
        el.style.setProperty('height', px + 'px');
        return el;
    }

    /** 丢掉一行：DOM、两张索引表、以及勾选用的 path→行 映射都要一起清。 */
    function dropRow(i) {
        const el = rendered.get(i);
        if (el) el.remove();
        const p = renderedPath.get(i);
        if (p !== undefined) rowElements.delete(p);
        rendered.delete(i);
        renderedPath.delete(i);
    }

    function dropAllRows() {
        Array.from(rendered.keys()).forEach(dropRow);
    }

    /**
     * 把窗口对齐到当前滚动位置与数据量。
     *
     * **已经在屏幕上的行一律复用**，只在两端增删。第一版每批都
     * `textContent = ''` 重建整窗，指数分批约 10 批就是重建 10 次 ——
     * 装机验收「加载过程中列表项整体会时不时闪一下」正是这个。
     *
     * @param animateFrom 从这个**数据下标**起的行算「刚到达」，给它们排入场阶梯。
     *   传 -1 表示这次不是新数据（滚动、或从缓存恢复），一律不排。
     */
    function syncVirtual(animateFrom) {
        if (!listEl || !bodyEl) return;
        const entries = allEntries();
        const total = entries.length;
        const pitch = rowPitch || ROW_PITCH_FALLBACK;

        const viewport = bodyEl.clientHeight || pitch * 12;
        const top = bodyEl.scrollTop || 0;
        let from = Math.floor(top / pitch) - VIRTUAL_OVERSCAN;
        if (from < 0) from = 0;
        let to = Math.ceil((top + viewport) / pitch) + VIRTUAL_OVERSCAN;
        if (to > total) to = total;

        // 什么都没变就直接返回。滚动事件很密，而大多数一次滚动并不移动窗口；
        // 不拦的话每个事件都要把窗口里所有行的首尾类重打一遍。
        if (animateFrom < 0 && from === winFrom && to === winTo && total === winTotal) return;

        // 1) 清掉离开窗口的行。窗口整体跳走（拖滚动条）时全清。
        if (to <= winFrom || from >= winTo) {
            dropAllRows();
        } else {
            Array.from(rendered.keys()).forEach(function (i) {
                if (i < from || i >= to) dropRow(i);
            });
        }

        // 2) 上下占位块。窗口贴边时对应的那个直接不存在 ——
        //    留一个零高的块会被 flex 的 gap 额外加上一份间距。
        const above = from;
        const below = total - to;
        if (above > 0) {
            if (!topSpacer) {
                topSpacer = spacer(above * pitch);
                listEl.insertBefore(topSpacer, listEl.children[0] || null);
            } else {
                topSpacer.style.setProperty('height', (above * pitch) + 'px');
            }
        } else if (topSpacer) {
            topSpacer.remove();
            topSpacer = null;
        }
        if (below > 0) {
            if (!bottomSpacer) {
                bottomSpacer = spacer(below * pitch);
                listEl.appendChild(bottomSpacer);
            } else {
                bottomSpacer.style.setProperty('height', (below * pitch) + 'px');
            }
        } else if (bottomSpacer) {
            bottomSpacer.remove();
            bottomSpacer = null;
        }

        // 3) 从后往前补齐，用锚点保证 DOM 顺序；**已在正确位置的行不动**
        //    （insertBefore 会把节点重新插一遍，那会重启它身上的 CSS 动画）。
        let anchor = bottomSpacer;
        for (let i = to - 1; i >= from; i -= 1) {
            let el = rendered.get(i);
            // 下标相同**不等于**内容相同：核对路径，不一致就重建。
            //
            // 这是把「行内容与它的下标一致」这个不变量做成**本地**保证。
            // 另一半保证是 renderShell 里的 `rendered.clear()` —— 那是**远端**的：
            // 它要求今后每一条换数据的路径都记得复位。而刚修的这个缺陷正是
            // 「一条我没想到的路径」（异步 scroll 事件），所以本地那一份值得留着。
            //
            // 诚实说明：**目前没有任何测试能到达这里** —— 每条换数据的路径都会先过
            // renderShell。逼红实测：删掉它零条红。留着是因为失败模式是静默的视觉
            // 损坏（本版已犯三次），而代价是每行一次字符串比较。将来谁加了原地更新
            // 列表的路径，请连测试一起补上。
            if (el && renderedPath.get(i) !== childPath(entries[i])) {
                dropRow(i);
                el = null;
            }
            if (!el) {
                renderRow(listEl, entries[i], i, total);
                el = listEl.children[listEl.children.length - 1];
                if (!el) continue;
                rendered.set(i, el);
                renderedPath.set(i, childPath(entries[i]));
                // 批内阶梯并**封顶**：上千行不封顶会拖成一场幻灯片。
                // 复用 chat.css 的 --flikky-stagger（含 --flikky-motion-scale，
                // 于是 reduce-motion 下自动归零）。
                const fresh = animateFrom >= 0 && i >= animateFrom;
                el.style.setProperty(
                    '--i',
                    fresh ? String(Math.min(i - animateFrom, STAGGER_CAP)) : '0',
                );
            }
            if (el.nextSibling !== anchor) {
                if (anchor) listEl.insertBefore(el, anchor);
                else listEl.appendChild(el);
            }
            anchor = el;
        }

        // 4) 首尾外圆角按**当前** total 重打。复用的代价：`is-last` 是建行时按
        //    当时的 total 打的，total 长大之后会留在一行不再是末行的行上。
        rendered.forEach(function (el, i) {
            el.classList.toggle('is-first', i === 0);
            el.classList.toggle('is-last', i === total - 1);
        });

        winFrom = from;
        winTo = to;
        winTotal = total;
        refreshPitch();

        if (!scrollBound) {
            // bodyEl 跨导航存活（renderShell 只清它的子节点），所以只挂一次。
            bodyEl.addEventListener('scroll', function () {
                // 滚动出来的行不算新数据 —— 传 -1，不排入场。
                // 同时摘掉入场类，否则新建的行会再放一遍动效。
                if (listEl) listEl.classList.remove('fk-list-in');
                syncVirtual(-1);
            });
            scrollBound = true;
        }
    }

    function appendBatch(entries, batchStart, restored) {
        if (!listEl) return;
        // 第一批到达才启动方向横移 —— 这时容器里马上就有内容可以动了。
        if (pendingDir) {
            listEl.setAttribute('data-dir', pendingDir);
            pendingDir = null;
        }
        // 从缓存恢复时阶梯归零：几千行是**同时**到位的，给它们排延迟等于把
        // 「秒回」变成一场幻灯片。方向横移仍然生效 —— 那是一个容器动画。
        syncVirtual(restored ? -1 : batchStart);
    }

    /** 批内阶梯封顶步数。与 App 端 STAGGER_CAP_STEPS 同值，两端观感一致。 */
    const STAGGER_CAP = 8;

    /** 换行符。用 charCode 而不是转义：写这些文件的脚本会篡改反斜杠。 */
    const LF = String.fromCharCode(10);

    /**
     * 流式加载：NDJSON 逐行解析，每来一批就追加。
     *
     * 服务端每行 flush，所以第一批在整个目录列举完之前就到了——上千项的目录里
     * 用户看到的是列表持续向下生长，而不是一条转很久的进度条。
     *
     * 不支持 `body.getReader()` 时把同一个响应整体取文本，用同一个解析器跑
     * —— 只有一个请求，两条路径共用一份解析逻辑。
     */
    async function loadStreaming(target, seq) {
        const r = await fetch(
            // 初次加载由**服务端**按当前排序下发：不带的话，把排序改成
            // 「按大小」的用户此后每次打开目录都会先看到按名称排的列表、
            // 流结束时再跳一次（spec §6.1）。形态就是 SortSpec.format()，
            // 两端共用同一个解析器。
            '/api/storage/list?stream=1&sort=' +
                encodeURIComponent(sorter.format(sortSpec)) +
                '&path=' + encodeURIComponent(target),
            { credentials: 'same-origin' },
        );
        if (seq !== requestSeq) return true;
        if (!r.ok) {
            loadingPath = null;
            let code = '';
            try {
                const body = await r.json();
                code = body && typeof body.code === 'string' ? body.code : '';
            } catch (e) { /* 非 JSON 正文（400 就没有正文），当作无 code */ }
            handleFailure(r.status, code);
            return true;
        }
        // 能增量读就增量读；不能就把同一个响应整体取文本，用**同一个**逐行解析器跑。
        // 关键是不再发第二个请求——第一版回落时重新 fetch 了一次，于是同一次导航
        // 打了两个请求（测试里表现为「第二次请求拿到了下一条排队的响应」）。
        const canStream = !!r.body && typeof r.body.getReader === 'function';
        const reader = canStream ? r.body.getReader() : null;
        const decoder = canStream ? new TextDecoder() : null;
        let wholeText = canStream ? null : await r.text();
        let buffered = '';
        let sawDone = false;
        let count = 0;
        let shellReady = false;
        let pending = [];

        const flushPending = () => {
            if (!pending.length) return;
            // `lastState.entries` 是被 push 追加的**同一个数组**，而 shownEntries
            // 是它排序 / 过滤之后的快照 —— 每批到达都必须重算，否则新到的行
            // 一个都不会显示（源变了、派生没跟上，本版那六个缺陷的形状）。
            recomputeShown();
            appendBatch(pending, count - pending.length);
            pending = [];
        };

        for (;;) {
            let step;
            if (canStream) {
                step = await reader.read();
            } else {
                // 一次性拿到全部文本：当作「一个大分片然后结束」喂给同一个解析循环。
                step = { value: null, done: true };
                buffered += wholeText;
                wholeText = '';
            }
            // 过期响应一律丢弃，并**主动断开**——否则服务端会把整个大目录白列举完。
            if (seq !== requestSeq) {
                if (reader) reader.cancel();
                return true;
            }
            if (step.value) buffered += decoder.decode(step.value, { stream: true });
            for (;;) {
                const nl = buffered.indexOf(LF);
                if (nl < 0) break;
                const line = buffered.slice(0, nl).trim();
                buffered = buffered.slice(nl + 1);
                if (!line) continue;
                let obj;
                try {
                    obj = JSON.parse(line);
                } catch (e) {
                    continue;
                }
                if (obj.done) { sawDone = true; continue; }
                if (typeof obj.name !== 'string') {
                    // 首行：路径确认。
                    //
                    // 壳在 load 里已经乐观地建过一次（点击的即时反馈）。这里**只有
                    // 服务端规范化后的路径与乐观值不同时**才重建：无条件重建会把刚
                    // 画好的壳扔掉再画一遍，而且 renderShell 会重算进/退方向——
                    // 第二次算的时候 shownPath 已经等于新路径，方向恒为 enter，
                    // 「返回上一级」的横移就永远反着（实测）。
                    const headPath = typeof obj.path === 'string' ? obj.path : target;
                    currentPath = headPath;
                    lastState = { path: headPath, entries: [] };
                    if (headPath !== shownPath) renderShell(headPath);
                    // renderShell 会清空 viewEntries，所以**必须在它之后**接上。
                    setViewEntries(lastState.entries);
                    shellReady = true;
                    continue;
                }
                if (!shellReady) {
                    // 条目行先到、首行没来（协议上不该发生，但流可能从中间被截断，
                    // 或将来协议改了）。此时用请求的路径把壳建起来，而不是让
                    // `lastState.entries` 抛 TypeError —— 那会被外层 catch 吞掉，
                    // 变成一句无从理解的「连接断开」，整份列表也不会出现。
                    currentPath = target;
                    lastState = { path: target, entries: [] };
                    renderShell(target);
                    setViewEntries(lastState.entries);
                    shellReady = true;
                }
                lastState.entries.push(obj);
                pending.push(obj);
                count += 1;
            }
            if (shellReady) {
                flushPending();
                syncFooter();
            }
            if (step.done) break;
        }
        loadingPath = null;
        if (!sawDone) {
            // 流被截断（休眠 / Wi-Fi 切换 / 服务端读到一半失败）。已经到的行保留——
            // 清空会让用户以为目录是空的——但必须说清楚这份列表不完整。
            notifyError(t('app.files.truncated'));
        }
        hasLoadedOnce = true;
        // 只有真的收到 done 行才算完整。被截断时全选保持置禁 —— 那时「全部」
        // 确实是未知的，页脚也仍然显示「正在载入 N 项」而不是骗人的「共 N 项」。
        listingComplete = sawDone;
        // **只缓存完整的列表**：被截断的那份存进去会让「秒回」永远回一份残缺的，
        // 而用户再也见不到完整的那一份。
        if (sawDone && lastState && typeof lastState.path === 'string') {
            cacheStore(lastState.path, lastState.entries);
        }
        syncSelectAll();
        syncToolbar();
        syncFooter();
        setBusy(false);
        return true;
    }

    async function load(relativePath, force) {
        // 开关关闭时一律不请求。这是缺陷 1b 的正面修法：服务端按 D33 返回 404
        // 且刻意不带 code（不暴露「功能存在但被关」），所以客户端分不清
        // 「功能被关」与「路径不存在」——那就别让它撞上。
        if (!enabled) return;
        const target = relativePath || '';
        // 离开当前目录之前记下滚动位置 —— 回退时要恢复到这里。
        rememberScroll();
        const seq = ++requestSeq;
        if (force) cacheDrop(target);
        const cached = dirCache.get(target);
        if (cached) {
            // 命中：一个请求都不发。重新插一次把它挪到 LRU 链尾。
            dirCache.delete(target);
            dirCache.set(target, cached);
            renderFromCache(target, cached.entries);
            return;
        }
        // 立即前进：面包屑先动、列表区画进度。大目录里服务端要几百毫秒，
        // 没有这一步用户点了什么都不变，以为没点上（App 端同一个问题的浏览器版）。
        loadingPath = target;
        // 新一次导航开始：列表不再完整，全选立刻置禁。
        //
        // 这一步必须在**发请求之前**。第一版写在 loadStreaming 里、`await fetch()`
        // 之后，于是整个请求往返期间它还是上一个目录留下的 true —— 而那段时间
        // `lastState` 也还是上一个目录的条目，点下去会把上一个目录的文件加进选择集，
        // 屏幕上却是新目录的空列表（装机验收：「全选按钮全程可点击」）。
        listingComplete = false;
        syncSelectAll();
        renderShell(target);
        setBusy(true);
        try {
            await loadStreaming(target, seq);
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
            listingComplete = false;
            footerEl = null;
            // 开关关掉就丢缓存：再打开时必须重新取，不能拿关闭期间的旧列表糊弄。
            cacheClear();
            syncSelectAll();
            render(lastState);
            return;
        }
        load(currentPath);
    }

    window.flikkyPanels = window.flikkyPanels || {};
    /**
     * 列举规则变了（目前只有「显示隐藏文件」）：缓存里那些列表是按旧规则列出来的，
     * 不失效的话开关翻了也看不出变化 —— 而用户会以为开关坏了。
     * 丢掉缓存并重取当前目录。
     */
    function invalidate() {
        cacheClear();
        if (enabled) load(currentPath, true);
    }

    window.flikkyPanels.files = {
        mount: mount,
        render: render,
        navigate: navigate,
        setEnabled: setEnabled,
        invalidate: invalidate,
    };
    window.flikky = window.flikky || {};
    window.flikky.renderFilesPanel = render;
    window.flikky.navigateStorage = navigate;
    /** 断线重连后必须回根目录：服务端可能已换手机、换授权状态。 */
    window.flikky.resetStorageBrowser = function () {
        currentPath = '';
        lastState = null;
        // 重连是另一台手机 / 另一次会话，旧目录内容一律不可信。
        cacheClear();
    };

    const host = document.getElementById('view-files');
    if (host) mount(host);
})();
