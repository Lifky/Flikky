const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEB = path.join(__dirname, '../../main/assets/web');
const SRC_PATH = path.join(WEB, 'panel-settings.js');
const SRC = fs.readFileSync(SRC_PATH, 'utf8');
const HTML = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const CSS = fs.readFileSync(path.join(WEB, 'panels.css'), 'utf8');
const I18N_PATH = path.join(WEB, 'i18n.js');
const I18N_SRC = fs.readFileSync(I18N_PATH, 'utf8');

// ---------------------------------------------------------------------------
// A minimal fake DOM. This suite renders nothing to a real browser, so the
// only way to prove the panel actually builds a tree (not just "doesn't
// throw") is to run the real file against stand-ins that support exactly the
// handful of DOM operations panel-settings.js uses: createElement,
// appendChild/removeChild, dataset, style.setProperty/removeProperty,
// addEventListener, and a `checked` accessor for <mdui-switch>.
// ---------------------------------------------------------------------------

function makeStyle() {
    const props = {};
    return {
        setProperty(name, value) { props[name] = value; },
        removeProperty(name) { delete props[name]; },
        getPropertyValue(name) { return props[name] || ''; },
        _props: props,
    };
}

function makeElement(tag) {
    const listeners = {};
    const el = {
        tagName: tag,
        className: '',
        textContent: '',
        children: [],
        dataset: {},
        style: makeStyle(),
        attributes: {},
        addEventListener(type, fn) { (listeners[type] = listeners[type] || []).push(fn); },
        fire(type) { (listeners[type] || []).forEach((fn) => fn()); },
        appendChild(child) { el.children.push(child); return child; },
        removeChild(child) {
            const i = el.children.indexOf(child);
            if (i >= 0) el.children.splice(i, 1);
            return child;
        },
        setAttribute(name, value) { el.attributes[name] = value; },
        get firstChild() { return el.children[0] || null; },
    };
    let checkedValue = false;
    Object.defineProperty(el, 'checked', {
        get() { return checkedValue; },
        set(v) { checkedValue = v; },
    });
    return el;
}

// Depth-first search for the first descendant matching `pred`, across the
// .children trees built by panel-settings.js (header + body only — nothing
// here is a real DOM, so no querySelector).
function findDescendant(root, pred) {
    for (const child of root.children || []) {
        if (pred(child)) return child;
        const found = findDescendant(child, pred);
        if (found) return found;
    }
    return null;
}

function makeLocalStorage() {
    const store = {};
    return {
        getItem(k) { return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null; },
        setItem(k, v) { store[k] = String(v); },
        removeItem(k) { delete store[k]; },
        _store: store,
    };
}

// Runs the real panel-settings.js in a fresh sandbox. Returns everything a
// test needs to poke at the result: the shell/root/body fakes, the
// localStorage stub, and how many times the i18n listener fired (proves
// mount() renders exactly once at load — D4).
function loadPanel({ language = 'zh-CN', railSide = 'left', swap = '0', timestamps = 'on', appVersion = '' } = {}) {
    const shell = makeElement('div');
    shell.dataset.railSide = railSide;
    shell.dataset.swap = swap;
    shell.dataset.panel = 'shown';

    const body = makeElement('body');
    // 两个都只在有值时才写：peer-info 到达前这两个属性根本不存在，
    // 无条件写会让「属性缺失」这个真实状态在测试里无法表达。
    if (timestamps) body.dataset.timestamps = timestamps;
    if (appVersion) body.dataset.appVersion = appVersion;

    const root = makeElement('div');

    const registry = { shell, 'view-settings': root };
    const localStorage = makeLocalStorage();

    let listenerCalls = 0;
    let currentLanguage = language;
    const i18n = {
        get language() { return currentLanguage; },
        t(key, values) {
            if (values && Object.prototype.hasOwnProperty.call(values, 'version')) {
                return `${key}:${values.version}`;
            }
            return key;
        },
        onChange(fn) {
            listenerCalls += 1;
            fn(currentLanguage);
            return () => {};
        },
    };

    // app.js 把 peer-info 的结果写到 <body> dataset 上，而面板在那之前就渲染完了。
    // 面板靠 MutationObserver 观察这些属性来重渲染，所以这里得有个能手动触发的替身：
    // observed 记下它观察了谁、过滤了哪些属性，fire() 模拟属性变化。
    const observed = [];
    function MutationObserverStub(callback) {
        this.callback = callback;
        this.observe = (target, options) => { observed.push({ target, options, fire: () => callback([]) }); };
        this.disconnect = () => {};
    }

    const context = {
        document: {
            createElement: (tag) => makeElement(tag),
            getElementById: (id) => registry[id] || null,
            body,
        },
        localStorage,
        console,
        MutationObserver: MutationObserverStub,
    };
    context.window = context;
    context.window.flikkyI18n = i18n;
    vm.createContext(context);
    vm.runInContext(SRC, context);

    return {
        context, shell, root, body, localStorage, observed,
        get renderCount() { return listenerCalls; },
    };
}

