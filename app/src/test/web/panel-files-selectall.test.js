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
 * 全选 / 取消全选，以及「怎么知道看到了全部」的终止标记。
 *
 * 用户提的两件事在这里合流：
 *   - 工具栏缺全选/取消全选；
 *   - 流式加载下用户无从判断「这些就是全部吗」。
 *
 * 裁决（2026-09-02）：**加载完才能全选**。流未结束时「全部」没有确定含义，
 * 一个能点的按钮会让用户以为自己选中了整个目录，而实际只选中了已到达的那部分。
 */

/** 分片可控的假流：`gate` 为 true 时停在最后一片之前，直到 release()。 */
function load(chunks, opts) {
  const doc = createDocument();
  const view = doc.register('view-files');
  let i = 0;
  let held = null;
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
    fetch: () => Promise.resolve({
      ok: true,
      status: 200,
      body: {
        getReader: () => ({
          read: () => {
            if (i >= chunks.length) return Promise.resolve({ value: undefined, done: true });
            if (opts && opts.holdAt === i) {
              return new Promise((resolve) => {
                held = () => {
                  const v = Buffer.from(chunks[i++], 'utf8');
                  resolve({ value: v, done: false });
                };
              });
            }
            return Promise.resolve({ value: Buffer.from(chunks[i++], 'utf8'), done: false });
          },
          cancel: () => { i = chunks.length; },
        }),
      },
    }),
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return { doc, view, api: ctx.window.flikkyPanels.files, release: () => held && held() };
}

const tick = async (n = 40) => { for (let k = 0; k < n; k += 1) await Promise.resolve(); };
const rows = (v) => byClass(v, 'fk-item');
const toolbar = (v) => byClass(v, 'fk-toolbar')[0];
/** 全选在**面板头部**（工具栏只在有选中时出现，全选放那里就必须先选一个）。 */
const btn = (v, label) => byClass(v, 'fk-icon-btn')
  .find((b) => (b.getAttribute('aria-label') || '').indexOf(label) >= 0);
const count = (v) => byClass(v, 'fk-toolbar-count')[0].textContent;
const selectedRows = (v) => rows(v).filter((r) => r.getAttribute('aria-selected') === 'true');
const footer = (v) => byClass(v, 'fk-files-footer')[0];

