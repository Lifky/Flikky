const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const LF = String.fromCharCode(10);

/*
 * 浏览器端文件面板的排序切换。
 *
 * 本组盯的三件事，都是「改一份状态却忘了跟着它的依据一起更新」那一族：
 *   · 派生数组 `shownEntries` 只能经一个写入口更新（结构守卫）；
 *   · 切换排序是**本地重排**，不重新请求（spec §6.2）；
 *   · 初次加载把当前排序作为 `?sort=` 传给服务端（spec §6.1）——
 *     不传的话，把排序改成「按大小」的用户此后每次打开目录都会先看到
 *     按名称排的列表、流结束时再跳一次。
 */

function load(bodyText, opts) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const o = opts || {};
  const store = o.store || {};
  const urls = [];
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
    localStorage: {
      getItem: (k) => (Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null),
      setItem: (k, v) => { store[k] = String(v); },
    },
    TextDecoder: TextDecoder,
    requestAnimationFrame: (fn) => { fn(0); return 1; },
    fetch: (url) => {
      urls.push(url);
      return Promise.resolve({
        ok: true,
        status: 200,
        body: {
          getReader: () => {
            let at = 0;
            return {
              read: () => {
                if (at > 0) return Promise.resolve({ value: undefined, done: true });
                at += 1;
                return Promise.resolve({ value: Buffer.from(bodyText, 'utf8'), done: false });
              },
              cancel: () => {},
            };
          },
        },
      });
    },
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  // sort.js 必须先加载：panel-files.js 在挂载时就要读排序偏好。
  vm.runInContext(fs.readFileSync(path.join(WEB, 'sort.js'), 'utf8'), ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return { doc, view, api: ctx.window.flikkyPanels.files, urls, store };
}

const tick = async (n = 60) => { for (let k = 0; k < n; k += 1) await Promise.resolve(); };
const rows = (v) => byClass(v, 'fk-item');
const body = (v) => byClass(v, 'fk-panel-body')[0];
const titles = (v) => rows(v).map((r) => byClass(r, 'fk-item-title')[0].textContent);

function entry(name, size, mtime, isDir) {
  return JSON.stringify({
    name: name,
    isDir: !!isDir,
    size: size,
    mtime: mtime,
    mime: null,
  });
}

/** 名称序与大小序**相反**的三行，任一个键坏掉都看得出来。 */
const LISTING = [
  JSON.stringify({ path: '' }),
  entry('a-small.txt', 1, 300),
  entry('m-mid.txt', 50, 200),
  entry('z-big.txt', 900, 100),
  JSON.stringify({ done: true }),
].join(LF) + LF;

