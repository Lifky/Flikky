const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');
// v1.19.0: message-action-bar styles live in chat.css now, not app.css.
const appCss = fs.readFileSync(path.join(webDir, 'chat.css'), 'utf8');
const appHtml = fs.readFileSync(path.join(webDir, 'app.html'), 'utf8');
const i18nJs = fs.readFileSync(path.join(webDir, 'i18n.js'), 'utf8');

// mediaKind..executeMessageAction 之间是纯函数区（含 buildMessageActions），vm 可跑。
const start = appJs.indexOf('function mediaKind');
const end = appJs.indexOf('function executeMessageAction');
assert.ok(start >= 0 && end > start, 'action helpers not found in app.js');
const slice = appJs.slice(start, end);

function fakeBubble({ kind, mime = '', mine = false, failed = false, uploading = false, transferring = false, fileId = '', messageId = '' }) {
    const classes = [];
    if (mine) classes.push('me');
    if (failed) classes.push('failed');
    if (uploading) classes.push('uploading');
    if (transferring) classes.push('transferring');
    return {
        classList: { contains: (className) => classes.includes(className) },
        dataset: { kind, mime, fileId, messageId, name: 'f.bin' },
    };
}

function actionsOf(bubble, recallOn, allowPeerRecall = false) {
    const context = { bubble, recallOn, allowPeerRecall };
    vm.createContext(context);
    vm.runInContext(`${slice}\nglobalThis.result = buildMessageActions(bubble, recallOn, allowPeerRecall).map(action => action.kind);`, context);
    return Array.from(context.result);
}

test('text bubbles offer copy; own text adds recall only when enabled', () => {
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'text', messageId: '5' }), true), ['copy']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'text', mine: true, messageId: '5' }), true), ['copy', 'recall']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'text', mine: true, messageId: '5' }), false), ['copy']);
});

test('peer messages add recall only when peer recall is enabled', () => {
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'text', messageId: '5' }), true, false), ['copy']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'text', messageId: '5' }), true, true), ['copy', 'recall']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', fileId: '9', mime: 'application/pdf', messageId: '5' }), true, true), ['download', 'recall']);
});

test('preview actions use the Visibility icon', () => {
    assert.match(appJs, /kind: 'preview', icon: 'visibility'/);
    assert.doesNotMatch(appJs, /kind: 'preview', icon: 'photo_library'/);
});

test('completed classic file offers download; media adds preview first', () => {
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', fileId: '9', mime: 'application/pdf', messageId: '5' }), true), ['download']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', fileId: '9', mime: 'image/png', messageId: '5' }), true), ['preview', 'download']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', fileId: '9', mime: 'video/mp4', mine: true, messageId: '5' }), true), ['preview', 'download', 'recall']);
});

test('in-progress and failed bubbles gate download/preview; own keeps recall', () => {
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', uploading: true, mine: true, messageId: '5' }), true), ['recall']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', transferring: true, fileId: '9', mime: 'image/png', mine: true, messageId: '5' }), true), ['recall']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', failed: true, fileId: '9', mime: 'image/png' }), true), []);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', failed: true, mine: true, messageId: '5' }), true), ['recall']);
});

test('recall needs a server-side message id (pre-upload bubbles have none)', () => {
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'file', uploading: true, mine: true }), true), []);
});

test('action bar CSS: tonal sizing matches the app, danger remaps to error tokens, INLINE is a persistent wrapped row', () => {
    assert.match(appCss, /\.msg-actions button\s*\{[^}]*width:\s*36px/);
    assert.match(appCss, /\.msg-actions \.material-symbols-outlined\s*\{[^}]*font-size:\s*22px/);
    assert.match(appCss, /\.msg-actions button\.danger\s*\{[^}]*--mdui-color-secondary-container:\s*var\(--mdui-color-error-container\)/);
    assert.match(appCss, /body\[data-action-style="INLINE"\] \.msg-actions\s*\{[^}]*flex-basis:\s*100%/);
    assert.match(appCss, /\.bubble-row\s*\{[^}]*flex-wrap:\s*wrap/);
});

test('default action style is INLINE and settings drive the body attribute idempotently', () => {
    assert.match(appHtml, /<body[^>]*data-action-style="INLINE"/);
    assert.match(appJs, /let actionStyle = 'INLINE'/);
    assert.match(appJs, /function normalizeActionStyle/);
    assert.match(appJs, /document\.body\.dataset\.actionStyle/);
    assert.match(appJs, /nextStyle !== actionStyle \|\| recallEnabled !== prevRecall/);
});

test('message rows enter with MD3-style motion controlled by the synced animation speed', () => {
    // v1.19.0: the enter animation is scoped to the .bubble-row--enter modifier
    // class, not the base .bubble-row rule — otherwise every list rebuild would
    // replay the animation for every existing row (a "flash" on re-render).
    assert.match(appCss, /@keyframes flikky-message-enter/);
    assert.match(appCss, /\.bubble-row--enter\s*\{[^}]*animation-name:\s*flikky-message-enter/);
    assert.match(appCss, /animation-duration:\s*var\(--flikky-message-enter-duration\)/);
    assert.match(appCss, /@media \(prefers-reduced-motion:\s*reduce\)[\s\S]*?\.bubble-row--enter[\s\S]*?animation-duration:\s*0ms/);
    assert.match(appJs, /function applyAnimationSpeed/);
    assert.match(appJs, /--flikky-message-enter-duration/);
});