test('mount is exported and invoked exactly once at load (D4)', () => {
    const { context, root, renderCount } = loadPanel();
    assert.equal(typeof context.flikkyPanels, 'object');
    assert.equal(typeof context.flikkyPanels.settings.mount, 'function');
    assert.equal(renderCount, 1, 'onChange listener must fire exactly once on load — twice means the panel renders twice');
    assert.equal(root.children.length, 2, 'root must hold exactly [header, body] after the single initial render');

    const source = SRC;
    const calls = source.match(/mount\(document\.getElementById\('view-settings'\)\)/g) || [];
    assert.equal(calls.length, 1, 'panel-settings.js must call mount(...) exactly once, at the end of the file');
    assert.ok(source.trim().endsWith('})();'), 'the mount() call must be the last statement the IIFE runs');
});

test('app.html links panels.css, loads panel-settings.js, and has no inline <script> (CSP script-src \'self\')', () => {
    assert.match(HTML, /<link rel="stylesheet" href="\/static\/panels\.css">/);
    assert.match(HTML, /<script src="\/static\/panel-settings\.js"><\/script>/);
    const inlineScripts = HTML.match(/<script(?![^>]*\bsrc=)[^>]*>/g) || [];
    assert.deepEqual(inlineScripts, [], `found inline <script> tag(s): ${inlineScripts.join(', ')}`);
});

test('panel-settings.js never uses innerHTML (project red line — textContent only)', () => {
    assert.equal(SRC.includes('innerHTML'), false);
});

test('both layout axes are independent — separate keys, separate dataset attributes', () => {
    assert.match(SRC, /flikky_rail_side/);
    assert.match(SRC, /flikky_pane_swap/);
    // Assignment, not the === comparison used when reading the switch's
    // initial checked state — a loose `\s*=` would match "===" too and miss
    // a bug where one switch's handler writes the other axis's attribute.
    assert.match(SRC, /shell\.dataset\.railSide\s*=\s*next/);
    assert.match(SRC, /shell\.dataset\.swap\s*=\s*next/);
    assert.match(SRC, /localStorage\.setItem\(STORAGE_RAIL_SIDE,\s*next\)/);
    assert.match(SRC, /localStorage\.setItem\(STORAGE_PANE_SWAP,\s*next\)/);
});

