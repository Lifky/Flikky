const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');
const SHEETS = ['base.css', 'shell.css', 'panels.css', 'chat.css', 'pages.css'];

/*
 * `font:` 简写必须带字体族。
 *
 * type token 的值形如 `500 14px/20px` —— 没有 family。而 CSS 的 font 简写
 * **要求** family，缺了整条声明就是非法的，被整条丢弃：字号、字重、行高全部不生效，
 * 只剩继承来的正文样式。没有报错、没有警告、控制台干净。
 *
 * v1.20.0 一次写出 4 处（面包屑、引导态标题与正文、面板提示），装机验收表现为
 * 「文件面板的字看起来跟别处不一样」（Screenshot_3）。
 * 与 D31 记的「缺失的 CSS 自定义属性静默降级」是同一类：**CSS 的失败模式是安静的**。
 */
test('every font shorthand using a type token also names a family', () => {
  const offenders = [];
  for (const file of SHEETS) {
    const css = scan.stripBlockComments(read(file));
    const lines = css.split(scan.LF);
    lines.forEach((line, i) => {
      const trimmed = line.trim();
      if (trimmed.indexOf('font:') !== 0) return;
      if (trimmed.indexOf('--flikky-type-') < 0) return;
      if (trimmed.indexOf('--flikky-font-family') >= 0) return;
      offenders.push(file + ':' + (i + 1) + '  ' + trimmed);
    });
  }
  assert.deepEqual(offenders, [],
    'a font shorthand without a family is invalid and dropped entirely, ' +
      'so the whole type scale silently stops applying');
});

test('the check can tell a bad shorthand from a good one', () => {
  // 防空转：判据必须真的能分辨。
  const bad = '  font: var(--flikky-type-label-large);';
  const good = '  font: var(--flikky-type-label-large) var(--flikky-font-family);';
  const isOffender = (line) => {
    const t = line.trim();
    return t.indexOf('font:') === 0 &&
      t.indexOf('--flikky-type-') >= 0 &&
      t.indexOf('--flikky-font-family') < 0;
  };
  assert.equal(isOffender(bad), true);
  assert.equal(isOffender(good), false);
});
