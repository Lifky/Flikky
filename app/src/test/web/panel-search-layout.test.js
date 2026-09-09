const test = require('node:test');
const assert = require('node:assert/strict');
const scan = require('./scan.js');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * 文件面板搜索行的**几何**，全是 2026-09-08 装机反馈的三个症状：
 *
 *   ① 搜索框比设计矮，且输入字符后忽然变高；
 *   ② 列表项宽度在搜索前后不一致（跟着搜索框跳变）；
 *   ③ 进出文件夹时搜索框高度跳变。
 *
 * 三个症状同一个族：**搜索行是 `.fk-view`（flex 列）的直接子元素**。
 * flex 子项默认 `flex-shrink: 1`，所以它会被压缩到内容高度以下 ——
 * 列表长短一变、IME 候选条一开，压力就变，高度跟着跳。
 *
 * ② 还多一层：搜索行在滚动容器**外**（不给滚动条留位置），列表在**内**（留），
 * 两者宽度天生不等；无命中时列表变短、滚动条消失，宽度又突变一次。
 * 所以滚动条必须**常留位置**，而不是按需出现。
 */

const panels = () => scan.stripBlockComments(read('panels.css'));
const shell = () => scan.stripBlockComments(read('shell.css'));

/**
 * 取某个选择器的规则体（最后一条，级联里胜出的那条）。
 *
 * 必须是**整条选择器**相等，不能用子串 —— `.fk-search` 的子串也命中
 * `#view-files > .fk-search`，于是两条规则会被搞混（写这个文件时踩到了）。
 */
function ruleFor(css, selector) {
  const hits = [];
  const re = /([^{}]+)\{([^}]*)\}/g;
  let m = re.exec(css);
  while (m) {
    if (m[1].trim() === selector) hits.push(m[2]);
    m = re.exec(css);
  }
  return hits.length ? hits[hits.length - 1] : null;
}

test('搜索行搬进滚动容器后不再是 flex 子项 —— 症状①③的根因', () => {
  // 上一版的修法是给容器**外**的搜索行加 flex: none（它当时是 .fk-view 这个
  // flex 列的直接子元素，默认 flex-shrink: 1，高度会跟着邻居变）。
  //
  // 这一版把它搬进了滚动容器内的 sticky 头块（为了拿到 scrollbar-gutter 让出的
  // 槽位、右边缘与列表对齐），于是它根本不再是 flex 子项 ——
  // 压缩问题从**结构上**消失了，比加一条 flex: none 更彻底。
  const css = panels();
  const sticky = ruleFor(css, '.fk-files-sticky');
  assert.ok(sticky, '.fk-files-sticky 的规则不见了');
  assert.doesNotMatch(
    sticky,
    /display:\s*flex/,
    '头块变成 flex 容器了 —— 那搜索行又会被压缩，症状①③会回来',
  );
  // 容器外那条规则必须**已经不存在**：留着等于两处规则同时定位搜索行。
  assert.equal(
    ruleFor(css, '#view-files > .fk-search'),
    null,
    '容器外那条 .fk-search 规则还在 —— 搜索行现在住在头块里，两处规则会打架',
  );
});

test('搜索行的高度由 token 定死，不随内容浮动', () => {
  // 只写 flex: none 还不够：高度仍然是「内容多高就多高」，
  // 而 IME 候选条会改变 input 的行盒高度。
  const rule = ruleFor(panels(), '.fk-search');
  assert.ok(rule, '.fk-search 的规则不见了');
  assert.match(
    rule,
    /min-height:\s*var\(--flikky-size-touch\)/,
    '搜索行没有把高度钉在 --flikky-size-touch 上',
  );
  assert.doesNotMatch(
    rule,
    /(^|[^-])height:\s*var\(--flikky-size-touch\)/m,
    '用的是固定 height 而不是 min-height —— 固定值在内容更高时会溢出，' +
      '而 min-height 既保底又允许长大',
  );
});

test('滚动条常留位置 —— 症状②的根因', () => {
  // 搜索行在滚动容器外，列表在内。滚动条按需出现的话：
  // 有滚动条时列表窄一条、无命中时列表变短滚动条消失、宽度立刻突变。
  // 用户看到的就是「listitem 宽度跟着搜索跳变」。
  const rule = ruleFor(shell(), '.flikky-scroll');
  assert.ok(rule, '.flikky-scroll 的规则不见了');
  assert.match(
    rule,
    /scrollbar-gutter:\s*stable/,
    '滚动容器没有 scrollbar-gutter: stable —— 滚动条出现/消失会让内容宽度跳变',
  );
});

test('头块与列表的左右边界用同一个 token —— 右侧才能对齐', () => {
  // 头块坐在 body 内，用「负 margin 逃出 body 内边距、再自己补回同样的
  // padding」这一手（与 .fk-crumbs 逐字同形）。两个值必须是**同一个 token**：
  // 这正是「左侧对齐、右侧没对齐」那一类问题的来源。
  const css = panels();
  const bodyRule = ruleFor(css, '.fk-panel-body');
  const sticky = ruleFor(css, '.fk-files-sticky');
  assert.ok(bodyRule && sticky);

  const firstToken = (s) => ((s || '').match(/var\(--flikky-space-[a-z]+\)/g) || [])[0];
  const propOf = (rule, prop) => {
    const m = rule.match(new RegExp('(?:^|[^-\w])' + prop + ':\s*([^;]+)'));
    return m ? m[1] : null;
  };

  const bodyH = firstToken(propOf(bodyRule, 'padding'));
  assert.ok(bodyH, '取不到 body 的水平内边距');
  assert.equal(
    firstToken(propOf(sticky, 'margin')), bodyH,
    '头块逃出的量与 body 内边距不是同一个 token',
  );
  assert.equal(
    firstToken(propOf(sticky, 'padding')), bodyH,
    '头块补回的量与 body 内边距不是同一个 token',
  );
});

