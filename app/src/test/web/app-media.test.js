const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');
const appCss = fs.readFileSync(path.join(webDir, 'app.css'), 'utf8');
const appHtml = fs.readFileSync(path.join(webDir, 'app.html'), 'utf8');

const start = appJs.indexOf('function mediaKind');
const end = appJs.indexOf('function buildClassicFileContent');
assert.ok(start >= 0 && end > start, 'media helpers not found in app.js');
const slice = appJs.slice(start, end);

function kindOf(mime) {
    const context = {};
    vm.createContext(context);
    vm.runInContext(`${slice}\nglobalThis.result = mediaKind(${JSON.stringify(mime)});`, context);
    return context.result;
}

test('mediaKind classifies image and video mimes', () => {
    assert.equal(kindOf('image/jpeg'), 'image');
    assert.equal(kindOf('IMAGE/PNG'), 'image');
    assert.equal(kindOf('video/mp4'), 'video');
    assert.equal(kindOf('application/pdf'), null);
    assert.equal(kindOf(''), null);
    assert.equal(kindOf(undefined), null);
});

test('lightbox starts hidden and its CSS keeps the hidden attribute effective', () => {
    assert.match(appHtml, /<div id="lightbox"[^>]*\bhidden\b/);
    assert.match(appCss, /\.lightbox\[hidden\]\s*\{\s*display:\s*none\s*;?\s*\}/);
});

test('all upload and transfer render paths stash mime on the bubble dataset', () => {
    assert.match(appJs, /div\.dataset\.mime = opts\.mime \|\| ''/);
    assert.match(appJs, /mime: file\.type/);
    assert.match(appJs, /div\.dataset\.mime = msg\.mime \|\| ''/);
});

test('media thumbs scale proportionally inside a max box, bubble hugs the image', () => {
    // 主流聊天方案：等比缩放放进 240x320 边界框，不裁剪（极端比例才由 96px 最小边
    // + cover 兜底）。气泡是纵向 flex + fit-content，宽度由缩略图决定；caption 用
    // width:0 + min-width:100% 不参与固有宽度计算，长文件名不会撑宽气泡。
    const media = appCss.match(/\.file-bubble\.media\s*\{[^}]*\}/);
    assert.ok(media, '.file-bubble.media rule missing');
    assert.match(media[0], /flex-direction:\s*column/);
    assert.match(media[0], /width:\s*fit-content/);

    const thumb = appCss.match(/\.file-bubble\.media \.thumb\s*\{[^}]*\}/);
    assert.ok(thumb, '.file-bubble.media .thumb rule missing');
    assert.match(thumb[0], /max-width:\s*240px/);
    assert.match(thumb[0], /max-height:\s*320px/);
    assert.match(thumb[0], /min-width:\s*96px/);
    assert.match(thumb[0], /min-height:\s*96px/);
    // 固定宽是旧方案（裁剪 + 死空间）——绝不允许回潮。
    assert.doesNotMatch(thumb[0], /[^-]width:\s*240px/);

    const caption = appCss.match(/\.file-bubble\.media \.thumb-caption\s*\{[^}]*\}/);
    assert.ok(caption, '.file-bubble.media .thumb-caption rule missing');
    assert.match(caption[0], /[^-]width:\s*0/);
    assert.match(caption[0], /min-width:\s*100%/);
});

test('lightbox media loads via the authenticated inline url', () => {
    assert.match(appJs, /\/api\/files\/\$\{fileId\}\?inline=1/);
    assert.match(appJs, /\/api\/files\/\$\{fileId\}\/thumb/);
});
