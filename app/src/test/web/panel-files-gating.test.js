const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * v1.20.0 装机验收缺陷 1b 的守卫。
 *
 * 症状：主开关关闭时，浏览器一连上就弹「这个位置已经不存在了」（Screenshot_1）。
 * 原因：panel-files.js 末尾 mount(host) 里无条件 load(currentPath) ——
 * 脚本一加载就发 /api/storage/list，早于 peer-info 到达，也不看开关状态。
 * 开关关着，服务端按 D33 返回 404（刻意不暴露「功能存在但被关」），
 * 面板把这个 404 当成「目录没了」报给用户。
 *
 * 顺带暴露 D33 的一个洞：404 让客户端分不清「功能被关」与「路径不存在」。
 * 服务端不能改（改了就暴露功能存在），所以修法在客户端：开关关闭时根本不请求。
 */

test('the panel does not fetch at mount time', () => {
  const src = scan.scrub(read('panel-files.js'));
  const body = scan.functionBody(src, 'function mount(');
  assert.ok(body, 'no mount function');
  assert.equal(
    body.indexOf('load(') >= 0,
    false,
    'mount must not fetch: it runs at script load, before peer-info arrives and ' +
      'regardless of the storage switch. Body was:' + scan.LF + body,
  );
});

test('the panel exposes an enable hook and only fetches once enabled', () => {
  const src = scan.scrub(read('panel-files.js'));
  const body = scan.functionBody(src, 'function setEnabled(');
  assert.ok(body, 'panel-files must expose setEnabled so app.js can drive it from the switch');
  assert.ok(body.indexOf('load(') >= 0, 'setEnabled(true) must be what triggers the first load');
  // 关闭时必须把缓存的列表清掉：重新开启后不该显示上一台手机 / 上一次授权状态的目录。
  assert.ok(
    body.indexOf('lastState = null') >= 0,
    'setEnabled(false) must drop the cached listing; body:' + scan.LF + body,
  );
});

test('app.js drives the panel from the master switch, in one place', () => {
  const src = scan.scrub(read('app.js'));
  const body = scan.functionBody(src, 'function applyStorageBrowsing(');
  assert.ok(body, 'no applyStorageBrowsing');
  // 逼红实测：只查出现过 'setEnabled' 是不够的——把调用换成 void 0 之后，
  // 外层那句 typeof ... === 'function' 里仍然有这个词，断言照样绿。
  // 必须钉住**真的调用了，并且把开关值传了进去**。
  assert.ok(
    body.indexOf('setEnabled(enabled)') >= 0,
    'applyStorageBrowsing is the single writer of the switch state, so it must call ' +
      'setEnabled(enabled) — otherwise the panel has to guess. Body:' + scan.LF + body,
  );
});

test('a failure before any successful load never blames a missing directory', () => {
  // 即便将来又有人让它抢跑，第一次加载失败也不该说「这个位置已经不存在了」——
  // 用户还没导航过任何位置，那句话无从理解。
  const src = scan.scrub(read('panel-files.js'));
  const body = scan.functionBody(src, 'function handleFailure(');
  assert.ok(body, 'no handleFailure');
  assert.ok(
    body.indexOf('hasLoadedOnce') >= 0,
    'handleFailure must tell a never-loaded panel from a real navigation; body:' +
      scan.LF + body,
  );
});
