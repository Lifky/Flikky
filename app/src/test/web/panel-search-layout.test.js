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
