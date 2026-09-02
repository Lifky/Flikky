const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');

/*
 * 浏览器端流式加载的行为守卫。
 *
 * 装机验收：上千项的目录里进度条要转很久。服务端改成 NDJSON 逐行 flush 之后，
 * 客户端必须**边读边追加**——攒完再画的话前面所有改动都白做，而且从
 * 「最终列表对不对」这个角度看两者完全一样，只有中间态能区分。
 * 所以这一组断言全都在看**中间态**。
 */

/** 一个可以分片喂数据的假 ReadableStream。 */
function chunkedBody(chunks) {
  let i = 0;
  return {
    getReader: () => ({
      read: () => Promise.resolve(
        i < chunks.length
          ? { value: Buffer.from(chunks[i++], 'utf8'), done: false }
          : { value: undefined, done: true },
      ),
      cancel: () => { i = chunks.length; },
    }),
  };
}

function load(chunks) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const errors = [];
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: { fileSymbolName: () => 'draft', showError: (t) => errors.push(t) },
      flikkyI18n: { t: (k, v) => (v && typeof v.count === 'number' ? k + ':' + v.count : k), onChange: () => {} },
    },
    TextDecoder: TextDecoder,
    fetch: () => Promise.resolve({ ok: true, status: 200, body: chunkedBody(chunks) }),
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return { doc, view, api: ctx.window.flikkyPanels.files, errors: errors };
}

const tick = async (n = 4) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };
const rows = (view) => byClass(view, 'fk-item');
const names = (view) => rows(view)
  .map((r) => byClass(r, 'fk-item-title').map((t) => t.textContent).join(''));
const LF = String.fromCharCode(10);
const entry = (n) => JSON.stringify({ name: n, isDir: false, size: 1, mtime: 0, mime: null });

test('rows appear before the stream has finished', async () => {
  // 这是「列表向下生长」的本体断言。
  const chunks = [
    JSON.stringify({ path: 'DCIM' }) + LF + entry('a.txt') + LF,
    entry('b.txt') + LF,
    entry('c.txt') + LF + JSON.stringify({ done: true }) + LF,
  ];
  const c = load(chunks);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  // 只放行几个微任务：第一片已处理，后两片还没。
  await tick(6);
  const early = names(c.view);
  assert.ok(early.length >= 1, 'no rows after the first chunk — nothing is incremental');
  assert.ok(
    early.length < 3,
    'all three rows are already there, so the reader is draining the whole ' +
      'stream before painting: ' + early.join(', '),
  );
  await tick(40);
  assert.deepEqual(names(c.view), ['a.txt', 'b.txt', 'c.txt']);
});

test('the breadcrumb comes from the head line, before any entry', async () => {
  const chunks = [JSON.stringify({ path: 'DCIM/Camera' }) + LF];
  const c = load(chunks);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick(8);
  const crumbs = byClass(c.view, 'fk-crumb').map((x) => x.textContent);
  assert.ok(crumbs.indexOf('Camera') >= 0, 'crumbs: ' + crumbs.join(' / '));
});

test('a line split across two chunks is still parsed once, correctly', async () => {
  // NDJSON 的边界不会照着 TCP 分片来。半行必须留在缓冲里等下一片，
  // 而不是被当成坏行丢掉——丢掉的话大目录里会随机少条目，且完全静默。
  const full = entry('split-me.txt');
  const chunks = [
    JSON.stringify({ path: '' }) + LF + full.slice(0, 12),
    full.slice(12) + LF + JSON.stringify({ done: true }) + LF,
  ];
  const c = load(chunks);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick(40);
  assert.deepEqual(names(c.view), ['split-me.txt']);
});

test('a stream with no done line is reported as possibly incomplete', async () => {
  // HTTP 状态码早就发完了，截断在客户端看起来与读完一样。
  // 不报的话用户会把半个目录当成完整目录。
  const chunks = [JSON.stringify({ path: '' }) + LF + entry('a.txt') + LF];
  const c = load(chunks);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick(40);
  // 行要保留（清空会让用户以为目录空了），但必须提示不完整。
  assert.deepEqual(names(c.view), ['a.txt']);
  // 第一版这里写的是 `assert.ok(notices.length + sn >= 0)` —— 恒真，纯空转。
  // 真正要验的是「用户被告知了」，也就是 showError 收到一条。
  assert.equal(c.errors.length, 1, 'a truncated stream must tell the user: ' + c.errors.join(' | '));
  assert.ok(
    c.errors[0].indexOf('truncated') >= 0,
    'the message must be the truncation one, got ' + c.errors[0],
  );
});

