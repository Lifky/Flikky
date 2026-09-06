/**
 * 排序语义的浏览器端镜像。
 *
 * Kotlin 侧是 `util/SortSpec.kt` + `StorageListingPolicy.filterAndSort`；
 * 两份实现由 `app/src/test/resources/sort-order.json` 这份**共享 fixture** 钉死
 * （backlog B26 担心的是「两端顺序不一致而各自的测试都是绿的」，共享 oracle 正是为它）。
 *
 * **改任一侧都必须先能解释 fixture 为什么变；加排序键必须同步扩 fixture。**
 *
 * 只在「用户切换排序」时用到 —— 初次加载由服务端按 `?sort=` 排好下发，
 * 所以这份镜像的爆炸半径只有一次重排。
 */
(function () {
    'use strict';

    const KEYS = ['NAME', 'TIME', 'SIZE'];

    /** 切到新键时的方向：名称升序，时间与大小降序。与 Kotlin 的 SortSpec.natural 一致。 */
    function natural(key) {
        return { key: key, desc: key !== 'NAME' };
    }

    function format(spec) {
        return spec.key + ':' + (spec.desc ? 'desc' : 'asc');
    }

    /** 任何不认识的输入返回 null，由调用方回落到自己那一处的默认值。 */
    function parse(raw) {
        const text = (raw || '').trim();
        if (!text) return null;
        const parts = text.split(':');
        if (parts.length !== 2) return null;
        if (KEYS.indexOf(parts[0]) < 0) return null;
        if (parts[1] !== 'asc' && parts[1] !== 'desc') return null;
        return { key: parts[0], desc: parts[1] === 'desc' };
    }

    /** 点当前键翻转方向，点新键用它的自然方向。 */
    function tap(spec, key) {
        if (spec && spec.key === key) return { key: key, desc: !spec.desc };
        return natural(key);
    }

    /**
     * 名称顺序 —— 与 Kotlin 的 `NAME_ORDER` 逐字对应：
     * 先比 `lowercase`（两端都是 locale-independent 的 Unicode 默认映射），
     * 相等再比原串（两端的 `<` / `compareTo` 都是 UTF-16 码位序）。
     *
     * **不要换成 `localeCompare`**：它带 locale 与排序规则，两端立刻分叉。
     */
    function compareName(a, b) {
        const la = String(a).toLowerCase();
        const lb = String(b).toLowerCase();
        if (la < lb) return -1;
        if (la > lb) return 1;
        if (a < b) return -1;
        if (a > b) return 1;
        return 0;
    }

    /**
     * 目录优先**不参与方向翻转**（目录 size 恒为 0，混排会把文件夹全挤到一端）；
     * 末尾恒按名称**升序**兜底，所以同 size / 同 mtime 的两项顺序也是确定的。
     *
     * 返回新数组，不改入参 —— 调用方还握着服务端给的原始顺序。
     */
    function sortEntries(entries, spec) {
        const s = spec || natural('NAME');
        return (entries || []).slice().sort(function (a, b) {
            const da = a.isDir ? 0 : 1;
            const db = b.isDir ? 0 : 1;
            if (da !== db) return da - db;
            let k;
            if (s.key === 'NAME') {
                k = compareName(a.name, b.name);
            } else {
                const ka = (s.key === 'TIME' ? a.mtime : a.size) || 0;
                const kb = (s.key === 'TIME' ? b.mtime : b.size) || 0;
                k = ka < kb ? -1 : (ka > kb ? 1 : 0);
            }
            if (s.desc) k = -k;
            if (k !== 0) return k;
            return compareName(a.name, b.name);
        });
    }

    /**
     * 关键词过滤：trim + 不区分大小写的子串，**只匹配名称、不匹配路径**。
     * 与 App 端 `FilesListBuilder.build` 同规则。目录也参与 —— 用户要找的可能就是个文件夹。
     */
    function filterEntries(entries, query) {
        const q = (query || '').trim().toLowerCase();
        if (!q) return entries || [];
        return (entries || []).filter(function (e) {
            return String(e.name || '').toLowerCase().indexOf(q) >= 0;
        });
    }

    /**
     * 排序偏好读写。非敏感的纯展示状态，进 localStorage 不触红线。
     * 隐私模式下 localStorage 会抛，所以读写一律包 try/catch（与 app.js 既有写法一致）。
     */
    function load(storageKey, fallback) {
        try {
            return parse(localStorage.getItem(storageKey)) || fallback;
        } catch (e) {
            return fallback;
        }
    }

    function save(storageKey, spec) {
        try {
            localStorage.setItem(storageKey, format(spec));
        } catch (e) { /* 隐私模式禁写，忽略 */ }
    }

    window.flikkySort = {
        KEYS: KEYS,
        natural: natural,
        format: format,
        parse: parse,
        tap: tap,
        compareName: compareName,
        sortEntries: sortEntries,
        filterEntries: filterEntries,
        load: load,
        save: save,
    };
}());
