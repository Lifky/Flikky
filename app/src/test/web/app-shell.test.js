const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const html = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const shell = fs.readFileSync(path.join(WEB, 'shell.css'), 'utf8');
const base = fs.readFileSync(path.join(WEB, 'base.css'), 'utf8');
const tokens = fs.readFileSync(path.join(WEB, 'tokens.css'), 'utf8');
const springs = fs.readFileSync(path.join(WEB, 'springs.css'), 'utf8');
const appJsSource = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');
const panelSettingsSource = fs.readFileSync(path.join(WEB, 'panel-settings.js'), 'utf8');

test('shell carries the four layout state attributes', () => {
  const m = html.match(/<div id="shell"[^>]*>/);
  assert.ok(m, 'no #shell element');
  for (const attr of ['data-rail-side', 'data-swap', 'data-panel', 'data-mobile-dest']) {
    assert.match(m[0], new RegExp(attr), attr);
  }
});

test('chat-list-shell still directly wraps #list', () => {
  // 钉的是「shell 直接包着 #list」这个父子关系，不是 class 属性的字面量 ——
  // #list 后来还要挂 flikky-scroll（滚动条显隐）之类的类。
  assert.match(html, /<div id="chat-list-shell" class="chat-list-shell">\s*<div id="list" class="[^"]*\bchat-list\b[^"]*"><\/div>/);
});

test('panels round only their top corners and sit flush to the bottom edge', () => {
  const rule = shell.match(/\.fk-pillar\s*\{[^}]*\}/)[0];
  assert.match(rule, /border-radius:\s*var\(--flikky-shape-xl-increased\)\s+var\(--flikky-shape-xl-increased\)\s+0\s+0/);
});

test('nothing in the shell casts a shadow', () => {
  const shadows = [...shell.matchAll(/box-shadow:\s*([^;]+);/g)]
    .map((m) => m[1].trim())
    .filter((v) => v !== 'none');
  assert.deepEqual(shadows, [], `unexpected shadows: ${shadows.join(' | ')}`);
});

test('rail is not a card: no background, no radius', () => {
  const rule = shell.match(/\.fk-pillar--rail\s*\{[^}]*\}/)[0];
  assert.match(rule, /background:\s*none/);
  assert.match(rule, /border-radius:\s*0/);
  assert.match(rule, /justify-content:\s*center/);
});

test('rail-side padding is mirrored so both gutters read equal', () => {
  assert.match(shell, /\.fk-shell\[data-rail-side="right"\]\s*\{[^}]*padding:/);
});

test('desktop rail has no chat destination', () => {
  const rail = html.match(/<nav class="fk-pillar fk-pillar--rail"[\s\S]*?<\/nav>/)[0];
  assert.equal(false, /data-dest="chat"/.test(rail));
});

test('collapsing the panel is what the toggle does — the rail is never hidden', () => {
  assert.match(shell, /\.fk-shell\[data-panel="hidden"\]\s+\.fk-pillar--panel/);
  assert.equal(false, /data-rail="hidden"/.test(shell));
});