/*
 * ── sticky 头块里的横向溢出（2026-09-09 装机反馈）─────────────────────────
 *
 * 症状两个，同一个根因：
 *   ① 文件面板底部**始终**有一条横向滚动条，滚动它没有任何意义（Screenshot_32）；
 *   ② 加载进度条右边超出范围、左边却空出一条（Screenshot_33）。
 *
 * 根因：`mdui-linear-progress` 的 host 样式是 `display: inline-block; width: 100%`
 * （见 vendor/mdui.global.js）。`width: 100%` 已经填满容器，再给它左右外边距，
 * 总占用宽度就变成「容器 + 2×外边距」—— 左边空出一条、右边溢出一条，
 * 而溢出让 `.fk-panel-body` 的 overflow-x（`overflow-y: auto` 会把另一轴的
 * `visible` 计算成 `auto`）长出一条永久的横向滚动条。
 *
 * 那对横向外边距是它还住在**无内边距容器**里时的遗留；搬进 sticky 头块之后，
 * 横向内缩已经由头块的 padding 统一给了。
 */

/** 规则体里出现的所有**横向**外边距值（shorthand 与 longhand 都认）。 */
function horizontalMargins(rule) {
  const out = [];
  const body = rule || '';
  const short = body.match(/(?:^|[^-\w])margin:\s*([^;]+)/);
  if (short) {
    const parts = short[1].trim().split(/\s+/);
    if (parts.length === 1) out.push(parts[0]);
    else if (parts.length === 2 || parts.length === 3) out.push(parts[1]);
    else if (parts.length >= 4) out.push(parts[1], parts[3]);
  }
  ['margin-left', 'margin-right', 'margin-inline', 'margin-inline-start', 'margin-inline-end']
    .forEach((prop) => {
      const m = body.match(new RegExp('(?:^|[^-\w])' + prop + ':\s*([^;]+)'));
      if (m) out.push(m[1].trim());
    });
  return out;
}

const isZero = (v) => /^(0|0px|0%|none)$/.test(v.trim());

test('sticky 头块里的元素不带横向外边距 —— 两个溢出症状的根因', () => {
  // 头块的子元素宽度都是「填满容器」，任何横向外边距都会直接变成溢出。
  // 头块**自己**那对负 margin 是刻意的（逃出 body 内边距），由上面那条测试盯。
  const css = panels();
  const inside = [
    '.fk-files-progress',
    '.fk-files-loading-count',
    '.fk-files-sticky .fk-crumbs',
    '.fk-files-sticky > .fk-search',
  ];
  const offenders = [];
  inside.forEach((sel) => {
    const rule = ruleFor(css, sel);
    if (!rule) return;   // 选择器可以不存在，但存在就必须没有横向外边距
    horizontalMargins(rule)
      .filter((v) => !isZero(v))
      .forEach((v) => offenders.push(sel + ' → ' + v));
  });
  assert.deepEqual(
    offenders,
    [],
    '这些头块内元素带了横向外边距。它们的宽度已经填满容器，外边距会直接溢出 ——\n' +
      '左边空一条、右边溢一条，还会给滚动容器撑出一条永久的横向滚动条。\n' +
      '横向内缩交给 .fk-files-sticky 的 padding：',
  );
});

test('进度条是块级 —— inline-block 的行高会在收起后仍占位', () => {
  // mdui 的 host 是 inline-block。作为行内盒它带一个行高，
  // `.is-done` 把 height 收到 0 之后那份行高还在，头块底部留一条空隙。
  const rule = ruleFor(panels(), '.fk-files-progress');
  assert.ok(rule, '.fk-files-progress 的规则不见了');
  assert.match(
    rule,
    /display:\s*block/,
    '进度条没有覆盖 mdui 的 inline-block —— 收起后会残留一条行高的空隙',
  );
});

test('头块自己那对负 margin 与补回的 padding 仍然成对', () => {
  // 上面把子元素的横向外边距一律清零之后，唯一允许的横向外边距就是头块自己
  // 这一对。它必须**成对**出现：只逃不补，头块会比列表宽一圈（也是溢出）。
  const rule = ruleFor(panels(), '.fk-files-sticky');
  assert.ok(rule, '.fk-files-sticky 的规则不见了');
  const negative = horizontalMargins(rule).filter((v) => v.indexOf('-1') >= 0);
  assert.ok(
    negative.length > 0,
    '头块不再用负 margin 逃出 body 内边距了 —— 请重新评估本组断言：' + rule,
  );
  assert.match(
    rule,
    /padding:\s*0\s+var\(--flikky-space-[a-z]+\)/,
    '头块逃出了 body 的内边距却没有补回同样的横向 padding：' + rule,
  );
});
