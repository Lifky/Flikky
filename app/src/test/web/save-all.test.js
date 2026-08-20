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

test('save-all dropdown creates a positioned box above the chat list', () => {
    // v1.19.0: the dropdown moved from its own absolutely-positioned box floating
    // over .chat-list-shell into .fk-dock-row, which is itself already
    // position:absolute + display:flex above the chat list. The dropdown only
    // needs a real box (mdui defaults custom elements to display:contents) that
    // won't get squeezed by the flexible input dock next to it.
    const rule = appCss.match(/#save-all-dropdown\s*\{[^}]*\}/)?.[0] ?? '';
    assert.match(rule, /display:\s*inline-block/);
    assert.match(rule, /flex-shrink:\s*0/);

    const dockRow = appCss.match(/\.fk-dock-row\s*\{[^}]*\}/)?.[0] ?? '';
    assert.match(dockRow, /position:\s*absolute/);
    assert.match(dockRow, /display:\s*flex/);

    assert.match(appCss, /#save-all-dropdown\[hidden\]\s*\{[^}]*display:\s*none/);
});
