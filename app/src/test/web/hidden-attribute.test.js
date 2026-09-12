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

/*
 * ── hidden + loading="lazy" = 图片永不加载（2026-09-12 装机反馈）─────────────
 *
 * 上面那条全局 reset 让 `[hidden]` 真正变成 `display: none !important` —— 它修对了
 * 一个问题，也**造出了一个新的交互**：
 *
 *   `loading="lazy"` 的图片，浏览器按「是否接近视口」决定何时发请求。
 *   一个 `display: none` 的元素没有盒子、永远不与视口相交，
 *   于是那张图**永远不会被请求**。
 *
 * v1.20.0 阶段二的存储/收藏缩略图正是这么写的：`img.loading = 'lazy'` 紧挨着
 * `img.hidden = true`（hidden 是为了「加载完成前不露出半张图」）。后果是双重的：
 *
 *   · load 事件不触发 → 类型图标不被移除 → 一张缩略图都看不到；
 *   · img 是 display:none → 接不到点击 → 预览也打不开。
 *
 * 两个症状同一个根因。
 *
 * ## 为什么只能是源码扫描
 *
 * mini-dom 没有布局引擎、也没有惰性加载语义：测试里给 `.src` 赋值后由测试自己派发
 * `load`，所以**行为测试在任何实现下都是绿的**。这个缺陷只在真实浏览器里存在。
 * 判据本身是结构性的（两个属性不该同时出现在一个元素上），用源码钉住是诚实的做法。
 *
 * ## 惰性加载在本项目本来也没有收益
 *
 * 列表是虚拟化的，只有视口附近的行存在于 DOM 里 —— 设计文档 §9 风险 4 预判过这点。
 */

/*
 * ── hidden + loading="lazy" = 图片永不加载（2026-09-12 装机反馈）─────────────
 *
 * 上面那条全局 reset 让 `[hidden]` 真正变成 `display: none !important` —— 它修对了
 * 一个问题，也**造出了一个新的交互**：
 *
 *   `loading="lazy"` 的图片，浏览器按「是否接近视口」决定何时发请求。
 *   一个 `display: none` 的元素没有盒子、永远不与视口相交，
 *   于是那张图**永远不会被请求**。
 *
 * v1.20.0 阶段二的存储/收藏缩略图正是这么写的：`img.loading = 'lazy'` 紧挨着
 * `img.hidden = true`（hidden 是为了「加载完成前不露出半张图」）。后果是双重的：
 *
 *   · load 事件不触发 → 类型图标不被移除 → 一张缩略图都看不到；
 *   · img 是 display:none → 接不到点击 → 预览也打不开。
 *
 * 两个症状同一个根因（用户报的正是这两条）。
 *
 * ## 为什么只能是源码扫描
 *
 * mini-dom 没有布局引擎、也没有惰性加载语义：测试里给 `.src` 赋值后由测试自己派发
 * `load`，所以**行为测试在任何实现下都是绿的**。这个缺陷只在真实浏览器里存在。
 * 判据本身是结构性的（两个属性不该同时出现在一个元素上），用源码钉住是诚实的做法。
 */

const JS_FILES = ['leading.js', 'app.js', 'panel-files.js', 'panel-favorites.js', 'panel-settings.js'];

/** 剥注释：本文件与被扫文件的注释里都写着反例，不剥会扫到它们。 */
function stripJsComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/.*$/gm, '$1');
}

test('the lazy-loading scan can actually see assignments', () => {
  // 防切片失效：剥注释若把整份文件吃掉，下面那条会在零个匹配上通过。
  const total = JS_FILES.reduce((n, f) => n + stripJsComments(read(f)).length, 0);
  assert.ok(total > 10000, `剥注释后只剩 ${total} 字符 —— 剥得太狠了，先修切片再谈守卫`);
  const hiddenAssignments = JS_FILES
    .reduce((n, f) => n + (stripJsComments(read(f)).match(/\.hidden\s*=/g) || []).length, 0);
  assert.ok(hiddenAssignments > 0, '扫不到任何 `.hidden =` 赋值 —— 切片失效');
});

