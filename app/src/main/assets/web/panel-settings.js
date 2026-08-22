/* ============================================================================
 * 设置面板：功能柱的第一个「面板」——一段独立脚本，渲染进 #shell 里的一个
 * .fk-view 插槽（此处是 #view-settings）。收藏面板（Task 6）会照抄这个形状：
 * window.flikkyPanels.<name>.mount(root) 作为唯一入口，脚本自己在文件末尾
 * 调用一次 mount，不等 DOMContentLoaded（script 标签在 </body> 前，DOM 已解析好）。
 *
 * D1：面板不向 app.js 要任何新接口。三个布局状态（rail-side / pane-swap /
 * split 宽度 / panel 折叠）都是 app.js 只在 init 时读一次的纯 DOM + localStorage
 * 状态，app.js 之后再也不会写它们——面板是它们唯一的写者，没有竞态。
 * 例外是「收起功能栏」：panel 状态 app.js 自己也写（setPanel），所以收起按钮这里
 * 只画不绑——app.js 有一个针对 .fk-panel-collapse 的委派 click 监听器统一处理。
 * 原先这里复刻过 setPanel 的两行，收藏面板要出现第三份时改成了委派。
 *
 * D2：语言行只读——手机是外观的唯一事实源（i18n.refresh() 拉 /api/web-theme
 * 并 setLanguage），浏览器端不提供切换入口，所以没有 chevron，没有点击。
 *
 * D5：关于区的版本号来自 peer-info（document.body.dataset.appVersion，
 * app.js 的 applyPeerAppearance 负责写入），peer-info 是异步到达的。这里选择
 * 「跟着 i18n.onChange 一起重渲染」而不是另开一个轮询或事件——面板本来就会在
 * 语言变化时重渲染，appVersion 落地前打开设置只是暂时不显示版本号，下一次
 * 重渲染（换语言，或重新打开面板）会带上它。
 * ==========================================================================*/

