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
  // 吸顶的承载者是**整个头块** `.fk-files-sticky`（搜索行 + 面包屑 + 计数 +
  // 进度条），不再是 .fk-crumbs 自己。改成一整块的理由：多个 sticky 元素要
  // 手工算彼此的 top 偏移，差一像素滚动时就互相穿透。
  // 这一条守的意图没变 —— 深路径滚下去之后仍然可知。
  const css = scan.stripBlockComments(read('panels.css'));
  const rule = scan.ruleBlock(css, '.fk-files-sticky');
  assert.ok(rule, 'no .fk-files-sticky rule');
  assert.ok(
    rule.indexOf('position: sticky') >= 0,
    'the breadcrumb must be sticky, or a deep path is unknowable once scrolled: ' + rule,
  );
  assert.ok(rule.indexOf('top: 0') >= 0, 'sticky needs an offset to stick at: ' + rule);
  // 必须有不透明背景：否则滚上来的行会从它下面透出来。
  //
  // 而且必须是**面板自己那个背景**。第一版用了 `--mdui-color-surface`，
  // 那是另一个 token —— 装机验收看到吸顶条与四周颜色不同（Screenshot_8 里
  // 「内部存储」后面有一条色带）。面板坐在 .fk-pillar 上，它的背景是
  // `--flikky-pillar-bg`，深浅色两档都由那个 token 自己解析。
  assert.ok(
    rule.indexOf('var(--flikky-pillar-bg)') >= 0,
    'the sticky band must use the panel background token, not a different surface: ' + rule,
  );
  assert.equal(
    rule.indexOf('--mdui-color-surface)') >= 0,
    false,
    'a bare surface token is a different colour from the panel: ' + rule,
  );
  // 必须压在行之上。行有 :active 的 transform，会创建层叠上下文。
  assert.ok(rule.indexOf('z-index') >= 0, 'it must sit above the rows: ' + rule);
});

test('the scroll container is the one the breadcrumb sticks inside', () => {
  // sticky 只相对**最近的滚动祖先**生效。面包屑是 renderBreadcrumb 塞进 bodyEl 的，
  // 而 .fk-panel-body 正是那个 overflow-y: auto 的元素 —— 这条把这个前提钉住，
  // 免得有人把面包屑挪出去之后 sticky 静默失效（CSS 不会报错）。
  // 头块是 mount 建的、跨换目录存活（搜索行住在里面，重建会连焦点一起摧毁），
  // 所以这一条查的是 mount 而不是 renderShell。
  const js = scan.scrub(read('panel-files.js'));
  assert.ok(
    js.indexOf('bodyEl.appendChild(stickyEl)') >= 0,
    'the sticky head block must live inside the scrolling body, or sticky ' +
      'silently stops working (CSS does not complain)',
  );
  const shell = scan.functionBody(js, 'function renderShell(');
  assert.ok(
    shell.indexOf('renderBreadcrumb(crumbsHost') >= 0,
    'renderShell must rewrite the breadcrumb into the head block. Body:' + scan.LF + shell,
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

test('the progress bar is the mdui component, not a hand-rolled one', () => {
  // 用户问的：这条进度是手搓的吗。不是 —— 但把这件事钉住，
  // 免得将来有人为了做收回动画改成自己画的 div。
  const js = scan.scrub(read('panel-files.js'));
  assert.ok(
    js.indexOf("createElement('mdui-linear-progress')") >= 0,
    'the loading bar must be mdui-linear-progress',
  );
});

test('the progress bar collapses instead of hard-cutting', () => {
  // 用户要求：加载完毕后向上缩回，下面的 listitem 不要硬切，要弹簧过渡
  // （参照 App 主页 chips 分组的显隐）。
  const css = scan.stripBlockComments(read('panels.css'));
  const rule = scan.ruleBlock(css, '.fk-files-progress');
  assert.ok(rule, 'no progress rule');
  // 判据必须落在**transition 那一段**里，不是整条规则里。
  // 整条规则本来就有 `margin: ...` 这个基础声明，所以「rule 里有 margin」
  // 这种断言把过渡删掉照样绿（逼红实测：零条红）。
  const tr = rule.slice(rule.indexOf('transition:'));
  assert.ok(tr.indexOf('transition:') === 0, 'no transition on the progress bar: ' + rule);
  // 高度与上下外边距都要过渡：只动高度、margin 还在，列表仍会硬跳一段。
  ['height', 'margin-top', 'margin-bottom'].forEach((prop) => {
    assert.ok(
      tr.indexOf(prop) >= 0,
      'the collapse must animate ' + prop + ', or the list still jumps: ' + tr,
    );
  });
  assert.ok(
    tr.indexOf('--flikky-spring-spatial') >= 0,
    'it must use a spatial spring token, like the chips group does: ' + tr,
  );
  // 收起态用类而不是 [hidden]：全局 reset 把 [hidden] 变成 display:none !important，
  // 那会让过渡完全不发生（base.css 里那条注释记着这个坑）。
  const done = scan.ruleBlock(css, '.fk-files-progress.is-done');
  assert.ok(done, 'no collapsed state rule');
  assert.ok(done.indexOf('height: 0') >= 0, 'collapsed means zero height: ' + done);
  const js = scan.scrub(read('panel-files.js'));
  assert.ok(
    js.indexOf("classList.toggle('is-done'") >= 0 || js.indexOf("classList.add('is-done')") >= 0,
    'the collapse must be driven by that class',
  );
  // 收起之后必须离开无障碍树，否则读屏器还在念一个不存在的进度条。
  assert.ok(
    done.indexOf('visibility: hidden') >= 0,
    'a collapsed progress bar must leave the accessibility tree: ' + done,
  );
});
