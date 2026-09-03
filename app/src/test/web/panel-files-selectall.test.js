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
  // chunks 既当「一次响应的多个分片」用，也当「多次导航各自的响应」用：
  // holdAt 那条测试要前者，缓存/计数这些要后者。区别在 i 的推进方式。
  const doc = createDocument();
  const view = doc.register('view-files');
  let i = 0;
  let held = null;
  let hold = null;
  const frames = [];
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
    // 真实的 rAF 由浏览器按帧调度；这里排队，测试自己决定什么时候放。
    requestAnimationFrame: (fn) => { frames.push(fn); return frames.length; },
    fetch: () => (hold ? hold() : Promise.resolve()).then(() => ({
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
    })),
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
    release: () => held && held(),
    /** 让下一次 fetch 先等一个由测试控制的 promise，用来观察请求往返期间的状态。 */
    holdFetch: (fn) => { hold = fn; },
    /** 追加后续导航要用的响应分片。 */
    queue: (more) => { chunks.push.apply(chunks, more); },
    queueBody: (b) => { chunks.push(b); },
    /** 把排着的 rAF 回调全部放完（含它们又排进来的）。 */
    drainFrames: async () => {
      let guard = 0;
      while (frames.length) {
        const fn = frames.shift();
        fn(0);
        await Promise.resolve();
        guard += 1;
        if (guard > 5000) throw new Error('rAF chain never terminated');
      }
    },
  };
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

const listing2 = (p, names) => [JSON.stringify({ path: p })]
  .concat(names.map((n) => (n.slice(-1) === '/'
    ? JSON.stringify({ name: n.slice(0, -1), isDir: true, size: 0, mtime: 0, childCount: 1 })
    : entry(n))))
  .concat([JSON.stringify({ done: true })])
  .join(LF) + LF;

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
  headerToggle(c.view).dispatch('click');
  await tick();
  await c.drainFrames();
  assert.equal(selectedRows(c.view).length, 2, 'only the two files may be selected');
  assert.ok(count(c.view).indexOf('2') >= 0, 'count reads: ' + count(c.view));
});

test('deselect clears everything and hides the toolbar', async () => {
  const c = await opened(['a.txt', 'b.txt']);
  headerToggle(c.view).dispatch('click');
  await tick();
  await c.drainFrames();
  const close = byClass(toolbar(c.view), 'fk-icon-btn')
    .find((x) => (x.getAttribute('aria-label') || '').indexOf('app.files.clear') >= 0);
  close.dispatch('click');
  await tick();
  await c.drainFrames();
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
  const sa = headerToggle(c.view);
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
    headerToggle(c.view).disabled,
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
    const sa = headerToggle(c.view);
    assert.ok(sa, 'select all must exist outside the selection toolbar');
    assert.equal(sa.disabled, false, 'and it must be usable with an empty selection');
    sa.dispatch('click');
    await tick();
    await c.drainFrames();
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
      headerToggle(c.view).disabled,
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
    headerToggle(c.view).dispatch('click');
    await tick();
    assert.equal(selectedRows(c.view).length, 0, 'a mid-stream select all must be refused');
    c.release();
    await tick();
  })();
});

test('select all is disabled during the request round trip, not just after it', () => {
  // 装机验收：「全选按钮目前全程可点击，会导致选中逻辑发生未知错误」。
  //
  // 门禁 armed 得太晚：`listingComplete = false` 写在 `await fetch(...)` **之后**，
  // 于是整个请求往返期间它还是上一个目录留下的 true。而那段时间 `lastState` 也还是
  // 上一个目录的条目 —— 点下去会把**上一个目录**的文件加进选择集，
  // 而屏幕上是新目录的空列表。
  //
  // 这条测试卡在 fetch 尚未 resolve 的那一刻检查，第一版实现过不去。
  return (async () => {
    const c = await opened(['a.txt', 'b.txt']);
    headerToggle(c.view).dispatch('click');
    await tick();
    await c.drainFrames();
    const before = selectedRows(c.view).length;
    assert.equal(before, 2, 'precondition: the first directory is fully selected');

    // 第二次导航：让 fetch 悬着不 resolve。
    let releaseFetch = null;
    c.holdFetch(() => new Promise((resolve) => { releaseFetch = resolve; }));
    c.api.navigate('DCIM');
    await tick(4);
    assert.equal(
      headerToggle(c.view).disabled,
      true,
      'select all must already be disabled while the request is in flight',
    );
    assert.ok(releaseFetch, 'the fetch should be held open');
    releaseFetch();
    await tick();
  })();
});

// ── 两态按钮 / 无可选时隐藏 / 上万行不卡 ──────────────────────────────────────

