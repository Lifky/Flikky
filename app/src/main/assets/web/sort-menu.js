/*
 * 排序菜单（浏览器端，文件面板与收藏面板共用）。
 *
 * 与 App 端的分工逐一对应：
 *   `util/SortSpec.kt`        ↔  `sort.js`       （纯逻辑，两端由共享 fixture 钉死）
 *   `ui/components/SortMenuAction.kt` ↔ 本文件   （共用的菜单 UI）
 *
 * ## 为什么不用 mdui-dropdown
 *
 * `.fk-pillar` 有 `overflow: hidden`，而 mdui 的菜单是普通 DOM 子元素
 * （那份 bundle 里 `showPopover` 出现 0 次 —— 它不走 top layer），
 * 于是被祖先裁切、只能往容器内部挤。加 z-index 没用：**z-index 不能突破
 * 祖先的 overflow 裁切**。2026-09-08 装机反馈的现象是菜单跑到了触发按钮的
 * 右边、而且「感觉上和按钮是分开的」。
 *
 * 项目已经解决过同一个坑：`chat.css` 的 `.recall-menu`
 * 「手写外层只负责 fixed 定位（mdui-dropdown 在这套布局里定位不对），
 * 里面用官方 mdui-menu」。本文件把那个模式收成一处共用的。
 *
 * **分工严格照 .recall-menu**：外层只管定位（position / z-index / min-width），
 * 面板背景、圆角、海拔、菜单项交互态全部归内部的官方 `mdui-menu` ——
 * 所以视觉仍然是 mdui 官方那套，没有自绘。
 *
 * ## 怎么让它「长在按钮上」
 *
 * 三件事一起做，少一件都会显得飘：
 *   · **左边缘对齐按钮左边缘**（bottom-start）。排序按钮在面板头部偏左，
 *     右对齐会把菜单推到按钮左侧一大截外。
 *   · **紧贴按钮下沿**，只留一个 xs 的缝。
 *   · **从左上角缩放展开**（`transform-origin: top left`），视觉上是从按钮
 *     长出来的，而不是淡入到旁边。
 */
(function () {
    'use strict';

    /** 菜单与触发按钮之间的缝。再大就开始显得是两个独立的东西。 */
    const GAP = 4;
    /** 夹在视口内时给边缘留的余量。 */
    const EDGE = 8;

    let openMenu = null;

    function closeMenu() {
        document.removeEventListener('pointerdown', onOutside, true);
        document.removeEventListener('keydown', onKey, true);
        if (openMenu && openMenu.parentNode) openMenu.parentNode.removeChild(openMenu);
        openMenu = null;
    }

    function onOutside(e) {
        if (openMenu && !openMenu.contains(e.target)) closeMenu();
    }

    function onKey(e) {
        if (e.key === 'Escape') {
            closeMenu();
            e.stopPropagation();
        }
    }

    /**
     * 把 `menuEl`（一个 `mdui-menu`）作为 `trigger` 的下拉菜单打开。
     *
     * 菜单挂到 **body** 上：留在面板里就还在裁切链上，而且祖先一旦有
     * transform / filter / contain，`fixed` 的包含块就会变成那个祖先，
     * 又被裁一次。挂 body 是唯一稳的做法（`.recall-menu` 同理）。
     */
    function open(trigger, menuEl) {
        closeMenu();
        const wrap = document.createElement('div');
        wrap.className = 'fk-sort-menu';
        wrap.appendChild(menuEl);
        document.body.appendChild(wrap);
        // **必须记下来**：closeMenu 靠它摘节点、isOpen 靠它判状态。
        // 漏了这一句的表现是「点两次叠出两个菜单、点外部也关不掉」
        // —— 由 panel-sort-menu-layer.test.js 的后两条守住。
        openMenu = wrap;

        // rect 与 fixed 用的是同一个坐标系（视口），所以不用再叠加滚动偏移。
        const rect = trigger.getBoundingClientRect();
        const w = wrap.offsetWidth || 180;
        const h = wrap.offsetHeight || 0;
        const vw = window.innerWidth || 0;
        const vh = window.innerHeight || 0;

        // 左对齐按钮左边缘，再夹进视口。Math.max 兜住窄窗口下 EDGE 反而
        // 把菜单推出左边的情况。
        const left = Math.max(EDGE, Math.min(rect.left, vw - w - EDGE));
        // 默认挂在按钮下方；下面装不住就翻到上方（而不是被视口截断）。
        let top = rect.bottom + GAP;
        if (h && top + h > vh - EDGE) {
            const above = rect.top - GAP - h;
            top = above >= EDGE ? above : Math.max(EDGE, vh - h - EDGE);
        }
        wrap.style.left = left + 'px';
        wrap.style.top = top + 'px';

        // 下一帧再装外部点击关闭，否则本次 pointerdown / click 会立刻关掉自己
        // （与 app.js 的 showActionsMenu 同一处理）。
        setTimeout(function () {
            document.addEventListener('pointerdown', onOutside, true);
            document.addEventListener('keydown', onKey, true);
        }, 0);
        return wrap;
    }

    window.flikkySortMenu = { open: open, close: closeMenu, isOpen: function () { return !!openMenu; } };
}());
