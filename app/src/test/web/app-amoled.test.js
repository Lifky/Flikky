const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEB = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');
const loginJs = fs.readFileSync(path.join(WEB, 'login.js'), 'utf8');
const exportJs = fs.readFileSync(path.join(WEB, 'export.js'), 'utf8');
const tokens = fs.readFileSync(path.join(WEB, 'tokens.css'), 'utf8');

// applyTheme 住在 app.js 的 IIFE 里；按既有做法按标记切片，不动它的顶层结构。
const start = appJs.indexOf('let lastThemeKey = null;');
const end = appJs.indexOf('// 气泡圆角双端联动');
assert.ok(start >= 0 && end > start, 'applyTheme slice markers not found in app.js');
const slice = appJs.slice(start, end);

function run(script, options) {
  const attrs = {};
  const calls = [];
  const ctx = {
    console,
    document: {
      documentElement: {
        setAttribute(name, value) { attrs[name] = String(value); },
        getAttribute(name) { return name in attrs ? attrs[name] : null; },
        style: { setProperty() {}, removeProperty() {} },
      },
    },
  };
  if (!options || options.mdui !== false) {
    ctx.mdui = {
      setTheme(v) { calls.push(['setTheme', v]); },
      setColorScheme(v) { calls.push(['setColorScheme', v]); },
      removeColorScheme() { calls.push(['removeColorScheme']); },
    };
  }
  ctx.window = ctx;
  vm.runInNewContext(`${slice}\n${script}`, ctx, { filename: 'app.js#applyTheme' });
  return { attrs, calls };
}

test('light plus amoled is still light — AMOLED is a dark variant only', () => {
  const { attrs, calls } = run("applyTheme('#33618D', false, true);");
  assert.deepEqual(calls, [['setTheme', 'light'], ['setColorScheme', '#33618D']]);
  assert.equal(attrs['data-amoled'], '0');
});

test('dark without amoled keeps the normal dark surfaces', () => {
  const { attrs, calls } = run("applyTheme('#33618D', true, false);");
  assert.deepEqual(calls, [['setTheme', 'dark'], ['setColorScheme', '#33618D']]);
  assert.equal(attrs['data-amoled'], '0');
});

test('dark plus amoled flips the page to pure black', () => {
  const { attrs, calls } = run("applyTheme('#33618D', true, true);");
  assert.deepEqual(calls, [['setTheme', 'dark'], ['setColorScheme', '#33618D']]);
  assert.equal(attrs['data-amoled'], '1');
});

test('a missing amoled field falls back to off', () => {
  // 旧版手机端不推这个字段；缺失必须当 false，不能变成 undefined 写进属性。
  const { attrs } = run("applyTheme('#33618D', true);");
  assert.equal(attrs['data-amoled'], '0');
});

test('toggling only amoled is not swallowed by the theme cache', () => {
  // lastThemeKey 只拼 dark|seed 时，AMOLED 开关会被当成「同一主题」提前 return。
  // 这正是「设置里点了但界面没变」这类线上 bug 的形状。
  const { attrs } = run("applyTheme('#33618D', true, false); applyTheme('#33618D', true, true);");
  assert.equal(attrs['data-amoled'], '1');
});

test('turning amoled back off also takes effect', () => {
  const { attrs } = run("applyTheme('#33618D', true, true); applyTheme('#33618D', true, false);");
  assert.equal(attrs['data-amoled'], '0');
});

test('the attribute is written even when mdui is absent', () => {
  // 纯黑底色由 tokens.css 的 [data-amoled="1"] 提供，跟 mdui 在不在无关。
  // 写在 `if (!window.mdui) return` 之后，页面在 mdui 加载失败时会停在深灰。
  const { attrs, calls } = run("applyTheme('#33618D', true, true);", { mdui: false });
  assert.deepEqual(calls, []);
  assert.equal(attrs['data-amoled'], '1');
});

test('applyPeerAppearance forwards the field it just started receiving', () => {
  assert.match(appJs, /applyTheme\([^)]*data\.amoled[^)]*\)/);
});

test('the login and export pages honour it too', () => {
  // 不然登录页 / 导出页到主界面会闪一次配色 —— 同一台手机，两种底色。
  assert.match(loginJs, /data-amoled/);
  assert.match(loginJs, /data\.amoled/);
  assert.match(exportJs, /data-amoled/);
  assert.match(exportJs, /data\.amoled/);
});

test('the token layer keys AMOLED off the same attribute', () => {
  assert.match(tokens, /\[data-amoled="1"\][\s\S]{0,300}--flikky-page-bg:\s*#000/);
});

test('the server already sends what the browser now reads', () => {
  // 浏览器端读 data.amoled；两个 DTO 都得真的有这个字段，否则这一整轮是空转。
  const dtos = fs.readFileSync(
    path.join(__dirname, '../../main/java/com/example/flikky/server/dto/Dtos.kt'),
    'utf8',
  );
  const blocks = ['PeerInfoDto', 'WebThemeDto'];
  for (const name of blocks) {
    const at = dtos.indexOf(`data class ${name}(`);
    assert.notEqual(at, -1, `${name} not found in Dtos.kt`);
    const body = dtos.slice(at, dtos.indexOf('\n)', at));
    assert.match(body, /val amoled: Boolean/, `${name} does not carry amoled`);
  }
});