/** 图标名。icon() 用 `dataset.icon` 存名字，mini-dom 把它落到 data-icon 属性上。 */
const iconOf = (el) => {
  const i = byClass(el, 'material-symbols-outlined')[0] || el.children[0];
  return i ? i.getAttribute('data-icon') : null;
};
/** 头部那个按钮**本体**（不按当前 label 找，否则会命中工具栏里的同名按钮）。 */
const headerToggle = (v) => byClass(byClass(v, 'fk-panel-head')[0], 'fk-icon-btn')
  .find((b) => iconOf(b) === 'select_all' || iconOf(b) === 'deselect');

test('one header button toggles between select all and deselect', () => {
  // 用户裁决：全选与取消全选合并为一个两态按钮；工具栏那个换回 close 及其原逻辑。
  return (async () => {
    const c = await opened(['a.txt', 'b.txt']);
    // 抓住**同一个元素**再看它变。按 label 重新找会命中工具栏里的同名按钮 ——
    // 第一版就是这样过的，测的其实是工具栏那个，不是头部这个在翻面。
    const b = headerToggle(c.view);
    assert.ok(b, 'no header toggle found');
    assert.equal(iconOf(b), 'select_all', 'starts as select all');
    assert.ok(
      (b.getAttribute('aria-label') || '').indexOf('app.files.selectAll') >= 0,
      'label must match the state',
    );

    b.dispatch('click');
    await tick();
    await c.drainFrames();
    assert.equal(selectedRows(c.view).length, 2);
    assert.equal(iconOf(b), 'deselect', 'the same element must flip to deselect');
    assert.ok(
      (b.getAttribute('aria-label') || '').indexOf('app.files.deselect') >= 0,
      'and so must its label, or screen readers still say select all',
    );

    b.dispatch('click');
    await tick();
    await c.drainFrames();
    assert.equal(selectedRows(c.view).length, 0, 'the same button clears');
    assert.equal(iconOf(b), 'select_all', 'and flips back');
  })();
});

test('the toolbar keeps its original close button', () => {
  // 换回 close：那个动作读起来是「收起这条工具栏」，deselect 图标把它讲成了别的事。
  return (async () => {
    const c = await opened(['a.txt']);
    rows(c.view)[0].dispatch('click');
    await tick();
    const tb = byClass(toolbar(c.view), 'fk-icon-btn');
    const close = tb.find((x) => (x.getAttribute('aria-label') || '').indexOf('app.files.clear') >= 0);
    assert.ok(close, 'the toolbar must offer the original clear/close button');
    assert.equal(iconOf(close), 'close', 'and it must be the close icon again');
    close.dispatch('click');
    await tick();
    assert.ok(toolbar(c.view).hidden);
  })();
});

test('the toggle is hidden when this folder has nothing selectable', () => {
  return (async () => {
    const c = await opened(['DCIM/', 'Music/']);
    const b = headerToggle(c.view);
    assert.ok(b, 'the button element should still exist');
    assert.equal(b.hidden, true, 'a folder of only directories offers nothing to select');
  })();
});

test('the toggle reappears once a folder with files is opened', () => {
  return (async () => {
    const c = await opened(['DCIM/']);
    assert.equal(headerToggle(c.view).hidden, true);
    c.queue([JSON.stringify({ path: 'DCIM' }) + LF + entry('a.jpg') + LF
      + JSON.stringify({ done: true }) + LF]);
    c.api.navigate('DCIM');
    await tick();
    assert.equal(headerToggle(c.view).hidden, false);
  })();
});

test('selecting thousands of rows stays bounded by what is rendered', () => {
  // 装机验收原话：「listitem 项非常多的情况下，点击全选会有卡顿」。
  //
  // 当时的根因是逐行 setAttribute 触发一次覆盖上万元素的样式重算。虚拟化之后
  // DOM 里本来就只有视口附近那几十行，`rowElements` 也只有那几十行 ——
  // **成本从此与目录大小无关**，分帧写只是多一层保险。
  // 所以判据换成：选择本身是瞬时的（计数立刻对），而要改的 DOM 是有界的。
  return (async () => {
    const names = [];
    for (let k = 0; k < 3000; k += 1) names.push('f' + k + '.txt');
    const c = await opened(names);
    assert.ok(
      rows(c.view).length < 200,
      'the DOM must already be windowed, got ' + rows(c.view).length + ' rows',
    );
    headerToggle(c.view).dispatch('click');
    await tick(4);
    assert.ok(
      count(c.view).indexOf('3000') >= 0,
      'the selection itself must be immediate and cover everything, got: ' + count(c.view),
    );
    await c.drainFrames();
    const marks = rows(c.view).map((r) => r.getAttribute('aria-selected'));
    assert.ok(marks.length > 0, 'some rows must be rendered');
    assert.ok(
      marks.every((m) => m === 'true'),
      'every rendered row must end up marked: ' + marks.slice(0, 5),
    );
  })();
});