test('mobile drops the rail, the splitter and the collapse button', () => {
  const mq = shell.match(/@media \(max-width:\s*839px\)\s*\{[\s\S]*?\n\}/)[0];
  assert.match(mq, /\.fk-pillar--rail[\s\S]*display:\s*none/);
  assert.match(mq, /\.fk-panel-collapse\s*\{\s*display:\s*none/);
});

test('the mobile navbar display override comes after its base rule', () => {
  // Two independent lookups instead of lastIndexOf on a short substring — ".fk-navbar {"
  // is a literal prefix of the override rule too, so a single shared search string can
  // resolve to the wrong occurrence. Matching each rule's own shape and comparing
  // match().index keeps this meaningful without constraining shell.css's formatting.
  const baseMatch = shell.match(/\.fk-navbar\s*\{[^}]*display:\s*none[^}]*\}/);
  assert.ok(baseMatch, 'no base .fk-navbar rule with display: none');
  const overrideMatch = shell.match(/@media \(max-width:\s*839px\)\s*\{\s*\.fk-navbar\s*\{\s*display:\s*flex/);
  assert.ok(overrideMatch, 'no mobile .fk-navbar display:flex override inside the 839px media query');
  assert.ok(overrideMatch.index > baseMatch.index,
    'navbar display:flex override must come after the base rule by source order');
});

test('scrollbars are hidden until hover', () => {
  assert.match(shell, /\.flikky-scroll\b/);
  assert.match(shell, /scrollbar-color:\s*transparent transparent/);
  assert.match(shell, /\.flikky-scroll:hover[\s\S]*scrollbar-color:/);
});

test('shell geometry tokens are defined where the shell lives', () => {
  for (const [name, value] of [
    ['--flikky-shell-pad', '20px'],
    ['--flikky-shell-gap', '12px'],
    ['--flikky-splitter-w', '16px'],
    ['--flikky-rail-w', '96px'],
    ['--flikky-navbar-h', '64px'],
    ['--flikky-indicator-w', '56px'],
    ['--flikky-indicator-h', '32px'],
  ]) {
    assert.match(shell, new RegExp(`${name}:\\s*${value}`), `${name} must be defined in shell.css`);
  }
  assert.match(shell, /--flikky-pillar-radius:\s*var\(--flikky-shape-xl-increased\)/);
});

test('every --flikky- variable the shell uses is defined somewhere', () => {
  const defined = new Set([
    ...[...base.matchAll(/(--flikky-[a-z0-9-]+)\s*:/g)].map((m) => m[1]),
    ...[...shell.matchAll(/(--flikky-[a-z0-9-]+)\s*:/g)].map((m) => m[1]),
    ...[...tokens.matchAll(/(--flikky-[a-z0-9-]+)\s*:/g)].map((m) => m[1]),
    ...[...springs.matchAll(/(--flikky-[a-z0-9-]+)\s*:/g)].map((m) => m[1]),
  ]);
  const used = [...shell.matchAll(/var\((--flikky-[a-z0-9-]+)/g)].map((m) => m[1]);
  const missing = [...new Set(used)].filter((v) => !defined.has(v));
  assert.deepEqual(missing, [], `undefined CSS variables: ${missing.join(', ')}`);
});

// v1.19.0 items 4/5/8/10 each add or remove <link>/<script> tags; nothing before this
// caught a page referencing a static file that doesn't exist on disk (see app.css
// being dropped from app.html's <head> in the Task 3 first pass). This only checks
// "everything referenced exists" — not the converse — because several later tasks
// legitimately land a file a commit before wiring it up.
test('every /static/ href and src referenced by a page exists on disk', () => {
  const pages = ['app.html', 'login.html', 'export.html'];
  for (const page of pages) {
    const source = fs.readFileSync(path.join(WEB, page), 'utf8');
    const refs = [
      ...[...source.matchAll(/href="\/static\/([^"]+\.css)"/g)].map((m) => m[1]),
      ...[...source.matchAll(/src="\/static\/([^"]+\.js)"/g)].map((m) => m[1]),
    ];
    for (const ref of refs) {
      const resolved = path.join(WEB, ref);
      assert.ok(fs.existsSync(resolved), `${page} references missing static file: /static/${ref}`);
    }
  }
});

test('collapse buttons are wired by one delegated handler, so panels only draw them', () => {
  // 每个面板一个「收起功能栏」按钮，而面板脚本在 app.js 之后加载、设置面板的按钮
  // 还是运行时建出来的 —— 用 querySelectorAll 在 init 时绑定会全部漏掉。委派监听器
  // 同时让 setPanel 保持 shell.dataset.panel 的唯一写入方。
  const appJs = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');
  assert.match(appJs, /closest\('\.fk-panel-collapse'\)\)\s*setPanel\(false\)/);

  // 两个面板都必须真的有这个按钮，否则委派监听器是死代码。
  assert.match(html, /class="fk-icon-btn fk-panel-collapse"/);
  const settingsJs = fs.readFileSync(path.join(WEB, 'panel-settings.js'), 'utf8');
  assert.match(settingsJs, /fk-icon-btn fk-panel-collapse/);
});

// ---------------------------------------------------------------------------
// v1.19.0 验收轮修复
// ---------------------------------------------------------------------------

test('the mobile collapse-button hide outranks the panels.css button rule', () => {
  // 这是同一个坑的第二次：媒体查询不增加特异性。
  // shell.css 里 .fk-panel-collapse { display: none } 与 panels.css 里
  // .fk-icon-btn { display: grid } 同为 0,1,0，而 panels.css 在 shell.css **之后**
  // 加载，靠源码顺序把它顶掉 —— 窄屏上按钮照常显示，点下去写的 data-panel="hidden"
  // 又被同一个媒体查询里的中和规则吃掉，于是「点了没反应」。
  const mobileBlocks = shell.match(/@media \(max-width:\s*839px\)\s*\{[\s\S]*?\n\}/g) || [];
  const mobile = mobileBlocks.join('\n');
  const hide = mobile.match(/([^\n{]*\.fk-panel-collapse[^\n{]*)\{\s*display:\s*none/);
  assert.ok(hide, 'no mobile hide rule for .fk-panel-collapse');
  const classCount = (hide[1].match(/\./g) || []).length;
  assert.ok(
    classCount >= 2,
    `the hide rule must carry at least two class selectors to outrank .fk-icon-btn, got: ${hide[1].trim()}`,
  );

  // 顺序前提也钉住：panels.css 在 shell.css 之后加载，正是上面为何需要 0,2,0。
  const sheets = [...html.matchAll(/href="\/static\/([a-z-]+\.css)"/g)].map((m) => m[1]);
  assert.ok(
    sheets.indexOf('panels.css') > sheets.indexOf('shell.css'),
    `sheet order changed (${sheets.join(' → ')}) — recheck why the compound selector is needed`,
  );
});

test('the rail avatar keeps its own size against chat.css .avatar-circle', () => {
  // app.js 的 renderAvatar 会给 #my-avatar-btn 加上 avatar-circle 类，而
  // chat.css 的 .avatar-circle（36px / display:flex）同特异性、后加载。
  const compound = shell.match(/\.fk-rail-avatar\.avatar-circle\s*\{[^}]*\}|\.fk-rail-avatar,\s*\n\.fk-rail-avatar\.avatar-circle\s*\{[^}]*\}/);
  assert.ok(compound, '.fk-rail-avatar must also be declared with .avatar-circle to win on specificity');
  assert.match(compound[0], /width:\s*48px/);
  // 字符头像的字号同理会被 .avatar-circle 的 18px 顶掉，必须写在这个复合选择器里。
  assert.match(compound[0], /font-size:\s*22px/);
});

test('the rail brand mark and avatar are sized for a 80dp rail', () => {
  const logo = shell.match(/\.fk-rail-logo\s*\{[^}]*\}/)[0];
  assert.match(logo, /width:\s*52px/);
  // margin-left 必须是宽度的一半取负 —— 它靠 left:50% + 负 margin 居中。
  assert.match(logo, /margin-left:\s*-26px/);
});

test('scrollbars appear while scrolling, not merely on hover', () => {
  // 只用 :hover 门控时，鼠标停在面板里不动滚动条也一直亮着；用滚轮快速掠过又可能
  // 根本没触发 hover。真正的触发条件是「正在滚动」，那是事件不是 CSS 状态。
  assert.match(shell, /\.flikky-scroll\[data-scrolling\][\s\S]{0,200}scrollbar-color:/);
  assert.match(shell, /\.flikky-scroll\[data-scrolling\]::-webkit-scrollbar-thumb/);
  // scroll 事件不冒泡，委派监听器必须挂在 capture 阶段，否则子元素的滚动收不到。
  assert.match(
    appJsSource,
    /document\.addEventListener\('scroll',[\s\S]{0,600}?\}, true\)/,
    'the scroll listener must be registered in the capture phase',
  );
  assert.match(appJsSource, /dataset\.scrolling = '1'/);
  // 聊天列表是最长的滚动容器，它必须也挂上这个类。
  assert.match(html, /<div id="list" class="[^"]*\bflikky-scroll\b/);
});

test('the two layout axes animate through a FLIP wrapper', () => {
  // rail 靠右 / 两栏对调都是切 flex order，而 order 不可插值 —— 直接写属性只会跳位。
  assert.match(appJsSource, /function animateShellLayout\(/);
  assert.match(appJsSource, /window\.flikky\.animateShellLayout = animateShellLayout/);
  const start = appJsSource.indexOf('function animateShellLayout(');
  const body = appJsSource.slice(start, appJsSource.indexOf('\n    }', start));
  assert.match(body, /getBoundingClientRect\(\)\.left/, 'FLIP needs the pre-mutation geometry');
  assert.match(body, /prefers-reduced-motion/);
  // 强制一次布局读取，否则设置 transform 与清除 transform 会被合并成无变化。
  assert.match(body, /void shell\.offsetWidth/);

  // 面板脚本必须经这个包装器改状态 —— 它拿不到「改之前」的几何。
  assert.match(panelSettingsSource, /animateLayout\(\(\) => \{ shell\.dataset\.railSide = next; \}\)/);
  assert.match(panelSettingsSource, /animateLayout\(\(\) => \{ shell\.dataset\.swap = next; \}\)/);
});
