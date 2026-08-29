const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom');

const WEB = path.join(__dirname, '../../main/assets/web');
const src = fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8');

const LISTING = {
  path: '',
  entries: [
    { name: 'DCIM', isDir: true, size: 0, mtime: 1755300000000, mime: null, childCount: 12, restricted: false },
    { name: 'report.pdf', isDir: false, size: 2516582, mtime: 1755310000000, mime: 'application/pdf', childCount: null, restricted: false },
    { name: '照片 1.png', isDir: false, size: 220100, mtime: 1755320000000, mime: 'image/png', childCount: null, restricted: false },
  ],
};

function load({ response = LISTING, status = 200, throwNetwork = false } = {}) {
  const doc = createDocument();
  const root = doc.register('view-files');
  const fetched = [];
  const ctx = {
    console,
    document: doc,
    setTimeout(fn) { fn(); return 1; },
    clearTimeout() {},
    Promise,
    encodeURIComponent,
    fetch(url, opts) {
      fetched.push({ url, opts: opts || {} });
      if (throwNetwork) return Promise.reject(new Error('offline'));
      return Promise.resolve({
        ok: status >= 200 && status < 300,
        status,
        json: () => Promise.resolve(response),
      });
    },
    // onChange 必须像真实 i18n.js 一样「订阅时立刻同步调用一次」。
    flikkyI18n: {
      t: (key, values) => (values ? key + ':' + JSON.stringify(values) : key),
      onChange(cb) { cb(); return () => {}; },
    },
  };
  ctx.window = ctx;
  ctx.globalThis = ctx;
  // 分类图标的唯一事实源由 app.js 导出；面板必须用它，不能自己再列一张映射表。
  ctx.window.flikky = {
    fileSymbolName: (mime) => 'sym(' + (mime || '') + ')',
    formatSize: (b) => b + 'B_fmt',
  };
  vm.runInNewContext(src, ctx, { filename: 'panel-files.js' });
  const api = ctx.window.flikkyPanels && ctx.window.flikkyPanels.files;
  assert.ok(api, 'panel-files.js must register window.flikkyPanels.files');
  return { doc, root, api, fetched, ctx };
}

const flush = async (n = 12) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };

async function mounted(opts) {
  const c = load(opts);
  c.api.mount(c.root);
  await flush();
  return c;
}

const rows = (doc) => byClass(doc.getElementById('view-files'), 'fk-item');
const rowByName = (doc, name) => rows(doc)
  .find((r) => byClass(r, 'fk-item-title').some((t) => t.textContent === name));
const trailOf = (row) => byClass(row, 'fk-item-trail')[0];
const iconsOf = (node) => byClass(node, 'material-symbols-outlined');
const buttonsIn = (node) => (node ? node.children.filter((c) => c.tagName === 'BUTTON') : []);

test('a directory row leads with a folder icon, counts children, and offers no download', async () => {
  const { doc } = await mounted();
  const row = rowByName(doc, 'DCIM');
  assert.ok(row, 'no DCIM row');
  const lead = byClass(row, 'fk-item-lead')[0];
  assert.ok(lead, 'directory row has no .fk-item-lead');
  assert.equal(iconsOf(lead)[0].dataset.icon, 'folder');
  // 副标题是「N 项」，不是大小——目录没有大小可言。
  assert.match(byClass(row, 'fk-item-sub')[0].textContent, /12/);
  // 行尾是进入指示，不是下载按钮。
  const trail = trailOf(row);
  assert.ok(trail, 'no trail slot');
  assert.equal(iconsOf(trail)[0].dataset.icon, 'chevron_right');
  assert.equal(buttonsIn(trail).length, 0, 'a directory must not ship a download button');
});

test('a file row takes its category icon from the shared source and shows size and time', async () => {
  const { doc } = await mounted();
  const row = rowByName(doc, 'report.pdf');
  assert.ok(row, 'no report.pdf row');
  // 分类图标必须来自 window.flikky.fileSymbolName —— 面板不许自带第二张映射表。
  assert.equal(iconsOf(byClass(row, 'fk-item-lead')[0])[0].dataset.icon,
    'sym(application/pdf)');
  const sub = byClass(row, 'fk-item-sub')[0].textContent;
  assert.match(sub, /B_fmt/, 'size must come from the shared formatter: ' + sub);
  assert.match(sub, /·/, 'no size-time separator in: ' + sub);
  assert.equal(buttonsIn(trailOf(row)).length, 1, 'file row needs exactly one action');
});

