const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { ndjson } = require('./ndjson.js');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');

/*
 * 装机验收：「每次选中一个文件项，整个文件列表会闪一次」。
 *
 * 原因：行的 click 回调调 render(lastState)，而 render 的第一件事是
 * `bodyEl.textContent = ''` —— 面包屑和每一行全部拆掉重建，
 * `.fk-files-list` 的入场动画也因此重跑一遍。勾选一下重建整份 DOM。
 *
 * 这一组断言钉的是**勾选不重建 DOM**：行元素的对象身份必须跨勾选保持不变。
 * 只断言 aria-selected 变了是不够的 —— 重建之后属性一样是对的，而闪烁照旧。
 */

function load(listing) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: { fileSymbolName: () => 'draft' },
      flikkyI18n: { t: (k, v) => (v && typeof v.count === 'number' ? k + ':' + v.count : k), onChange: () => {} },
    },
    fetch: () => Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve(listing),
      text: () => Promise.resolve(ndjson(listing)),
    }),
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return { doc, view, api: ctx.window.flikkyPanels.files };
}

const flush = async (n = 20) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };
const file = (name) => ({ name: name, isDir: false, size: 10, mtime: 0, mime: null });
const rows = (view) => byClass(view, 'fk-item');
const rowNamed = (view, name) => rows(view)
  .find((r) => byClass(r, 'fk-item-title').some((t) => t.textContent === name));

async function open(entries) {
  const c = load({ path: '', entries: entries });
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  return c;
}

test('selecting a row does not rebuild any other row', async () => {
  const c = await open([file('a.txt'), file('b.txt'), file('c.txt')]);
  const before = rows(c.view);
  assert.equal(before.length, 3);
  rowNamed(c.view, 'b.txt').dispatch('click');
  await flush();
  const after = rows(c.view);
  assert.equal(after.length, 3, 'row count changed');
  for (let i = 0; i < before.length; i += 1) {
    // 用 assert.ok(identity) 而不是 assert.equal：后者失败时要序列化两个带父指针的
    // DOM 对象求 diff，直接把 node 的堆吃爆（实测 FATAL ERROR: Reached heap limit）。
    // 断言的**失败路径**也得是安全的，否则红的时候看不到原因、只看到崩溃。
    assert.ok(
      before[i] === after[i],
      'row ' + i + ' is a different element after selecting — the list was rebuilt, ' +
        'which is exactly the flash the user saw',
    );
  }
});

test('selecting a row does not rebuild the breadcrumb either', async () => {
  const c = await open([file('a.txt')]);
  const crumbsBefore = byClass(c.view, 'fk-crumb');
  rowNamed(c.view, 'a.txt').dispatch('click');
  await flush();
  const crumbsAfter = byClass(c.view, 'fk-crumb');
  assert.equal(crumbsBefore.length, crumbsAfter.length);
  for (let i = 0; i < crumbsBefore.length; i += 1) {
    assert.ok(crumbsBefore[i] === crumbsAfter[i], 'the breadcrumb was rebuilt on selection');
  }
});

test('the selected row still reflects its state in the DOM', async () => {
  // 上面两条只保证「没重建」。这条保证「确实变了」——两条都要，
  // 否则一个什么都不做的实现也能过。
  const c = await open([file('a.txt')]);
  const row = rowNamed(c.view, 'a.txt');
  assert.equal(row.getAttribute('aria-selected'), 'false');
  row.dispatch('click');
  await flush();
  assert.equal(row.getAttribute('aria-selected'), 'true', 'the same element must update in place');
  row.dispatch('click');
  await flush();
  assert.equal(row.getAttribute('aria-selected'), 'false');
});

test('clearing the selection also updates in place', async () => {
  const c = await open([file('a.txt'), file('b.txt')]);
  rowNamed(c.view, 'a.txt').dispatch('click');
  await flush();
  const before = rows(c.view);
  // 按 aria-label 取，不按下标（工具栏的按钮增减过两轮，下标会静默指错）。
  const clear = byClass(byClass(c.view, 'fk-toolbar')[0], 'fk-icon-btn')
    .find((b) => (b.getAttribute('aria-label') || '').indexOf('app.files.clear') >= 0);
  assert.ok(clear, 'no clear button in the toolbar');
  clear.dispatch('click');
  await flush();
  const after = rows(c.view);
  for (let i = 0; i < before.length; i += 1) {
    assert.ok(before[i] === after[i], 'clearing rebuilt the list');
  }
  assert.equal(after[0].getAttribute('aria-selected'), 'false');
});
