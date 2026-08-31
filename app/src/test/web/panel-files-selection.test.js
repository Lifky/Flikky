const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');

/*
 * 装机验收缺陷 3（交互）与 7（浏览器侧性能）的行为守卫。
 *
 * 缺陷 3：文件面板只能逐个点下载，与收藏面板的多选 + 批量保存「天差地别」。
 * 缺陷 7：大目录的响应要几百毫秒，期间用户已经点进别的目录，
 *         旧响应后到就把他已经离开的目录重新画上来。
 */

function load(replies) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const state = { fetched: [], resolvers: [] };
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: { fileSymbolName: () => 'draft' },
      // 真实的 t() 会插值（i18n.js 有 interpolate）。不给桩函数的话计数断言
      // 只能看到原始 key，测不到「数字真的进了文案」。
      flikkyI18n: {
        t: (key, values) => (values && typeof values.count === 'number'
          ? key + ':' + values.count
          : key),
        onChange: () => {},
      },
    },
    fetch: (url) => {
      state.fetched.push(url);
      const next = replies.shift();
      if (next && next.defer) {
        return new Promise((resolve) => {
          state.resolvers.push(() => resolve({
            ok: next.status === 200,
            status: next.status,
            json: () => Promise.resolve(next.body || {}),
          }));
        });
      }
      const r = next || { status: 404 };
      return Promise.resolve({
        ok: r.status === 200,
        status: r.status,
        json: () => Promise.resolve(r.body || {}),
      });
    },
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return { doc, view, api: ctx.window.flikkyPanels.files, state };
}

const flush = async (n = 20) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };
const file = (name) => ({ name: name, isDir: false, size: 10, mtime: 0, mime: null });
const dir = (name) => ({ name: name, isDir: true, size: 0, mtime: 0, childCount: 2 });

const rows = (view) => byClass(view, 'fk-item');
const rowNamed = (view, name) => rows(view)
  .find((r) => byClass(r, 'fk-item-title').some((t) => t.textContent === name));
const toolbar = (view) => byClass(view, 'fk-toolbar')[0];
const crumbLabels = (view) => byClass(view, 'fk-crumb').map((c) => c.textContent);

async function open(listing) {
  const c = load([{ status: 200, body: listing }]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  return c;
}

test('a file row selects on a single tap, like the favourites rows', async () => {
  const c = await open({ path: '', entries: [file('a.txt')] });
  const row = rowNamed(c.view, 'a.txt');
  assert.equal(row.getAttribute('aria-selected'), 'false');
  row.dispatch('click');
  await flush();
  assert.equal(rowNamed(c.view, 'a.txt').getAttribute('aria-selected'), 'true');
  rowNamed(c.view, 'a.txt').dispatch('click');
  await flush();
  assert.equal(rowNamed(c.view, 'a.txt').getAttribute('aria-selected'), 'false');
});

test('a directory row navigates and never joins the selection', async () => {
  const c = load([
    { status: 200, body: { path: '', entries: [dir('DCIM')] } },
    { status: 200, body: { path: 'DCIM', entries: [] } },
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  rowNamed(c.view, 'DCIM').dispatch('click');
  await flush();
  assert.equal(c.state.fetched.length, 2, 'the directory tap must navigate');
  assert.ok(toolbar(c.view).hidden, 'a directory must not enter the selection');
});

test('the toolbar appears with a count and hides when cleared', async () => {
  const c = await open({ path: '', entries: [file('a.txt'), file('b.txt')] });
  assert.ok(toolbar(c.view).hidden, 'the toolbar must start hidden');
  rowNamed(c.view, 'a.txt').dispatch('click');
  await flush();
  rowNamed(c.view, 'b.txt').dispatch('click');
  await flush();
  assert.equal(toolbar(c.view).hidden, false, 'the toolbar must show once something is selected');
  const count = byClass(c.view, 'fk-toolbar-count')[0];
  assert.ok(count.textContent.indexOf('2') >= 0, 'count reads: ' + count.textContent);
  const buttons = byClass(toolbar(c.view), 'fk-icon-btn');
  assert.equal(buttons.length, 2, 'the toolbar must have clear and save, in that order');
  buttons[0].dispatch('click');
  await flush();
  assert.ok(toolbar(c.view).hidden, 'clearing must hide the toolbar');
});

test('the selection survives walking into another directory', async () => {
  // 跨目录累积是它的设计（上游 D5）。清空会让「从两个目录各挑几个」变成不可能。
  const c = load([
    { status: 200, body: { path: '', entries: [file('a.txt'), dir('DCIM')] } },
    { status: 200, body: { path: 'DCIM', entries: [file('b.jpg')] } },
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  rowNamed(c.view, 'a.txt').dispatch('click');
  await flush();
  rowNamed(c.view, 'DCIM').dispatch('click');
  await flush();
  assert.equal(toolbar(c.view).hidden, false, 'the toolbar vanished across a navigation');
  assert.ok(
    byClass(c.view, 'fk-toolbar-count')[0].textContent.indexOf('1') >= 0,
    'count reads: ' + byClass(c.view, 'fk-toolbar-count')[0].textContent,
  );
  rowNamed(c.view, 'b.jpg').dispatch('click');
  await flush();
  assert.ok(
    byClass(c.view, 'fk-toolbar-count')[0].textContent.indexOf('2') >= 0,
    'selection did not accumulate across directories',
  );
});

test('the download button does not also select the row', async () => {
  const c = await open({ path: '', entries: [file('a.txt')] });
  const btn = byClass(rowNamed(c.view, 'a.txt'), 'fk-icon-btn')[0];
  btn.dispatch('click');
  await flush();
  assert.equal(
    rowNamed(c.view, 'a.txt').getAttribute('aria-selected'),
    'false',
    'the row got selected by its own download button',
  );
});

test('a slow response cannot repaint a directory the user already left', async () => {
  // 缺陷 7 的浏览器版：大目录列举慢，用户点进去又改主意，
  // 旧响应后到就把界面拽回那个大目录。
  const c = load([
    { status: 200, body: { path: '', entries: [dir('Big'), dir('Small')] } },
    { status: 200, body: { path: 'Big', entries: [file('huge.bin')] }, defer: true },
    { status: 200, body: { path: 'Small', entries: [file('tiny.txt')] } },
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  c.api.navigate('Big');
  await flush();
  c.api.navigate('Small');
  await flush();
  assert.ok(rowNamed(c.view, 'tiny.txt'), 'the newer directory should be on screen');
  c.state.resolvers.forEach((r) => r());
  await flush();
  assert.ok(
    rowNamed(c.view, 'tiny.txt'),
    'the stale response repainted the directory the user had left',
  );
  assert.equal(rowNamed(c.view, 'huge.bin'), undefined, 'stale rows landed');
});

test('a tap shows progress and moves the breadcrumb before the reply arrives', async () => {
  // 只挪到后台还不够：点击到列表出现之间界面毫无变化，用户以为没点上。
  const c = load([
    { status: 200, body: { path: '', entries: [dir('DCIM')] } },
    { status: 200, body: { path: 'DCIM', entries: [] }, defer: true },
  ]);
  c.api.mount(c.view);
  c.api.setEnabled(true);
  await flush();
  rowNamed(c.view, 'DCIM').dispatch('click');
  await flush();
  assert.equal(byClass(c.view, 'fk-files-progress').length, 1, 'no progress while loading');
  assert.ok(
    crumbLabels(c.view).some((l) => l === 'DCIM'),
    'the breadcrumb must advance immediately; crumbs: ' + crumbLabels(c.view).join(' / '),
  );
});
