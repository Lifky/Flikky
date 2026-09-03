const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom.js');
const scan = require('./scan.js');

const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

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
    fetch: () => Promise.resolve({
      ok: true,
      status: 200,
      body: {
        getReader: () => {
          // opts.chunks：把响应切成 N 段分次交付，用来观察**追加期间**的行为
          // （整体一次交付看不到「每批重建一次」这类缺陷）。
          const parts = [];
          const n = o.chunks || 1;
          if (n <= 1) {
            parts.push(bodyText);
          } else {
            const lines = bodyText.split(LF).filter((x) => x.length > 0);
            const per = Math.ceil(lines.length / n);
            for (let k = 0; k < lines.length; k += per) {
              parts.push(lines.slice(k, k + per).join(LF) + LF);
            }
          }
          let at = 0;
          return {
            read: () => {
              if (at >= parts.length) return Promise.resolve({ value: undefined, done: true });
              const part = parts[at];
              at += 1;
              const chunk = { value: Buffer.from(part, 'utf8'), done: false };
              // opts.hold：第二批之后卡住，直到测试调用 release()。
              // 不这样的话两批会在同一轮微任务里被吃光，「追加期间」根本观察不到。
              if (at >= 2 && o.hold) return gate.then(() => chunk);
              return Promise.resolve(chunk);
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
  return { doc, view, api: ctx.window.flikkyPanels.files, release: () => release() };
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

test('the scroll extent grows with the listing, not with the window', async () => {
  const extent = async (n) => {
    const c = await opened(n);
    return byClass(c.view, 'fk-vspace')
      .reduce((sum, el) => sum + (parseFloat(el.style.getPropertyValue('height')) || 0), 0);
  };
  const small = await extent(500);
  const huge = await extent(10000);
  assert.ok(small > 0, 'a 500-entry listing must reserve room below the window');
  assert.ok(
    huge > small * 10,
    'the reserved room must scale with the directory: ' + small + ' vs ' + huge,
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

test('row spacing comes from CSS, not from JS geometry', () => {
  // 装机验收第三轮同一个症状：「listitem 挨得太近了，应该和收藏面板一样」。
  //
  // 前两次都是**测量**错了：一次靠正常流量 offsetTop（但样式表把行设成了
  // 无条件绝对定位，探针从未进入流），一次靠 getComputedStyle 拼 height + rowGap。
  // 两次的共同点是：**绝对定位要求 JS 知道行距**，而行距取决于字体、主题、缩放
  // 与内容 —— 算错一次就直接毁掉行距。
  //
  // 换架构：行留在正常流里，行距完全由 CSS 的 gap 负责 ——
  // 与收藏面板同一个 token、同一条声明。于是「视觉零差异」是**结构保证**的，
  // 不再取决于我是否算对。测量只用来撑滚动区间，算错也只是滚动条略不准。
  const js = scan.scrub(read('panel-files.js'));
  const sync = scan.functionBody(js, 'function syncVirtual(');
  assert.ok(sync, 'no syncVirtual');
  assert.equal(
    /setProperty\('top'/.test(sync),
    false,
    'rows must not be positioned by JS. Body:' + scan.LF + sync,
  );
  const css = scan.stripBlockComments(read('panels.css'));
  const abs = scan.ruleBlock(css, '.fk-files-list > .fk-item');
  assert.ok(
    abs === null || abs.indexOf('position: absolute') < 0,
    'the stylesheet must not take rows out of flow: ' + abs,
  );
  // 而容器的几何必须与收藏面板逐项相同。
  const group = scan.ruleBlock(css, '.fk-group');
  const files = scan.ruleBlock(css, '.fk-files-list');
  ['display: flex', 'flex-direction: column', 'gap: var(--flikky-listgroup-gap)'].forEach((bit) => {
    assert.ok(group.indexOf(bit) >= 0, 'favourites is expected to declare ' + bit);
    assert.ok(files.indexOf(bit) >= 0, 'files must declare ' + bit + ' too: ' + files);
  });
  assert.equal(
    files.indexOf('position: relative') >= 0,
    false,
    'no positioning context is needed once rows are in flow: ' + files,
  );
});

test('the scroll extent is carried by spacers, not an assumed height', async () => {
  const c = await opened(10000);
  assert.equal(
    list(c.view).style.getPropertyValue('height'),
    '',
    'the container must not declare a height; the spacers carry the extent',
  );
  const spacers = byClass(c.view, 'fk-vspace');
  assert.ok(spacers.length > 0, 'a long listing must have at least a trailing spacer');
  const tall = spacers.some((s) => parseFloat(s.style.getPropertyValue('height')) > 1000);
  assert.ok(tall, 'the trailing spacer must stand in for the rows below the window');
});

test('appending a batch reuses the rows already on screen', async () => {
  // 装机验收：「加载过程中列表项整体会时不时闪一下」。
  //
  // 根因是每批都 `textContent = ''` 把整窗拆掉重建 —— 指数分批约 10 批，
  // 于是重建 10 次。判据是**元素同一性**：同一行在追加前后必须是同一个对象。
  const c = load(listingOf(400), { chunks: 2, hold: true });
  c.api.mount(c.view);
  body(c.view).clientHeight = 600;
  c.api.setEnabled(true);
  await tick(40);
  const before = rows(c.view);
  assert.ok(before.length > 2, 'the first batch must be on screen, got ' + before.length);
  const kept = before.slice(0, 3);
  c.release();
  await tick(60);
  const after = rows(c.view);
  kept.forEach((el, i) => {
    assert.equal(
      after[i],
      el,
      'row ' + i + ' was torn down and rebuilt by a later batch — that is the flash',
    );
  });
});

test('the last-row corner moves as more rows arrive', async () => {
  // 复用行的代价：`is-last` 是建行时按**当时的** total 打的。total 长大之后
  // 那个类会留在一行不再是末行的行上 —— 圆角画错。所以每次 sync 都要重打。
  //
  // 判据必须落在**留在窗口里**的那一行上。第一版用了 400 行 2 批 ——
  // 首批的末行（第 199 行）根本不在窗口里，把重打删掉照样全绿（逼红实测：零条红）。
  // 所以这里用一个整份都在窗口内的小目录。
  const c = load(listingOf(8), { chunks: 2, hold: true });
  c.api.mount(c.view);
  body(c.view).clientHeight = 2000;
  c.api.setEnabled(true);
  await tick(40);
  const first = rows(c.view);
  assert.equal(first.length, 4, 'the first half must be on screen');
  assert.ok(
    first[3].className.indexOf('is-last') >= 0,
    'row 3 is genuinely the last one right now',
  );
  c.release();
  await tick(60);
  const all = rows(c.view);
  assert.equal(all.length, 8, 'the whole listing must be on screen');
  assert.equal(
    all[3].className.indexOf('is-last') >= 0,
    false,
    'row 3 stopped being last, so it must lose the bottom corner',
  );
  assert.ok(
    all[7].className.indexOf('is-last') >= 0,
    'and row 7 must gain it',
  );
});

test('the pitch is measured from real rows, not from a probe or the token', () => {
  const js = scan.scrub(read('panel-files.js'));
  assert.equal(
    js.indexOf('function calibrate(') >= 0,
    false,
    'the probe-based calibration is gone; the pitch comes from rendered rows',
  );
  assert.equal(
    /getComputedStyle\([^)]*\)[\s\S]{0,80}rowGap/.test(js),
    false,
    'the gap must not be read into JS at all any more',
  );
  const m = scan.functionBody(js, 'function refreshPitch(');
  assert.ok(m, 'no refreshPitch');
  assert.ok(
    m.indexOf('offsetTop') >= 0,
    'the pitch is the offset difference of two rows actually laid out. Body:' + scan.LF + m,
  );
});

test('a measured pitch actually replaces the fallback', async () => {
  // 结构断言在这里不够：把 `b.offsetTop - a.offsetTop` 换成 0，
  // `offsetTop` 仍然出现在上一行的 typeof 守卫里，子串判据照样绿
  // （逼红实测：零条红）。所以这条从**行为**上看 —— 给渲染出来的行装上
  // 真实布局，之后的滚动区间必须按量到的行距算，而不是兜底常量。
  const c = await opened(1000, 600);
  const extent = () => byClass(c.view, 'fk-vspace')
    .reduce((n, el) => n + (parseFloat(el.style.getPropertyValue('height')) || 0), 0);
  const withFallback = extent();
  assert.ok(withFallback > 0, 'there must be room reserved below the window');

  // mini-dom 没有布局引擎，所以这里替它给出一份布局：行距 160。
  rows(c.view).forEach((el, i) => { el.offsetTop = i * 160; });
  // 一次滚动让 refreshPitch 量到 160，再一次让新的行距被用上。
  await scrollTo(c, 400);
  await scrollTo(c, 800);
  const measured = extent();
  assert.ok(
    measured > withFallback * 1.5,
    'the measured pitch of 160 must supersede the 64px fallback: ' +
      withFallback + ' -> ' + measured,
  );
});
