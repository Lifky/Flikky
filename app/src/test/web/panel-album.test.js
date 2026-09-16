const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, findAll, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const PANEL = path.join(WEB, 'panel-album.js');
const rawSource = fs.existsSync(PANEL) ? fs.readFileSync(PANEL, 'utf8') : '';
/**
 * 扫描判据一律用剥掉注释的源码。
 *
 * 本文件的守卫会点名被禁的 API（offsetHeight / getFullYear …），而 panel-album.js
 * 的注释里正好要解释「为什么不用它们」—— 扫原文就会在「注释提到它」上误判。
 * Kotlin 侧的 stripCommentsAndImports 记着同一条教训。
 */
const stripJsComments = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
const source = stripJsComments(rawSource);
const LF = String.fromCharCode(10);
const FIXED_NOW = new Date(2026, 8, 15, 12, 0, 0).getTime();

class FixedDate extends Date {
  constructor(...args) { super(...(args.length ? args : [FIXED_NOW])); }
  static now() { return FIXED_NOW; }
}

/**
 * 手机算好的日期键（D65）。测试夹具必须自己生成它，因为真实服务端就是这么下发的 ——
 * 面板自己**不再**从 takenAtMs 推日期。这里用本地时区模拟「手机的时区」。
 */
function keyOf(takenAtMs) {
  const at = new Date(takenAtMs);
  const pad = (value) => String(value).padStart(2, '0');
  return `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}`;
}

const TODAY_KEY = keyOf(FIXED_NOW);
const YESTERDAY_KEY = keyOf(FIXED_NOW - 24 * 60 * 60 * 1000);

const item = (id, takenAtMs, extra) => Object.assign({
  id,
  name: id + '.jpg',
  mime: 'image/jpeg',
  size: 1024,
  takenAtMs,
  durationMs: 0,
  dateKey: keyOf(takenAtMs),
}, extra || {});

function albumNdjson(items, done = true) {
  const lines = [JSON.stringify({
    total: items.length,
    todayKey: TODAY_KEY,
    yesterdayKey: YESTERDAY_KEY,
  })];
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
  if (rawSource) vm.runInContext(rawSource, ctx);
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

test('geometry is never measured from the live layout', () => {
  // 原判据的后半钉的是「一行三个」，而那正是装机反馈要改掉的东西
  // （面板宽度可变，按比例分会让缩略图跟着缩放）。前半的意图不变并保留：
  // JS 不读实际布局 —— 那类自测量在本项目已经错过两次。
  assert.ok(source, 'panel-album.js is missing');
  for (const measurement of ['offsetHeight', 'offsetWidth', 'getBoundingClientRect', 'getComputedStyle']) {
    assert.equal(source.includes(measurement), false, 'JS measures album geometry with ' + measurement);
  }
});

test('tiles are a fixed size and wrap, instead of splitting the width three ways', () => {
  const css = fs.readFileSync(path.join(WEB, 'panels.css'), 'utf8');
  const grid = css.match(/\.fk-album-grid\s*\{[^}]*\}/);
  const tile = css.match(/\.fk-album-tile\s*\{[^}]*\}/);
  assert.ok(grid && tile, 'no .fk-album-grid / .fk-album-tile CSS rule');

  // 装机反馈（Screenshot_8 / 9）：格子跟着面板宽度缩放、永远一行三个。
  assert.equal(/grid-template-columns/.test(grid[0]), false,
    'a fixed column count makes tiles scale with the panel width');
  assert.match(tile[0], /width:\s*var\(--flikky-album-tile\)/);
  assert.match(tile[0], /height:\s*var\(--flikky-album-tile\)/);
  assert.match(tile[0], /flex:\s*none/);
});

