const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const html = read('app.html');
const appJs = read('app.js');

test('the shell carries a files destination in both rail and navbar', () => {
  assert.match(html, /class="fk-rail-item"[^>]*data-dest="files"/);
  assert.match(html, /class="fk-navbar-item"[^>]*data-dest="files"/);
  assert.match(html, /<div class="fk-view" id="view-files"[^>]*hidden>/);
  // panel-files.js 必须在 app.js 之后加载：面板依赖 app.js 导出的 fileSymbolName
  // 与「收起功能栏」的委派监听器。
  const order = ['app.js', 'panel-files.js'].map((f) => html.indexOf('/static/' + f));
  assert.ok(order[0] > 0 && order[1] > order[0], 'bad script order: ' + order);
  // CSP script-src 'self' —— 不得出现内联 script。
  assert.deepEqual(html.match(/<script(?![^>]*\bsrc=)[^>]*>/g) || [], []);
});

test('all four navbar items keep a visible label (deliberate MD3 deviation)', () => {
  // 2026-08-29 用户裁决：官方 LABEL_VISIBILITY_AUTO 在 4 项及以上只显示选中项 label，
  // 这里刻意四项全显示——「文件」是全新目的地，可发现性优先于拥挤。
  // 这条断言把「有意偏离」钉成契约：将来有人按官方默认把 label 藏掉会立刻转红。
  const navbar = html.match(/<nav class="fk-navbar"[\s\S]*?<\/nav>/)[0];
  const labels = [...navbar.matchAll(/data-i18n="app\.nav\.[a-z]+"/g)];
  assert.equal(labels.length, 4, 'each navbar item must ship its own label span');
});

test('the files destination is driven by the storage browsing switch', () => {
  // applyStorageBrowsing 是事实点。开关关闭时目的地必须消失，且兜底切换要用
  // navigate:false —— 窄屏上连 mobileDest 一起改，用户会在广播到达的瞬间被从
  // 会话页甩走，而他并没有点任何东西（app.js 的 favoriteEnabled 那段注释记的就是这个坑）。
  const at = appJs.indexOf('function applyStorageBrowsing(');
  assert.ok(at > 0, 'applyStorageBrowsing is where the switch must be honoured');
  const body = appJs.slice(at, at + 700);
  assert.match(body, /\[data-dest="files"\]/);
  assert.match(body, /hidden = !enabled/);
  assert.match(body, /navigate:\s*false/);
});

test('the default destination is the first available one, applied only once', () => {
  // 主开关默认关闭。写死「默认 files」会让默认安装状态下功能栏空着。
  const fdAt = appJs.indexOf('function firstAvailableDest(');
  assert.ok(fdAt > 0, 'no firstAvailableDest');
  assert.ok((appJs.match(/firstAvailableDest\(\)/g) || []).length >= 1,
    'firstAvailableDest is declared but never called');
  // 光有声明和调用不够 —— 逼红时把函数体换成 `return 'files';` 一条都没红。
  // 必须钉住「它真的从 DOM 推导」：查 rail 项、按 hidden 过滤。
  const fdBody = appJs.slice(fdAt, appJs.indexOf('\n    }', fdAt));
  assert.match(fdBody, /querySelectorAll\('\.fk-rail-item'\)/,
    'firstAvailableDest must read the rail, not hard-code a destination');
  assert.match(fdBody, /hidden/, 'it must skip hidden destinations');
  assert.equal(/return '(files|favorites)';/.test(fdBody), false,
    'firstAvailableDest must not hard-code a specific destination');
  // 只施加一次：peer-info 与 settings_changed 走同一个处理函数，不加闸就变成
  // 「每次改设置都把用户拽回第一个目的地」。
  const at = appJs.indexOf('function applyDefaultFocusOnce(');
  assert.ok(at > 0, 'no applyDefaultFocusOnce');
  assert.match(appJs.slice(at, at + 260), /if \(defaultFocusApplied\) return;/);
});

test('every icon span in the shell is data-icon driven and carries no DOM text', () => {
  // 这条只管 data-icon 驱动 + 无 DOM 文本（长按取词）。「对读屏隐藏」是另一件事，
  // 由 app-a11y-nav.test.js 单独钉——原名号称 hidden from AT 但断言里根本没查，
  // 又是一次「断言绿着、它命名的东西没被测」。
  for (const m of html.matchAll(/<span([^>]*class="material-symbols-outlined"[^>]*)>([^<]*)<\/span>/g)) {
    assert.match(m[1], /data-icon="[a-z0-9_]+"/, 'icon span without data-icon: ' + m[0]);
    assert.equal(m[2].trim(), '', 'icon span still carries text: ' + m[0]);
  }
});

test('the new files strings exist in both dictionaries', () => {
  // 直接比对两份字典的键集合，不走 t()：t() 会回落 zh-CN，
  // 于是「英文缺一个键」永远看不见（v1.19.0 踩过）。
  const i18n = read('i18n.js');
  // 键名一份带引号（'zh-CN'）一份不带（en），两种都要认——写死一种会让这条断言
  // 在「字典找不到」上转红，而不是在它真正要查的「英文缺键」上。
  const dictOf = (lang) => {
    const at = [`'${lang}': {`, `${lang}: {`]
      .map((needle) => i18n.indexOf(needle))
      .find((i) => i > 0);
    assert.ok(at !== undefined && at > 0, 'dictionary ' + lang + ' not found');
    const next = i18n.indexOf('\n        },', at);
    assert.ok(next > at, 'dictionary ' + lang + ' has no terminator');
    return new Set([...i18n.slice(at, next).matchAll(/'([\w.]+)':/g)].map((m) => m[1]));
  };
  const zh = dictOf('zh-CN');
  const en = dictOf('en');
  // 防空转：切片没命中就得到空集合，而空集合让下面两条断言无条件通过。
  assert.ok(zh.size > 50, 'zh-CN dictionary looks empty: ' + zh.size);
  assert.ok(en.size > 50, 'en dictionary looks empty: ' + en.size);
  for (const key of ['app.nav.files', 'app.files.title', 'app.files.loading', 'app.files.empty']) {
    assert.ok(zh.has(key), 'zh-CN missing ' + key);
  }
  assert.deepEqual([...zh].filter((k) => !en.has(k)), [],
    'keys present in zh-CN but missing in en');
});
