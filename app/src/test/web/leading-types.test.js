const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../../../..');
const webDir = path.join(root, 'app/src/main/assets/web');
const fixture = JSON.parse(fs.readFileSync(
  path.join(root, 'app/src/test/resources/leading-types.json'),
  'utf8',
));
const generated = fs.readFileSync(path.join(webDir, 'leading-types.js'), 'utf8');
const leading = fs.readFileSync(path.join(webDir, 'leading.js'), 'utf8');

function runtime() {
  const context = {};
  vm.createContext(context);
  vm.runInContext(generated, context, { filename: 'leading-types.js' });
  vm.runInContext(leading, context, { filename: 'leading.js' });
  return context.flikkyLeading;
}

test('typeOf reads every mime rule from the shared fixture', () => {
  const { typeOf } = runtime();
  for (const type of fixture) {
    for (const prefix of type.mimePrefixes) {
      assert.equal(typeOf(`${prefix}flikky-test`).id, type.id, prefix);
    }
    for (const mime of type.mimeExact) {
      assert.equal(typeOf(mime).id, type.id, mime);
    }
  }
});

test('typeOf preserves the svg exception and other fallback', () => {
  const { typeOf } = runtime();
  assert.equal(typeOf('image/svg+xml').symbol, 'draft');
  assert.equal(typeOf('IMAGE/SVG+XML').symbol, 'draft');
  assert.equal(typeOf('application/octet-stream').id, 'other');
});

test('generated type table has exactly one row per fixture entry', () => {
  const context = {};
  vm.createContext(context);
  vm.runInContext(generated, context, { filename: 'leading-types.js' });
  assert.equal(context.flikkyLeadingTypes.length, fixture.length);
  assert.deepEqual(
    Array.from(context.flikkyLeadingTypes, (entry) => entry.id),
    fixture.map((entry) => entry.id),
  );
});

test('the leading runtime loads before app.js', () => {
  const html = fs.readFileSync(path.join(webDir, 'app.html'), 'utf8');
  const order = ['leading-types.js', 'leading.js', 'app.js']
    .map((name) => html.indexOf(`/static/${name}`));
  assert.ok(order.every((index) => index >= 0));
  assert.deepEqual(order, [...order].sort((a, b) => a - b));
});
