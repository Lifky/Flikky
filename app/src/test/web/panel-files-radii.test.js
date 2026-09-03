const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * 连接列表组的首尾外圆角，判据从 **DOM 位置** 换成 **数据下标**。
 *
 * 现在靠 `.fk-group > .fk-item:first-child / :last-child`。虚拟化之后 DOM 里只剩
 * 视口附近那几十行，「第一个子元素」就不再是「列表第一行」——滚到中间时会有一行
 * 莫名其妙带上外圆角。所以文件行改成由 JS 按**数据下标**打 `.is-first` / `.is-last`。
 *
 * 这一步本身是视觉零改动（两者今天完全重合），单独落一次是为了让下一步只做窗口化。
 */

test('the files list marks first and last by data index', () => {
  const js = scan.scrub(read('panel-files.js'));
  const row = scan.functionBody(js, 'function renderRow(');
  assert.ok(row, 'no renderRow');
  assert.ok(
    row.indexOf('is-first') >= 0 && row.indexOf('is-last') >= 0,
    'rows must carry explicit first/last markers. Body:' + scan.LF + row,
  );
  // 判据必须是数据下标与总数，不是 DOM 位置。
  assert.ok(
    row.indexOf('index === 0') >= 0,
    'first must be decided by the data index. Body:' + scan.LF + row,
  );
  assert.ok(
    row.indexOf('total - 1') >= 0,
    'last must be decided against the total, not the rendered count. Body:' + scan.LF + row,
  );
});

test('the stylesheet gives those classes the outer radius', () => {
  const css = scan.stripBlockComments(read('panels.css'));
  const first = scan.ruleBlock(css, '.fk-item.is-first');
  const last = scan.ruleBlock(css, '.fk-item.is-last');
  assert.ok(first, 'no .fk-item.is-first rule');
  assert.ok(last, 'no .fk-item.is-last rule');
  assert.ok(
    first.indexOf('radius-outer') >= 0,
    'the first row needs the outer radius at the top: ' + first,
  );
  assert.ok(
    last.indexOf('radius-outer') >= 0,
    'the last row needs the outer radius at the bottom: ' + last,
  );
  // 与结构选择器给的是同一组 token —— 否则「视觉零改动」只是说法。
  const structuralFirst = scan.ruleBlock(css, '.fk-group > .fk-item:first-child');
  assert.ok(structuralFirst, 'the structural rule should still exist for favourites');
  ['border-start-start-radius', 'border-start-end-radius'].forEach((prop) => {
    assert.ok(
      first.indexOf(prop) >= 0 && structuralFirst.indexOf(prop) >= 0,
      'the class must set the same corner properties as the structural rule: ' + prop,
    );
  });
});
