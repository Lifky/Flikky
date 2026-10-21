const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody } = require('./scan.js');

// v1.24 装机反馈：手机浏览器在系统选择器里挑图、回到页面后，自己刚发的图出现两个气泡（其他设备正常）。
// 页面在后台时 WebSocket 断了，自己那条 file_added 广播没收到 —— 平时它负责把这条记进 seen ——
// 重连后拉历史，历史里有它，本地上传气泡也已完成，于是又画了一个。
const WEB = path.join(__dirname, '../../main/assets/web');
const source = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');

function listOf(nodes) {
    return {
        querySelectorAll: () => [...nodes.values()],
        querySelector: (sel) => {
            const m = String(sel).match(/"(\d+)"/);
            return m ? nodes.get(m[1]) || null : null;
        },
    };
}

test('history does not draw a message this page already shows', async () => {
    const rendered = [];
    const nodes = new Map([['7', { dataset: { messageId: '7' }, classList: { contains: () => false } }]]);
    const history = {
        ordered: [
            { kind: 'file', id: 7, status: 'COMPLETED', origin: 'BROWSER', fileId: 'f7' },
            { kind: 'text', id: 8, origin: 'PHONE', content: 'hi' },
        ],
    };
    const context = {
        currentWs: {}, serverStopped: false, seen: new Set(), recalledMessageIds: new Set(),
        list: listOf(nodes),
        apiFetch: async () => ({ ok: true, status: 200, json: async () => history }),
        renderFile: (m) => { rendered.push(m.id); return { dataset: {} }; },
        renderText: (m) => rendered.push(m.id),
        renderTransferringBubble: (m) => rendered.push(m.id),
        reconcileHistorySnapshot() {}, setFavoritedIds() {}, refreshSaveAllFab() {},
    };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'async function loadHistory('), context);
    await context.loadHistory();
    assert.deepEqual(rendered, [8]);
    assert.ok(context.seen.has('file_added:7'), 'and it is remembered, so a later replay skips it too');
});

test('a finished upload is remembered like a received broadcast', () => {
    const context = {
        seen: new Set(),
        mediaKind: () => null,
        buildClassicFileContent() {}, applyMediaBubble() {}, attachBubbleGestureHandlers() {},
        renderMessageActionBar() {}, refreshSaveAllFab() {},
    };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'function markBubbleCompleted('), context);
    const bubble = { dataset: { mime: '' }, classList: { remove() {} }, closest: () => ({}) };
    context.markBubbleCompleted(bubble, { id: 7, fileId: 'f7', name: 'a.jpg', sizeBytes: 1 });
    assert.ok(context.seen.has('file_added:7'));
});