test('reset-split removes both custom properties and the localStorage key, without reimplementing applySplit', () => {
    assert.match(SRC, /removeProperty\('--flikky-split-chat'\)/);
    assert.match(SRC, /removeProperty\('--flikky-split-panel'\)/);
    assert.match(SRC, /localStorage\.removeItem\(STORAGE_SPLIT_CHAT\)/);
    assert.equal(/applySplit\s*\(/.test(SRC), false, 'must not call/re-implement applySplit\'s clamp logic');
});

test('reset-split actually clears shell state at runtime', () => {
    const { context, shell, root, localStorage } = loadPanel();
    shell.style.setProperty('--flikky-split-chat', '61%');
    shell.style.setProperty('--flikky-split-panel', '39%');
    localStorage.setItem('flikky_split_chat', '61');

    // Must not match the header's collapse button (also a <button>, and found
    // first in traversal order since the header is appended before the body) —
    // the reset-split row is a plain buildRow() button with class "fk-item".
    const resetButton = findDescendant(root, (n) => n.tagName === 'button' && n.className === 'fk-item');
    assert.ok(resetButton, 'reset-split button must exist in the rendered tree');
    resetButton.fire('click');

    assert.equal(shell.style.getPropertyValue('--flikky-split-chat'), '');
    assert.equal(shell.style.getPropertyValue('--flikky-split-panel'), '');
    assert.equal(localStorage.getItem('flikky_split_chat'), null);
});

test('the phone-owned group has no click handlers and no mdui-switch (read-only, D2)', () => {
    const start = SRC.indexOf('function buildPhoneGroup');
    assert.notEqual(start, -1, 'buildPhoneGroup must exist');
    const end = SRC.indexOf('function buildAboutGroup');
    assert.notEqual(end, -1, 'buildAboutGroup must exist (used as the end marker for buildPhoneGroup)');
    const body = SRC.slice(start, end);
    assert.equal(body.includes("addEventListener('click'"), false, 'phone-owned rows must not be clickable');
    assert.equal(body.includes('mdui-switch'), false, 'phone-owned rows must not contain a toggle');
});

test('the language row has no chevron (D2 drops the prototype\'s chevron_right)', () => {
    const start = SRC.indexOf('function buildPhoneGroup');
    const end = SRC.indexOf('function buildAboutGroup');
    const body = SRC.slice(start, end);
    assert.equal(body.includes('chevron_right'), false);
    assert.equal(SRC.includes('chevron_right'), false, 'chevron_right must not appear anywhere in the file');
});

test('panels.css declares the connected-group geometry tokens and the press-squeeze selector', () => {
    assert.match(CSS, /--flikky-listgroup-radius-outer:\s*var\(--flikky-shape-lg\)/);
    assert.match(CSS, /--flikky-listgroup-radius-inner:\s*var\(--flikky-shape-xs\)/);
    assert.match(CSS, /--flikky-listgroup-gap:\s*2px/);
    assert.match(CSS, /\.fk-item:active \+ \.fk-item/);
});

test('.fk-item-title and .fk-item-sub are display: block (they are <span>s)', () => {
    const titleRule = CSS.match(/\.fk-item-title\s*\{[^}]*\}/);
    const subRule = CSS.match(/\.fk-item-sub\s*\{[^}]*\}/);
    assert.ok(titleRule, '.fk-item-title rule not found');
    assert.ok(subRule, '.fk-item-sub rule not found');
    assert.match(titleRule[0], /display:\s*block/);
    assert.match(subRule[0], /display:\s*block/);
});

test('new app.settings.* i18n keys exist in both dictionaries', () => {
    const keys = [
        'app.settings.title', 'app.settings.layout',
        'app.settings.railSide', 'app.settings.railSideSub',
        'app.settings.paneSwap', 'app.settings.paneSwapSub',
        'app.settings.resetSplit', 'app.settings.resetSplitSub',
        'app.settings.fromPhone', 'app.settings.language',
        'app.settings.languageZh', 'app.settings.languageEn',
        'app.settings.theme', 'app.settings.themeSub',
        'app.settings.timestamps', 'app.settings.timestampsSubOn', 'app.settings.timestampsSubOff',
        'app.settings.about', 'app.settings.browserClient', 'app.settings.browserClientNoVersion',
        'app.settings.github',
    ];

    // Same minimal loader shape as web-i18n.test.js's loadI18n, just local to
    // this file so this suite doesn't reach across into another test's helper.
    function loadI18n() {
        const documentElement = { lang: '', setAttribute(name, value) { if (name === 'lang') this.lang = value; } };
        const context = {
            document: { documentElement, querySelectorAll: () => [] },
            fetch: async () => ({ ok: false }),
            // i18n.js schedules refresh() via setInterval at load; the sandbox
            // never runs an event loop for it, so a no-op stub is enough.
            setInterval: () => 0,
        };
        context.window = context;
        vm.createContext(context);
        vm.runInContext(I18N_SRC, context);
        return context.flikkyI18n;
    }

    const i18n = loadI18n();
    for (const language of ['zh-CN', 'en']) {
        i18n.setLanguage(language);
        for (const key of keys) {
            assert.notEqual(i18n.t(key), key, `${language} is missing ${key}`);
        }
    }

    // The check above goes through i18n.t(), which falls back to the zh-CN
    // entry when a key is missing from a language's own dictionary — so an
    // en-only omission would still pass it (t() never returns the raw key,
    // it just silently serves Chinese text in English mode). Slice the two
    // dictionary object literals directly so a per-language omission is
    // actually provable red, independent of t()'s fallback.
    const zhStart = I18N_SRC.indexOf("'zh-CN': {");
    const enStart = I18N_SRC.indexOf('en: {', zhStart);
    const enEnd = I18N_SRC.indexOf('\n    };', enStart);
    assert.ok(zhStart !== -1 && enStart !== -1 && enEnd !== -1, 'could not locate the zh-CN/en dictionary blocks in i18n.js');
    const zhBlock = I18N_SRC.slice(zhStart, enStart);
    const enBlock = I18N_SRC.slice(enStart, enEnd);
    for (const key of keys) {
        assert.ok(zhBlock.includes(`'${key}'`), `zh-CN dictionary literal is missing '${key}'`);
        assert.ok(enBlock.includes(`'${key}'`), `en dictionary literal is missing '${key}'`);
    }
});

