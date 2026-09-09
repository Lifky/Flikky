const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '../../../..');
const fixture = JSON.parse(fs.readFileSync(
  path.join(ROOT, 'app/src/test/resources/leading-shapes.json'),
  'utf8',
));
const markup = fs.readFileSync(
  path.join(ROOT, 'app/src/main/assets/web/shapes.svg.html'),
  'utf8',
);
const appMarkup = fs.readFileSync(
  path.join(ROOT, 'app/src/main/assets/web/app.html'),
  'utf8',
);

function clipPaths(source = markup) {
  const clips = new Map();
  const pattern = /<clipPath\s+id="([^"]+)"\s+clipPathUnits="([^"]+)">\s*<path\s+d="([^"]+)"\s*\/>\s*<\/clipPath>/g;
  for (const match of source.matchAll(pattern)) {
    clips.set(match[1], { units: match[2], d: match[3] });
  }
  return clips;
}

function expectedPathNumbers(cubics) {
  return [
    cubics[0][0], cubics[0][1],
    ...cubics.flatMap((cubic) => cubic.slice(2)),
  ];
}

function actualPathNumbers(d) {
  return Array.from(d.matchAll(/-?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?/gi), (match) => Number(match[0]));
}

test('all selected shapes are emitted with objectBoundingBox units', () => {
  const clips = clipPaths();
  assert.equal(Object.keys(fixture.shapes).length, 25);
  for (const id of Object.keys(fixture.shapes)) {
    const clip = clips.get(`flikky-shape-${id}`);
    assert.ok(clip, `missing flikky-shape-${id}`);
    assert.equal(clip.units, 'objectBoundingBox');
  }
});

test('every generated SVG path matches the shared cubic fixture', () => {
  const clips = clipPaths();
  for (const [id, shape] of Object.entries(fixture.shapes)) {
    const actual = actualPathNumbers(clips.get(`flikky-shape-${id}`).d);
    const expected = expectedPathNumbers(shape.cubics);
    assert.equal(actual.length, expected.length, `${id} path coordinate count`);
    expected.forEach((value, index) => {
      assert.ok(Math.abs(value - actual[index]) <= 1e-4, `${id} coordinate ${index}`);
    });
  }
});

test('legacy cookie9 id remains an exact alias of the official cookie9 path', () => {
  const clips = clipPaths();
  const legacy = clips.get('flikky-cookie9');
  const current = clips.get('flikky-shape-cookie9Sided');
  assert.ok(legacy, 'missing legacy flikky-cookie9 alias');
  assert.ok(current, 'missing official cookie9 path');
  assert.equal(legacy.units, 'objectBoundingBox');
  assert.equal(legacy.d, current.d);
  assert.equal(clips.size, 26);
});

test('the served app page embeds every generated leading shape verbatim', () => {
  const generated = clipPaths();
  const served = clipPaths(appMarkup);
  assert.equal(generated.size, 26, 'generated fragment must contain 25 shapes plus the legacy alias');
  assert.equal(served.size, generated.size, 'app.html does not serve every generated clipPath');
  for (const [id, expected] of generated) {
    assert.deepEqual(served.get(id), expected, `${id} differs between shapes.svg.html and app.html`);
  }
});
