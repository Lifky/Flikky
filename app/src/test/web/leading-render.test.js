const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const { createDocument } = require('./mini-dom');

const ROOT = path.resolve(__dirname, '../../../..');
const WEB = path.join(ROOT, 'app/src/main/assets/web');
const fixture = JSON.parse(fs.readFileSync(
  path.join(ROOT, 'app/src/test/resources/leading-types.json'),
  'utf8',
));
const generatedTypes = fs.readFileSync(path.join(WEB, 'leading-types.js'), 'utf8');
const leadingSource = fs.readFileSync(path.join(WEB, 'leading.js'), 'utf8');
const appSource = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');
const panelsSource = fs.readFileSync(path.join(WEB, 'panels.css'), 'utf8');
const appHtml = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const generatedCssPath = path.join(WEB, 'leading-types.css');
const generatedCss = fs.existsSync(generatedCssPath)
  ? fs.readFileSync(generatedCssPath, 'utf8')
  : '';

function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

function runtime() {
  const document = createDocument();
  const context = { document };
  context.globalThis = context;
  context.window = context;
  vm.createContext(context);
  vm.runInContext(generatedTypes, context, { filename: 'leading-types.js' });
  vm.runInContext(leadingSource, context, { filename: 'leading.js' });
  return { document, api: context.flikkyLeading };
}

test('peer leading visual changes CSS variables without rebuilding existing rows', () => {
  const { document, api } = runtime();
  const row = document.createElement('div');
  row.className = 'fk-item';
  document.body.appendChild(row);
  const createdBefore = document.created.length;

  assert.equal(typeof api.applyVisual, 'function');
  api.applyVisual({
    shape: 'flower',
    colors: { image: ['#123456', '#ABCDEF'] },
  });

  assert.equal(document.documentElement.style.getPropertyValue('--flikky-leading-clip'),
    'url(#flikky-shape-flower)');
  assert.equal(document.documentElement.style.getPropertyValue('--flikky-leading-bg-image'), '#123456');
  assert.equal(document.documentElement.style.getPropertyValue('--flikky-leading-fg-image'), '#ABCDEF');
  assert.equal(document.body.children[0], row, 'changing shape must keep the existing row node');
  assert.equal(document.created.length, createdBefore, 'changing shape must not create DOM nodes');
});

test('missing peer leading visual silently restores the compatible defaults', () => {
  const { document, api } = runtime();
  assert.doesNotThrow(() => api.applyVisual(null));
  assert.equal(document.documentElement.style.getPropertyValue('--flikky-leading-clip'),
    'url(#flikky-shape-cookie9Sided)');
  fixture.forEach((type) => {
    assert.equal(document.documentElement.style.getPropertyValue(`--flikky-leading-bg-${type.id}`), '');
    assert.equal(document.documentElement.style.getPropertyValue(`--flikky-leading-fg-${type.id}`), '');
  });
});

test('app applies leading visual whenever peer appearance is applied', () => {
  const source = stripComments(appSource);
  assert.match(source, /flikkyLeading\.applyVisual\(data\.leadingVisual\)/);
});

test('type colour selectors are generated for every catalogue row', () => {
  assert.match(appHtml, /<link rel="stylesheet" href="\/static\/leading-types\.css">/);
  fixture.forEach((type) => {
    assert.match(generatedCss, new RegExp(`data-leading-type="${type.id}"`), type.id);
    assert.ok(generatedCss.includes(`var(--flikky-leading-bg-${type.id},`), type.id);
    assert.ok(generatedCss.includes(`var(--flikky-leading-fg-${type.id},`), type.id);
  });
  assert.equal(/\[data-leading-type="/.test(stripComments(panelsSource)), false,
    'type-specific selectors belong to the generated stylesheet');
});