function fakeGroupClassList() {
    const classes = new Set();
    return {
        add: (...names) => names.forEach((n) => classes.add(n)),
        remove: (...names) => names.forEach((n) => classes.delete(n)),
        contains: (n) => classes.has(n),
        _classes: classes,
    };
}

function fakeRowElement(tag) {
    return {
        tagName: tag,
        className: '',
        dataset: {},
        classList: fakeGroupClassList(),
        appendChild() {},
    };
}

// vm-slices appendBubbleRow the way save-all.test.js slices its own targets: the
// function is self-contained enough that its free variables (list, document,
// replayingHistory, pendingGroupBreak, avatar helpers) can all be supplied as
// stubs on the vm context instead of booting the whole app.js IIFE.
function appendBubbleRowWithFlag(replayingHistory) {
    const start = appJs.indexOf('    function appendBubbleRow(bubbleEl, origin) {');
    const end = appJs.indexOf('    function rowOrigin(row) {');
    assert.ok(start >= 0 && end > start, 'appendBubbleRow not found in app.js');
    const fnSlice = appJs.slice(start, end);

    const context = {
        list: {
            appendChild() {},
            scrollTop: 0,
            scrollHeight: 0,
        },
        document: { createElement: (tag) => fakeRowElement(tag) },
        lastBubbleOrigin: null,
        replayingHistory,
        pendingGroupBreak: false,
        myAvatarKey: 'icon:desktop_windows',
        phoneAvatarKey: 'icon:smartphone',
        makeAvatarEl: () => fakeRowElement('mdui-avatar'),
        reflowMessageAvatars() {},
        renderMessageActionBar() {},
    };
    vm.createContext(context);
    vm.runInContext(`${fnSlice}\nglobalThis.row = appendBubbleRow({ appendChild() {} }, 'BROWSER');`, context);
    return context.row;
}

test('appendBubbleRow plays the enter animation for a freshly arrived row (C1)', () => {
    const row = appendBubbleRowWithFlag(false);
    assert.equal(row.classList.contains('bubble-row--enter'), true);
});

test('appendBubbleRow suppresses the enter animation while replaying history (C1)', () => {
    const row = appendBubbleRowWithFlag(true);
    assert.equal(row.classList.contains('bubble-row--enter'), false);
});

test('loadHistory guards the replay with replayingHistory and restores it in a finally (C1)', () => {
    // Reuses the same boundary markers session-timestamp.test.js relies on for
    // loadHistory, so this assertion moves in lockstep with that slice.
    const start = appJs.indexOf('    async function loadHistory()');
    const end = appJs.indexOf('\n    let currentConnKey', start);
    assert.ok(start >= 0 && end > start, 'loadHistory not found in app.js');
    const body = appJs.slice(start, end);
    // The assertion that goes red if someone "simplifies" the guard away: the
    // flag must be set before the replay and restored in a finally block (not
    // just at the end of the try), so an early return from the `ordered` branch
    // cannot leave it stuck true.
    assert.match(body, /replayingHistory = true;/);
    assert.match(body, /finally\s*\{[\s\S]*?replayingHistory = false;[\s\S]*?\}/);
});

function applyGroupingScenario(row, sameAsPrev, sameAsNext) {
    const start = appJs.indexOf('    function applyGroupingClasses');
    const end = appJs.indexOf('    function reflowMessageAvatars');
    assert.ok(start >= 0 && end > start, 'applyGroupingClasses not found in app.js');
    const slice = appJs.slice(start, end);
    const context = { row, sameAsPrev, sameAsNext };
    vm.createContext(context);
    vm.runInContext(`${slice}\napplyGroupingClasses(row, sameAsPrev, sameAsNext);`, context);
}

test('applyGroupingClasses marks singleton/first/middle/last runs and clears stale classes (C2)', () => {
    const singleton = fakeRowElement('div');
    applyGroupingScenario(singleton, false, false);
    assert.deepEqual(Array.from(singleton.classList._classes), []);

    const first = fakeRowElement('div');
    applyGroupingScenario(first, false, true);
    assert.equal(first.classList.contains('grouped-start'), true);

    const middle = fakeRowElement('div');
    applyGroupingScenario(middle, true, true);
    assert.equal(middle.classList.contains('grouped-mid'), true);

    const last = fakeRowElement('div');
    applyGroupingScenario(last, true, false);
    assert.equal(last.classList.contains('grouped-end'), true);

    // The case that fails if the leading `remove()` is deleted: a row that
    // already carries a stale class from a previous reflow must have it
    // replaced, not accumulated alongside the new one.
    const stale = fakeRowElement('div');
    stale.classList.add('grouped-mid');
    applyGroupingScenario(stale, false, true);
    assert.deepEqual(Array.from(stale.classList._classes), ['grouped-start']);
});

