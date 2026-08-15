const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');
const appCss = fs.readFileSync(path.join(webDir, 'app.css'), 'utf8');
const start = appJs.indexOf('function computeSaveAllState');
assert.ok(start >= 0, 'save-all state helper not found in app.js');
const end = appJs.indexOf('    const ua', start);
assert.ok(end > start, 'save-all state helper is incomplete');
const slice = appJs.slice(start, end);

function compute(files, savedIds) {
    const context = { files, savedIds };
    vm.createContext(context);
    vm.runInContext(`${slice}\nglobalThis.result = computeSaveAllState(files, savedIds);`, context);
    return {
        visible: context.result.visible,
        unsavedCount: context.result.unsavedCount,
    };
}

test('save-all stays hidden for fewer than two received files', () => {
    assert.deepEqual(compute([{ fileId: 'a', name: 'a.txt' }], new Set()), {
        visible: false,
        unsavedCount: 1,
    });
});

test('save-all remains visible when all received files are already saved', () => {
    assert.deepEqual(compute([
        { fileId: 'a', name: 'a.txt' },
        { fileId: 'b', name: 'b.txt' },
    ], new Set(['a', 'b'])), {
        visible: true,
        unsavedCount: 0,
    });
});

test('save-all shows the number of unsaved received files', () => {
    assert.deepEqual(compute([
        { fileId: 'a', name: 'a.txt' },
        { fileId: 'b', name: 'b.txt' },
        { fileId: 'c', name: 'c.txt' },
    ], new Set(['b'])), {
        visible: true,
        unsavedCount: 2,
    });
});

test('save-all dropdown creates a positioned box above the chat list', () => {
    const rule = appCss.match(/#save-all-dropdown\s*\{[^}]*\}/)?.[0] ?? '';

    assert.match(rule, /display:\s*inline-block/);
    assert.match(rule, /position:\s*absolute/);
});