async function opened(names) {
  const lines = [JSON.stringify({ path: '' })]
    .concat(names.map((n) => (n.endsWith('/') ? dirLine(n.slice(0, -1)) : entry(n))))
    .concat([JSON.stringify({ done: true })]);
  const c = load([lines.join(LF) + LF]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  return c;
}

test('select all picks every file and skips directories', async () => {
  const c = await opened(['a.txt', 'DCIM/', 'b.txt']);
  btn(c.view, 'app.files.selectAll').dispatch('click');
  await tick();
  assert.equal(selectedRows(c.view).length, 2, 'only the two files may be selected');
  assert.ok(count(c.view).indexOf('2') >= 0, 'count reads: ' + count(c.view));
});

test('deselect clears everything and hides the toolbar', async () => {
  const c = await opened(['a.txt', 'b.txt']);
  btn(c.view, 'app.files.selectAll').dispatch('click');
  await tick();
  btn(c.view, 'app.files.deselect').dispatch('click');
  await tick();
  assert.equal(selectedRows(c.view).length, 0);
  assert.ok(toolbar(c.view).hidden, 'an empty selection must hide the toolbar');
});

test('select all is disabled while the listing is still streaming', async () => {
  // 流未结束时「全部」没有确定含义。一个能点的按钮会让用户以为选中了整个目录。
  const chunks = [
    JSON.stringify({ path: '' }) + LF + entry('a.txt') + LF,
    entry('b.txt') + LF + JSON.stringify({ done: true }) + LF,
  ];
  const c = load(chunks, { holdAt: 1 });
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  assert.equal(rows(c.view).length, 1, 'only the first chunk should have landed');
  const sa = btn(c.view, 'app.files.selectAll');
  assert.equal(sa.disabled, true, 'select all must be disabled mid-stream');
  c.release();
  await tick();
  assert.equal(rows(c.view).length, 2);
  assert.equal(sa.disabled, false, 'select all must become available once the stream ends');
});

test('select all is disabled again when a new navigation starts', async () => {
  // 否则上一个目录留下的「可点」状态会在新目录还在加载时被点到。
  const c = await opened(['a.txt']);
  assert.equal(btn(c.view, 'app.files.selectAll').disabled, false);
  c.api.navigate('DCIM');
  await tick(4);
  assert.equal(
    btn(c.view, 'app.files.selectAll').disabled,
    true,
    'a fresh navigation must re-disable select all',
  );
});

test('the footer says how many rows there are once loading finishes', async () => {
  // 用户原话：「用户如何知道自己是否看到了全部」。加载完必须有一个确定的终止标记。
  const c = await opened(['a.txt', 'b.txt', 'c.txt']);
  const f = footer(c.view);
  assert.ok(f, 'no end-of-list footer');
  assert.ok(f.textContent.indexOf('3') >= 0, 'footer reads: ' + f.textContent);
  assert.ok(
    f.textContent.indexOf('app.files.total') >= 0,
    'the finished footer must use the total wording, got: ' + f.textContent,
  );
});

test('the footer says loading while rows are still arriving', async () => {
  const chunks = [
    JSON.stringify({ path: '' }) + LF + entry('a.txt') + LF,
    entry('b.txt') + LF + JSON.stringify({ done: true }) + LF,
  ];
  const c = load(chunks, { holdAt: 1 });
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await tick();
  const mid = footer(c.view).textContent;
  assert.ok(
    mid.indexOf('app.files.loadingCount') >= 0,
    'mid-stream the footer must say it is still loading, got: ' + mid,
  );
  assert.ok(mid.indexOf('1') >= 0, 'and how many are in so far, got: ' + mid);
  c.release();
  await tick();
  assert.ok(
    footer(c.view).textContent.indexOf('app.files.total') >= 0,
    'when done it must switch to the total wording',
  );
});

test('an empty directory says so instead of claiming a total of zero', async () => {
  const c = await opened([]);
  assert.equal(footer(c.view), undefined, 'an empty listing needs the empty notice, not a total');
  const notices = byClass(c.view, 'fk-panel-notice');
  assert.equal(notices.length, 1, 'expected the empty notice');
});

test('select all is reachable before anything is selected', () => {
  // 这一条是「全选放哪」的本体断言。
  // 工具栏只在 selected.size > 0 时出现（与收藏面板一致），全选放进去就变成
  // 「必须先手动选中一个，才能点全选」——逼红时发现的自造缺陷。
  return (async () => {
    const c = await opened(['a.txt', 'b.txt']);
    assert.ok(toolbar(c.view).hidden, 'nothing selected yet, so the toolbar is hidden');
    const sa = btn(c.view, 'app.files.selectAll');
    assert.ok(sa, 'select all must exist outside the selection toolbar');
    assert.equal(sa.disabled, false, 'and it must be usable with an empty selection');
    sa.dispatch('click');
    await tick();
    assert.equal(selectedRows(c.view).length, 2);
  })();
});

test('a truncated stream never claims the listing is complete', () => {
  // 逼红实测：把 listingComplete 写死 true 时零条红 —— 截断那条测试只看了
  // 错误提示，没看全选与页脚。截断时「全部」确实未知，全选必须仍然置禁，
  // 页脚也不能骗人说「共 N 项」。
  return (async () => {
    const chunks = [JSON.stringify({ path: '' }) + LF + entry('a.txt') + LF];
    const c = load(chunks);
    c.api.mount(c.view);
    c.api.setEnabled(true);
    await tick();
    assert.equal(
      btn(c.view, 'app.files.selectAll').disabled,
      true,
      'select all must stay disabled after a truncated stream',
    );
    assert.ok(
      footer(c.view).textContent.indexOf('app.files.loadingCount') >= 0,
      'the footer must not claim a total it cannot know, got: ' + footer(c.view).textContent,
    );
  })();
});

test('clicking select all while incomplete does nothing', () => {
  // 真实浏览器不给 disabled 按钮派发 click，所以这条守的是「显隐逻辑被改了但
  // disabled 忘了同步」。测试直接 dispatch，绕过 disabled，让那道兜底可测。
  return (async () => {
    const chunks = [
      JSON.stringify({ path: '' }) + LF + entry('a.txt') + LF,
      entry('b.txt') + LF + JSON.stringify({ done: true }) + LF,
    ];
    const c = load(chunks, { holdAt: 1 });
    c.api.mount(c.view);
    c.api.setEnabled(true);
    await tick();
    btn(c.view, 'app.files.selectAll').dispatch('click');
    await tick();
    assert.equal(selectedRows(c.view).length, 0, 'a mid-stream select all must be refused');
    c.release();
    await tick();
  })();
});
