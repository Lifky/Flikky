const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const html = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const shell = fs.readFileSync(path.join(WEB, 'shell.css'), 'utf8');
const chat = fs.readFileSync(path.join(WEB, 'chat.css'), 'utf8');
const appJs = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');

const mediaQuery = shell.match(/@media \(max-width:\s*839px\)\s*\{[\s\S]*?\n\}/g);
assert.ok(mediaQuery && mediaQuery.length >= 1, 'shell.css must carry the <840px branch');
const mobile = mediaQuery.join('\n');

test('the bottom navbar offers exactly three destinations, chat included', () => {
  // 窄屏是单栏，会话必须有自己的入口 —— 与桌面档相反（那里会话栏常驻，给入口是伪入口）。
  const nav = html.match(/<nav class="fk-navbar"[\s\S]*?<\/nav>/);
  assert.ok(nav, 'app.html has no .fk-navbar');
  const dests = [...nav[0].matchAll(/data-dest="([a-z]+)"/g)].map((m) => m[1]);
  assert.deepEqual(dests, ['chat', 'favorites', 'settings']);
});

test('no files destination ships before v1.20', () => {
  // 不摆不可用入口（spec §4.1a）。
  assert.equal(/data-dest="files"/.test(html), false);
});

test('the navbar is the Expressive 64dp and clears the system gesture inset', () => {
  const rule = shell.match(/\.fk-navbar\s*\{[^}]*\}/)[0];
  assert.match(rule, /height:\s*var\(--flikky-navbar-h\)/);
  assert.match(shell, /--flikky-navbar-h:\s*64px/);
  assert.match(rule, /padding-bottom:\s*env\(safe-area-inset-bottom/);
});

test('the selected destination shows its indicator in place', () => {
  const rule = shell.match(/\.fk-navbar-item\[aria-selected="true"\]\s+\.fk-navbar-icon\s*\{[^}]*\}/);
  assert.ok(rule, 'no selected-state rule for the navbar indicator');
  assert.match(rule[0], /secondary-container/);
  assert.match(rule[0], /--flikky-icon-fill:\s*1/);
});

test('mobile shows one destination at a time', () => {
  assert.match(mobile, /\[data-mobile-dest="chat"\]\s+\.fk-pillar--panel\s*\{\s*display:\s*none/);
  assert.match(mobile, /\[data-mobile-dest="panel"\]\s+\.fk-pillar--chat\s*\{\s*display:\s*none/);
});

test('the desktop collapse state is neutralised on narrow screens', () => {
  // 桌面存下来的 data-panel="hidden" 若在窄屏仍生效，整栏会被吃掉 —— 面板永远打不开。
  assert.match(mobile, /\[data-panel="hidden"\]\s+\.fk-pillar--panel\s*\{[^}]*flex:\s*1 1 auto/);
  assert.match(mobile, /\.fk-panel-collapse\s*\{\s*display:\s*none/);
});

test('the two desktop layout axes are inert on mobile', () => {
  // 单栏没有「左右镜像」与「两栏对调」可言；rail 与手柄本身已隐藏。
  assert.match(mobile, /\.fk-pillar--rail[\s\S]*display:\s*none/);
  assert.match(mobile, /\.fk-splitter/);
});

test('the mobile-ua floating-actions contract is untouched', () => {
  assert.match(
    `${chat}\n${shell}`,
    /body\.mobile-ua\[data-action-style="FLOATING"\]\s+\.msg-actions\s*\{[^}]*display:\s*none/,
  );
});

test('app.js drives data-mobile-dest and keeps both navigators in sync', () => {
  assert.match(appJs, /function setMobileDest\(/);
  assert.match(appJs, /mobileDest\s*=/);
  // rail 与 navbar 共用一条点击绑定，不各写一遍。原计划写的是 `[data-dest]`
  // 选择器；实际用的是两个类名并列 —— 同样只有一处，但不会误捕将来任何
  // 别的带 data-dest 的元素，所以按精确选择器断言。
  assert.match(
    appJs,
    /querySelectorAll\('\.fk-rail-item, \.fk-navbar-item'\)\.forEach\(\(btn\) => \{\s*\n\s*btn\.addEventListener\('click', \(\) => selectDest\(btn\.dataset\.dest\)\);/,
    'both navigators must share one click binding',
  );
});

test('the mobile destination is deliberately not persisted', () => {
  // 会话是窄屏的落地页。把上次停留的面板存下来，会让人一开页看不到消息。
  assert.equal(/flikky_mobile_dest/.test(appJs), false);
});

// ---------------------------------------------------------------------------
// 以下三条针对本任务真正修掉的缺陷，不是布局描述。
// ---------------------------------------------------------------------------

test('the favoriteEnabled fallback is not treated as a navigation', () => {
  // peer-info 带回 favoriteEnabled=false 时要把「收藏」视图换成「设置」。
  // 这个切换若走普通导航路径，会连带把 mobileDest 从 chat 改成 panel ——
  // 窄屏用户正在看消息，peer-info 一到就被甩进设置页，而他什么都没点。
  assert.match(
    appJs,
    /selectDest\('settings',\s*\{\s*navigate:\s*false\s*\}\)/,
    'the favoriteEnabled fallback must pass navigate:false',
  );
  assert.match(
    appJs,
    /if \(options && options\.navigate === false\) syncNavbarSelection\(\);/,
    'navigate:false must still re-derive the navbar selection — the view did change',
  );
});

test('the navbar selection is derived from what is actually shown, never set ad hoc', () => {
  // 每个调用点各写一遍 aria-selected 就会分叉出「显示 A 高亮 B」。
  // 唯一的 navbar 写入方是 syncNavbarSelection，它从 DOM 反推当前目的地。
  // 注意锚到 querySelectorAll( 上：点击绑定用的是 '.fk-rail-item, .fk-navbar-item'，
  // 只匹配尾部 ".fk-navbar-item')" 会把它一起算进来。
  const writes = appJs.match(/querySelectorAll\('\.fk-navbar-item'\)/g) || [];
  assert.equal(writes.length, 1, 'exactly one place may write the navbar selection');
  const start = appJs.indexOf('function syncNavbarSelection(');
  assert.notEqual(start, -1, 'syncNavbarSelection must exist');
  const body = appJs.slice(start, appJs.indexOf('function setMobileDest('));
  assert.match(body, /\.fk-view:not\(\[hidden\]\)/, 'it must read the visible view, not a parameter');
  assert.match(body, /dataset\.mobileDest !== 'chat'/);
});

test('setMobileDest owns which column shows, selectDest owns who is in it', () => {
  // 结束标记取 selectDest 上方的注释块开头，不是 `function selectDest(` ——
  // 那段注释本身就在讲 .fk-view，会把这条断言变成永远红的。
  const start = appJs.indexOf('function setMobileDest(');
  const body = appJs.slice(start, appJs.indexOf('    // 点任一导航目的地'));
  assert.ok(body.length > 0 && body.length < 600, 'slice boundaries drifted');
  assert.equal(
    /\.fk-view/.test(body),
    false,
    'setMobileDest must not touch .fk-view hidden — that split is the whole point',
  );
  assert.match(body, /shell\.dataset\.mobileDest = dest === 'chat' \? 'chat' : 'panel'/);
});
