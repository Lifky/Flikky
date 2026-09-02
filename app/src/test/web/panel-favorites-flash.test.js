const test = require('node:test');
const assert = require('node:assert/strict');
const scan = require('./scan.js');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * 收藏面板的勾选也必须**就地更新**，不许整份重绘。
 *
 * 文件面板已经因为这个问题被用户抓到过（「每次选中一个文件项，整个文件列表会闪一次」），
 * 而收藏面板是同一个形状：行的 click 回调调 `render()`，而 renderList 第一件事就是
 * 把 listHost 的子节点全部摘掉重建。
 *
 * 它此前没被察觉，是因为收藏行**没有入场动画**——重绘只是瞬间替换，看不太出来。
 * 用户 2026-09-02 要求把文件行那套逐行入场动效也用到收藏行上；一旦加上，
 * 这个重绘就会变成每次勾选都闪一整屏。所以顺序只能是：先修重绘，再加动效。
 *
 * `rowsById`（id -> 行元素）本来就在那儿，只是没被用来做更新。
 */

test('selecting a favourite does not rebuild the list', () => {
  const src = scan.scrub(read('panel-favorites.js'));
  // 行的 click 回调里不许调 render()。
  //
  // 锚点用 `row.addEventListener` 而不是 `if (isFile) {` —— 后者在文件里出现多次
  // （复选框那一段也是），indexOf 会命中第一处、量到一段无关代码，
  // 断言于是在错误的地方判真假（实测：报的是复选框那几行）。
  const at = src.indexOf("row.addEventListener('click'");
  assert.ok(at > 0, 'cannot find the row click wiring');
  const wiring = src.slice(at, at + 700);
  assert.equal(
    wiring.indexOf('render();') >= 0,
    false,
    'the row click must not re-render the whole panel; that tears down every row ' +
      'and the breadcrumb. Wiring was:' + scan.LF + wiring,
  );
  assert.ok(
    wiring.indexOf('aria-selected') >= 0,
    'the click must update this row in place. Wiring was:' + scan.LF + wiring,
  );
});

test('the row map is what makes in-place updates possible', () => {
  const src = scan.scrub(read('panel-favorites.js'));
  assert.ok(src.indexOf('rowsById.set(') >= 0, 'rows must be indexed by id');
  // 清除全部选择同样就地更新。
  const at = src.indexOf('function clearSelection(');
  assert.ok(at > 0, 'no clearSelection function');
  const body = scan.functionBody(src, 'function clearSelection(');
  assert.ok(
    body.indexOf('rowsById') >= 0,
    'clearing must walk the row map instead of re-rendering. Body:' + scan.LF + body,
  );
  assert.equal(
    body.indexOf('render();') >= 0,
    false,
    'clearing must not re-render either. Body:' + scan.LF + body,
  );
});

test('favourite rows use the same staggered entrance as file rows', () => {
  // 用户裁决：收藏不加流式（数据本来就整份在内存里，流式解决不了任何问题），
  // 但行入场动效要与文件面板对齐，让两个面板看起来是一套东西。
  const css = scan.stripBlockComments(read('panels.css'));
  const rule = scan.ruleBlock(css, '.fk-list-in > .fk-item');
  assert.ok(rule, 'expected a shared row-entrance rule .fk-list-in > .fk-item');
  assert.ok(rule.indexOf('animation') >= 0, 'no animation in the shared rule: ' + rule);
  assert.ok(
    rule.indexOf('--flikky-stagger') >= 0,
    'the stagger step must come from the motion token: ' + rule,
  );
  assert.ok(
    rule.indexOf('backwards') >= 0,
    'fill-mode must be backwards, or the entrance pins transform and kills :active',
  );
  // 两个面板都要挂上这个共用类，否则「一套东西」只是说法。
  assert.ok(
    scan.scrub(read('panel-favorites.js')).indexOf('fk-list-in') >= 0,
    'favourites groups must carry the shared entrance class',
  );
  assert.ok(
    scan.scrub(read('panel-files.js')).indexOf('fk-list-in') >= 0,
    'the files list must carry the same shared entrance class',
  );
});

test('both panels cap the stagger at the same step count', () => {
  // 逼红实测：把收藏那边的 Math.min 去掉时零条红 —— 没有任何断言钉着封顶。
  // 不封顶的话，收藏项一多，末行要等好几秒才出现（文件面板那边记着同一个坑）。
  const fav = scan.scrub(read('panel-favorites.js'));
  const files = scan.scrub(read('panel-files.js'));
  for (const [name, src] of [['panel-favorites.js', fav], ['panel-files.js', files]]) {
    assert.ok(
      src.indexOf('Math.min(i, STAGGER_CAP)') >= 0,
      name + ' must clamp the stagger index to STAGGER_CAP',
    );
    assert.ok(
      src.indexOf('STAGGER_CAP = 8') >= 0,
      name + ' must use the same cap as the other panel, or the two look different',
    );
  }
});
