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

function load(responses, opts) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const asked = [];
  const queued = responses.slice();
  const o = opts || {};
  // opts.hold：请求 URL 里含这个片段时，把响应体扣住直到测试调 release()。
  // 用来观察「壳已经换了、数据还没到」那个窗口。
  let release = () => {};
  const gate = new Promise((res) => { release = res; });
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
            const held = o.hold && String(url).indexOf(o.hold) >= 0;
            return {
              read: () => {
                if (sent) return Promise.resolve({ value: undefined, done: true });
                sent = true;
                const chunk = { value: Buffer.from(bodyText, 'utf8'), done: false };
                return held ? gate.then(() => chunk) : Promise.resolve(chunk);
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
    release: () => release(),
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

test('rows restored from cache carry paths for the directory they belong to', async () => {
  // 装机验收（2026-09-03，秒回上线后的严重回归）：
  // 「进入子文件夹并返回后，再次进入文件夹，必定出现 snackbar 这个位置已经不存在了，
  //   并且面包屑导航错误，路径混乱」。
  //
  // 根因：renderFromCache 先 appendBatch、后设 currentPath，而 renderRow 是靠
  // childPath() 拼路径的，它读的就是 currentPath。于是从缓存恢复出来的每一行都带着
  // **上一个目录**的前缀，点进去请求的是一个不存在的路径。
  const c = load([
    listing('', ['DCIM/', 'Music/']),
    listing('Music', ['song.mp3']),
    listing('DCIM', ['p.jpg']),
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();

  // 进 Music，再返回根（命中缓存）。
  c.api.navigate('Music');
  await tick();
  c.api.navigate('');
  await tick();
  assert.deepEqual(titles(c.view), ['DCIM', 'Music'], 'precondition: the root came back');

  // 现在点 DCIM。请求必须是 path=DCIM，而不是 Music/DCIM。
  const before = c.asked.length;
  rows(c.view)[0].dispatch('click');
  await tick();
  assert.ok(c.asked.length > before, 'clicking a folder must issue a request');
  const url = c.asked[c.asked.length - 1];
  assert.ok(
    url.indexOf('path=DCIM') >= 0,
    'a restored row must not carry the previous directory as a prefix; asked: ' + url,
  );
  assert.equal(
    url.indexOf('Music') >= 0,
    false,
    'the directory we came back from must not appear in the path: ' + url,
  );
});

test('deep navigation and back keeps every level addressable', async () => {
  // 多层来回：路径拼装错一次就会层层放大（面包屑与请求一起跑偏）。
  const c = load([
    listing('', ['A/', 'Z/']),
    listing('A', ['B/']),
    listing('A/B', ['C/']),
    listing('A/B/C', ['leaf.txt']),
    listing('Z', ['z.txt']),
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  c.api.navigate('A');
  await tick();
  c.api.navigate('A/B');
  await tick();
  c.api.navigate('A/B/C');
  await tick();

  // 一路退回根，全部命中缓存。
  const askedAfterDescent = c.asked.length;
  c.api.navigate('A/B');
  await tick();
  c.api.navigate('A');
  await tick();
  c.api.navigate('');
  await tick();
  assert.equal(c.asked.length, askedAfterDescent, 'the way back must be all cache hits');
  assert.deepEqual(titles(c.view), ['A', 'Z'], 'and the root must be intact');

  // 现在进一个**从未去过**的目录：它不在缓存里，所以会真的发请求 ——
  // 请求路径必须是干净的 Z，不带任何刚才那趟深入留下的前缀。
  rows(c.view)[1].dispatch('click');
  await tick();
  const url = c.asked[c.asked.length - 1];
  assert.ok(url.indexOf('path=Z') >= 0, 'must ask for Z, asked: ' + url);
  // 只看 **path 参数**，不看整个 URL：v1.20.0 起 URL 里还带 sort=NAME:asc，
  // 而在整个 URL 里找字母 'A' 会命中它（编码后的 %3A 也含 A）。
  // 这条断言想说的一直是「path 里不带刚才那趟深入留下的前缀」。
  const askedPath = decodeURIComponent(url.split('path=')[1] || '');
  assert.equal(
    askedPath.indexOf('A') >= 0,
    false,
    'no leftover prefix from the descent, path was: ' + askedPath,
  );
  assert.deepEqual(titles(c.view), ['z.txt'], 'and Z must actually render');
});

test('a child directory never shows the parent rows it replaced', async () => {
  // 装机验收（2026-09-03，最严重的一个）：从一个**滚动过的**父目录进入子目录，
  // 首次进入显示的是父目录的前 N 行，N 正好等于子目录的条目数
  // （子目录只有 1 项时就只显示父目录第 1 项）。
  //
  // 根因：`renderShell` 会把 `bodyEl.scrollTop` 归零，而对一个已经滚动过的
  // 容器赋值会**触发一次 scroll 事件**。监听里调 `syncVirtual`，而它取数据的
  // `allEntries()` 读的是 `lastState` —— 那是「最后一次成功的列举」，
  // 加载期间仍然是**上一个目录**的。于是父目录的行被画进了新壳，
  // 随后子目录的批次因为「这些下标已经渲染过了」而复用它们，再也不会被替换。
  //
  // 父目录必须先滚动过，否则 scrollTop 本来就是 0、赋值不触发事件 ——
  // 这也正是用户观察到的触发条件。
  const parent = [];
  for (let i = 0; i < 60; i += 1) parent.push('p' + i + '/');
  const c = load([
    listing('', parent),
    listing('p0', ['a.txt', 'b.txt', 'c.txt', 'd.txt']),
  ]);
  c.api.mount(c.view);
  const bodyEl = byClass(c.view, 'fk-panel-body')[0];
  bodyEl.clientHeight = 600;
  c.api.setEnabled(true);
  await tick();
  assert.equal(titles(c.view)[0], 'p0', 'the parent starts at the top');

  // 把父目录滚下去，再进第一个子目录。
  bodyEl.scrollTop = 400;
  bodyEl.dispatch('scroll');
  await tick(6);
  c.api.navigate('p0');
  await tick();

  const shown = titles(c.view);
  assert.deepEqual(
    shown,
    ['a.txt', 'b.txt', 'c.txt', 'd.txt'],
    'the child must show its own entries, not the rows the parent left behind',
  );
});

test('a scroll that arrives before the listing renders nothing', async () => {
  // 上一条的判据是结果；这一条钉住机制：**壳已经换成新目录、但数据还没到**的
  // 那个窗口里，任何一次 sync 都不许画出行来。
  const parent = [];
  for (let i = 0; i < 60; i += 1) parent.push('q' + i + '/');
  const c = load([listing('', parent), listing('q0', ['x.txt'])], { hold: 'q0' });
  c.api.mount(c.view);
  const bodyEl = byClass(c.view, 'fk-panel-body')[0];
  bodyEl.clientHeight = 600;
  c.api.setEnabled(true);
  await tick();
  bodyEl.scrollTop = 400;
  bodyEl.dispatch('scroll');
  await tick(6);

  c.api.navigate('q0');
  await tick(6);
  // 数据还被扣着。这时再来一次滚动。
  bodyEl.dispatch('scroll');
  await tick(6);
  assert.deepEqual(
    titles(c.view),
    [],
    'nothing may be rendered for a directory whose listing has not arrived',
  );
  c.release();
  await tick(20);
  assert.deepEqual(titles(c.view), ['x.txt'], 'and then the real listing shows up');
});

test('the DOM double fires a scroll event when scrollTop is assigned', async () => {
  // 这是一条**测试替身自身**的测试，值得单独存在：上面那两条回归测试之所以
  // 能复现，全靠这个行为。真实浏览器给一个已滚动的容器赋 scrollTop 会异步触发
  // 一次 scroll；替身以前只把它当普通属性，于是 2026-09-03 那个最严重的缺陷
  // （子目录首次进入显示父目录的行）在测试里完全看不见。
  //
  // 逼红实测：把替身里那一行去掉时零条红 —— 没有任何断言钉着这份保真度。
  const doc = createDocument();
  const el = doc.register('probe');
  let fired = 0;
  el.addEventListener('scroll', () => { fired += 1; });
  el.scrollTop = 120;
  assert.equal(fired, 0, 'it must be asynchronous, like the browser');
  await tick(2);
  assert.equal(fired, 1, 'assigning a new scrollTop must fire scroll');
  el.scrollTop = 120;
  await tick(2);
  assert.equal(fired, 1, 'assigning the same value must not fire');
});
