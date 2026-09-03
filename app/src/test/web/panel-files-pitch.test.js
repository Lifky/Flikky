const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');
const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

test('the row pitch is read from the real gap, not from flow layout', () => {
  const js = scan.scrub(read('panel-files.js'));
  const cal = scan.functionBody(js, 'function calibrate(');
  assert.ok(cal, 'no calibrate');
  // 装机验收：行「挨得太近了」。
  //
  // 第一版靠「让前两行走正常流、取 offsetTop 之差」量行距。但样式表里
  // `.fk-files-list > .fk-item` 是**无条件** `position: absolute` 的 ——
  // 校准时两行早就脱离了流，差值恒为 0，于是退回只有行高、少一个行距。
  // mini-dom 没有布局引擎，永远走兜底，所以这个错测不出来。
  assert.equal(
    cal.indexOf('offsetTop') >= 0,
    false,
    'calibration must not depend on flow layout: the rows are absolutely positioned ' +
      'by the stylesheet before it ever runs. Body:' + scan.LF + cal,
  );
  assert.ok(
    cal.indexOf('getComputedStyle') >= 0,
    'the gap must come from the resolved style, so the token stays the single source. ' +
      'Body:' + scan.LF + cal,
  );
  assert.ok(
    cal.indexOf('rowGap') >= 0 || cal.indexOf('listgroup-gap') >= 0,
    'it must read the gap itself, not guess it. Body:' + scan.LF + cal,
  );
  // 兜底常量不许把行距吞掉：它只在完全没有布局信息时用得上。
  assert.ok(
    cal.indexOf('ROW_STEP_FALLBACK') >= 0,
    'a fallback is still needed for environments without layout',
  );
});

test('the stylesheet still declares the gap the calibration reads', () => {
  // getComputedStyle 读的是**解析后**的 gap。token 没了的话它会读出 0，
  // 行就贴在一起 —— 又一次静默失效（D31 那一族）。所以声明本身要钉住。
  const css = scan.stripBlockComments(read('panels.css'));
  const files = scan.ruleBlock(css, '.fk-files-list');
  assert.ok(files, 'no .fk-files-list rule');
  assert.ok(
    files.indexOf('gap: var(--flikky-listgroup-gap)') >= 0,
    'the container must declare the gap for the calibration to resolve: ' + files,
  );
  assert.ok(
    css.indexOf('--flikky-listgroup-gap:') >= 0,
    'and the token itself must be declared somewhere in this stylesheet',
  );
});
