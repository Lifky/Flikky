const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');
// v1.19.0: save-all FAB/dropdown styles live in chat.css now, not app.css.
const appCss = fs.readFileSync(path.join(webDir, 'chat.css'), 'utf8');
const start = appJs.indexOf('function computeSaveAllState');
assert.ok(start >= 0, 'save-all state helper not found in app.js');
const end = appJs.indexOf('    const ua', start);
assert.ok(end > start, 'save-all state helper is incomplete');
const slice = appJs.slice(start, end);

const actionsStart = appJs.indexOf('    function triggerDownload');
const actionsEnd = appJs.indexOf('    function saveAllAsZip', actionsStart);
assert.ok(actionsStart >= 0 && actionsEnd > actionsStart, 'save-all actions not found in app.js');
const actionsSlice = appJs.slice(actionsStart, actionsEnd);

function compute(files) {
    const context = { files };
    vm.createContext(context);
    vm.runInContext(`${slice}\nglobalThis.result = computeSaveAllState(files);`, context);
    return {
        visible: context.result.visible,
        fileCount: context.result.fileCount,
    };
}

test('save-all stays hidden for fewer than two received files', () => {
    assert.deepEqual(compute([{ fileId: 'a', name: 'a.txt' }]), {
        visible: false,
        fileCount: 1,
    });
});

test('save-all remains visible for two received files', () => {
    assert.deepEqual(compute([
        { fileId: 'a', name: 'a.txt' },
        { fileId: 'b', name: 'b.txt' },
    ]), {
        visible: true,
        fileCount: 2,
    });
});

test('save-all reports the total number of received files', () => {
    assert.deepEqual(compute([
        { fileId: 'a', name: 'a.txt' },
        { fileId: 'b', name: 'b.txt' },
        { fileId: 'c', name: 'c.txt' },
    ]), {
        visible: true,
        fileCount: 3,
    });
});

test('saving individually downloads every received file on every click', async () => {
    const downloads = [];
    const bubbles = [
        {
            dataset: { fileId: 'a', name: 'a.txt' },
            classList: { contains: () => false },
        },
        {
            dataset: { fileId: 'b', name: 'b.txt' },
            classList: { contains: () => false },
        },
    ];
    const context = {
        document: {
            createElement: () => ({
                href: '',
                download: '',
                click() { downloads.push(this.href); },
                remove() {},
            }),
            body: { appendChild() {} },
        },
        list: { querySelectorAll: () => bubbles },
        saveAllDropdown: { hidden: true },
        saveAllEachItem: { textContent: '' },
        saveAllFab: { hidden: false },
        saveAllEachLabel: { textContent: '' },
        setTimeout: (callback) => callback(),
        t: (_key, values) => String(values.count),
        downloads,
    };
    vm.createContext(context);
    vm.runInContext(
        `${slice}
        ${actionsSlice}
        globalThis.runScenario = async () => {
            await saveAllIndividually();
            await saveAllIndividually();
        };
        `,
        context,
    );

    await context.runScenario();
    assert.deepEqual(downloads, [
        '/api/files/a',
        '/api/files/b',
        '/api/files/a',
        '/api/files/b',
    ]);
});

test('save-all dropdown is a self-drawn menu anchored above the FAB', () => {
    // v1.19.0 fix round 1: #save-all-dropdown is no longer the mdui-dropdown wrapper —
    // it is now the plain .fk-fab-menu itself (a column of pill buttons floating above
    // the FAB), because mdui-menu can't reproduce the prototype's per-item stagger-in
    // animation the user explicitly asked to keep.
    const rule = appCss.match(/\.fk-fab-menu\s*\{[^}]*\}/)?.[0] ?? '';
    assert.match(rule, /position:\s*absolute/);
    assert.match(rule, /display:\s*flex/);
    assert.match(rule, /flex-direction:\s*column/);

    assert.match(appCss, /\.fk-fab-menu\[hidden\]\s*\{[^}]*display:\s*none/);
});

test('refreshSaveAllFab closes the menu when the FAB disappears (G7 desync guard)', () => {
    // actionsSlice 只切到 saveAllAsZip 之前，closeSaveAllMenu 定义在它之后——
    // 之前只有 2 文件场景测过 refreshSaveAllFab，state.visible 恒为 true，
    // `if (!state.visible) closeSaveAllMenu()` 这条分支从没被跑到过。
    // 1 文件场景会让它跑到，vm 里没有 closeSaveAllMenu 就会抛 ReferenceError。
    let closed = 0;
    const bubbles = [
        {
            dataset: { fileId: 'a', name: 'a.txt' },
            classList: { contains: () => false },
        },
    ];
    const context = {
        list: { querySelectorAll: () => bubbles },
        saveAllFab: { hidden: false },
        saveAllEachLabel: { textContent: '' },
        closeSaveAllMenu: () => { closed += 1; },
        t: (_key, values) => String(values.count),
    };
    vm.createContext(context);
    vm.runInContext(
        `${slice}
        ${actionsSlice}
        globalThis.runScenario = () => { refreshSaveAllFab(); };
        `,
        context,
    );

    context.runScenario();
    assert.equal(closed, 1, 'closeSaveAllMenu should run exactly once when the FAB becomes hidden');
    assert.equal(context.saveAllFab.hidden, true);
});