(function () {
    'use strict';

    const STORAGE_RAIL_SIDE = 'flikky_rail_side';
    const STORAGE_PANE_SWAP = 'flikky_pane_swap';
    const STORAGE_SPLIT_CHAT = 'flikky_split_chat';
    const GITHUB_URL = 'https://github.com/Lifky/Flikky';

    function t(key, values) {
        return window.flikkyI18n ? window.flikkyI18n.t(key, values) : key;
    }

    // app.js 提供的 FLIP 包装器。它不在时（单测沙箱、加载顺序意外）直接执行改动——
    // 没有动画也要保证开关本身是好的。
    function animateLayout(mutate) {
        if (window.flikky && typeof window.flikky.animateShellLayout === 'function') {
            window.flikky.animateShellLayout(mutate);
            return;
        }
        mutate();
    }

    function icon(name) {
        const span = document.createElement('span');
        span.className = 'material-symbols-outlined';
        span.textContent = name;
        return span;
    }

    // 组装一行 .fk-item 的骨架：前导图标 + 标题/副标题两个 span（必须显式
    // display:block，见 panels.css 注释——否则默认 inline 会把两行挤成一行）。
    // 调用方在拿到的 { item, title, sub } 上继续追加 trailing 内容。
    function buildRow(tag, leadIcon) {
        const item = document.createElement(tag);
        if (tag === 'button') item.type = 'button';
        item.className = 'fk-item';
        const lead = document.createElement('span');
        lead.className = 'fk-item-lead fk-item-lead--plain';
        lead.appendChild(icon(leadIcon));
        const text = document.createElement('span');
        text.className = 'fk-item-text';
        const title = document.createElement('span');
        title.className = 'fk-item-title';
        const sub = document.createElement('span');
        sub.className = 'fk-item-sub';
        text.appendChild(title);
        text.appendChild(sub);
        item.appendChild(lead);
        item.appendChild(text);
        return { item, title, sub };
    }

    function buildTrailIcon(name) {
        const trail = document.createElement('span');
        trail.className = 'fk-item-trail';
        trail.appendChild(icon(name));
        return trail;
    }

    function sectionTitle(text) {
        const el = document.createElement('div');
        el.className = 'fk-section-title';
        el.textContent = text;
        return el;
    }

    // ── 布局：本组是可写的——两个 mdui-switch + 一个重置按钮，都直接写
    // shell.dataset / localStorage，读的时候也从同一处读，保证重开面板不失真。
    function buildLayoutGroup(shell) {
        const group = document.createElement('div');
        group.className = 'fk-group';

        const rail = buildRow('div', 'left_panel_open');
        rail.item.style.cursor = 'default';
        rail.title.textContent = t('app.settings.railSide');
        rail.sub.textContent = t('app.settings.railSideSub');
        const railSwitch = document.createElement('mdui-switch');
        railSwitch.id = 'rail-side-switch';
        railSwitch.checked = shell.dataset.railSide === 'right';
        railSwitch.addEventListener('change', () => {
            const next = railSwitch.checked ? 'right' : 'left';
            // 经 animateShellLayout 包一层：这两个轴改的是 flex order，而 order 不可
            // 插值，直接写属性只会瞬间跳位。包装器负责 FLIP，状态仍由这里写。
            animateLayout(() => { shell.dataset.railSide = next; });
            try { localStorage.setItem(STORAGE_RAIL_SIDE, next); } catch (e) { /* 隐私模式禁写，忽略 */ }
        });
        rail.item.appendChild(railSwitch);
        group.appendChild(rail.item);

        const swap = buildRow('div', 'splitscreen_right');
        swap.item.style.cursor = 'default';
        swap.title.textContent = t('app.settings.paneSwap');
        swap.sub.textContent = t('app.settings.paneSwapSub');
        const swapSwitch = document.createElement('mdui-switch');
        swapSwitch.id = 'pane-swap-switch';
        swapSwitch.checked = shell.dataset.swap === '1';
        swapSwitch.addEventListener('change', () => {
            const next = swapSwitch.checked ? '1' : '0';
            animateLayout(() => { shell.dataset.swap = next; });
            try { localStorage.setItem(STORAGE_PANE_SWAP, next); } catch (e) { /* 隐私模式禁写，忽略 */ }
        });
        swap.item.appendChild(swapSwitch);
        group.appendChild(swap.item);

        const reset = buildRow('button', 'width');
        reset.title.textContent = t('app.settings.resetSplit');
        reset.sub.textContent = t('app.settings.resetSplitSub');
        reset.item.appendChild(buildTrailIcon('restart_alt'));
        reset.item.addEventListener('click', () => {
            // D1：只做 applySplit 反过来的两行 + 一个 localStorage 键，
            // 不重实现它的 clamp——那是 app.js 拖拽逻辑自己的职责。
            shell.style.removeProperty('--flikky-split-chat');
            shell.style.removeProperty('--flikky-split-panel');
            try { localStorage.removeItem(STORAGE_SPLIT_CHAT); } catch (e) { /* 隐私模式禁写，忽略 */ }
        });
        group.appendChild(reset.item);

        return group;
    }

    // ── 来自手机的设置：整组只读——没有点击，没有 mdui-switch，语言行也没有
    // chevron（D2：手机是外观的唯一事实源，这里不提供一个不存在的操作）。
    function buildPhoneGroup() {
        const group = document.createElement('div');
        group.className = 'fk-group';

        const languageName = window.flikkyI18n && window.flikkyI18n.language === 'en'
            ? t('app.settings.languageEn')
            : t('app.settings.languageZh');
        const language = buildRow('div', 'language');
        language.item.style.cursor = 'default';
        language.title.textContent = t('app.settings.language');
        language.sub.textContent = languageName;
        language.item.appendChild(buildTrailIcon('smartphone'));
        group.appendChild(language.item);

        const theme = buildRow('div', 'palette');
        theme.item.style.cursor = 'default';
        theme.title.textContent = t('app.settings.theme');
        theme.sub.textContent = t('app.settings.themeSub');
        theme.item.appendChild(buildTrailIcon('smartphone'));
        group.appendChild(theme.item);

        // 属性缺失时读作「关」，与 FlikkySettings.sessionTimestampEnabled 的默认值一致。
        // 写成 !== 'off' 会在 peer-info 到达前把默认值猜反，显示「已开启」。
        const timestampsOn = document.body.dataset.timestamps === 'on';
        const timestamps = buildRow('div', 'schedule');
        timestamps.item.style.cursor = 'default';
        timestamps.title.textContent = t('app.settings.timestamps');
        timestamps.sub.textContent = t(timestampsOn ? 'app.settings.timestampsSubOn' : 'app.settings.timestampsSubOff');
        timestamps.item.appendChild(buildTrailIcon('smartphone'));
        group.appendChild(timestamps.item);

        return group;
    }

    // ── 关于：Flikky + 版本号（peer-info 异步到达，见文件头注释）+ 项目链接。
    // GitHub 链接是用户点击才会跳转的真实 <a href>，不是运行时 fetch，
    // 不触碰「禁止运行时外联」红线。
    function buildAboutGroup() {
        const group = document.createElement('div');
        group.className = 'fk-group';

        const about = buildRow('div', 'info');
        about.item.style.cursor = 'default';
        about.title.textContent = 'Flikky';
        const version = document.body.dataset.appVersion || '';
        about.sub.textContent = version
            ? t('app.settings.browserClient', { version })
            : t('app.settings.browserClientNoVersion');
        group.appendChild(about.item);

        const link = document.createElement('a');
        link.className = 'fk-item';
        link.href = GITHUB_URL;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        const linkLead = document.createElement('span');
        linkLead.className = 'fk-item-lead fk-item-lead--plain';
        linkLead.appendChild(icon('code'));
        const linkText = document.createElement('span');
        linkText.className = 'fk-item-text';
        const linkTitle = document.createElement('span');
        linkTitle.className = 'fk-item-title';
        linkTitle.textContent = t('app.settings.github');
        const linkSub = document.createElement('span');
        linkSub.className = 'fk-item-sub';
        linkSub.textContent = GITHUB_URL.replace('https://', '');
        linkText.appendChild(linkTitle);
        linkText.appendChild(linkSub);
        link.appendChild(linkLead);
        link.appendChild(linkText);
        link.appendChild(buildTrailIcon('open_in_new'));
        group.appendChild(link);

        return group;
    }

    function buildHeader() {
        const header = document.createElement('header');
        header.className = 'fk-panel-head';
        const title = document.createElement('h1');
        title.className = 'fk-panel-title';
        title.textContent = t('app.settings.title');
        const collapse = document.createElement('button');
        collapse.type = 'button';
        collapse.className = 'fk-icon-btn fk-panel-collapse';
        // 纯图标按钮必须有可访问名：否则读屏念的是图标字体的连字文本（'close'），
        // 换个图标就会念出 'restart_alt' 这种内部标识符。
        collapse.setAttribute('aria-label', t('app.settings.collapse'));
        collapse.appendChild(icon('close'));
        // 不在这里绑 click：app.js 有一个针对 .fk-panel-collapse 的委派监听器，
        // 收起动作统一走它的 setPanel(false)。面板只负责画按钮，shell.dataset.panel
        // 保持单一写入方（原先这里复刻过 setPanel 的两行，收藏面板要第三份时改掉了）。
        header.appendChild(title);
        header.appendChild(collapse);
        return header;
    }

    function render(root) {
        const shell = document.getElementById('shell');
        if (!shell) return;
        while (root.firstChild) root.removeChild(root.firstChild);

        const body = document.createElement('div');
        body.className = 'fk-panel-body flikky-scroll';
        body.appendChild(sectionTitle(t('app.settings.layout')));
        body.appendChild(buildLayoutGroup(shell));
        body.appendChild(sectionTitle(t('app.settings.fromPhone')));
        body.appendChild(buildPhoneGroup());
        body.appendChild(sectionTitle(t('app.settings.about')));
        body.appendChild(buildAboutGroup());

        root.appendChild(buildHeader());
        root.appendChild(body);
    }

    // app.js 发布到 <body> dataset 的那几项都是 peer-info 到达后才写的，而本脚本
    // 在文件末尾就渲染了一次——严格早于 fetchPeerInfo() 的 await 返回。所以只订阅
    // i18n 的话，「关于」区永远拿不到版本号、时间戳行永远停在初始猜测上。
    // 这里不让 app.js 反过来通知面板（那会让 app.js 知道面板存在），而是观察它已经
    // 发布出来的 DOM 状态——正是 D1 定的方向：app.js 只管发布，面板自己读。
    // 观察的是 <body>，面板写的是 #shell 和自己的子树，不会自激。
    const PUBLISHED_ATTRS = ['data-app-version', 'data-timestamps'];

    function observePublishedState(root) {
        if (typeof MutationObserver !== 'function') return;
        new MutationObserver(() => render(root)).observe(document.body, {
            attributes: true,
            attributeFilter: PUBLISHED_ATTRS,
        });
    }

    function mount(root) {
        if (!root || !window.flikkyI18n) return;
        // onChange 订阅时会立即用当前语言调用一次监听器——这就是初次渲染，
        // 不要在这之外再手动调用一次 render，否则面板会渲染两遍。
        window.flikkyI18n.onChange(() => render(root));
        observePublishedState(root);
    }

    window.flikkyPanels = window.flikkyPanels || {};
    window.flikkyPanels.settings = { mount };

    mount(document.getElementById('view-settings'));
})();
