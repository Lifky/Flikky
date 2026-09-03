const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');
const SHEETS = ['base.css', 'shell.css', 'panels.css', 'chat.css', 'pages.css'];
const allCss = () => SHEETS.map((f) => scan.stripBlockComments(read(f))).join(scan.LF);

/*
 * 装机验收缺陷 3 的守卫（Screenshot_3：浏览器文件列表是一片扁平灰板）。
 *
 * 根因是一个**拼错的类名**：列表容器写的是 `fk-list`，而 panels.css 里从来只有
 * `fk-group`（收藏面板用的连接列表组：组间距 + 首尾外圆角 + 按下挤压）。
 * 于是只剩 .fk-item 的内圆角，组的形状全丢了。
 *
 * 这类错误**不报错、不转红、只静默退化**——与 D31 记的「缺失的 CSS 自定义属性
 * 静默降级为无圆角无内边距」完全同形。所以守卫不是「断言用了 fk-group」这一条，
 * 而是**面板 emit 的每个类名都必须在样式表里真实存在**。
 */

/** 从 JS 源码里收集所有 `className = '...'` 与 `classList.add('...')` 里的类名。 */
function emittedClasses(src) {
  const out = new Set();
  const marker = "className = '";
  let i = 0;
  for (;;) {
    const at = src.indexOf(marker, i);
    if (at < 0) break;
    const rest = src.slice(at + marker.length);
    const end = rest.indexOf("'");
    if (end > 0) rest.slice(0, end).split(' ').filter(Boolean).forEach((c) => out.add(c));
    i = at + marker.length;
  }
  const add = "classList.add('";
  i = 0;
  for (;;) {
    const at = src.indexOf(add, i);
    if (at < 0) break;
    const rest = src.slice(at + add.length);
    const end = rest.indexOf("'");
    if (end > 0) rest.slice(0, end).split(' ').filter(Boolean).forEach((c) => out.add(c));
    i = at + add.length;
  }
  return out;
}

test('every class the files panel emits exists in a stylesheet', () => {
  const css = allCss();
  const emitted = emittedClasses(read('panel-files.js'));
  assert.ok(emitted.size >= 8, 'class scan looks empty: ' + emitted.size);
  const missing = [...emitted].filter((c) => css.indexOf('.' + c) < 0);
  assert.deepEqual(missing, [],
    'these classes are emitted but styled nowhere, so they degrade silently');
});

test('the scan can actually tell a real class from a made-up one', () => {
  // 上一条的判据是 css.indexOf('.' + c) —— 空转过一次就白写了，这里正反各钉一次。
  const css = allCss();
  assert.ok(css.indexOf('.fk-group') >= 0, 'the scan cannot find .fk-group');
  assert.equal(css.indexOf('.fk-list-that-never-existed') >= 0, false);
});

test('file rows reuse the favourites row, and the container copies its geometry', () => {
  // 「复用」的标准是视觉零差异。**行**仍然是同一个 `.fk-item` —— 所有共享外观
  // （背景、圆角、按下缩放、相邻挤压）都长在那个类上，所以两个面板的行逐像素相同。
  //
  // **容器**这一层刻意分了家：`.fk-group` 用 `:first-child` / `:last-child` 定外圆角，
  // 而文件列表要虚拟化，第一个渲染出来的行往往不是列表首行 —— 那条规则会让中间
  // 某一行莫名带上外圆角。所以文件列表用 `.fk-files-list`，几何逐项照抄
  // （同 flex 方向、同 --flikky-listgroup-gap），首尾改由数据下标打 class。
  const src = scan.scrub(read('panel-files.js'));
  assert.ok(
    src.indexOf("row.className = 'fk-item'") >= 0,
    'rows must be the very same .fk-item favourites uses',
  );
  assert.equal(src.indexOf("'fk-list'") >= 0, false,
    'fk-list does not exist in any stylesheet');

  const css = scan.stripBlockComments(read('panels.css'));
  const group = scan.ruleBlock(css, '.fk-group');
  const files = scan.ruleBlock(css, '.fk-files-list');
  assert.ok(group && files, 'both containers must be declared');
  // 几何必须逐项一致，否则「视觉零差异」只是说法。
  ['flex-direction: column', 'var(--flikky-listgroup-gap)'].forEach((bit) => {
    assert.ok(
      group.indexOf(bit) >= 0,
      'the favourites group is expected to declare ' + bit + ': ' + group,
    );
    assert.ok(
      files.indexOf(bit) >= 0,
      'the files container must copy ' + bit + ' verbatim: ' + files,
    );
  });
  // 而它**不能**继承那条结构化的外圆角规则。
  assert.equal(
    src.indexOf("'fk-group fk-files-list") >= 0,
    false,
    'the virtualised list must not carry .fk-group: its :first-child rule would put ' +
      'an outer corner on whichever row happens to be rendered first',
  );
});

