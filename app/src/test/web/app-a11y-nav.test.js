const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const html = read('app.html');
const appJs = read('app.js');
const shellCss = read('shell.css');

// 去注释后再检索：本文件与 app.html 的注释里会出现 aria-selected 这类字样，
// 直接搜原文会在「注释提到它」上转红，而不是在它真正要查的「代码里还在用它」上。
const stripHtmlComments = (s) => s.replace(/<!--[\s\S]*?-->/g, '');
const stripJsComments = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
const stripCssComments = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '');

const htmlBody = stripHtmlComments(html);

test('every decorative icon span is hidden from assistive tech', () => {
  // v1.19.0 把图标字形从元素文本改成 `::before { content: attr(data-icon) }`，
  // 解决了手机长按选中整个字形的问题。但 CSS 生成内容多数读屏仍会朗读——
  // 于是「收藏」按钮会被念成「star 收藏」，图标名是内部标识符，对用户毫无意义。
  //
  // 这里不依赖「当前某个读屏到底念不念」：装饰性图标标 aria-hidden 是基线正确的标记，
  // 与症状是否复现无关，且能覆盖我们测不到的读屏。真正需要小心的是下一条断言。
  const spans = [...htmlBody.matchAll(/<span([^>]*material-symbols-outlined[^>]*)>/g)];
  assert.ok(spans.length > 15, 'icon span scan looks empty: ' + spans.length);
  for (const m of spans) {
    assert.match(m[1], /aria-hidden="true"/, 'icon span exposed to AT: <span' + m[1] + '>');
  }
});

test('no control loses its accessible name to aria-hidden', () => {
  // 上一条的唯一真实风险：图标是某个控件的**全部**内容时，给它标 aria-hidden
  // 会把控件的可访问名一起抹掉，留下一个读屏只会念「按钮」的东西——
  // 那比朗读 `star` 更糟。所以逐个控件核算：要么自己有 aria-label，
  // 要么还剩至少一个没被隐藏的文本节点。
  const buttons = [...htmlBody.matchAll(/<button\b([^>]*)>([\s\S]*?)<\/button>/g)];
  assert.ok(buttons.length > 8, 'button scan looks empty: ' + buttons.length);
  const nameless = [];
  for (const [, attrs, inner] of buttons) {
    if (/aria-label=/.test(attrs)) continue;
    // 剩下的可见文本来源：带 data-i18n 的 span、有 id 待 JS 填的 label span，
    // 或直接写死的字面文本。三者都不算被 aria-hidden 吃掉。
    const visible = [...inner.matchAll(/<span([^>]*)>([^<]*)<\/span>/g)]
      .some(([, a, text]) => !/aria-hidden="true"/.test(a)
        && (/data-i18n=/.test(a) || /\bid=/.test(a) || text.trim().length > 0));
    if (!visible) nameless.push(attrs.trim().slice(0, 80));
  }
  assert.deepEqual(nameless, [], 'buttons left with no accessible name');
});

test('every icon factory hides its output, not just the static markup', () => {
  // app.html 只是初始标记。收藏行、设置行、文件气泡、消息操作条的图标全是运行时
  // 造出来的 —— 只修 HTML 等于修了看得见的那一小半。
  // 四个文件各有一个唯一的图标工厂，是天然的收口点；在工厂里设，新增调用点自动继承。
  const factories = {
    'app.js': 'materialSymbolEl',
    'panel-favorites.js': 'icon',
    'panel-settings.js': 'icon',
    'panel-files.js': 'icon',
    'panel-album.js': 'icon',
  };
  for (const [file, fn] of Object.entries(factories)) {
    const src = stripJsComments(read(file));
    const at = src.indexOf('function ' + fn + '(');
    assert.ok(at > 0, file + ' has no ' + fn + ' factory');
    // 切到函数结束大括号为止：固定长度切片可能越过函数体，把无关代码里的
    // aria-hidden 算进来，那就成了「断言绿着，工厂其实没设」。
    const end = src.slice(at).search(/^ {4}}/m);
    const body = src.slice(at, at + (end > 0 ? end : 600));
    assert.match(body, /setAttribute\('aria-hidden', 'true'\)/,
      file + ': ' + fn + ' must hide its icon from AT');
  }
});

test('navigation destinations report state with aria-current, not aria-selected', () => {
  // aria-selected 只在 tab / option / row / gridcell 这类角色上有效。
  // 这两组导航项是 <nav> 里的裸 <button>，读屏会**直接忽略** aria-selected——
  // 也就是说 v1.19.0 以来「当前在哪个目的地」从未被朗读过。不是观感问题。
  // 面包屑（panel-files.js）本来就用 aria-current="page"，这里跟它对齐。
  const navs = [...htmlBody.matchAll(/<(nav|button)\b[^>]*(fk-rail-item|fk-navbar-item)[^>]*>/g)];
  assert.ok(navs.length >= 7, 'nav item scan looks empty: ' + navs.length);
  for (const m of navs) {
    assert.equal(/aria-selected/.test(m[0]), false, 'nav item still uses aria-selected: ' + m[0]);
    assert.match(m[0], /aria-current="(page|false)"/, 'nav item without aria-current: ' + m[0]);
  }

  // 单一写入点：两个 sync 函数是事实点，各调用点各写一遍会分叉出「显示 A 高亮 B」。
  const js = stripJsComments(appJs);
  for (const fn of ['syncRailSelection', 'syncNavbarSelection']) {
    const at = js.indexOf('function ' + fn + '(');
    assert.ok(at > 0, 'no ' + fn);
    const body = js.slice(at, js.indexOf('\n    }', at));
    assert.match(body, /setAttribute\('aria-current'/, fn + ' must write aria-current');
    assert.equal(/aria-selected/.test(body), false, fn + ' still writes aria-selected');
  }

  // 样式选择器必须一起迁移，否则高亮整体失效（属性改了、CSS 还在等旧属性）。
  const css = stripCssComments(shellCss);
  for (const cls of ['fk-rail-item', 'fk-navbar-item']) {
    // 用 includes 而不是 new RegExp('...')：拼字符串时 '\[' 少写一个反斜杠就变成
    // 字符类，正则照样"匹配得上"，断言绿着而选择器根本没迁移。本条第一版就是这么错的。
    assert.ok(css.includes('.' + cls + '[aria-current="page"]'),
      cls + ' has no aria-current rule');
    assert.equal(css.includes('.' + cls + '[aria-selected'), false,
      cls + ' still styled on aria-selected');
  }
});

test('list selection keeps aria-selected and is NOT swept into the migration', () => {
  // 反向守卫。收藏列表的行是**多选**语义，头像网格是选择网格——
  // 那里 aria-selected 才是对的属性，aria-current 反而错（"当前页"不是"已勾选"）。
  // 上一条断言很容易被人用一次全局替换"满足"，那会把这两处一起改坏。
  // v1.20.0 Task 6 已实测过一次 sed 全局替换误伤无关行，这条就是给它上闸。
  const panelsCss = stripCssComments(read('panels.css'));
  const chatCss = stripCssComments(read('chat.css'));
  const favJs = stripJsComments(read('panel-favorites.js'));
  assert.match(panelsCss, /\.fk-item\[aria-selected="true"\]/,
    'favorites row selection must stay on aria-selected');
  assert.match(chatCss, /\.avatar-circle\[aria-selected="true"\]/,
    'avatar picker selection must stay on aria-selected');
  assert.match(favJs, /setAttribute\('aria-selected'/,
    'panel-favorites must keep writing aria-selected');
  assert.equal(/aria-current/.test(favJs), false,
    'panel-favorites must not adopt aria-current');
});
