const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');
// v1.19.0: connection dialog / chat-list-shell styles live in chat.css now.
const appCss = fs.readFileSync(path.join(webDir, 'chat.css'), 'utf8');
const appHtml = fs.readFileSync(path.join(webDir, 'app.html'), 'utf8');

test('connection loss uses a blurred dialog that users cannot dismiss', () => {
    const dialogTag = appHtml.match(/<mdui-dialog id="connection-dialog"[^>]*>/)?.[0] ?? '';

    assert.ok(dialogTag, 'connection dialog must exist');
    assert.doesNotMatch(dialogTag, /close-on-overlay-click/);
    assert.doesNotMatch(dialogTag, /close-on-esc/);
    // 图标字形现在挂在 data-icon 上（base.css 用 ::before 生成，见那里的注释）——
    // 不再是元素的文本内容。
    assert.match(appHtml, /slot="icon"[^>]*data-icon="signal_disconnected"/);
    assert.doesNotMatch(appHtml, /slot="icon"[^>]*>wifi_off<\/span>/);
    assert.match(appCss, /#connection-dialog::part\(overlay\)\s*\{[^}]*backdrop-filter:\s*blur\(/);
    assert.match(appCss, /#connection-dialog::part\(header\)\s*\{[^}]*align-items:\s*center/);
    assert.match(appCss, /#connection-dialog::part\(headline\)\s*\{[^}]*justify-content:\s*center/);
    assert.match(appCss, /#connection-dialog::part\(body\)\s*\{[^}]*text-align:\s*center/);
    assert.match(appJs, /function showConnectionDialog[\s\S]*?textContent\s*=\s*t\([\s\S]*?\.open\s*=\s*true/);
    assert.match(appJs, /function hideConnectionDialog[\s\S]*?\.open\s*=\s*false/);
    assert.doesNotMatch(appHtml, /id="conn-banner"/);
});

test('default connection watermark stays centered in the chat viewport while messages scroll', () => {
    // 见 app-shell.test.js 同名断言：钉父子关系，不钉 class 字面量。
    assert.match(appHtml, /<div id="chat-list-shell" class="chat-list-shell">\s*<div id="list" class="[^"]*\bchat-list\b[^"]*"><\/div>/);
    assert.match(appCss, /\.chat-list-shell\s*\{[^}]*position:\s*relative[^}]*overflow:\s*hidden/);
    assert.match(appCss, /\.chat-list\s*\{[^}]*height:\s*100%[^}]*overflow-y:\s*auto/);
    assert.match(appJs, /listShell\.appendChild\(wm\)/);
    assert.doesNotMatch(appJs, /list\.appendChild\(wm\)/);
});
