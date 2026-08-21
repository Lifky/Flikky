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
