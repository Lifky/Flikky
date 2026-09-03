const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const scan = require('./scan.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * 装机验收（2026-09-03）两条：
 *   - 面包屑要固定在列表顶部，滚到任何位置都能知道自己在哪；
 *   - 全选按钮加载中虽然点不动，但**看起来**能点 —— 要显式的 disabled 外观。
 */

test('the breadcrumb sticks to the top of the scrolling list', () => {
  const css = scan.stripBlockComments(read('panels.css'));
  const rule = scan.ruleBlock(css, '.fk-crumbs');
  assert.ok(rule, 'no .fk-crumbs rule');
  assert.ok(
    rule.indexOf('position: sticky') >= 0,
    'the breadcrumb must be sticky, or a deep path is unknowable once scrolled: ' + rule,
  );
  assert.ok(rule.indexOf('top: 0') >= 0, 'sticky needs an offset to stick at: ' + rule);
  // 必须有不透明背景：否则滚上来的行会从它下面透出来。
  assert.ok(
    rule.indexOf('background') >= 0,
    'a sticky header needs an opaque background or rows show through: ' + rule,
  );
  // 必须压在行之上。行有 :active 的 transform，会创建层叠上下文。
  assert.ok(rule.indexOf('z-index') >= 0, 'it must sit above the rows: ' + rule);
});

test('the scroll container is the one the breadcrumb sticks inside', () => {
  // sticky 只相对**最近的滚动祖先**生效。面包屑是 renderBreadcrumb 塞进 bodyEl 的，
  // 而 .fk-panel-body 正是那个 overflow-y: auto 的元素 —— 这条把这个前提钉住，
  // 免得有人把面包屑挪出去之后 sticky 静默失效（CSS 不会报错）。
  const js = scan.scrub(read('panel-files.js'));
  const shell = scan.functionBody(js, 'function renderShell(');
  assert.ok(
    shell.indexOf('renderBreadcrumb(bodyEl') >= 0,
    'the breadcrumb must live inside the scrolling body. Body:' + scan.LF + shell,
  );
  const css = scan.stripBlockComments(read('panels.css'));
  const body = scan.ruleBlock(css, '.fk-panel-body');
  assert.ok(body && body.indexOf('overflow-y: auto') >= 0, 'body must be the scroller: ' + body);
});

test('a disabled icon button looks disabled and stops reacting', () => {
  const css = scan.stripBlockComments(read('panels.css'));
  const rule = scan.ruleBlock(css, '.fk-icon-btn:disabled');
  assert.ok(
    rule,
    'there is no :disabled rule at all, so the state is real but invisible — ' +
      'which is exactly what was reported',
  );
  assert.ok(
    rule.indexOf('.38') >= 0,
    'MD3 disabled content is on-surface at 38%, matching .fk-btn:disabled: ' + rule,
  );
  assert.ok(rule.indexOf('cursor') >= 0, 'the cursor must stop saying clickable: ' + rule);
  // 悬停与按下必须排除 disabled —— 否则它还在给「可点」的反馈，比没有灰化更糟。
  //
  // 判据是「**每一处** :hover / :active 后面都紧跟 :not(:disabled)」，
  // 而不是「存在一处带 :not 的」：后者被一条裸规则同时满足，等于没查
  // （ruleBlock 是子串匹配，`.fk-icon-btn:hover` 也命中带 :not 的那条）。
  ['hover', 'active'].forEach((state) => {
    const needle = '.fk-icon-btn:' + state;
    let at = css.indexOf(needle);
    let seen = 0;
    while (at >= 0) {
      const after = css.slice(at + needle.length, at + needle.length + 15);
      assert.ok(
        after.indexOf(':not(:disabled)') === 0,
        'a bare :' + state + ' rule still gives feedback on a disabled button; ' +
          'found "' + needle + after + '"',
      );
      seen += 1;
      at = css.indexOf(needle, at + 1);
    }
    assert.ok(seen > 0, 'no :' + state + ' rule found at all, so nothing was checked');
  });
});
