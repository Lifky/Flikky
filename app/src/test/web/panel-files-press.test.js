const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * 装机验收：浏览器端文件行「鼠标点按没有类似于收藏里的点按效果」。
 *
 * 根因：行的入场动画用了 `animation-fill-mode: both`。`both` 包含 `forwards`，
 * 于是末帧的 `transform: none` 在动画结束后**永久生效**；而 CSS 动画的优先级
 * 压过普通声明，`.fk-item:active { transform: scale(.985) }` 和相邻行挤压
 * 在文件行上全都失效。收藏行没有这条 animation，所以只有文件行不一致。
 *
 * 修法是 `backwards`：它只在**延迟期间**应用首帧（阶梓需要这个，否则行会先
 * 以完全不透明闪一下），动画结束后不再钉住任何属性。
 */

// 规则已改名为**两个面板共用**的 `.fk-list-in > .fk-item`（收藏也用它）。
const ROW_RULE = '.fk-list-in > .fk-item';

test('the row entrance does not pin transform after it ends', () => {
  const css = scan.stripBlockComments(read('panels.css'));
  const rule = scan.ruleBlock(css, ROW_RULE);
  assert.ok(rule, 'no row entrance rule found');
  const anim = rule.split('animation-delay')[0];
  assert.ok(
    anim.indexOf('backwards') >= 0,
    'the entrance must use fill-mode backwards, so the delay holds the first frame ' +
      'but the end state is not pinned. Rule: ' + rule,
  );
  assert.equal(
    anim.indexOf('both') >= 0,
    false,
    'fill-mode both keeps transform: none forever, which outranks :active and kills ' +
      'the press effect on files rows only. Rule: ' + rule,
  );
  assert.equal(
    anim.indexOf('forwards') >= 0,
    false,
    'forwards pins the end state just like both does. Rule: ' + rule,
  );
});

test('the press effect and neighbour squeeze are still declared for every row', () => {
  // 反向守卫：上一条只保证「没有东西压着它」。
  // 这条保证「被压的那个效果确实存在」——
  // 否则把 `:active` 规则删了也能让第一条绿。
  const css = scan.stripBlockComments(read('panels.css'));
  assert.ok(
    css.indexOf('.fk-item:active') >= 0,
    'the shared row press effect must exist',
  );
  assert.ok(
    css.indexOf('.fk-item:active + .fk-item') >= 0,
    'the neighbour squeeze must exist',
  );
  // 文件行用的就是 `.fk-item`，没有另一套类，所以上面两条对它天然适用。
  const js = scan.scrub(read('panel-files.js'));
  assert.ok(
    js.indexOf("row.className = 'fk-item'") >= 0,
    'file rows must be plain .fk-item so the shared press rules apply',
  );
});

test('the direction attribute is set when rows arrive, not when the list is created', () => {
  // 容器刚建好时还空着；224ms 的横移在第一批到达前就跑完了，
  // 等于演给一个空盒子看。所以 data-dir 必须在第一批追加时才上。
  const js = scan.scrub(read('panel-files.js'));
  const shell = scan.functionBody(js, 'function renderShell(');
  assert.ok(shell, 'no renderShell');
  assert.equal(
    shell.indexOf("setAttribute('data-dir'") >= 0,
    false,
    'renderShell must not stamp the direction: the list is empty at that moment, ' +
      'so the slide would animate nothing. Body:' + scan.LF + shell,
  );
  const append = scan.functionBody(js, 'function appendBatch(');
  assert.ok(append, 'no appendBatch');
  assert.ok(
    append.indexOf("setAttribute('data-dir'") >= 0,
    'appendBatch must stamp the direction on the first batch. Body:' + scan.LF + append,
  );
});
