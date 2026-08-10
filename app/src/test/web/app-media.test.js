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

test('mediaKind treats svg as non-media, matching the app', () => {
    // SVG 归「其他」：无预览操作、无 lightbox、无缩略图气泡（与 App categoryOf 一致）。
    assert.equal(kindOf('image/svg+xml'), null);
    assert.equal(kindOf('IMAGE/SVG+XML'), null);
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

test('media bubble is frameless like the app: edge-to-edge image, no width fight', () => {
    // 无边框：气泡零 padding，图片边到边，由气泡自身圆角 + overflow:hidden 裁顶角
    // （不给图片单独圆角——radius-4px 与 padding 不同心是旧方案的角部瑕疵）。
    // 宽度唯一事实源 = 图片 max 约束：气泡 max-width:none 压掉 base .file-bubble 的
    // 70% / calc(100vw-96px)，否则窄窗口下气泡被压得比 240px 图片窄、图片顶穿右缘。
    const media = appCss.match(/\.file-bubble\.media\s*\{[^}]*\}/);
    assert.match(media[0], /padding:\s*0\s*;/);
    assert.match(media[0], /overflow:\s*hidden/);
    assert.match(media[0], /max-width:\s*none/);

    const thumb = appCss.match(/\.file-bubble\.media \.thumb\s*\{[^}]*\}/);
    assert.doesNotMatch(thumb[0], /border-radius/);

    // caption 自带内衬（气泡 padding 归零后文字不能贴边）。
    const caption = appCss.match(/\.file-bubble\.media \.thumb-caption\s*\{[^}]*\}/);
    assert.match(caption[0], /padding:/);
});

test('lightbox media loads via the authenticated inline url', () => {
    assert.match(appJs, /\/api\/files\/\$\{fileId\}\?inline=1/);
    assert.match(appJs, /\/api\/files\/\$\{fileId\}\/thumb/);
});