async function opened(opts) {
  const c = load(LISTING, opts);
  c.api.mount(c.view);
  body(c.view).clientHeight = 600;
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

/** 点排序菜单里的某个键。 */
function pickSort(view, key) {
  const items = byClass(view, 'fk-sort-item');
  const hit = items.filter((i) => i.getAttribute('value') === key)[0];
  assert.ok(hit, 'sort menu item not found: ' + key);
  hit.dispatch('click');
}

test('viewEntries 只在 setViewEntries 里被赋值', () => {
  // 结构守卫，不是行为断言。
  //
  // shownEntries 是 viewEntries 的派生缓存（虚拟化在每个 scroll 事件里读它，
  // 不能现算 —— 一万项每次滚动重排一遍，虚拟化的意义就没了）。
  // 谁改了源却忘了重算派生，屏幕上就是上一个目录 / 上一次关键词的行 ——
  // 静默的视觉损坏，没有异常。v1.20.0 的六个缺陷全是这个形状。
  //
  // 所以用结构钉死：写入口只有一个，它必然同时重算。
  const src = fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8');
  const at = src.indexOf('function setViewEntries');
  assert.ok(at > 0, 'setViewEntries 不存在');
  // 函数体到下一个顶层 function 之前
  const bodyText = src.slice(at, src.indexOf('function ', src.indexOf('{', at)));
  // 排除 `let viewEntries = []` 这个声明 —— 它是必需的，不是「另一处写入口」。
  const WRITE = /(?<!let\s)viewEntries\s*=[^=]/g;
  const all = (src.match(WRITE) || []).length;
  const inside = (bodyText.match(WRITE) || []).length;
  assert.equal(
    all,
    inside,
    'viewEntries 在 setViewEntries 之外被赋值了（' + all + ' 处，函数内 ' + inside +
      ' 处）—— 派生的 shownEntries 会悄悄过期',
  );
});

test('初次加载把当前排序作为 ?sort= 传给服务端', async () => {
  const c = await opened({ store: { flikky_sort_files: 'SIZE:desc' } });
  const listUrl = c.urls.filter((u) => u.indexOf('/api/storage/list') === 0)[0];
  assert.ok(listUrl, '没有发出列举请求');
  assert.ok(
    listUrl.indexOf('sort=SIZE%3Adesc') >= 0 || listUrl.indexOf('sort=SIZE:desc') >= 0,
    '请求里没有带排序：' + listUrl,
  );
});

test('切换排序在本地重排，不重新请求', async () => {
  const c = await opened();
  assert.deepEqual(titles(c.view), ['a-small.txt', 'm-mid.txt', 'z-big.txt']);
  const before = c.urls.length;

  pickSort(c.view, 'SIZE');
  await tick();

  assert.deepEqual(
    titles(c.view),
    ['z-big.txt', 'm-mid.txt', 'a-small.txt'],
    '按大小降序应当反过来',
  );
  assert.equal(c.urls.length, before, '切换排序**不该**发新请求');
});

test('再点同一个键翻转方向', async () => {
  const c = await opened();
  pickSort(c.view, 'SIZE');
  await tick();
  pickSort(c.view, 'SIZE');
  await tick();
  assert.deepEqual(titles(c.view), ['a-small.txt', 'm-mid.txt', 'z-big.txt']);
});

/** 一个**超过一屏**的目录。名称序与大小序恰好相反。 */
function bigListing(count) {
  const lines = [JSON.stringify({ path: '' })];
  for (let i = 0; i < count; i += 1) {
    // 名字升序 = 大小降序：任一个键坏掉都看得出来。
    lines.push(entry('f' + String(i).padStart(4, '0') + '.txt', count - i, i));
  }
  lines.push(JSON.stringify({ done: true }));
  return lines.join(LF) + LF;
}

async function openedBig(count, opts) {
  const c = load(bigListing(count), opts);
  c.api.mount(c.view);
  body(c.view).clientHeight = 600;
  c.api.setEnabled(true);
  await tick();
  return c;
}

test('切换排序把列表滚回顶部', async () => {
  // 顺序全变之后，停在原来的偏移上看到的是一堆无关的东西。
  //
  // **必须用超过一屏的目录**：三行的目录里 syncVirtual 一次就渲染全部、
  // 窗口从不改变，于是「先回顶后重算」与「先重算后回顶」结果相同 ——
  // 逼红实测零条红就是这么来的。
  const c = await openedBig(500);
  await scrollTo(c, 4000);
  assert.ok(body(c.view).scrollTop > 0, '前提：确实滚下去了');

  pickSort(c.view, 'SIZE');
  await tick();

  assert.equal(body(c.view).scrollTop, 0, '切换排序后必须回到顶部');
});

test('切换排序后窗口对准列表开头', async () => {
  // 注意这条**测不出「先回顶还是先重算」**：赋 scrollTop 触发的那次异步
  // scroll 事件会再调一次 syncVirtual 把窗口修正回来，所以两种顺序的最终
  // 状态相同（逼红实测零条红）。它守的是「最终窗口对准开头」这个结果；
  // 顺序要求写在 applyViewChange 的注释里，靠人盯。
  const c = await openedBig(500);
  await scrollTo(c, 4000);

  pickSort(c.view, 'SIZE');
  await tick();

  // 按大小降序，第一行应当是 size 最大的那个 = f0000.txt。
  assert.equal(
    titles(c.view)[0],
    'f0000.txt',
    '回顶之后窗口必须对准列表开头，实际首行：' + titles(c.view)[0],
  );
});

test('切换排序会清掉目录缓存 —— 缓存里存的是旧顺序', () => {
  // 不清的话返回上级目录会看到按旧排序排的列表：
  // 又一次「一份状态没跟着它的依据一起更新」。
  //
  // 这一条是**源码守卫**而不是行为断言：要在行为上验证，得让 fetch 按路径
  // 返回不同的列表、进子目录再退回来 —— 而本文件的 harness 只喂一份列表，
  // 为这一条重写它不划算（`cacheClear` 本身的行为由 panel-files-cache.test.js
  // 的「关掉主开关会清缓存」覆盖）。这里钉的是「pickSort 确实调了它」。
  const src = fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8');
  const at = src.indexOf('function pickSort');
  assert.ok(at > 0, 'pickSort 不存在');
  const fn = src.slice(at, src.indexOf('function ', src.indexOf('{', at)));
  assert.ok(fn.indexOf('cacheClear()') >= 0, 'pickSort 没有清缓存');
  assert.ok(fn.indexOf('applyViewChange()') >= 0, 'pickSort 没有重排当前视图');
});

test('切换排序不走 renderShell —— 面包屑不重建、方向横移不重播', async () => {
  // renderShell 是「换目录」的入口：它重建面包屑、重放方向横移、清空 viewEntries。
  // 排序变化时路径没变，走它等于让列表无故横移一次，还会把刚排好的数据丢掉。
  const c = await opened();
  const crumbsBefore = byClass(c.view, 'fk-crumbs')[0];
  const listBefore = byClass(c.view, 'fk-files-list')[0];

  pickSort(c.view, 'SIZE');
  await tick();

  assert.equal(byClass(c.view, 'fk-crumbs')[0], crumbsBefore, '面包屑被重建了');
  assert.equal(byClass(c.view, 'fk-files-list')[0], listBefore, '列表容器被重建了');
});

test('排序偏好写进 localStorage，重新挂载后仍然生效', async () => {
  const c = await opened();
  pickSort(c.view, 'SIZE');
  await tick();
  assert.equal(c.store.flikky_sort_files, 'SIZE:desc');

  // 用同一份 store 重新挂载：顺序应当直接是按大小降序。
  const again = await opened({ store: c.store });
  assert.deepEqual(titles(again.view), ['z-big.txt', 'm-mid.txt', 'a-small.txt']);
});

test('当前排序键在菜单里带方向箭头，其余项没有', async () => {
  const c = await opened();
  const marked = () => byClass(c.view, 'fk-sort-item')
    .filter((i) => byClass(i, 'fk-sort-dir').length > 0)
    .map((i) => i.getAttribute('value'));

  assert.deepEqual(marked(), ['NAME'], '默认应当是名称键带箭头');
  pickSort(c.view, 'SIZE');
  await tick();
  assert.deepEqual(marked(), ['SIZE'], '切换后箭头应当只跟着当前键');
});
