const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * 文件面板头部要有刷新按钮，与收藏面板那个**同一套外观与槽位**。
 *
 * 用户装机验收（2026-09-03）点名了收藏那个元素：
 *   button "刷新" / #fav-refresh / inside header "收藏"
 * 文件面板没有对应的东西 —— 而目录缓存落地后它更是必需：缓存故意不做自动刷新
 * （子目录里待久了父目录可能已经变了），代价就是必须给用户一个手动的出口。
 */

test('the files panel head has a refresh button shaped like the favourites one', () => {
  const html = read('app.html');
  // 收藏那个的形状：.fk-icon-btn + .material-symbols-outlined[data-icon="refresh"]
  const favHead = html.slice(html.indexOf('id="view-favorites"'), html.indexOf('id="view-settings"'));
  assert.ok(favHead.indexOf('id="fav-refresh"') >= 0, 'the reference button moved; update this test');
  assert.ok(favHead.indexOf('data-icon="refresh"') >= 0);

  const js = scan.scrub(read('panel-files.js'));
  const shell = scan.functionBody(js, 'function buildShell(');
  assert.ok(shell, 'no buildShell');
  assert.ok(
    shell.indexOf("icon('refresh')") >= 0,
    'the files head must carry a refresh button using the same icon. Body:' + scan.LF + shell,
  );
  // 同一个类，才谈得上「视觉零差异」。
  const at = shell.indexOf("icon('refresh')");
  const around = shell.slice(Math.max(0, at - 400), at);
  assert.ok(
    around.indexOf("className = 'fk-icon-btn'") >= 0,
    'the refresh button must reuse .fk-icon-btn verbatim. Around:' + scan.LF + around,
  );
  // 槽位：与收藏一致，刷新紧挨在折叠按钮之前。
  assert.ok(
    shell.indexOf("icon('refresh')") < shell.indexOf('fk-panel-collapse'),
    'refresh must sit immediately before the collapse button, as in favourites',
  );
});

test('refresh has its own label key, and it is translated on both locales', () => {
  const js = scan.scrub(read('panel-files.js'));
  assert.ok(js.indexOf("t('app.files.refresh')") >= 0, 'refresh needs its own label key');
  const i18n = read('i18n.js');
  const hits = i18n.split('app.files.refresh').length - 1;
  assert.equal(hits, 2, 'app.files.refresh must exist in both zh and en, found ' + hits);
});

test('refresh reloads the current directory and bypasses any cache', () => {
  const js = scan.scrub(read('panel-files.js'));
  const shell = scan.functionBody(js, 'function buildShell(');
  const at = shell.indexOf("icon('refresh')");
  const wiring = shell.slice(at, at + 500);
  assert.ok(
    wiring.indexOf('load(currentPath') >= 0,
    'refresh must reload the directory currently shown. Wiring:' + scan.LF + wiring,
  );
  // 缓存落地后刷新必须绕过它，否则这个按钮什么也刷不了。
  assert.ok(
    wiring.indexOf('true') >= 0,
    'refresh must pass the cache-bypass flag. Wiring:' + scan.LF + wiring,
  );
});