test('the shared tile geometry has one source of truth across CSS and JS', () => {
  // JS 需要格子尺寸才能算「每行几个」与滚动区间；它刻意不测量布局，
  // 所以两边各有一份常量 —— 那就必须钉住它们相等，否则滚动位置会越滚越偏
  // （首版硬编码了一个 132px 的行距，与真实行高不符）。
  const css = fs.readFileSync(path.join(WEB, 'panels.css'), 'utf8');
  const cssTile = css.match(/--flikky-album-tile:\s*(\d+)px/);
  const cssDate = css.match(/\.fk-album-date\s*\{[^}]*height:\s*(\d+)px/);
  const cssGap = css.match(/\.fk-album-grid\s*\{[^}]*gap:\s*(\d+)px/);
  assert.ok(cssTile && cssDate && cssGap, 'album CSS must state tile, gap and date-row sizes in px');

  const jsTile = source.match(/const TILE_PX = (\d+);/);
  const jsGap = source.match(/const TILE_GAP_PX = (\d+);/);
  const jsDate = source.match(/const DATE_ROW_PX = (\d+);/);
  assert.ok(jsTile && jsGap && jsDate, 'panel-album.js must declare TILE_PX / TILE_GAP_PX / DATE_ROW_PX');

  assert.equal(jsTile[1], cssTile[1], 'tile size drifted between CSS and JS');
  assert.equal(jsGap[1], cssGap[1], 'tile gap drifted between CSS and JS');
  assert.equal(jsDate[1], cssDate[1], 'date row height drifted between CSS and JS');
});

test('the panel derives no date of its own', () => {
  // D65：日期由手机算一次并下发。面板一旦自己取年月日，两台设备时区不同就会
  // 把同一张照片分到不同的天（装机 Screenshot_12 / 13）。
  for (const banned of ['getFullYear', 'getMonth', 'getDate(', 'getUTCDate', 'toISOString']) {
    assert.equal(source.includes(banned), false,
      'panel-album.js computes a date itself with ' + banned + ' — that is the double-derivation bug');
  }
  assert.ok(source.includes('todayKey'), 'the panel must consume the phone-side todayKey');
  assert.ok(source.includes('dateKey'), 'the panel must group by the phone-side dateKey');
});