test('a complete stream reports nothing at all', async () => {
  // 上一条的反面。少了它，一个「永远报截断」的实现也能过。
  const chunks = [
    JSON.stringify({ path: '' }) + LF + entry('a.txt') + LF + JSON.stringify({ done: true }) + LF,
  ];
  const c = load(chunks);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick(40);
  assert.deepEqual(c.errors, [], 'a complete stream must be silent');
});

test('each row carries a capped stagger index within its batch', async () => {
  // 逐行阶梯靠 CSS 的 --i；封顶在 JS 侧做，因为 CSS 没法 clamp 自定义属性的整数。
  // 不封顶的话上千行会拖成一场幻灯片。
  const lines = [JSON.stringify({ path: '' })];
  for (let i = 0; i < 14; i += 1) lines.push(entry('f' + i + '.txt'));
  lines.push(JSON.stringify({ done: true }));
  const c = load([lines.join(LF) + LF]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick(40);
  const idx = rows(c.view).map((r) => Number(r.style.getPropertyValue('--i')));
  assert.equal(idx.length, 14);
  assert.deepEqual(idx.slice(0, 9), [0, 1, 2, 3, 4, 5, 6, 7, 8]);
  assert.ok(
    idx.every((v) => v <= 8),
    'the stagger index must be capped, got ' + idx.join(', '),
  );
});

test('an entry line before the head still renders, instead of dying silently', async () => {
  // 协议上首行总是 head，但流可能从中间被截断。原先这里会在
  // `lastState.entries.push` 上抛 TypeError，被外层 catch 吞成一句
  // 「连接断开」，而列表一行都不出现 —— 症状与真的断线一模一样，极难查。
  const chunks = [entry('orphan.txt') + LF + JSON.stringify({ done: true }) + LF];
  const c = load(chunks);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick(40);
  assert.deepEqual(names(c.view), ['orphan.txt']);
  assert.deepEqual(c.errors, [], 'a missing head line must not surface as a connection error');
});

test('a stale stream is cancelled, not just ignored', async () => {
  // 只丢弃解析结果是不够的：服务端会把整个大目录白列举完，手机白烧几千次系统调用。
  // 用户要求的「避免无效开销」在这里就是主动 cancel。
  let cancelled = false;
  // 用**有界但很长**的流模拟「还在列举的大目录」。
  // 第一版用的是永不结束的流，于是 navigate 之后新开的那条流一直跑，
  // 纯微任务循环把事件循环饿死，测试直接 heap OOM —— 与之前那次
  // 「失败分支反复重拉」是同一类：假数据源必须有终点。
  const TOTAL = 4000;
  let served = 0;
  const READ_CAP = TOTAL + 50;
  const doc = createDocument();
  const view = doc.register('view-files');
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: { fileSymbolName: () => 'draft' },
      flikkyI18n: { t: (k) => k, onChange: () => {} },
    },
    TextDecoder: TextDecoder,
    fetch: () => Promise.resolve({
      ok: true,
      status: 200,
      body: {
        getReader: () => {
          let first = true;
          return {
            read: () => {
              served += 1;
              if (served > READ_CAP) {
                throw new Error('runaway reader: ' + served + ' reads, the loop never stops');
              }
              if (served > TOTAL) return Promise.resolve({ value: undefined, done: true });
              const line = first
                ? JSON.stringify({ path: 'Big' })
                : entry('x' + served + '.txt');
              first = false;
              return Promise.resolve({ value: Buffer.from(line + LF, 'utf8'), done: false });
            },
            cancel: () => { cancelled = true; },
          };
        },
      },
    }),
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  const api = ctx.window.flikkyPanels.files;
  api.mount(view);
  api.setEnabled(true);
  await tick(8);
  api.navigate('Elsewhere');
  await tick(30);
  assert.ok(cancelled, 'the superseded reader must be cancelled, not merely ignored');
});

