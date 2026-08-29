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

    const t = (key) => (window.i18n && window.i18n.t ? window.i18n.t(key) : key);

    /** 当前相对路径。只存内存：不敏感，但也没有跨会话价值，且重连必须回根目录。 */
    let currentPath = '';

    let root = null;
    let bodyEl = null;

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
    }

    /** 中性提示（加载中 / 空文件夹）。错误与引导态在 Task 8 接。 */
    function renderNotice(key) {
        if (!bodyEl) return;
        bodyEl.textContent = '';
        const p = document.createElement('p');
        p.className = 'fk-panel-notice';
        p.textContent = t(key);
        bodyEl.appendChild(p);
    }

    function render(state) {
        if (!bodyEl) return;
        if (!state || !Array.isArray(state.entries)) {
            renderNotice('app.files.loading');
            return;
        }
        currentPath = typeof state.path === 'string' ? state.path : '';
        if (state.entries.length === 0) {
            renderNotice('app.files.empty');
            return;
        }
        // Task 7 在这里画面包屑与列表。
        renderNotice('app.files.loading');
    }

    function mount(container) {
        if (!container) return;
        root = container;
        buildShell(root);
        renderNotice('app.files.loading');
        if (window.i18n && window.i18n.onChange) {
            window.i18n.onChange(() => {
                buildShell(root);
                render(null);
            });
        }
    }

    window.flikkyPanels = window.flikkyPanels || {};
    window.flikkyPanels.files = { mount: mount, render: render };
    window.flikky = window.flikky || {};
    window.flikky.renderFilesPanel = render;
    /** 断线重连后必须回根目录：服务端可能已换手机、换授权状态。 */
    window.flikky.resetStorageBrowser = function () { currentPath = ''; };

    const host = document.getElementById('view-files');
    if (host) mount(host);
})();
