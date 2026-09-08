const test = require('node:test');
const assert = require('node:assert/strict');
const scan = require('./scan.js');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * 排序菜单必须挂在 **body 上、用 fixed 定位**，不能靠 `mdui-dropdown`。
 *
 * 2026-09-08 装机反馈：菜单从触发按钮的左下方弹出、压在列表上。
 *
 * 根因不是 z-index，而是 **`.fk-pillar` 有 `overflow: hidden`**。
 * mdui 的菜单是普通 DOM 子元素（那份 bundle 里 `showPopover` 出现 0 次 ——
 * 它不走 top layer），所以被这个祖先裁切，只能往容器内部挤。
 * 给它加多大的 z-index 都没用：z-index 不能突破祖先的 overflow 裁切。
 *
 * **项目已经解决过同一个坑**：`chat.css` 的 `.recall-menu`
 * 「手写外层只负责 fixed 定位（mdui-dropdown 在这套布局里定位不对），
 * 里面用官方 mdui-menu」。这一组把那个模式钉在排序菜单上 ——
 * 视觉仍然是 mdui 官方的（面板/圆角/海拔/菜单项都归 mdui-menu）。
 */

const filesJs = () => read('panel-files.js');
const favoritesJs = () => read('panel-favorites.js');
const appHtml = () => read('app.html');
const menuJs = () => read('sort-menu.js');
const panelsCss = () => scan.stripBlockComments(read('panels.css'));
const shellCss = () => scan.stripBlockComments(read('shell.css'));

test('前提：面板柱确实有 overflow hidden —— 这正是裁切源', () => {
  // 这一条不是要求，是把「为什么不能用 mdui-dropdown」的前提记录下来。
  // 哪天 .fk-pillar 不再裁切了，本组的其余断言就可以放宽。
  const css = shellCss();
  const at = css.indexOf('.fk-pillar {');
  assert.ok(at > 0, '.fk-pillar 的规则不见了');
  const rule = css.slice(at, css.indexOf('}', at));
  assert.match(
    rule,
    /overflow:\s*hidden/,
    '.fk-pillar 不再裁切了 —— 那 panel-sort-menu-layer 这一组的前提就变了，' +
      '请重新评估是否还需要手写 fixed 外层',
  );
});

