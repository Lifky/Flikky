const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (n) => fs.readFileSync(path.join(WEB, n), 'utf8');

/*
 * v1.20.0 装机验收缺陷 1a 的守卫。
 *
 * `[hidden]` 之所以能隐藏元素，只是因为 UA 样式表里有一条 `[hidden]{display:none}`。
 * 那条规则权重极低——作者样式里任何 `display:` 都压得过它。于是给一个 `display:flex`
 * 的元素设 `hidden`，属性设上了、元素照样画出来。
 *
 * 项目里这个坑踩过 7 次（chat.css / pages.css / panels.css / shell.css 各自补了显式
 * `[hidden]{display:none}`，chat.css:541 还留了注释），但一直没有全局 reset，
 * 于是每加一个新的可隐藏元素就得重新记得一次。v1.20.0 的「文件」导航入口就是这么漏的：
 * JS 把 hidden 设对了（firstAvailableDest 因此正确地选了收藏），CSS 照样把它画在 rail 上。
 * 修法是一条全局 reset，不是再补第 8 处。
 *
 * 本文件**一个正则都不用**。第一版用 `new RegExp('\.' + cls + ...)` 拼选择器，
 * 而写文件的 heredoc 吃掉了一层反斜杠，正则变成 `.fk-rail-items*{...}`，
 * 第 4 条断言因此空转全绿——它本该在修复前就转红。改成纯字符串查找后无从转义出错。
 */

const stripCss = (s) => {
  // 去块注释，不用正则：注释里出现 display: 会让规则块判断误判。
  let out = '';
  let i = 0;
  while (i < s.length) {
    const start = s.indexOf('/*', i);
    if (start < 0) { out += s.slice(i); break; }
    out += s.slice(i, start);
    const end = s.indexOf('*/', start + 2);
    if (end < 0) break;
    i = end + 2;
  }
  return out;
};

/** 取 `selector {` 到下一个 `}` 之间的声明块；找不到返回空串。 */
const ruleBlock = (css, selector) => {
  const at = css.indexOf(selector + ' {');
  if (at < 0) return '';
  const end = css.indexOf('}', at);
  return end < 0 ? '' : css.slice(at, end);
};

/** app.html 里所有开标签的属性串。 */
const openTags = (html) => html.split('<').slice(1)
  .map((s) => {
    const gt = s.indexOf('>');
    return gt < 0 ? '' : s.slice(0, gt);
  })
  .filter(Boolean);

/**
 * 带**裸** hidden 属性的标签。刻意不用 `hidden`：那个会连
 * `aria-hidden="true"` 一起匹配（第一版就是这么把 19 个图标 span 也算进来的）。
 */
const hasBareHidden = (tag) => tag.endsWith(' hidden') || tag.includes(' hidden ');

const classesOf = (tag) => {
  const at = tag.indexOf('class="');
  if (at < 0) return [];
  const rest = tag.slice(at + 7);
  return rest.slice(0, rest.indexOf('"')).split(' ').filter(Boolean);
};

test('a global [hidden] reset exists so the attribute always wins', () => {
  const base = stripCss(read('base.css'));
  const rule = ruleBlock(base, '[hidden]:not([data-hidden-animated])');
  assert.ok(rule, 'base.css has no global [hidden] reset');
  assert.ok(rule.includes('display: none') || rule.includes('display:none'),
    'the reset must set display:none, got: ' + rule);
  // !important 是必需的：`.fk-rail-item{display:flex}` 与它同为作者层、同权重，
  // 谁写在后面谁生效——而 base.css 在 shell.css **之前**加载。
  assert.ok(rule.includes('!important'),
    'without !important a later display: rule still wins, got: ' + rule);
});

test('every page loads base.css, so the reset is never missing', () => {
  for (const page of ['app.html', 'login.html', 'export.html']) {
    assert.ok(read(page).includes('href="/static/base.css"'), page + ' does not load base.css');
  }
});

test('the reset loads before the sheets that set display on hideable elements', () => {
  const html = read('app.html');
  const at = (f) => html.indexOf('/static/' + f);
  for (const later of ['shell.css', 'panels.css', 'chat.css']) {
    assert.ok(at('base.css') < at(later),
      'base.css must load before ' + later + ' (' + at('base.css') + ' vs ' + at(later) + ')');
  }
});

test('every element shipping hidden is actually hidden by CSS', () => {
  // 正向核算，也是这条守卫的本体：凡是初始就带 hidden 的元素，它的 class 若在任一
  // 样式表里被设了 display，就必须有东西让 [hidden] 赢——全局 reset，或自己的
  // `.cls[hidden]` 规则。有了 reset 这条恒成立；留着它是为了在有人删掉 reset 时立刻转红。
  const html = read('app.html');
  const css = ['base.css', 'shell.css', 'panels.css', 'chat.css', 'pages.css']
    .map((f) => stripCss(read(f))).join(String.fromCharCode(10));
  const reset = ruleBlock(css, '[hidden]:not([data-hidden-animated])');
  const globalReset = reset.includes('!important') &&
    (reset.includes('display: none') || reset.includes('display:none'));

  const tags = openTags(html).filter(hasBareHidden);
  const hideable = new Set(tags.flatMap(classesOf));
  assert.ok(hideable.size >= 4, 'hideable-element scan looks empty: ' + hideable.size);
  // 防空转：`.fk-rail-item` 必须在集合里，否则扫描逻辑坏了而断言还在绿着。
  assert.ok(hideable.has('fk-rail-item'),
    'the files rail entry must be in the scan; got ' + [...hideable].join(', '));

  // 显式退出 reset 的类（标了 data-hidden-animated 的元素身上那些 class）。
  const optedOut = new Set(
    tags.filter((t) => t.includes('data-hidden-animated')).flatMap(classesOf),
  );

  const offenders = [];
  for (const cls of hideable) {
    if (!ruleBlock(css, '.' + cls).includes('display:')) continue;
    const own = ruleBlock(css, '.' + cls + '[hidden]');
    // 真正的不变量不是「有没有 display:none」，是**隐藏后有没有离开焦点树与无障碍树**。
    // display:none 与 visibility:hidden 都能做到；宽度塌缩动画只能用后者
    // （.fk-fab 就是这一档：写了 display:none 宽度就没得动画）。
    const ownHides = own.includes('display: none') || own.includes('display:none') ||
      own.includes('visibility: hidden') || own.includes('visibility:hidden');
    if (optedOut.has(cls)) {
      assert.ok(ownHides,
        '.' + cls + ' opts out of the reset, so it must hide itself ' +
        '(visibility:hidden at minimum) or it stays focusable while invisible');
      continue;
    }
    if (globalReset || ownHides) continue;
    offenders.push(cls);
  }
  assert.deepEqual(offenders, [],
    'these classes set display but nothing makes [hidden] win');
});

test('the scan itself can tell a display rule from no rule', () => {
  // 上一条的判据是 ruleBlock(...).includes('display:')。第一版这个判断因为转义被吃
  // 而恒为 false，于是整条断言空转。这里正反各钉一次，让判据自己被测到。
  const shell = stripCss(read('shell.css'));
  assert.ok(ruleBlock(shell, '.fk-rail-item').includes('display:'),
    'the scan cannot see .fk-rail-item display rule — judgement is broken');
  assert.equal(ruleBlock(shell, '.fk-no-such-class').includes('display:'), false,
    'the scan reports display for a class that does not exist');
});
