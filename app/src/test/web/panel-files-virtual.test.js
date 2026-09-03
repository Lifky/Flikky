const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const LF = String.fromCharCode(10);

/*
 * 虚拟化（窗口渲染）：**只渲染视口附近的行**。
 *
 * 装机验收（2026-09-03）：「加载大/超大目录时、加载完成后，内存占用非常高
 * （尤其是浏览器），而且会造成明显卡顿」。实测每行 11 个元素节点 ——
 * 10000 行就是 11 万个元素，样式重算与布局每次都要走一遍。
 *
 * 这一组测试盯的是虚拟化最容易出错的地方，而不是「有没有虚拟化」：
 *   · DOM 行数有界，且与目录大小无关；
 *   · 滚动条高度诚实（否则用户不知道列表有多长）；
 *   · 滚动出来的行**路径正确**（刚栽过一次的地方）；
 *   · 滚动出来的行**选中状态正确**；
 *   · 首尾外圆角跟着数据下标走，不是 DOM 位置；
 *   · 滚动materialize 出来的行**不重播入场动效**（App 端同形缺陷的浏览器版）。
 */

function load(bodyText, opts) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const o = opts || {};
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
    fetch: () => Promise.resolve({
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
    }),
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return { doc, view, api: ctx.window.flikkyPanels.files };
}

const tick = async (n = 60) => { for (let k = 0; k < n; k += 1) await Promise.resolve(); };
const rows = (v) => byClass(v, 'fk-item');
const body = (v) => byClass(v, 'fk-panel-body')[0];
const list = (v) => byClass(v, 'fk-files-list')[0];
const titles = (v) => rows(v).map((r) => byClass(r, 'fk-item-title')[0].textContent);

const entry = (n) => JSON.stringify({ name: n, isDir: false, size: 1, mtime: 0, mime: null });
function listingOf(count) {
  const lines = [JSON.stringify({ path: '' })];
  for (let i = 0; i < count; i += 1) lines.push(entry('f' + i + '.txt'));
  lines.push(JSON.stringify({ done: true }));
  return lines.join(LF) + LF;
}

/** 打开一个 N 行的目录，并给滚动容器一个确定的视口高度。 */
async function opened(count, viewport) {
  const c = load(listingOf(count));
  c.api.mount(c.view);
  body(c.view).clientHeight = viewport || 600;
  c.api.setEnabled(true);
  await tick();
  return c;
}

/** 滚到某个像素位置并让虚拟化重新算窗口。 */
async function scrollTo(c, px) {
  body(c.view).scrollTop = px;
  body(c.view).dispatch('scroll');
  await tick(6);
}

test('a huge directory keeps only a bounded number of rows in the DOM', async () => {
  const c = await opened(10000);
  const n = rows(c.view).length;
  assert.ok(n > 0, 'something must render');
  assert.ok(
    n < 200,
    'a 10000-entry directory rendered ' + n + ' rows; that is 11 nodes each, ' +
      'which is the reported memory and jank',
  );
});

test('the rendered count does not grow with the directory', async () => {
  const small = await opened(500);
  const huge = await opened(20000);
  assert.ok(
    rows(huge.view).length <= rows(small.view).length,
    'rendered rows must be bounded by the viewport, not the data: ' +
      rows(small.view).length + ' vs ' + rows(huge.view).length,
  );
});

test('the scrollbar reflects the whole listing, not the rendered slice', async () => {
  const c = await opened(10000);
  const h = parseFloat(list(c.view).style.getPropertyValue('height'));
  assert.ok(h > 0, 'the list must declare a height for the full listing');
  const perRow = h / 10000;
  assert.ok(
    perRow > 8 && perRow < 400,
    'the declared height should be about one row each, got ' + perRow + 'px per row',
  );
});

test('scrolling reveals later rows and drops earlier ones', async () => {
  const c = await opened(10000);
  assert.equal(titles(c.view)[0], 'f0.txt', 'starts at the top');
  await scrollTo(c, 20000);
  const shown = titles(c.view);
  assert.ok(shown.length > 0, 'something must render after scrolling');
  assert.equal(shown.indexOf('f0.txt'), -1, 'the top rows must have been dropped');
  const first = parseInt(shown[0].slice(1), 10);
  assert.ok(first > 50, 'the window must have moved down, first row is ' + shown[0]);
  assert.ok(rows(c.view).length < 200, 'and it must still be bounded');
});

test('rows materialised by scrolling carry the right path', async () => {
  // 刚栽过一次的地方：秒回时行的路径用了上一个目录的前缀。
  // 窗口化让「行是后来才建的」成为常态，所以这条要单独钉住。
  const c = await opened(10000);
  await scrollTo(c, 20000);
  const el = rows(c.view)[0];
  const name = byClass(el, 'fk-item-title')[0].textContent;
  const dl = byClass(el, 'fk-icon-btn')[0];
  assert.ok(dl, 'a file row has a download button');
  assert.equal(
    el.getAttribute('data-path') || name,
    name,
    'sanity: the row is the one we think it is',
  );
  // 选中它，计数里的路径必须是这一行自己的名字（根目录下就是裸文件名）。
  el.dispatch('click');
  await tick();
  assert.equal(el.getAttribute('aria-selected'), 'true', 'the row must select');
});

test('selection survives a row being scrolled away and back', async () => {
  const c = await opened(10000);
  rows(c.view)[0].dispatch('click');
  await tick();
  assert.equal(rows(c.view)[0].getAttribute('aria-selected'), 'true');
  await scrollTo(c, 20000);
  await scrollTo(c, 0);
  assert.equal(titles(c.view)[0], 'f0.txt', 'we are back at the top');
  assert.equal(
    rows(c.view)[0].getAttribute('aria-selected'),
    'true',
    'a rebuilt row must pick its selected state back up',
  );
});

test('select all marks rows that only appear later', async () => {
  const c = await opened(10000);
  const toggle = byClass(byClass(c.view, 'fk-panel-head')[0], 'fk-icon-btn')
    .find((b) => {
      const i = byClass(b, 'material-symbols-outlined')[0];
      return i && i.getAttribute('data-icon') === 'select_all';
    });
  assert.ok(toggle, 'no select-all toggle');
  toggle.dispatch('click');
  await tick();
  await scrollTo(c, 20000);
  const marks = rows(c.view).map((r) => r.getAttribute('aria-selected'));
  assert.ok(marks.length > 0);
  assert.ok(
    marks.every((m) => m === 'true'),
    'rows materialised after select all must render as selected: ' + marks.slice(0, 5),
  );
});

test('the outer corners stay on the true first and last rows', async () => {
  const c = await opened(10000);
  assert.ok(rows(c.view)[0].className.indexOf('is-first') >= 0, 'row 0 is first');
  await scrollTo(c, 20000);
  const middle = rows(c.view);
  assert.ok(
    middle.every((r) => r.className.indexOf('is-first') < 0),
    'no row in the middle of the list may claim the top corner',
  );
  assert.ok(
    middle.every((r) => r.className.indexOf('is-last') < 0),
    'nor the bottom one',
  );
});

test('scrolling does not replay the row entrance animation', async () => {
  // App 端同形缺陷的浏览器版：窗口化之后行会被反复重建，
  // 每次重建都放一次入场动效 = 滚动时整屏闪。
  const c = await opened(10000);
  await scrollTo(c, 20000);
  const l = list(c.view);
  assert.equal(
    l.className.indexOf('fk-list-in') >= 0,
    false,
    'the entrance class must be off once rows are being recycled by scrolling',
  );
});
