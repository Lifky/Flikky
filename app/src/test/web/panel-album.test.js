const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, findAll, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const PANEL = path.join(WEB, 'panel-album.js');
const source = fs.existsSync(PANEL) ? fs.readFileSync(PANEL, 'utf8') : '';
const LF = String.fromCharCode(10);
const FIXED_NOW = new Date(2026, 8, 15, 12, 0, 0).getTime();

class FixedDate extends Date {
  constructor(...args) { super(...(args.length ? args : [FIXED_NOW])); }
  static now() { return FIXED_NOW; }
}

const item = (id, takenAtMs, extra) => Object.assign({
  id,
  name: id + '.jpg',
  mime: 'image/jpeg',
  size: 1024,
  takenAtMs,
  durationMs: 0,
}, extra || {});

function albumNdjson(items, done = true) {
  const lines = [JSON.stringify({ total: items.length })];
  items.forEach((value) => lines.push(JSON.stringify(value)));
  if (done) lines.push(JSON.stringify({ done: true }));
  return lines.join(LF) + LF;
}

function response(text) {
  let sent = false;
  return {
    ok: true,
    status: 200,
    body: {
      getReader: () => ({
        read: () => Promise.resolve(sent
          ? { value: undefined, done: true }
          : ((sent = true), { value: Buffer.from(text, 'utf8'), done: false })),
        cancel: () => {},
      }),
    },
  };
}

function load(options) {
  const opts = options || {};
  const doc = createDocument();
  const view = doc.register('view-album');
  const fetched = [];
  const errors = [];
  const lightboxes = [];
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: {
        openLightbox: (value) => lightboxes.push(value),
        showError: (value) => errors.push(value),
      },
      flikkyI18n: {
        t: (key, values) => {
          if (key === 'app.album.monthDay') return `${values.month}/${values.day}`;
          if (key === 'app.album.yearMonthDay') return `${values.year}/${values.month}/${values.day}`;
          if (values && typeof values.count === 'number') return `${key}:${values.count}`;
          return opts.titleMarkup && key === 'app.album.title' ? '<b>Album</b>' : key;
        },
        onChange: () => {},
      },
    },
    Date: FixedDate,
    TextDecoder,
    requestAnimationFrame: (fn) => { fn(0); return 1; },
    fetch: (url, init) => {
      fetched.push({ url, init });
      if (opts.fetch) return opts.fetch(url, init, fetched.length);
      return Promise.resolve(response(opts.body || albumNdjson([])));
    },
    console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  if (source) vm.runInContext(source, ctx);
  return {
    doc,
    view,
    api: ctx.window.flikkyPanels.album,
    fetched,
    errors,
    lightboxes,
  };
}

const tick = async (n = 60) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };
const localAt = (year, month, day, hour = 10) => new Date(year, month - 1, day, hour).getTime();

async function opened(items, options) {
  const c = load(Object.assign({}, options, { body: albumNdjson(items) }));
  assert.ok(c.api, 'panel-album.js did not publish window.flikkyPanels.album');
  const body = byClass(c.view, 'fk-panel-body')[0];
  assert.ok(body, 'album panel body was not mounted');
  body.clientHeight = 600;
  c.api.setEnabled(true);
  await tick();
  return c;
}

test('the panel renders no text through innerHTML', async () => {
  assert.ok(source, 'panel-album.js is missing');
  assert.doesNotMatch(source, /\.innerHTML\s*=/);
  const c = await opened([item('img:1', FIXED_NOW, { name: '<img src=x>.jpg' })], {
    titleMarkup: true,
  });
  assert.equal(byClass(c.view, 'fk-panel-title')[0].textContent, '<b>Album</b>');
  assert.equal(findAll(c.view, (el) => el.tagName === 'B').length, 0,
    'translated text was parsed as markup');
});

test('only a window of rows is in the DOM, regardless of album size', async () => {
  const items = Array.from({ length: 10000 }, (_, i) => item('img:' + i, FIXED_NOW - i));
  const c = await opened(items);
  const renderedRows = byClass(c.view, 'fk-album-date').length + byClass(c.view, 'fk-album-grid').length;
  assert.ok(renderedRows > 0, 'the first window is empty');
  assert.ok(renderedRows < 80, '10000 items rendered ' + renderedRows + ' logical rows');
  assert.ok(byClass(c.view, 'fk-album-tile').length < 200, 'the full album entered the DOM');
});

test('row height comes from CSS, never measured in JS', () => {
  assert.ok(source, 'panel-album.js is missing');
  for (const measurement of ['offsetHeight', 'offsetWidth', 'getBoundingClientRect', 'getComputedStyle']) {
    assert.equal(source.includes(measurement), false, 'JS measures album geometry with ' + measurement);
  }
  const css = fs.readFileSync(path.join(WEB, 'panels.css'), 'utf8');
  const rule = css.match(/\.fk-album-grid\s*\{[^}]*\}/);
  assert.ok(rule, 'no .fk-album-grid CSS rule');
  assert.match(rule[0], /grid-template-columns:\s*repeat\(3,/);
  assert.match(rule[0], /gap:\s*var\(--flikky-listgroup-gap\)/);
});

