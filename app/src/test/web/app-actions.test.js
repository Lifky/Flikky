const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');
const appCss = fs.readFileSync(path.join(webDir, 'app.css'), 'utf8');
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

function actionsOf(bubble, recallOn) {
    const context = { bubble, recallOn };
    vm.createContext(context);
    vm.runInContext(`${slice}\nglobalThis.result = buildMessageActions(bubble, recallOn).map(action => action.kind);`, context);
    return Array.from(context.result);
}

test('text bubbles offer copy; own text adds recall only when enabled', () => {
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'text', messageId: '5' }), true), ['copy']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'text', mine: true, messageId: '5' }), true), ['copy', 'recall']);
    assert.deepEqual(actionsOf(fakeBubble({ kind: 'text', mine: true, messageId: '5' }), false), ['copy']);
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
    assert.match(appCss, /\.msg-actions mdui-button-icon\s*\{[^}]*width:\s*36px/);
    assert.match(appCss, /\.msg-actions \.material-symbols-outlined\s*\{[^}]*font-size:\s*22px/);
    assert.match(appCss, /\.msg-actions mdui-button-icon\.danger\s*\{[^}]*--mdui-color-secondary-container:\s*var\(--mdui-color-error-container\)/);
    assert.match(appCss, /body\[data-action-style="INLINE"\] \.msg-actions\s*\{[^}]*flex-basis:\s*100%/);
    assert.match(appCss, /\.bubble-row\s*\{[^}]*flex-wrap:\s*wrap/);
});

test('default action style is FLOATING and settings drive the body attribute idempotently', () => {
    assert.match(appHtml, /<body[^>]*data-action-style="FLOATING"/);
    assert.match(appJs, /function normalizeActionStyle/);
    assert.match(appJs, /document\.body\.dataset\.actionStyle/);
    assert.match(appJs, /nextStyle !== actionStyle \|\| recallEnabled !== prevRecall/);
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
