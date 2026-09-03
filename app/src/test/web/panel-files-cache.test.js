const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const LF = String.fromCharCode(10);
const entry = (n) => JSON.stringify({ name: n, isDir: false, size: 1, mtime: 0, mime: null });
const dirLine = (n) => JSON.stringify({ name: n, isDir: true, size: 0, mtime: 0, childCount: 1 });

/*
 * 目录缓存：**回退不重新加载，状态完美恢复**（用户 2026-09-03 第 7 条）。
 *
 * 四条要求逐一落测：
 *   1. 数据层：命中缓存不发请求；
 *   2. UI 层：滚动位置恢复；
 *   3. 边界：不做自动刷新（子目录待久了父目录可能已变），给手动刷新出口；
 *      **被截断的列表绝不入缓存** —— 否则「秒回」会永远回一份残缺的列表；
 *   4. 安全边界：LRU 上限，按目录数与总条目数双卡，防内存无界增长。
 */

function load(responses) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const asked = [];
  const queued = responses.slice();
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: { fileSymbolName: () => 'draft' },
      flikkyI18n: {
        t: (k, v) => (v && typeof v.count === 'number' ? k + ':' + v.count : k),
        onChange: () => {},
      },
    },
    TextDecoder: TextDecoder,
    requestAnimationFrame: (fn) => { fn(0); return 1; },
    fetch: (url) => {
      asked.push(url);
      const bodyText = queued.length > 1 ? queued.shift() : queued[0];
      return Promise.resolve({
        ok: true,
        status: 200,
        body: {
          getReader: () => {
            let sent = false;
            return {
              read: () => {
                if (sent) return Promise.resolve({ value: undefined, done: true });
                sent = true;
                return Promise.resolve({ value: Buffer.from(bodyText, 'utf8'), done: false });
              },
              cancel: () => {},
            };
          },
        },
      });
    },
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return {
    doc,
    view,
    api: ctx.window.flikkyPanels.files,
    asked,
    push: (b) => queued.push(b),
  };
}

const tick = async (n = 40) => { for (let k = 0; k < n; k += 1) await Promise.resolve(); };
const rows = (v) => byClass(v, 'fk-item');
const body = (v) => byClass(v, 'fk-panel-body')[0];
const titles = (v) => rows(v).map((r) => byClass(r, 'fk-item-title')[0].textContent);
const listing = (p, names, complete) => [JSON.stringify({ path: p })]
  .concat(names.map((n) => (n.slice(-1) === '/' ? dirLine(n.slice(0, -1)) : entry(n))))
  .concat(complete === false ? [] : [JSON.stringify({ done: true })])
  .join(LF) + LF;

