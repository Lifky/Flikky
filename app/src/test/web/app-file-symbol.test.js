const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');

const start = appJs.indexOf('const DOCUMENT_MIMES');
const end = appJs.indexOf('M9b: Avatar constants');
assert.ok(start >= 0 && end > start, 'file symbol helpers not found in app.js');
const slice = appJs.slice(start, appJs.lastIndexOf('\n', end));

function symbolOf(mime) {
    const context = {};
    vm.createContext(context);
    vm.runInContext(`${slice}\nglobalThis.result = fileSymbolName(${JSON.stringify(mime)});`, context);
    return context.result;
}

test('fileSymbolName mirrors app-side category icons', () => {
    // 镜像 FilesListBuilder.categoryOf + FileCategoryUi.iconResource 的映射。
    assert.equal(symbolOf('image/png'), 'image');
    assert.equal(symbolOf('video/mp4'), 'movie');
    assert.equal(symbolOf('audio/mpeg'), 'audio_file');
    assert.equal(symbolOf('application/pdf'), 'description');
    assert.equal(symbolOf('text/plain'), 'description');
    assert.equal(symbolOf('application/zip'), 'draft');
    assert.equal(symbolOf(''), 'draft');
    assert.equal(symbolOf(undefined), 'draft');
});

test('fileSymbolName treats svg as other despite image prefix', () => {
    // SVG 与 App 端一致归「其他」：系统不当媒体处理。
    assert.equal(symbolOf('image/svg+xml'), 'draft');
    assert.equal(symbolOf('IMAGE/SVG+XML'), 'draft');
});

test('every classic file bubble render path leads with the category icon', () => {
    // 经典气泡按分类取图标（不再写死 description）。
    assert.ok(appJs.includes('materialSymbolEl(fileSymbolName(bubble.dataset.mime), false)'));
    assert.ok(!appJs.includes("materialSymbolEl('description'"));
    // 传输中 / 上传中骨架也带图标，完成后不需要补。
    assert.match(appJs, /materialSymbolEl\(fileSymbolName\(msg\.mime\), false\)/);
    assert.match(appJs, /materialSymbolEl\(fileSymbolName\(opts\.mime\), false\)/);
});

test('markBubbleCompleted rebuilds non-media bubbles through buildClassicFileContent', () => {
    // 完成态两条路径整体重建，非媒体不再只打补丁（旧版补不上缺失的图标）。
    const fn = appJs.slice(
        appJs.indexOf('function markBubbleCompleted'),
        appJs.indexOf('function markBubbleFailed'),
    );
    assert.match(fn, /applyMediaBubble\(bubble, dto\.fileId, dto\.name, dto\.sizeBytes, kind\)/);
    assert.match(fn, /buildClassicFileContent\(bubble, dto\.fileId, dto\.name, dto\.sizeBytes\)/);
});