test('the list carries the travel direction so the transition can slide', async () => {
  // 用户要的第三件事：「文件夹进入/退出时的列表动画」。
  // 方向由长度判定，不是前缀比较——点面包屑中间一级也是「返回」，
  // 而那时新路径恰好是旧路径的前缀，用前缀判会判成「进入」。
  const listing = (p, names) => [
    JSON.stringify({ path: p }) + LF
      + names.map((n) => entry(n)).join(LF) + (names.length ? LF : '')
      + JSON.stringify({ done: true }) + LF,
  ];
  const c = load(listing('', ['a.txt']));
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick(40);
  const list = () => byClass(c.view, 'fk-files-list')[0];
  assert.equal(list().getAttribute('data-dir'), 'enter', 'the first load counts as entering');
});

test('going back marks the list as exiting', async () => {
  // 两次导航：先进 DCIM/Camera，再回 DCIM。第二次必须是 exit。
  const doc = createDocument();
  const view = doc.register('view-files');
  const bodies = [
    JSON.stringify({ path: 'DCIM/Camera' }) + LF + JSON.stringify({ done: true }) + LF,
    JSON.stringify({ path: 'DCIM' }) + LF + JSON.stringify({ done: true }) + LF,
  ];
  let n = 0;
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: { fileSymbolName: () => 'draft' },
      flikkyI18n: { t: (k) => k, onChange: () => {} },
    },
    TextDecoder: TextDecoder,
    fetch: () => {
      const body = bodies[Math.min(n++, bodies.length - 1)];
      let sent = false;
      return Promise.resolve({
        ok: true,
        status: 200,
        body: {
          getReader: () => ({
            read: () => Promise.resolve(
              sent
                ? { value: undefined, done: true }
                : ((sent = true), { value: Buffer.from(body, 'utf8'), done: false }),
            ),
            cancel: () => {},
          }),
        },
      });
    },
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  const api = ctx.window.flikkyPanels.files;
  api.mount(view);
  api.setEnabled(true);
  await tick(40);
  assert.equal(byClass(view, 'fk-files-list')[0].getAttribute('data-dir'), 'enter');
  api.navigate('DCIM');
  await tick(40);
  assert.equal(
    byClass(view, 'fk-files-list')[0].getAttribute('data-dir'),
    'exit',
    'walking back up must slide the other way',
  );
});

test('a sideways move is not called a retreat just because the name is shorter', async () => {
  // 判据必须是层级深度。用字符串长度时：DCIM -> Music 判「进入」，
  // DCIM -> A 判「返回」，而两者都是同一层的平移。这条钉的就是那个差别。
  const doc = createDocument();
  const view = doc.register('view-files');
  const bodies = [
    JSON.stringify({ path: 'DCIM' }) + LF + JSON.stringify({ done: true }) + LF,
    JSON.stringify({ path: 'A' }) + LF + JSON.stringify({ done: true }) + LF,
  ];
  let n = 0;
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: { fileSymbolName: () => 'draft' },
      flikkyI18n: { t: (k) => k, onChange: () => {} },
    },
    TextDecoder: TextDecoder,
    fetch: () => {
      const body = bodies[Math.min(n++, bodies.length - 1)];
      let sent = false;
      return Promise.resolve({
        ok: true,
        status: 200,
        body: {
          getReader: () => ({
            read: () => Promise.resolve(
              sent
                ? { value: undefined, done: true }
                : ((sent = true), { value: Buffer.from(body, 'utf8'), done: false }),
            ),
            cancel: () => {},
          }),
        },
      });
    },
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  const api = ctx.window.flikkyPanels.files;
  api.mount(view);
  api.setEnabled(true);
  await tick(40);
  api.navigate('A');
  await tick(40);
  assert.equal(
    byClass(view, 'fk-files-list')[0].getAttribute('data-dir'),
    'enter',
    'DCIM -> A is a lateral move at the same depth, not a retreat',
  );
});