test('date headers participate in the virtual row count', async () => {
  const items = [
    item('img:future', localAt(2027, 1, 1)),
    item('img:today-1', localAt(2026, 9, 15, 11)),
    item('img:today-2', localAt(2026, 9, 15, 10)),
    item('img:today-3', localAt(2026, 9, 15, 9)),
    item('img:yesterday', localAt(2026, 9, 14)),
    item('img:same-year', localAt(2026, 9, 10)),
    item('img:older', localAt(2025, 9, 10)),
  ];
  const c = await opened(items);
  const headers = byClass(c.view, 'fk-album-date');
  const grids = byClass(c.view, 'fk-album-grid');
  assert.deepEqual(headers.map((el) => el.textContent), [
    'app.album.today',
    'app.album.yesterday',
    '9/10',
    '2025/9/10',
  ]);
  assert.equal(grids.length, 5, 'four today items must occupy two media rows');
  const rowIndices = headers.concat(grids).map((el) => Number(el.dataset.rowIndex)).sort((a, b) => a - b);
  assert.deepEqual(rowIndices, [0, 1, 2, 3, 4, 5, 6, 7, 8],
    'date headers must consume their own logical row indices');
});

test('a superseded stream cannot draw over the current one', async () => {
  let releaseFirst;
  const first = new Promise((resolve) => { releaseFirst = resolve; });
  const c = load({
    fetch: (_url, _init, requestNumber) => requestNumber === 1
      ? first
      : Promise.resolve(response(albumNdjson([item('img:2', FIXED_NOW, { name: 'current.jpg' })]))),
  });
  assert.ok(c.api, 'panel-album.js did not publish window.flikkyPanels.album');
  c.api.setEnabled(true);
  await tick(4);
  c.api.setEnabled(false);
  c.api.setEnabled(true);
  await tick();
  releaseFirst(response(albumNdjson([item('img:1', FIXED_NOW, { name: 'stale.jpg' })])));
  await tick();
  const labels = byClass(c.view, 'fk-album-tile').map((el) => el.getAttribute('aria-label'));
  assert.deepEqual(labels, ['current.jpg']);
});

test('a stream without the done line is treated as truncated', async () => {
  const c = load({ body: albumNdjson([item('img:1', FIXED_NOW)], false) });
  assert.ok(c.api, 'panel-album.js did not publish window.flikkyPanels.album');
  c.api.setEnabled(true);
  await tick();
  assert.equal(c.errors.length, 1, 'a truncated stream must tell the user');
  assert.ok(c.errors[0].includes('truncated'), 'wrong message: ' + c.errors.join(' | '));
  assert.equal(byClass(c.view, 'fk-album-retry').length, 1, 'a truncated stream needs a retry action');

  const empty = load({ body: albumNdjson([], false) });
  empty.api.setEnabled(true);
  await tick();
  assert.equal(byClass(empty.view, 'fk-album-retry').length, 1,
    'a stream truncated before its first item still needs a retry action');
});

test('the panel does not request anything before the gate is known', async () => {
  const c = load();
  assert.ok(c.api, 'panel-album.js did not publish window.flikkyPanels.album');
  await tick();
  assert.deepEqual(c.fetched, [], 'mount fetched before peer-info: ' + JSON.stringify(c.fetched));
});

test('thumbnails are requested per visible row only', async () => {
  const items = Array.from({ length: 1000 }, (_, i) => item('img:' + i, FIXED_NOW - i));
  const c = load({ body: albumNdjson(items) });
  assert.ok(c.api, 'panel-album.js did not publish window.flikkyPanels.album');
  const body = byClass(c.view, 'fk-panel-body')[0];
  body.clientHeight = 420;
  c.api.setEnabled(true);
  await tick();
  const images = findAll(c.view, (el) => el.tagName === 'IMG');
  assert.ok(images.length > 0 && images.length < 100, 'thumbnail count is ' + images.length);
  assert.ok(images.every((img) => String(img.src).startsWith('/api/album/thumb?id=')));
  const first = images[0];
  body.scrollTop = 12000;
  body.dispatch('scroll');
  await tick(8);
  assert.equal(first.getAttribute('src'), null, 'a thumbnail kept loading after its row left the window');
});

test('tapping a thumbnail opens the shared lightbox', async () => {
  const c = await opened([item('img:7', FIXED_NOW, { name: 'holiday.jpg' })]);
  const tile = byClass(c.view, 'fk-album-tile')[0];
  assert.ok(tile, 'no album tile');
  tile.dispatch('click');
  assert.equal(c.lightboxes.length, 1);
  assert.equal(c.lightboxes[0].kind, 'image');
  assert.equal(c.lightboxes[0].thumbnailUrl, '/api/album/thumb?id=img%3A7');
  assert.equal(c.lightboxes[0].fullUrl, '/api/album/file?id=img%3A7&inline=1');
});
