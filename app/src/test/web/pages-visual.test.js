const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const pages = read('pages.css');
const tokens = read('tokens.css');
const login = read('login.html');
const exportHtml = read('export.html');
const loginJs = read('login.js');
const exportJs = read('export.js');

/** 从生成的 tokens.css 里反查每个 type token 的 px 字号。 */
const TYPE_PX = new Map(
  // 生产的 type token 是 `--flikky-type-<md3-style-name>: <weight> <size>px/<lineHeight>px`。
  [...tokens.matchAll(/--flikky-type-([a-z-]+):\s*\d+\s+(\d+)px/g)]
    .map((m) => [m[1], Number(m[2])]),
);

test('the token layer actually exposes a type scale to read', () => {
  assert.ok(TYPE_PX.size >= 10, `only ${TYPE_PX.size} type tokens found in tokens.css`);
});

test('neither standalone page casts a shadow', () => {
  // 登录页与导出页也是「贴在染色底上的一块面」，不是悬浮卡片。
  // inset 阴影不在此列：它画在元素内部，表达不出「浮起来」，而这是按钮 state layer
  // 唯一能压在背景之上、裸文本标签之下的做法（见 .fk-btn:hover 注释）。
  const shadows = [...pages.matchAll(/box-shadow:\s*([^;]+);/g)]
    .map((m) => m[1].trim())
    .filter((v) => v !== 'none' && !v.startsWith('inset '));
  assert.deepEqual(shadows, [], `unexpected elevation shadows: ${shadows.join(' | ')}`);
});

test('type is expressed only through tokens, never hard-coded px', () => {
  const literal = [...pages.matchAll(/font(?:-size)?:\s*[^;]*?\d+px[^;]*;/g)].map((m) => m[0].trim());
  assert.deepEqual(literal, [], `hard-coded type: ${literal.join(' | ')}`);
});

test('the standalone pages keep to three type sizes', () => {
  // 旧导出页实测 5+ 档，观感发散。24 页标题 / 16 主文本 / 14 次要。
  const used = new Set();
  for (const decl of pages.matchAll(/font:\s*([^;]+);/g)) {
    for (const v of decl[1].matchAll(/var\(--flikky-type-([a-z-]+)\)/g)) {
      if (TYPE_PX.has(v[1])) used.add(TYPE_PX.get(v[1]));
    }
  }
  const sizes = [...used].sort((a, b) => b - a);
  assert.deepEqual(sizes, [24, 16, 14], `type scale sprawl: ${sizes.join('/')}`);
});

test('every page drops app.css and loads the new sheets in the right order', () => {
  // app.html 也在内：计划的文件清单漏了它，但它同样 <link> 着 app.css，
  // 删文件而不删这行会让会话页 404 一个样式表。
  const appHtml = read('app.html');
  for (const [name, page] of [['login.html', login], ['export.html', exportHtml], ['app.html', appHtml]]) {
    assert.equal(/app\.css/.test(page), false, `${name} still references app.css`);
  }
  for (const [name, page] of [['login.html', login], ['export.html', exportHtml]]) {
    // vendor/mdui.css 带斜杠，这条正则刻意不匹配它 —— 只看我们自己的分区表。
    const sheets = [...page.matchAll(/href="\/static\/([a-z-]+\.css)"/g)].map((m) => m[1]);
    for (const required of ['base.css', 'tokens.css', 'pages.css']) {
      assert.ok(sheets.includes(required),
        `${name} must load ${required} — sheets: ${sheets.join(' → ')}`);
    }
    assert.ok(sheets.indexOf('tokens.css') < sheets.indexOf('pages.css'),
      `${name}: tokens must be declared before the sheet that consumes them`);
  }
});

test('app.css is gone', () => {
  assert.equal(fs.existsSync(path.join(WEB, 'app.css')), false,
    'app.css must be deleted, not left as dead weight in the APK');
});

