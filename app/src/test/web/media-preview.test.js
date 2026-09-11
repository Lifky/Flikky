const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEB = path.join(__dirname, '../../main/assets/web');
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '../resources/media-preview.json'), 'utf8',
));
const appJs = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');
const start = appJs.indexOf('function mediaKind');
const end = appJs.indexOf('\n    // 操作集唯一事实源', start);
assert.ok(start >= 0 && end > start, 'mediaKind source slice must be present');
const mediaSlice = appJs.slice(start, end);

function mediaKind(mime) {
  const context = { result: null };
  vm.createContext(context);
  vm.runInContext(`${mediaSlice}\nglobalThis.result = mediaKind(${JSON.stringify(mime)});`, context);
  return context.result;
}

test('fixture media entries are the browser thumbnail catalogue', () => {
  for (const mime of fixture.thumbnailable) assert.ok(mediaKind(mime), mime);
  for (const mime of fixture.excluded) assert.equal(mediaKind(mime), null, mime);
  assert.equal(fixture.thumbnailable.length, 9);
  assert.equal(fixture.inlineable.length, 9);
  assert.equal(fixture.excluded.length, 3);
});

test('SVG remains a separately named security exclusion', () => {
  assert.ok(fixture.excluded.includes('image/svg+xml'));
  assert.equal(mediaKind('image/svg+xml'), null);
});
