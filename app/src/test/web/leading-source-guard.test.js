const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');

function withoutCommentsAndImports(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*@import\s+[^;]+;\s*$/gm, '');
}

test('every CSS leading container reads the shared clip variable', () => {
  const violations = fs.readdirSync(WEB)
    .filter((name) => name.endsWith('.css'))
    .filter((name) => {
      const css = withoutCommentsAndImports(fs.readFileSync(path.join(WEB, name), 'utf8'));
      return /url\(#flikky-(?:cookie9|shape-)/.test(css);
    });
  assert.deepEqual(violations, []);
});

test('chat file leading records its type from the shared catalogue', () => {
  const app = withoutCommentsAndImports(fs.readFileSync(path.join(WEB, 'app.js'), 'utf8'));
  assert.match(
    app,
    /iconWrap\.dataset\.leadingType\s*=\s*[^;]*flikkyLeading[^;]*typeOf\(bubble\.dataset\.mime\)\.id/,
  );
});