test('file rows lead with the same tinted container as favourites', () => {
  // Screenshot_3 的另一半：行首是裸图标（fk-item-lead--plain 无底色无异形容器），
  // 而收藏行是 .fk-item-lead（secondary-container 底 + cookie 异形）。
  // 并排看一眼就知道不是一套东西。
  const src = scan.scrub(read('panel-files.js'));
  const body = scan.functionBody(src, 'function leadFor(');
  assert.ok(body, 'no leadFor');
  assert.ok(body.indexOf("'fk-item-lead'") >= 0 || body.indexOf('fk-item-lead ') >= 0,
    'the lead must use .fk-item-lead like favourites; body:' + scan.LF + body);
  assert.equal(body.indexOf('fk-item-lead--plain') >= 0, false,
    'the plain variant has no container and no tint — that is the flat grey slab');
});

test('the row entrance animation is per row and staggered, not one group fade', () => {
  // 上一版是「整组淡入一次」，理由是「逐行在几千行里会拖成幻灯片」。
  // 结论下错了：正确答案是封顶而不是放弃。这条钉住新形态，
  // 也防止有人凭那句旧注释把它改回去（改回去时 JS 侧的 --i 会静默失效）。
  const css = scan.stripBlockComments(read('panels.css'));
  // 规则名是共用的 `.fk-list-in`（文件面板与收藏面板同一条），不是 files 专有。
  const rowRule = scan.ruleBlock(css, '.fk-list-in > .fk-item');
  assert.ok(rowRule, 'the entrance animation must target the row, not the list container');
  assert.ok(rowRule.indexOf('animation') >= 0, 'no animation on the row: ' + rowRule);
  assert.ok(
    rowRule.indexOf('animation-delay') >= 0 && rowRule.indexOf('--i') >= 0,
    'the row must take its stagger step from --i: ' + rowRule,
  );
  // 步长必须走 --flikky-stagger（含 --flikky-motion-scale），
  // 这样 reduce-motion 与「动画速度」设置自动生效。写死毫秒就绕过了它们。
  assert.ok(
    rowRule.indexOf('--flikky-stagger') >= 0,
    'the stagger step must come from the motion token: ' + rowRule,
  );
  // 反向：列表容器自己不该再有整组动画，否则两层动画叠着跑。
  const listRule = scan.ruleBlock(css, '.fk-files-list');
  assert.equal(
    listRule.indexOf('animation') >= 0,
    false,
    'the container must not animate as a group any more: ' + listRule,
  );
});

test('reduced motion disables the row entrance', () => {
  const css = scan.stripBlockComments(read('panels.css'));
  // 扫**所有** prefers-reduced-motion 块，不是第一个。
  // 只看第一个的话，新增一个别的 reduce 块（进度条的收回）就会把判据挪到
  // 一段无关代码上 —— 实测：加进度条那块之后这条立刻误报。
  const marker = 'prefers-reduced-motion';
  let at = css.indexOf(marker);
  let blocks = 0;
  let covered = false;
  while (at >= 0) {
    blocks += 1;
    if (css.slice(at, at + 400).indexOf('.fk-list-in > .fk-item') >= 0) covered = true;
    at = css.indexOf(marker, at + 1);
  }
  assert.ok(blocks > 0, 'panels.css must handle prefers-reduced-motion');
  assert.ok(
    covered,
    'the row entrance must be switched off under reduced motion; ' +
      'searched ' + blocks + ' reduced-motion blocks',
  );
});