test('maybeInsertTimeDivider marks a group break only on the branch that actually inserts a divider (C2)', () => {
    const start = appJs.indexOf('    function maybeInsertTimeDivider');
    const end = appJs.indexOf('    // Track last rendered origin');
    assert.ok(start >= 0 && end > start, 'maybeInsertTimeDivider not found in app.js');
    const slice = appJs.slice(start, end);

    const context = {
        list: { appendChild() {} },
        document: { createElement: (tag) => fakeRowElement(tag) },
        formatSessionTimestamp: () => 'ts',
        lastDividerBaseTs: null,
        pendingGroupBreak: false,
        TIME_DIVIDER_GAP_MS: 5 * 60 * 1000,
    };
    vm.createContext(context);
    vm.runInContext(
        `${slice}
        maybeInsertTimeDivider(1000);
        globalThis.afterInsert = pendingGroupBreak;
        pendingGroupBreak = false;
        maybeInsertTimeDivider(1001);
        globalThis.afterSkip = pendingGroupBreak;`,
        context,
    );
    assert.equal(context.afterInsert, true, 'inserting a divider must set the break flag');
    assert.equal(context.afterSkip, false, 'skipping the divider (gap too small) must not set the break flag');
});

test('buildClassicFileContent wraps the category icon in the Cookie9Sided .file-icon container (C3)', () => {
    const start = appJs.indexOf('    function buildClassicFileContent');
    const end = appJs.indexOf('    function applyMediaBubble');
    assert.ok(start >= 0 && end > start, 'buildClassicFileContent not found in app.js');
    const slice = appJs.slice(start, end);

    function fakeEl(tag) {
        return {
            tagName: tag,
            className: '',
            textContent: '',
            children: [],
            appendChild(child) { this.children.push(child); return child; },
        };
    }
    const bubble = {
        classList: { remove() {} },
        firstChild: null,
        dataset: { mime: 'application/pdf' },
        children: [],
        appendChild(child) { this.children.push(child); return child; },
    };
    const context = {
        document: { createElement: fakeEl },
        materialSymbolEl: (name) => { const s = fakeEl('span'); s.__symbol = name; return s; },
        formatSize: () => '1 KB',
        fileSymbolName: () => 'description',
        bubble,
    };
    vm.createContext(context);
    vm.runInContext(`${slice}\nbuildClassicFileContent(bubble, 'f1', 'a.pdf', 100);`, context);

    const iconWrap = bubble.children.find((c) => c.className === 'file-icon');
    assert.ok(iconWrap, 'a .file-icon wrapper must be appended to the bubble');
    assert.equal(iconWrap.children.length, 1, 'the wrapper must contain exactly the category icon');
    assert.equal(iconWrap.children[0].__symbol, 'description');
});

test('copy has an insecure-context fallback and i18n strings exist in both languages', () => {
    assert.match(appJs, /navigator\.clipboard/);
    assert.match(appJs, /execCommand\('copy'\)/);
    for (const key of ['app.copy', 'app.download', 'app.preview', 'app.copied', 'app.copy_failed']) {
        const hits = i18nJs.split(`'${key}'`).length - 1;
        assert.ok(hits >= 2, `${key} must exist in zh-CN and en`);
    }
});

test('language changes refresh action labels without rebuilding message bubbles', () => {
    assert.match(appJs, /i18n\.onChange\(\(\) => \{[\s\S]*?refreshAllMessageActions\(\)/);
});

test('FLOATING mode: hover reveal on desktop, hidden on touch, contextmenu opens the actions menu', () => {
    assert.match(appCss, /body\[data-action-style="FLOATING"\] \.msg-actions\s*\{[^}]*opacity:\s*0/);
    assert.match(appCss, /body\[data-action-style="FLOATING"\] \.bubble-row:hover \.msg-actions\s*\{[^}]*opacity:\s*1/);
    assert.match(appCss, /body\.mobile-ua\[data-action-style="FLOATING"\] \.msg-actions\s*\{[^}]*display:\s*none/);
    assert.match(appJs, /addEventListener\('contextmenu'/);
    assert.match(appJs, /function showActionsMenu/);
    assert.match(appJs, /function attachBubbleGestureHandlers/);
    assert.doesNotMatch(appJs, /function attachRecallHandler/);
    assert.doesNotMatch(appJs, /function showRecallMenu/);
});

test('classic file bubble is fully clickable for download', () => {
    assert.match(appJs, /function triggerDownload/);
    assert.match(appJs, /dataset\.kind !== 'file'/);
    assert.match(appJs, /closest\('a'\)/);
    assert.match(appCss, /\.file-bubble:not\(\.media\):not\(\.uploading\):not\(\.transferring\):not\(\.failed\)\s*\{[^}]*cursor:\s*pointer/);
});

test('server-side transferring bubbles use the shared gesture path', () => {
    assert.match(appJs, /function renderTransferringBubble[\s\S]*?appendBubbleRow\(div,[\s\S]*?attachBubbleGestureHandlers\(div\)/);
});
