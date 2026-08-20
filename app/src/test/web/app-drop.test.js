const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const appJs = fs.readFileSync(path.join(__dirname, '../../main/assets/web/app.js'), 'utf8');
// v1.19.0: drop-overlay styles live in chat.css now, not app.css.
const appCss = fs.readFileSync(path.join(__dirname, '../../main/assets/web/chat.css'), 'utf8');
const appHtml = fs.readFileSync(path.join(__dirname, '../../main/assets/web/app.html'), 'utf8');
const start = appJs.indexOf('function isDirectoryItem');
const end = appJs.indexOf('function setDropOverlayVisible');
assert.ok(start >= 0 && end > start, 'drop helpers not found in app.js');
const slice = appJs.slice(start, end);

function run(items) {
    const context = { items };
    vm.createContext(context);
    vm.runInContext(`${slice}\nglobalThis.result = splitDropItems({ items });`, context);
    return context.result;
}

function fileItem(name) {
    return {
        kind: 'file',
        webkitGetAsEntry: () => ({ isDirectory: false }),
        getAsFile: () => ({ name }),
    };
}

test('collects plain files and flags folders', () => {
    const folder = {
        kind: 'file',
        webkitGetAsEntry: () => ({ isDirectory: true }),
        getAsFile: () => null,
    };
    const result = run([fileItem('a.txt'), folder, fileItem('b.png')]);
    assert.deepEqual(Array.from(result.files, (file) => file.name), ['a.txt', 'b.png']);
    assert.equal(result.hadFolder, true);
});

test('skips non-file kinds and null files and tolerates a missing entry API', () => {
    const stringItem = { kind: 'string', getAsFile: () => null };
    const nullFile = { kind: 'file', getAsFile: () => null };
    const noEntryApi = { kind: 'file', getAsFile: () => ({ name: 'c.bin' }) };
    const result = run([stringItem, nullFile, noEntryApi]);
    assert.deepEqual(Array.from(result.files, (file) => file.name), ['c.bin']);
    assert.equal(result.hadFolder, false);
});

test('overlay starts hidden and its CSS keeps the hidden attribute effective', () => {
    // .drop-overlay sets display:flex, which outranks the UA sheet's
    // [hidden] { display: none } — an explicit [hidden] rule is required.
    assert.match(appHtml, /<div id="drop-overlay"[^>]*\bhidden\b/);
    assert.match(appCss, /\.drop-overlay\[hidden\]\s*\{\s*display:\s*none\s*;?\s*\}/);
});