test('a download button names its file, so a screen reader can tell ten of them apart', async () => {
  const { doc } = await mounted();
  const la = buttonsIn(trailOf(rowByName(doc, 'report.pdf')))[0].getAttribute('aria-label');
  const lb = buttonsIn(trailOf(rowByName(doc, '照片 1.png')))[0].getAttribute('aria-label');
  assert.ok(la && lb, 'download buttons must carry aria-label');
  assert.notEqual(la, lb, 'two rows share one label — a screen reader hears "download" ten times');
});

test('a restricted row is disabled, explains itself, and offers no action at all', async () => {
  const listing = {
    path: 'Android',
    entries: [
      { name: 'data', isDir: true, size: 0, mtime: 1, mime: null, childCount: null, restricted: true },
    ],
  };
  const { doc } = await mounted({ response: listing });
  const row = rowByName(doc, 'data');
  assert.ok(row, 'no restricted row');
  assert.equal(row.getAttribute('aria-disabled'), 'true');
  // 置灰而不隐藏，是为了让用户知道「不是 Flikky 漏了，是系统不给」——
  // 所以必须有说明文案，且既无 chevron 也无下载按钮。
  assert.match(byClass(row, 'fk-item-sub')[0].textContent, /restricted/i);
  // 「没有按钮」判得太窄：逼红时把门禁改成恒真，restricted 目录行拿到的是 chevron span
  // 加整行 click 监听，一条都没红——而那照样在骗用户「这里能进去」。
  // 三条一起钉：无按钮、无任何行尾图标（chevron 就是「可进入」的暗示）、整行无 click 监听。
  const trail = trailOf(row);
  assert.equal(buttonsIn(trail).length, 0, 'a restricted row must not offer any action');
  assert.equal(iconsOf(trail).length, 0, 'a chevron implies the row can be entered — it cannot');
  assert.equal((row.listeners.get('click') || []).length, 0,
    'a restricted row must not be clickable');
});

test('every icon the panel creates is data-icon driven, empty, and hidden from AT', async () => {
  const { doc } = await mounted();
  const all = iconsOf(doc.getElementById('view-files'));
  assert.ok(all.length >= 4, 'expected several icons, found ' + all.length);
  for (const el of all) {
    assert.ok(el.dataset.icon, 'icon span without data-icon');
    assert.equal((el.textContent || '').trim(), '',
      'icon span carries text — that puts the glyph back into DOM text');
    assert.equal(el.getAttribute('aria-hidden'), 'true');
  }
});

test('the breadcrumb is a nav/ol, collapses from the left, and keeps the last two levels', async () => {
  const { doc } = await mounted({ response: { path: 'a/b/c/d/e', entries: [] } });
  const view = doc.getElementById('view-files');
  const nav = byClass(view, 'fk-crumbs')[0];
  assert.ok(nav, 'no breadcrumb container');
  assert.equal(nav.tagName, 'NAV');
  assert.ok(nav.getAttribute('aria-label'), 'breadcrumb nav needs an aria-label');
  assert.ok(nav.children.some((c) => c.tagName === 'OL'), 'breadcrumb must be an ordered list');

  const labels = byClass(view, 'fk-crumb').map((c) => c.textContent);
  // 首级恒显示，末两级恒显示，中间折叠为一个展开控件。
  // 从右侧折叠会藏掉「我在哪」，方向是反的。
  assert.ok(labels.length < 6, 'breadcrumb did not collapse: ' + labels.join('/'));
  assert.ok(labels.some((l) => l.includes('…')), 'no collapse control in ' + labels.join('/'));
  assert.equal(labels[labels.length - 1], 'e', 'the current level must be last');
  assert.equal(labels[labels.length - 2], 'd', 'the parent level must stay visible');
});

test('the current breadcrumb level is marked current and is not a button', async () => {
  const { doc } = await mounted({ response: { path: 'DCIM/Camera', entries: [] } });
  const crumbs = byClass(doc.getElementById('view-files'), 'fk-crumb');
  const last = crumbs[crumbs.length - 1];
  assert.equal(last.getAttribute('aria-current'), 'page');
  assert.notEqual(last.tagName, 'BUTTON', 'the level you are already on must not be clickable');
  // 上级必须可点，否则面包屑没有回退能力。
  const parents = crumbs.slice(0, -1);
  assert.ok(parents.length >= 1);
  assert.ok(parents.every((c) => c.tagName === 'BUTTON'), 'ancestor crumbs must be buttons');
});

test('the panel never reaches for innerHTML', () => {
  // 剥掉注释再查：本文件的头部注释里就写着「禁 innerHTML」，直接搜原文会把
  // 一条说明文字判成违规。v1.19.0 复盘记过这一类「测试被注释文本绊倒」。
  const code = src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
  assert.equal(/innerHTML/.test(code), false, 'panel-files.js must render through textContent only');
});
