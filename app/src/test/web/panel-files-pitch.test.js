const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');
const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * 行距这件事在装机验收里连错三轮，前两轮都是**测量**的错法不同：
 *   1. 让两行走正常流量 offsetTop 之差 —— 但样式表把行设成了无条件绝对定位，
 *      探针从未进入流，差值恒为 0；
 *   2. 改成 height + getComputedStyle().rowGap —— 把一个整体拆成两半分别量再相加。
 *
 * 两轮的共同前提是「绝对定位要求 JS 知道行距」。第三轮换掉的是那个前提：
 * 行留在正常流里，**行距归 CSS**，测量只用来撑滚动区间。
 * 于是同一个测量出错，爆炸半径从「毁掉布局」降到「滚动条略不准」。
 *
 * 这个文件守的就是那个前提没有被悄悄改回去。
 */

test('nothing in JS decides how far apart two rows sit', () => {
  const js = scan.scrub(read('panel-files.js'));
  assert.equal(
    js.indexOf('function calibrate(') >= 0,
    false,
    'the probe-based calibration must be gone',
  );
  assert.equal(
    /rowGap|listgroup-gap/.test(js),
    false,
    'JS must not know about the gap at all — that is the whole point',
  );
  const sync = scan.functionBody(js, 'function syncVirtual(');
  assert.equal(
    /setProperty\('top'/.test(sync),
    false,
    'rows must not be positioned by JS. Body:' + scan.LF + sync,
  );
});

test('the measured pitch is only spent on the scroll extent', () => {
  const js = scan.scrub(read('panel-files.js'));
  const sync = scan.functionBody(js, 'function syncVirtual(');
  // pitch 允许出现在两个地方：算窗口范围，和给占位块定高。
  // 只要它没被用来摆行，量错就毁不掉布局。
  assert.ok(sync.indexOf('pitch') >= 0, 'the window still needs a pitch estimate');
  assert.ok(
    sync.indexOf('spacer(') >= 0 || sync.indexOf('Spacer') >= 0,
    'the extent must be carried by spacer elements. Body:' + scan.LF + sync,
  );
});

test('the stylesheet, and only the stylesheet, sets the row gap', () => {
  const css = scan.stripBlockComments(read('panels.css'));
  const files = scan.ruleBlock(css, '.fk-files-list');
  const group = scan.ruleBlock(css, '.fk-group');
  assert.ok(files && group, 'both containers must be declared');
  assert.ok(
    files.indexOf('gap: var(--flikky-listgroup-gap)') >= 0,
    'the files container must declare the gap: ' + files,
  );
  assert.ok(
    group.indexOf('gap: var(--flikky-listgroup-gap)') >= 0,
    'and favourites must declare the very same one: ' + group,
  );
  assert.ok(
    css.indexOf('--flikky-listgroup-gap:') >= 0,
    'and the token itself must be declared in this stylesheet',
  );
});
