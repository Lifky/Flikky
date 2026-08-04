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

test('lightbox media loads via the authenticated inline url', () => {
    assert.match(appJs, /\/api\/files\/\$\{fileId\}\?inline=1/);
    assert.match(appJs, /\/api\/files\/\$\{fileId\}\/thumb/);
});
