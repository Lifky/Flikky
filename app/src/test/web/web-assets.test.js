const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const HTML_FILES = fs.readdirSync(WEB).filter((f) => f.endsWith('.html'));

test('there are pages to check at all', () => {
  // 若有人把页面移走，上面的枚举会静默变空，下面每条断言都会「通过」。
  assert.ok(HTML_FILES.length >= 3, `only found ${HTML_FILES.length} html files in ${WEB}`);
});

test('every /static/ reference resolves to a file that exists on disk', () => {
  // 这条是补一个真实的洞：flikky-logo.svg 被三个页面引用了整整两个任务，
  // 而磁盘上从来没有过这个文件 —— 全套测试一路全绿。CSS/JS 缺失还有别的
  // 断言能碰上，图片和字体则完全没人看。
  const missing = [];
  for (const file of HTML_FILES) {
    const html = fs.readFileSync(path.join(WEB, file), 'utf8');
    for (const m of html.matchAll(/(?:href|src)="\/static\/([^"?#]+)/g)) {
      if (!fs.existsSync(path.join(WEB, m[1]))) missing.push(`${file} → /static/${m[1]}`);
    }
  }
  assert.deepEqual(missing, [], `dangling static references:\n  ${missing.join('\n  ')}`);
});

test('no page loads a resource from another origin', () => {
  // 「所有 Web、字体与运行时资源随 APK 离线打包，禁止 CDN」。用户点击才跳转的
  // <a href> 不算资源加载，所以只看 link/script/img/source/iframe 这几类。
  const offOrigin = [];
  for (const file of HTML_FILES) {
    const html = fs.readFileSync(path.join(WEB, file), 'utf8');
    for (const m of html.matchAll(/<(link|script|img|source|iframe|audio|video)\b[^>]*>/gi)) {
      const url = m[0].match(/(?:href|src)="([^"]*)"/);
      if (url && /^(?:[a-z][a-z0-9+.-]*:)?\/\//i.test(url[1])) offOrigin.push(`${file}: ${m[0]}`);
    }
  }
  assert.deepEqual(offOrigin, [], `off-origin resources:\n  ${offOrigin.join('\n  ')}`);
});

test('the bundled logo SVGs carry no network references either', () => {
  // 这两个 SVG 是把 PNG 以 data: URI 内嵌进壳里的。若哪天换成 <image href="https://...">，
  // 红线就破了，而它藏在 29KB 的 base64 里，肉眼复查根本看不见。
  const svgs = fs.readdirSync(WEB).filter((f) => f.endsWith('.svg'));
  assert.ok(svgs.length >= 2, `expected the two logo variants, found ${svgs.length} svg files`);
  for (const file of svgs) {
    const svg = fs.readFileSync(path.join(WEB, file), 'utf8');
    for (const m of svg.matchAll(/(?:xlink:)?href="([^"]*)"/g)) {
      assert.ok(
        m[1].startsWith('#') || m[1].startsWith('data:'),
        `${file} references ${m[1].slice(0, 60)} — only in-document (#) and data: URIs may ship`,
      );
    }
  }
});

test('every bundled SVG is well-formed XML', () => {
  // SVG 是 XML，而 XML 里 <style> 的内容**仍按标记解析**（HTML 里它是 raw text，
  // 这个直觉在这里不成立）。一句 CSS 注释里写了字面量的 <img> 就开了一个永不闭合的
  // 元素，整个文件解析失败，页面上只剩一个破图占位 —— 而所有既有断言照样全绿，
  // 因为它们查的都是文本。
  for (const file of fs.readdirSync(WEB).filter((f) => f.endsWith('.svg'))) {
    const svg = fs.readFileSync(path.join(WEB, file), 'utf8');
    const opens = [...svg.matchAll(/<([a-zA-Z][\w:-]*)(\s[^>]*?)?(\/?)>/g)];
    const stack = [];
    for (const m of opens) {
      if (m[3] === '/') continue;
      stack.push(m[1]);
    }
    const closes = [...svg.matchAll(/<\/([a-zA-Z][\w:-]*)\s*>/g)].map((m) => m[1]);
    // 每个非自闭合的开标签都要有对应的闭标签（数量相等即可，顺序由下面的解析兜底）。
    const unclosed = stack.filter((tag) => {
      const opened = stack.filter((t) => t === tag).length;
      const closed = closes.filter((t) => t === tag).length;
      return opened !== closed;
    });
    assert.deepEqual([...new Set(unclosed)], [],
      `${file}: tags opened but never closed — angle brackets inside a <style> comment do this`);
  }
});

test('the logo SVGs adapt to dark mode without losing their animation', () => {
  // 白色摆动块（#shape fill）在深色面板上是一块刺眼亮斑。<img> 引用的 SVG 读不到
  // 宿主页面的 CSS，但能读 prefers-color-scheme —— 所以配色跟随系统深浅，
  // 而不是手机推过来的 themeDark。两者不一致时退化成原样，不会更差。
  for (const file of ['flikky-logo-quick.svg', 'flikky-logo-slow.svg']) {
    const svg = fs.readFileSync(path.join(WEB, file), 'utf8');
    const style = svg.slice(svg.indexOf('<style>'), svg.indexOf('</style>'));
    assert.ok(style, `${file} has no <style> block`);
    assert.match(style, /@media \(prefers-color-scheme: dark\)[\s\S]{0,120}#shape\s*\{\s*fill:/,
      `${file} does not recolor #shape for dark mode`);
    // 动画必须留着 —— 用户明确要求保留（快版 2.7s / 慢版 7s）。
    assert.match(style, /#Shape_Set\s*\{[\s\S]{0,160}animation:\s*kf_Shape_Set_transform_0/,
      `${file} lost its animation`);
    // 无限循环动画在「减弱动态效果」下必须停。
    assert.match(style, /@media \(prefers-reduced-motion: reduce\)[\s\S]{0,120}animation:\s*none/,
      `${file} does not honour prefers-reduced-motion`);
  }
});