test('pages.css is self-contained — it may not read a token only shell.css declares', () => {
  // login/export 不加载 shell.css。--flikky-shape-full / --flikky-pillar-radius /
  // --flikky-shell-pad 都只在 shell.css 里声明；引用它们会静默退化成无圆角、无留白，
  // 而不是报错 —— 正是那种「测试全绿但页面是坏的」。
  const shell = read('shell.css');
  const declaredHere = new Set([...pages.matchAll(/^\s*(--flikky-[a-z0-9-]+):/gm)].map((m) => m[1]));
  const declaredInTokens = new Set([...tokens.matchAll(/^\s*(--flikky-[a-z0-9-]+):/gm)].map((m) => m[1]));
  const declaredInBase = new Set([...read('base.css').matchAll(/^\s*(--flikky-[a-z0-9-]+):/gm)].map((m) => m[1]));
  const declaredInSprings = new Set([...read('springs.css').matchAll(/^\s*(--flikky-[a-z0-9-]+):/gm)].map((m) => m[1]));
  const shellOnly = new Set(
    [...shell.matchAll(/^\s*(--flikky-[a-z0-9-]+):/gm)].map((m) => m[1])
      .filter((v) => !declaredInTokens.has(v) && !declaredInBase.has(v) && !declaredInSprings.has(v)),
  );
  const leaked = [...new Set([...pages.matchAll(/var\((--flikky-[a-z0-9-]+)/g)].map((m) => m[1]))]
    .filter((v) => shellOnly.has(v) && !declaredHere.has(v));
  assert.deepEqual(leaked, [], `pages.css reads shell.css-only tokens: ${leaked.join(', ')}`);
});

test('the login page is a segmented PIN over one real OTP input', () => {
  const cells = login.match(/class="fk-pin-cell"/g) || [];
  assert.equal(cells.length, 6);
  assert.match(login, /id="pin-input"/);
  assert.match(login, /autocomplete="one-time-code"/);
  assert.match(login, /inputmode="numeric"/);
});

test('the hidden OTP input stays focusable', () => {
  // display:none / visibility:hidden 会让它拿不到焦点，分段格子就永远填不进字符。
  const rule = pages.match(/\.fk-pin-input\s*\{[^}]*\}/);
  assert.ok(rule, 'no .fk-pin-input rule');
  assert.match(rule[0], /opacity:\s*0/);
  assert.equal(/display:\s*none|visibility:\s*hidden/.test(rule[0]), false);
});

test('the login page keeps the three ids the theme harness depends on', () => {
  for (const id of ['pin-form', 'pin-input', 'submit-btn']) {
    assert.match(login, new RegExp(`id="${id}"`), `login.html lost #${id}`);
  }
});

test('the login page has no redundant wordmark', () => {
  // logo 自己就是标识；再加一行「Flikky」是重复。
  assert.equal(/<h1/.test(login), false);
  // 登录页用 quick 版（2.7s）、其余页面用 slow 版（7s）—— 用户指定的分配。
  assert.match(login, /src="\/static\/flikky-logo-quick\.svg"/);
  assert.match(exportHtml, /src="\/static\/flikky-logo-slow\.svg"/);
  assert.match(read('app.html'), /src="\/static\/flikky-logo-slow\.svg"/);
});

test('the export page keeps every id export.js reaches for', () => {
  for (const id of ['summary', 'session-list', 'sessions-card', 'download-btn',
                    'export-hint', 'export-banner', 'export-cancel-dialog']) {
    assert.match(exportHtml, new RegExp(`id="${id}"`), `export.html lost #${id}`);
  }
});

test('the export session list keeps mdui-list-item', () => {
  // export.js:108-120 用 headline/description 属性建行；换成自研行就得改 export.js，
  // 而 export.js 被 export-theme-sync.test.js 按标记切片 —— 不值得为视觉动它。
  assert.match(exportHtml, /<mdui-list id="session-list">/);
});

