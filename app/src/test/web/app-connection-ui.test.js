const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');
const appCss = fs.readFileSync(path.join(webDir, 'app.css'), 'utf8');
const appHtml = fs.readFileSync(path.join(webDir, 'app.html'), 'utf8');

test('connection loss uses a blurred dialog that users cannot dismiss', () => {
    const dialogTag = appHtml.match(/<mdui-dialog id="connection-dialog"[^>]*>/)?.[0] ?? '';

    assert.ok(dialogTag, 'connection dialog must exist');
    assert.doesNotMatch(dialogTag, /close-on-overlay-click/);
    assert.doesNotMatch(dialogTag, /close-on-esc/);
    assert.match(appCss, /#connection-dialog::part\(overlay\)\s*\{[^}]*backdrop-filter:\s*blur\(/);
    assert.match(appJs, /function showConnectionDialog[\s\S]*?textContent\s*=\s*t\([\s\S]*?\.open\s*=\s*true/);
    assert.match(appJs, /function hideConnectionDialog[\s\S]*?\.open\s*=\s*false/);
    assert.doesNotMatch(appHtml, /id="conn-banner"/);
});

test('default connection watermark stays centered in the chat viewport while messages scroll', () => {
    assert.match(appHtml, /<div id="chat-list-shell" class="chat-list-shell">\s*<div id="list" class="chat-list"><\/div>\s*<\/div>/);
    assert.match(appCss, /\.chat-list-shell\s*\{[^}]*position:\s*relative[^}]*overflow:\s*hidden/);
    assert.match(appCss, /\.chat-list\s*\{[^}]*height:\s*100%[^}]*overflow-y:\s*auto/);
    assert.match(appJs, /listShell\.appendChild\(wm\)/);
    assert.doesNotMatch(appJs, /list\.appendChild\(wm\)/);
});