/**
 * 一个元素是否「既 lazy 又 hidden」。
 *
 * 判据必须**精确到同一个元素**，不能一刀切禁掉 lazy：`app.js` 的聊天气泡缩略图
 * 也是 lazy，但它**没有**被设 hidden，所以一直工作正常；而聊天列表没有虚拟化
 * （backlog B32），那里的惰性加载是真有收益的。为了守卫去改能用的代码是本末倒置。
 *
 * 做法：取 `X.loading = 'lazy'` 里的 X，在它前后各 12 行内找 `X.hidden = true`。
 *
 * **不用 `new RegExp` 拼判据** —— 本文件开头记着那个坑（写文件的 heredoc 会吃掉
 * 一层反斜杠，拼出来的正则永不匹配、断言空转全绿）。写这一条时又踩了一次，
 * 靠下面那条自检才发现。所以这里只用字面量正则 + 纯字符串查找。
 */
function lazyAndHidden(src) {
  const lines = src.split(/\r?\n/);
  const isWordChar = (ch) => !!ch && /[\w$]/.test(ch);
  const hits = [];
  lines.forEach((line, i) => {
    const m = line.match(/(\w+)\.loading\s*=\s*['"]lazy['"]/);
    if (!m) return;
    const name = m[1];
    const near = lines.slice(Math.max(0, i - 12), i + 13).join('\n');
    const needle = name + '.hidden';
    let at = near.indexOf(needle);
    while (at >= 0) {
      // 前一个字符不能是标识符字符，否则 `thumbImg.hidden` 会被当成 `img.hidden`
      const before = at > 0 ? near.charAt(at - 1) : '';
      const after = near.slice(at + needle.length, at + needle.length + 24).replace(/\s/g, '');
      if (!isWordChar(before) && after.indexOf('=true') === 0) {
        hits.push({ line: i + 1, name });
        break;
      }
      at = near.indexOf(needle, at + 1);
    }
  });
  return hits;
}

test('no element is both hidden and lazy-loaded', () => {
  const offenders = [];
  JS_FILES.forEach((f) => {
    lazyAndHidden(stripJsComments(read(f))).forEach((h) => {
      offenders.push(`${f}:${h.line} → ${h.name} 既 lazy 又 hidden`);
    });
  });
  assert.deepEqual(
    offenders,
    [],
    '这些元素既设了 loading="lazy" 又设了 hidden。base.css 的全局 reset 让 hidden\n' +
      '等于 display:none !important，而 display:none 的元素永远不与视口相交 ——\n' +
      '浏览器**永不发起请求**，图片永远不出现，也接不到点击。\n' +
      '要么去掉 lazy（虚拟化列表里它本来就没收益），要么别用 hidden 遮加载中的图：',
  );
});

test('the lazy-plus-hidden scan really fires on the shape it guards', () => {
  // 判据跨行、按变量名匹配，正则写错就会**恒绿** —— 而它正是为一个
  // 「测试全绿、生产全坏」的缺陷加的，恒绿会让它彻底失去意义。
  // 所以用一段合成源码证明它会响。
  const bad = [
    "const img = document.createElement('img');",
    "img.alt = 'x';",
    "img.loading = 'lazy';",
    "img.hidden = true;",
  ].join('\n');
  assert.equal(lazyAndHidden(bad).length, 1, '判据对它本该抓的形状都不响 —— 正则写错了');

  // 反向：只 lazy 不 hidden（聊天气泡那种）必须放行，否则守卫会逼人改能用的代码。
  const ok = [
    "const img = document.createElement('img');",
    "img.loading = 'lazy';",
    "wrap.appendChild(img);",
  ].join('\n');
  assert.equal(lazyAndHidden(ok).length, 0, '只 lazy 不 hidden 被误判了');

  // 反向：两个不同的元素各占一半，不该合判成一个。
  const twoVars = [
    "a.loading = 'lazy';",
    "b.hidden = true;",
  ].join('\n');
  assert.equal(lazyAndHidden(twoVars).length, 0, '不同元素被当成了同一个');
});

test('HTML markup carries no lazy images either', () => {
  // 静态 markup 里更容易漏看。页面可以不存在；存在就必须守。
  ['app.html', 'login.html', 'export.html'].forEach((f) => {
    let html;
    try {
      html = read(f);
    } catch {
      return;
    }
    assert.doesNotMatch(
      html.replace(/<!--[\s\S]*?-->/g, ''),
      /loading\s*=\s*["']lazy["'][\s\S]{0,200}?\bhidden\b/,
      `${f} 里有元素既 loading="lazy" 又 hidden —— 它永远不会加载`,
    );
  });
});