test('virtual rows opt out of browser scroll anchoring', () => {
  const css = fs.readFileSync(path.join(WEB, 'panels.css'), 'utf8');
  const rule = css.match(/\.fk-album-rows\s*\{[^}]*\}/);
  assert.ok(rule, 'no .fk-album-rows CSS rule');
  assert.match(rule[0], /overflow-anchor:\s*none\s*;/,
    'virtual row replacement can cancel mouse-wheel scrolling through browser anchoring');
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
  const labels = byClass(c.view, 'fk-album-open').map((el) => el.getAttribute('aria-label'));
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

test('a small wheel scroll keeps the current virtual window mounted', async () => {
  const items = Array.from({ length: 1000 }, (_, i) => item('img:' + i, FIXED_NOW - i));
  const c = await opened(items);
  const body = byClass(c.view, 'fk-panel-body')[0];
  const firstTile = byClass(c.view, 'fk-album-tile')[0];

  body.scrollTop = 120;
  await tick(8);

  assert.equal(byClass(c.view, 'fk-album-tile')[0] === firstTile, true,
    'scrolling inside the current virtual window replaced the browser scroll anchor');
});

test('tapping a thumbnail opens the shared lightbox', async () => {
  const c = await opened([item('img:7', FIXED_NOW, { name: 'holiday.jpg' })]);
  // 格子现在是 tile > open：选择角标要一个自己的按钮，所以打开动作下移了一层。
  const open = byClass(c.view, 'fk-album-open')[0];
  assert.ok(open, 'no album tile');
  open.dispatch('click');
  assert.equal(c.lightboxes.length, 1);
  assert.equal(c.lightboxes[0].kind, 'image');
  assert.equal(c.lightboxes[0].thumbnailUrl, '/api/album/thumb?id=img%3A7');
  assert.equal(c.lightboxes[0].fullUrl, '/api/album/file?id=img%3A7&inline=1');
});

test('thumbnails cannot be dragged into the chat drop zone', () => {
  // 相册项就在手机上。把缩略图拖出去会被会话页的全局 drop zone 当成
  // 「拖了个文件进来」并提示「松开以发送文件」，于是用户会把手机上的照片
  // 又发一遍给手机（装机反馈 2026-09-16）。
  // 两处都要：按钮与它里面的 <img> 各自都能起拖。只断言「出现过一次」
  // 会在删掉其中一处时零红（逼红实测），所以逐个点名。
  assert.ok(source.includes('open.draggable = false'), 'the tile button must opt out of dragging');
  assert.ok(source.includes('image.draggable = false'), 'the thumbnail image must opt out of dragging');
  const dragstarts = source.match(/addEventListener\('dragstart'/g) || [];
  assert.ok(dragstarts.length >= 2,
    'both the button and the image need a dragstart handler — draggable=false alone does not stop a child drag');
});

test('a thumbnail that 404s is not requested again', async () => {
  // 装机时同一个失败项被窗口反复重发，控制台 244 次请求 / 2.6MB。
  const c = await opened([item('img:bad', FIXED_NOW)]);
  const image = findAll(c.view, (el) => el.tagName === 'IMG')[0];
  assert.ok(image, 'no thumbnail image');
  assert.ok(image.getAttribute('src'), 'the first attempt must actually request');

  image.dispatch('error');
  const afterFailure = findAll(c.view, (el) => el.tagName === 'IMG');
  assert.equal(afterFailure.length, 0, 'the failed image element must be replaced by a placeholder');

  // 触发一次窗口重算：失败项不该再产生 <img>。
  const body = byClass(c.view, 'fk-panel-body')[0];
  body.scrollTop = 0;
  c.api.render();
  await tick(4);
  assert.equal(findAll(c.view, (el) => el.tagName === 'IMG').length, 0,
    'a failed thumbnail was requested again after a re-render');
});

test('picking a thumbnail selects it without opening the lightbox', async () => {
  const c = await opened([
    item('img:1', FIXED_NOW),
    item('img:2', FIXED_NOW - 1000),
  ]);
  const pick = byClass(c.view, 'fk-album-pick')[0];
  assert.ok(pick, 'tiles need their own selection affordance — a mouse has no long press');

  pick.dispatch('click');
  assert.equal(c.lightboxes.length, 0, 'picking must not open the preview');
  const tiles = byClass(c.view, 'fk-album-tile');
  assert.equal(tiles[0].getAttribute('data-selected'), '1');
  assert.equal(tiles[1].getAttribute('data-selected'), null);

  // 选择态下点图是继续勾选，与 App 端多选态同一手感。
  byClass(c.view, 'fk-album-open')[1].dispatch('click');
  assert.equal(c.lightboxes.length, 0);
  assert.equal(byClass(c.view, 'fk-album-tile')[1].getAttribute('data-selected'), '1');
});

test('the two views are both reachable, and a bucket has a way back', async () => {
  // 2026-09-16 裁决：时间线回答「最近拍的」，相册簿回答「微信存的那张在哪」。
  const c = load({
    body: albumNdjson([item('img:1', FIXED_NOW)]),
    fetch: (url, _init, n) => {
      if (String(url).includes('/api/album/buckets')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve([
            { name: 'Camera', count: 12, coverId: 'img:9' },
            { name: '', count: 2, coverId: 'img:4' },
          ]),
        });
      }
      return Promise.resolve(response(albumNdjson([item('img:1', FIXED_NOW)])));
    },
  });
  const body = byClass(c.view, 'fk-panel-body')[0];
  body.clientHeight = 600;
  c.api.setEnabled(true);
  await tick();

  const views = byClass(c.view, 'fk-album-view');
  assert.equal(views.length, 2, 'the timeline needs a switch to the album view');

  views[1].dispatch('click');
  await tick();
  const cards = byClass(c.view, 'fk-album-bucket');
  assert.equal(cards.length, 2, 'the album view must list buckets');
  // 空簿名是合法的（未知相册），不能因此少一张卡。
  assert.equal(byClass(c.view, 'fk-album-bucket-name')[1].textContent, 'app.album.unknownBucket');

  cards[0].dispatch('click');
  await tick();
  const requested = c.fetched.map((f) => String(f.url));
  assert.ok(requested.some((url) => url.includes('bucket=Camera')),
    'opening a bucket must ask the server for just that bucket: ' + requested.join(' | '));
  const back = byClass(c.view, 'fk-album-view');
  assert.equal(back.length, 1, 'inside a bucket there is one action: going back');
});

test('the selection toolbar reuses the shared classes and counts what is picked', async () => {
  const c = await opened([item('img:1', FIXED_NOW)]);
  const toolbar = byClass(c.view.parentNode || c.view, 'fk-toolbar')[0]
    || byClass(c.view, 'fk-toolbar')[0];
  assert.ok(toolbar, 'the album panel must reuse .fk-toolbar, not invent its own bar');
  assert.equal(toolbar.hidden, true, 'the toolbar only exists while something is picked');

  byClass(c.view, 'fk-album-pick')[0].dispatch('click');
  assert.equal(toolbar.hidden, false);
  const count = byClass(toolbar, 'fk-toolbar-count')[0];
  assert.ok(count, 'the toolbar must carry a .fk-toolbar-count, like the files panel');
  assert.equal(count.textContent, 'app.album.selected:1');
});