test('两个面板都不再用 mdui-dropdown 包排序菜单', () => {
  // 扫的是**元素创建**，不是字面量 —— 注释里要能解释「为什么不用它」。
  assert.doesNotMatch(
    filesJs(),
    /createElement\(\s*['"]mdui-dropdown/,
    '文件面板又用回 mdui-dropdown 了 —— 它会被 .fk-pillar 的 overflow 裁切',
  );
  assert.doesNotMatch(
    appHtml(),
    /<mdui-dropdown/,
    '收藏面板（app.html）又用回 mdui-dropdown 了',
  );
});

test('定位层在两个面板之前加载', () => {
  // 面板在 mount 时就要绑点击回调。顺序错了只会静默回落成「菜单打不开」
  // （防御性回退），比抛异常更难发现。
  const html = appHtml();
  const at = (n) => html.indexOf('/static/' + n);
  assert.ok(at('sort-menu.js') > 0, 'app.html 没有加载 sort-menu.js');
  assert.ok(
    at('sort-menu.js') < at('panel-files.js') && at('sort-menu.js') < at('panel-favorites.js'),
    'sort-menu.js 必须排在两个面板之前',
  );
});

test('两个面板都把定位委托给菜单层，不自己算坐标', () => {
  // 定位逻辑只有一份（sort-menu.js）。面板里各写一份的话，
  // 「菜单在按钮正下方」这件事就有两个可能分叉的实现。
  for (const [name, src] of [['panel-files.js', filesJs()], ['panel-favorites.js', favoritesJs()]]) {
    assert.match(src, /menus\.open\(/, name + ' 没有调用菜单层打开菜单');
    assert.match(src, /menus\.close\(\)/, name + ' 点了菜单项之后没有收起');
    assert.doesNotMatch(
      src,
      /style\.(left|top)\s*=/,
      name + ' 自己算了菜单坐标 —— 定位应当只有 sort-menu.js 一份',
    );
  }
});

test('菜单挂到 body 上，而不是面板内部', () => {
  // 挂在面板里就还在裁切链上，fixed 也救不了 —— 祖先一旦有
  // transform / filter / contain，fixed 的包含块就变成那个祖先。
  // 挂 body 是唯一稳的做法（.recall-menu 也是这样）。
  assert.match(menuJs(), /document\.body\.appendChild\(/, '菜单没有挂到 body 上');
});

test('外层容器只管定位，视觉全部交给 mdui-menu', () => {
  // 这是「保留 mdui 视觉」的结构保证：外层不许自己画面板背景 / 圆角 / 阴影，
  // 那些归 mdui-menu。外层只有 position / z-index / min-width / 动效原点。
  const css = panelsCss();
  const at = css.indexOf('.fk-sort-menu {');
  assert.ok(at > 0, '.fk-sort-menu 的规则不见了');
  const rule = css.slice(at, css.indexOf('}', at));

  assert.match(rule, /position:\s*fixed/, '外层不是 fixed 定位');
  assert.match(rule, /z-index:/, '外层没有 z-index');
  assert.match(
    rule,
    /transform-origin:\s*top\s+left/,
    '动效原点不在左上角 —— 菜单是左对齐按钮的，原点不一致就会显得' +
      '「淡入到按钮旁边」而不是「从按钮里展开」（用户原话：感觉上和按钮是分开的）',
  );
  for (const prop of ['background', 'border-radius', 'box-shadow']) {
    assert.doesNotMatch(
      rule,
      new RegExp(prop + ':'),
      '外层自己画了 ' + prop + ' —— 视觉应当全部由 mdui-menu 提供，' +
        '外层只负责定位（与 .recall-menu 同一分工）',
    );
  }
});

test('点外部与 Esc 都能关掉菜单', () => {
  // mdui-dropdown 免费给的两件事，自己写外层就要自己接上。
  const src = menuJs();
  assert.match(src, /pointerdown/, '没有接点外部关闭');
  assert.match(src, /Escape/, '没有接 Esc 关闭');
});

/*
 * 下面是**行为断言**：直接跑 sort-menu.js 的 open()，看它把菜单放在哪。
 *
 * 上面那些源码扫描守的是「结构没被改回去」；这几条守的是用户报的症状本身
 * ——「按钮在左侧，但弹出的 menu 不在按钮正下方，反而在右侧」。
 */

const vm = require('node:vm');

/** 造一个刚好够 sort-menu.js 跑起来的环境。 */
function layer(opts) {
  const o = opts || {};
  const appended = [];
  const listeners = {};
  const mk = () => ({
    style: {},
    className: '',
    children: [],
    offsetWidth: o.menuWidth === undefined ? 180 : o.menuWidth,
    offsetHeight: o.menuHeight === undefined ? 150 : o.menuHeight,
    appendChild(c) { this.children.push(c); c.parentNode = this; return c; },
    removeChild(c) { this.children = this.children.filter((x) => x !== c); return c; },
    contains(n) { return n === this || this.children.some((c) => c.contains && c.contains(n)); },
  });
  const body = mk();
  const ctx = {
    document: {
      createElement: mk,
      body: Object.assign(body, {
        appendChild(c) { appended.push(c); c.parentNode = body; body.children.push(c); return c; },
      }),
      addEventListener: (k, fn) => { listeners[k] = fn; },
      removeEventListener: (k) => { delete listeners[k]; },
    },
    window: {
      innerWidth: o.vw === undefined ? 1400 : o.vw,
      innerHeight: o.vh === undefined ? 900 : o.vh,
    },
    setTimeout: (fn) => { fn(); return 1; },
    console: console,
  };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(read('sort-menu.js'), ctx);
  return { api: ctx.window.flikkySortMenu, appended, listeners, mk, ctx };
}

/** 一个位于面板头部偏左的触发按钮（就是排序按钮的真实处境）。 */
function trigger(rect) {
  return {
    getBoundingClientRect: () => rect,
    contains: () => false,
  };
}

test('菜单的左边缘对齐按钮的左边缘 —— 用户报的正是这一条', () => {
  // 排序按钮是面板头部四个图标里**最左**的那个。右对齐（bottom-end）会把
  // 菜单整体推到按钮左侧一大截外，装机时的表现是它跑到了按钮右边
  // （mdui 发现左边装不下就翻转了）。左对齐才是「正下方」。
  const L = layer();
  const wrap = L.api.open(trigger({ left: 60, right: 100, top: 40, bottom: 84 }), L.mk());
  assert.equal(wrap.style.left, '60px', '菜单左边缘没有对齐按钮左边缘');
});

test('菜单紧贴按钮下沿，不飘在远处', () => {
  // 「感觉上和按钮是分开的」——缝隙必须小。这里钉的是「贴着按钮底边」，
  // 具体几像素由 sort-menu.js 的 GAP 决定，测试只要求它不超过一个行高。
  const L = layer();
  const wrap = L.api.open(trigger({ left: 60, right: 100, top: 40, bottom: 84 }), L.mk());
  const top = parseInt(wrap.style.top, 10);
  assert.ok(top >= 84, '菜单盖在按钮上了：top=' + top);
  assert.ok(top <= 84 + 16, '菜单离按钮太远，会显得是两个独立的东西：top=' + top);
});

test('窄窗口下菜单被夹进视口，不溢出右边', () => {
  const L = layer({ vw: 200, menuWidth: 180 });
  const wrap = L.api.open(trigger({ left: 150, right: 190, top: 40, bottom: 84 }), L.mk());
  const left = parseInt(wrap.style.left, 10);
  assert.ok(left + 180 <= 200, '菜单溢出了视口右边：left=' + left);
  assert.ok(left >= 0, '夹过头了，菜单跑到视口左边外：left=' + left);
});

test('下方装不住时翻到按钮上方，而不是被视口截断', () => {
  // 面板很矮或按钮很靠下时。截断比翻转更糟：最后几个选项永远点不到。
  const L = layer({ vh: 300, menuHeight: 150 });
  const wrap = L.api.open(trigger({ left: 60, right: 100, top: 240, bottom: 280 }), L.mk());
  const top = parseInt(wrap.style.top, 10);
  assert.ok(top + 150 <= 300, '菜单底部超出了视口：top=' + top);
  assert.ok(top < 240, '应当翻到按钮上方：top=' + top);
});

test('再次打开会先关掉上一个 —— 不会叠出两个菜单', () => {
  const L = layer();
  const btn = trigger({ left: 60, right: 100, top: 40, bottom: 84 });
  L.api.open(btn, L.mk());
  L.api.open(btn, L.mk());
  assert.equal(L.ctx.document.body.children.length, 1, 'body 上叠了多个菜单');
});

test('close() 把菜单摘掉并撤掉两个全局监听', () => {
  // 忘了撤监听的话，菜单关了之后每次点击仍在跑 onOutside —— 泄漏。
  const L = layer();
  L.api.open(trigger({ left: 60, right: 100, top: 40, bottom: 84 }), L.mk());
  assert.equal(L.api.isOpen(), true);
  assert.ok(L.listeners.pointerdown && L.listeners.keydown, '监听没装上');

  L.api.close();
  assert.equal(L.api.isOpen(), false);
  assert.equal(L.ctx.document.body.children.length, 0, '菜单没从 body 上摘掉');
  assert.ok(!L.listeners.pointerdown && !L.listeners.keydown, '全局监听没撤掉');
});
