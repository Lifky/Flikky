const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { ndjson } = require('./ndjson.js');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');

/*
 * 行为层的缺陷 1b 守卫。上面那份 panel-files-gating.test.js 钉的是结构
 * （mount 不许 load、setEnabled 存在），这份钉的是**跑起来真的不发请求**——
 * 结构断言挡不住「setEnabled 里又加了一处提前请求」。
 */
function load(replies) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const fetched = [];
  const ctx = {
    document: doc,
    window: { flikkyPanels: {} },
    fetch: (url) => {
      fetched.push(url);
      const next = replies.shift() || { status: 404 };
      return Promise.resolve({
        ok: next.status === 200,
        status: next.status,
        json: () => Promise.resolve(next.body || {}),
        text: () => Promise.resolve(ndjson(next.body || { path: '', entries: [] })),
      });
    },
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return { doc, view, fetched, api: ctx.window.flikkyPanels.files };
}

const flush = async (n = 12) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };
const OK = { path: '', entries: [] };

test('mounting sends no request at all', async () => {
  // 这是 Screenshot_1 那条 toast 的正面回归：开关关闭时连一个请求都不该发出去。
  const c = load([{ status: 200, body: OK }]);
  c.api.mount(c.view);
  await flush();
  assert.deepEqual(c.fetched, [], 'mount fetched: ' + c.fetched.join(', '));
});

test('navigation while disabled sends no request either', async () => {
  // 面板不可见时理论上点不到，但 navigate 是导出的公共入口，
  // 防的是将来有人从别处调它。
  const c = load([{ status: 200, body: OK }]);
  c.api.mount(c.view);
  c.api.navigate('DCIM');
  await flush();
  assert.deepEqual(c.fetched, [], 'navigate while disabled fetched: ' + c.fetched.join(', '));
});

test('enabling triggers exactly one request', async () => {
  const c = load([{ status: 200, body: OK }]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  assert.equal(c.fetched.length, 1, 'requests: ' + c.fetched.join(', '));
});

test('enabling twice does not fetch twice', async () => {
  const c = load([{ status: 200, body: OK }, { status: 200, body: OK }]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  c.api.setEnabled(true);
  await flush();
  assert.equal(c.fetched.length, 1, 'requests: ' + c.fetched.join(', '));
});

test('disabling drops the listing so a re-enable cannot show stale files', async () => {
  const listing = { path: 'DCIM', entries: [{ name: 'a.jpg', isDir: false, size: 1, mtime: 0 }] };
  const c = load([
    { status: 200, body: listing },
    { status: 200, body: { path: '', entries: [] } },
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  assert.equal(byClass(c.view, 'fk-item').length, 1, 'the listing did not render');
  c.api.setEnabled(false);
  await flush();
  assert.equal(byClass(c.view, 'fk-item').length, 0, 'stale rows survived a disable');
  // 重新开启必须从根目录重新拉，而不是复用上次的路径：
  // 期间可能换了手机、换了授权状态。
  c.api.setEnabled(true);
  await flush();
  assert.equal(c.fetched.length, 2, 'requests: ' + c.fetched.join(', '));
  assert.ok(
    c.fetched[1].indexOf('path=') >= 0 && c.fetched[1].slice(-5) === 'path=',
    'the re-enable must request the root, got ' + c.fetched[1],
  );
});

test('a failure before any success shows guidance, not a missing-directory toast', async () => {
  const c = load([{ status: 404 }]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  const g = byClass(c.view, 'fk-guidance');
  assert.equal(g.length, 1, 'expected guidance, got ' + g.length + ' blocks');
});