test('rail-side and pane-swap switches reflect current shell state on render, and write both storage keys on toggle', () => {
    const { shell, root, localStorage } = loadPanel({ railSide: 'right', swap: '1' });

    const railSwitch = findDescendant(root, (n) => n.tagName === 'mdui-switch' && n.id === 'rail-side-switch') || root.railSwitch;
    const swapSwitch = findDescendant(root, (n) => n.tagName === 'mdui-switch' && n.id === 'pane-swap-switch') || root.swapSwitch;
    assert.ok(railSwitch, 'rail-side-switch must be rendered');
    assert.ok(swapSwitch, 'pane-swap-switch must be rendered');
    assert.equal(railSwitch.checked, true, 'switch must reflect data-rail-side="right" on render, not just after toggling');
    assert.equal(swapSwitch.checked, true, 'switch must reflect data-swap="1" on render, not just after toggling');

    railSwitch.checked = false;
    railSwitch.fire('change');
    assert.equal(shell.dataset.railSide, 'left');
    assert.equal(localStorage.getItem('flikky_rail_side'), 'left');

    swapSwitch.checked = false;
    swapSwitch.fire('change');
    assert.equal(shell.dataset.swap, '0');
    assert.equal(localStorage.getItem('flikky_pane_swap'), '0');
});

test('the collapse button hides the panel by writing shell.dataset.panel and flikky_panel (mirrors setPanel, D1)', () => {
    const { shell, root, localStorage } = loadPanel();
    const collapseButton = findDescendant(root, (n) => n.tagName === 'button' && n.className.includes('fk-panel-collapse'));
    assert.ok(collapseButton, 'collapse button must exist');
    collapseButton.fire('click');
    assert.equal(shell.dataset.panel, 'hidden');
    assert.equal(localStorage.getItem('flikky_panel'), '0');
});

test('the About row shows no version placeholder until peer-info lands, then shows it on the next render', () => {
    const withoutVersion = loadPanel({ appVersion: '' });
    const aboutSubEmpty = findDescendant(withoutVersion.root, (n) => n.textContent === 'Flikky');
    assert.ok(aboutSubEmpty, 'About row title "Flikky" must be rendered');

    const withVersion = loadPanel({ appVersion: '1.19.0' });
    const versionSub = findDescendant(withVersion.root, (n) => typeof n.textContent === 'string' && n.textContent.includes('1.19.0'));
    assert.ok(versionSub, 'once document.body.dataset.appVersion is set, a render must show it');
});

test('the panel re-renders when app.js publishes peer-info onto body, not just at load', () => {
    // 这是生产环境真正走的时序：panel-settings.js 在文件末尾同步渲染，而
    // app.js 的 applyPeerAppearance 要等 fetchPeerInfo() 的 await 回来才写 dataset，
    // 严格晚于首渲染。所以「渲染时 dataset 里已经有版本号」这个前提在真实页面上
    // 永远不成立——上一条用例正是靠预置 dataset 才绿的，它证明不了版本号会显示。
    const { root, body, observed } = loadPanel({ appVersion: '' });

    assert.equal(
        findDescendant(root, (n) => typeof n.textContent === 'string' && n.textContent.includes('1.19.0')),
        null,
        'version must be absent before peer-info lands',
    );

    const watcher = observed.find((o) => o.target === body);
    assert.ok(watcher, 'the panel must observe <body> for the state app.js publishes there');
    assert.ok(
        watcher.options.attributeFilter.includes('data-app-version'),
        'the observer must watch data-app-version',
    );

    body.dataset.appVersion = '1.19.0';
    watcher.fire();

    assert.ok(
        findDescendant(root, (n) => typeof n.textContent === 'string' && n.textContent.includes('1.19.0')),
        'after peer-info lands the panel must re-render and show the version',
    );
});

test('the timestamps row reads an absent attribute as OFF, matching the phone default', () => {
    // FlikkySettings.sessionTimestampEnabled 默认 false。写成 `!== 'off'` 会在
    // peer-info 到达前把默认值猜反，显示「已开启」，而且之后再也不纠正。
    const { root } = loadPanel({ timestamps: '' });
    assert.ok(
        findDescendant(root, (n) => n.textContent === 'app.settings.timestampsSubOff'),
        'with no data-timestamps attribute the row must read as off',
    );
});