test('no element export.js overwrites with textContent may ship with child markup', () => {
  // export.js 对 #download-btn / #export-banner / #summary 都是整体赋 textContent。
  // 原型在按钮和横幅里各放了一个图标 <span> —— 那个图标会在第一次状态更新时
  // 被静默抹掉，页面从此少一个图标，而没有任何测试会注意到。
  const overwritten = [...exportJs.matchAll(/(\w+)\.textContent\s*=/g)].map((m) => m[1]);
  assert.ok(overwritten.includes('btn') && overwritten.includes('bannerEl'),
    'export.js no longer assigns textContent where this test assumes it does');
  for (const id of ['download-btn', 'export-banner', 'summary']) {
    const el = exportHtml.match(new RegExp(`<([a-z-]+)[^>]*id="${id}"[^>]*>([\\s\\S]*?)</\\1>`));
    assert.ok(el, `could not locate #${id} in export.html`);
    assert.equal(/<[a-z]/.test(el[2]), false,
      `#${id} ships child elements that export.js's textContent assignment will destroy`);
  }
});

test('neither page carries inline script or inline style', () => {
  for (const [name, page] of [['login.html', login], ['export.html', exportHtml]]) {
    const inlineScript = (page.match(/<script(?![^>]*\bsrc=)[^>]*>/g) || []);
    assert.deepEqual(inlineScript, [], `${name} has inline <script> — CSP script-src 'self'`);
    const inlineStyle = (page.match(/\sstyle="/g) || []);
    assert.deepEqual(inlineStyle, [], `${name} has ${inlineStyle.length} inline style attrs — move them to pages.css`);
  }
});

test('login.js drives the segmented cells and writes errors through textContent', () => {
  // 换掉 mdui-text-field 后 error/helper 这两个属性不再存在；继续写它们
  // 只会在一个普通 <input> 上挂两个没人读的 expando，错误提示永远不显示。
  assert.equal(/pinField\.(error|helper)\s*=/.test(loginJs), false,
    'login.js still writes the mdui-text-field-only error/helper properties');
  assert.match(loginJs, /helperEl\.textContent\s*=/);
  assert.match(loginJs, /pinGroup\.dataset\.error\s*=/);
  assert.match(loginJs, /dataset\.filled\s*=/);
  assert.equal(/innerHTML/.test(loginJs), false);
});

// ---------------------------------------------------------------------------
// v1.19.0 验收轮修复
// ---------------------------------------------------------------------------

test('the standalone pages primary button has the state layer the chat page has', () => {
  // 会话页的按钮早就有 hover 8% / pressed 12% 这层，这两页当初漏了，
  // 所以按下去只有圆角形变、没有明暗反馈。
  const hover = pages.match(/\.fk-btn:hover:not\(:disabled\)\s*\{[^}]*\}/);
  assert.ok(hover, 'no hover state layer on .fk-btn');
  assert.match(hover[0], /box-shadow:\s*inset [^;]*\.08\)/);
  const active = pages.match(/\.fk-btn:active:not\(:disabled\)\s*\{[^}]*box-shadow[^}]*\}/);
  assert.ok(active, 'no pressed state layer on .fk-btn');
  assert.match(active[0], /\.12\)/);
  // 形变本身也还在
  assert.match(pages, /\.fk-btn:active:not\(:disabled\)\s*\{[^}]*border-radius:\s*14px/);
});

test('the export session rows carry a leading icon', () => {
  // 原型有，之前漏了。走 slot="icon" 而不是 mdui 的 icon 属性 —— 后者渲染
  // <mdui-icon>，依赖 mdui 自带的图标字体，而本项目打包的是 Material Symbols。
  assert.match(exportJs, /setAttribute\('slot', 'icon'\)/);
  assert.match(exportJs, /textContent = 'forum'/);
  assert.match(pages, /\.fk-list-lead\s*\{[^}]*\}/);
  // 不能用 #flikky-cookie9：那个 clipPath 只内联在 app.html 里，导出页引用不到。
  assert.equal(/flikky-cookie9/.test(pages), false);
  assert.equal(/flikky-cookie9/.test(exportHtml), false);
});