test('going back to a cached directory issues no request', async () => {
  const c = load([listing('', ['DCIM/', 'a.txt']), listing('DCIM', ['p1.jpg', 'p2.jpg'])]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  assert.equal(c.asked.length, 1);

  c.api.navigate('DCIM');
  await tick();
  assert.equal(c.asked.length, 2);
  assert.equal(rows(c.view).length, 2, 'DCIM has two photos');

  c.api.navigate('');
  await tick();
  assert.equal(c.asked.length, 2, 'a cached directory must not be re-requested');
  assert.deepEqual(titles(c.view), ['DCIM', 'a.txt'], 'and it comes back complete and in order');
});

test('the scroll position comes back with it', async () => {
  const many = [];
  for (let k = 0; k < 200; k += 1) many.push('f' + k + '.txt');
  const c = load([listing('', ['DCIM/'].concat(many)), listing('DCIM', ['p.jpg'])]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  body(c.view).scrollTop = 4200;

  c.api.navigate('DCIM');
  await tick();
  assert.equal(body(c.view).scrollTop, 0, 'a new directory starts at the top');

  c.api.navigate('');
  await tick();
  assert.equal(body(c.view).scrollTop, 4200, 'coming back must land where the user left off');
});

test('a truncated listing is never cached', async () => {
  // 否则「秒回」会永远回一份残缺的列表，用户再也见不到完整的那份。
  const c = load([
    listing('', ['a.txt', 'b.txt'], false),
    listing('DCIM', ['p.jpg']),
    listing('', ['a.txt', 'b.txt', 'c.txt']),
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  c.api.navigate('DCIM');
  await tick();
  c.api.navigate('');
  await tick();
  assert.equal(c.asked.length, 3, 'an incomplete listing must be re-fetched, not replayed');
  assert.equal(rows(c.view).length, 3, 'and the complete version must land');
});

test('refresh bypasses the cache for the directory shown', async () => {
  const c = load([listing('', ['a.txt']), listing('', ['a.txt', 'b.txt'])]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  assert.equal(rows(c.view).length, 1);

  const refresh = byClass(byClass(c.view, 'fk-panel-head')[0], 'fk-icon-btn')
    .find((b) => (b.getAttribute('aria-label') || '').indexOf('app.files.refresh') >= 0);
  assert.ok(refresh, 'no refresh button in the head');
  refresh.dispatch('click');
  await tick();
  assert.equal(c.asked.length, 2, 'refresh must go to the network');
  assert.equal(rows(c.view).length, 2, 'and show what came back');
});

test('the cache evicts old directories rather than growing without bound', async () => {
  // 行为断言，不暴露测试专用 API：逛过足够多的目录之后，最早那个必须重新请求。
  // 80 比任何合理上限都大，所以这条只要求「存在一个上限」，不写死它是多少。
  const c = load([listing('d0', ['x.txt'])]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  c.api.navigate('d0');
  await tick(8);
  const afterFirst = c.asked.length;

  for (let k = 1; k < 80; k += 1) {
    c.push(listing('d' + k, ['x.txt']));
    c.api.navigate('d' + k);
    await tick(8);
  }
  // 最近那个仍应命中缓存 —— 否则「有上限」就退化成「根本没缓存」。
  const beforeRecent = c.asked.length;
  c.api.navigate('d78');
  await tick(8);
  assert.equal(c.asked.length, beforeRecent, 'a recently visited directory must still be cached');

  c.push(listing('d0', ['x.txt']));
  const beforeOld = c.asked.length;
  c.api.navigate('d0');
  await tick(8);
  assert.ok(
    c.asked.length > beforeOld,
    'the oldest directory must have been evicted, not kept for ever',
  );
  assert.ok(afterFirst > 0);
});

test('a few huge directories are evicted too, on total entries', async () => {
  // 判别式：目录数很少（5 个，远低于目录上限），但总条目远超条目上限。
  // 只有**按总条目**卡的那道上限才能让最早那个被淘汰 ——
  // 少了它，32 个各含一万条的目录一样会撑爆内存。
  const big = [];
  for (let k = 0; k < 9000; k += 1) big.push('f' + k + '.txt');
  const c = load([listing('big0', big)]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  c.api.navigate('big0');
  await tick(20);

  for (let k = 1; k < 5; k += 1) {
    c.push(listing('big' + k, big));
    c.api.navigate('big' + k);
    await tick(20);
  }
  c.push(listing('big0', big));
  const before = c.asked.length;
  c.api.navigate('big0');
  await tick(20);
  assert.ok(
    c.asked.length > before,
    'five directories of 9000 entries must not all stay cached; ' +
      'a directory-count cap alone would have kept them',
  );
});

test('turning the master switch off drops the cache', async () => {
  const c = load([listing('', ['a.txt']), listing('', ['a.txt', 'b.txt'])]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  const before = c.asked.length;
  c.api.setEnabled(false);
  c.api.setEnabled(true);
  await tick();
  assert.ok(
    c.asked.length > before,
    're-enabling must fetch again, not replay a listing from while it was off',
  );
  assert.equal(rows(c.view).length, 2, 'and the fresh listing must be what shows');
});

test('a cached render does not replay the per-row stagger', async () => {
  // 回退时几千行同时到位；给它们排阶梯延迟等于把「秒回」变成一场幻灯片。
  // 方向横移保留（那是一个容器动画），逐行阶梯归零。
  const many = [];
  for (let k = 0; k < 60; k += 1) many.push('f' + k + '.txt');
  const c = load([listing('', ['DCIM/'].concat(many)), listing('DCIM', ['p.jpg'])]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  c.api.navigate('DCIM');
  await tick();
  c.api.navigate('');
  await tick();
  const stagger = rows(c.view).map((r) => r.style.getPropertyValue('--i'));
  assert.ok(stagger.length > 10, 'expected the restored rows');
  assert.ok(
    stagger.every((v) => v === '0' || v === '' || v === null),
    'restored rows must not carry a stagger index, got ' + JSON.stringify(stagger.slice(0, 8)),
  );
  const list = byClass(c.view, 'fk-files-list')[0];
  assert.equal(list.getAttribute('data-dir'), 'exit', 'but the directional slide still applies');
});

const countText = (v) => byClass(v, 'fk-toolbar-count')[0].textContent;
const headToggle = (v) => byClass(byClass(v, 'fk-panel-head')[0], 'fk-icon-btn')
  .find((b) => {
    const i = byClass(b, 'material-symbols-outlined')[0];
    const n = i ? i.getAttribute('data-icon') : null;
    return n === 'select_all' || n === 'deselect';
  });

test('the count says how many of the selection are in other folders', async () => {
  // 选择集跨目录保留（刻意的：可以逛几个文件夹攒一批再一起发），但装机验收里
  // 工具栏报「已选 3 项」而当前目录一行都没选中 —— 用户既不知道那些在哪，
  // 也无从判断按下下载会下什么。用户裁决：保留能力，把「有多少在别处」写清楚。
  const c = load([listing('', ['DCIM/', 'a.txt']), listing('DCIM', ['p1.jpg', 'p2.jpg'])]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  rows(c.view)[1].dispatch('click');
  await tick();
  assert.equal(
    countText(c.view).indexOf('app.files.elsewhere') >= 0,
    false,
    'nothing is elsewhere yet, so do not say so: ' + countText(c.view),
  );

  c.api.navigate('DCIM');
  await tick();
  headToggle(c.view).dispatch('click');
  await tick();
  const text = countText(c.view);
  assert.ok(text.indexOf('3') >= 0, 'three are selected in total: ' + text);
  assert.ok(
    text.indexOf('app.files.elsewhere') >= 0,
    'and one of them is in another folder: ' + text,
  );
});

test('a nested subfolder counts as elsewhere, not here', async () => {
  // 「当前目录」是这一屏能看到的那些行。孙子项算作 here 会让计数与屏幕上的勾
  // 再次对不上 —— 正是这条要修的毛病。判据与 App 端 StorageSelectionScope 同一条。
  const c = load([
    listing('DCIM', ['Camera/', 'a.jpg']),
    listing('DCIM/Camera', ['p.jpg']),
    listing('DCIM', ['Camera/', 'a.jpg']),
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  c.api.navigate('DCIM/Camera');
  await tick();
  headToggle(c.view).dispatch('click');
  await tick();
  c.api.navigate('DCIM');
  await tick();
  assert.ok(
    countText(c.view).indexOf('app.files.elsewhere') >= 0,
    'a grandchild must not count as being in this folder: ' + countText(c.view),
  );
});
