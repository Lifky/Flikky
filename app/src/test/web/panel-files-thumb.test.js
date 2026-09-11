const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const LF = String.fromCharCode(10);
const leadingTypes = fs.readFileSync(path.join(WEB, 'leading-types.js'), 'utf8');
const leading = fs.readFileSync(path.join(WEB, 'leading.js'), 'utf8');
const panel = fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8');

const tick = async (n = 60) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };
const rows = (view) => byClass(view, 'fk-item');
const body = (view) => byClass(view, 'fk-panel-body')[0];
const lead = (row) => byClass(row, 'fk-item-lead')[0];
const images = (view) => byClass(view, 'fk-item-lead--thumb')
  .flatMap((wrap) => wrap.children.filter((child) => child.tagName === 'IMG'));
const icons = (node) => byClass(node, 'material-symbols-outlined');

function line(entry) { return JSON.stringify(entry); }

function listing(entries) {
  return [line({ path: '' }), ...entries.map(line), line({ done: true })].join(LF) + LF;
}

function load(entries, { viewport = 600 } = {}) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const requestedThumbs = [];
  const originalCreate = doc.createElement.bind(doc);
  doc.createElement = (tag) => {
    const el = originalCreate(tag);
    if (String(tag).toLowerCase() === 'img') {
      let value = '';
      Object.defineProperty(el, 'src', {
        configurable: true,
        get: () => value,
        set: (next) => {
          value = next === null || next === undefined ? '' : String(next);
          el.setAttribute('src', value);
          if (value) requestedThumbs.push(value);
        },
      });
    }
    return el;
  };
  const ctx = {
    console,
    document: doc,
    TextDecoder,
    requestAnimationFrame: (fn) => { fn(0); return 1; },
    fetch: () => Promise.resolve({
      ok: true,
      status: 200,
      body: {
        getReader: () => {
          let sent = false;
          return {
            read: () => {
              if (sent) return Promise.resolve({ value: undefined, done: true });
              sent = true;
              return Promise.resolve({ value: Buffer.from(listing(entries), 'utf8'), done: false });
            },
            cancel: () => {},
          };
        },
      },
    }),
    window: {
      flikkyPanels: {},
      flikky: {
        fileSymbolName: () => 'draft',
        formatSize: (bytes) => String(bytes) + 'B',
        mediaKind: (mime) => {
          const value = String(mime || '').toLowerCase();
          if (['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(value)) return 'image';
          if (['video/mp4', 'video/webm', 'video/3gpp', 'video/quicktime', 'video/x-matroska'].includes(value)) {
            return 'video';
          }
          return null;
        },
      },
      flikkyI18n: { t: (key) => key, onChange: () => {} },
    },
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(leadingTypes, ctx, { filename: 'leading-types.js' });
  vm.runInContext(leading, ctx, { filename: 'leading.js' });
  ctx.window.flikkyLeading = ctx.flikkyLeading;
  vm.runInContext(panel, ctx, { filename: 'panel-files.js' });
  const api = ctx.window.flikkyPanels.files;
  api.mount(view);
  body(view).clientHeight = viewport;
  api.setEnabled(true);
  return { doc, view, api, requestedThumbs };
}

const file = (name, mime) => ({
  name,
  isDir: false,
  size: 10,
  mtime: 0,
  mime,
  childCount: null,
  restricted: false,
});

test('storage rows request thumbnails only for media entries', async () => {
  const c = load([
    file('photo.jpg', 'image/jpeg'),
    file('scan.tiff', 'image/tiff'),
    file('document.pdf', 'application/pdf'),
    file('notes.txt', 'text/plain'),
  ]);
  await tick();
  assert.equal(c.requestedThumbs.length, 1, 'non-media rows must not create 404 thumbnail requests');
  assert.match(c.requestedThumbs[0], /^\/api\/storage\/thumb\?path=photo\.jpg$/);
  assert.equal(images(c.view).length, 1, 'the media row must contain one image in its existing leading slot');
  assert.equal(images(c.view)[0].getAttribute('alt'), 'photo.jpg');
});

test('an in-flight thumbnail cannot land on a row bound to another path', async () => {
  const c = load([file('first.jpg', 'image/jpeg')]);
  await tick();
  const img = images(c.view)[0];
  assert.ok(img, 'sanity: the first media row must have a thumbnail image');
  assert.equal(img.dataset.forPath, 'first.jpg');
  assert.equal(img.hidden, true, 'an in-flight image must not replace the type icon yet');

  // Model recycling the same image node for another row while its first request is pending.
  img.dataset.forPath = 'second.jpg';
  img.dispatch('load');
  assert.equal(img.hidden, true, 'the stale first image must not appear on the second row');
});

test('recycling a row clears the in-flight thumbnail source', async () => {
  const entries = [file('first.jpg', 'image/jpeg')];
  for (let i = 1; i < 40; i += 1) entries.push(file('note' + i + '.txt', 'text/plain'));
  const c = load(entries, { viewport: 64 });
  await tick();
  const img = images(c.view)[0];
  assert.ok(img && img.src, 'sanity: the first thumbnail request must be in flight');

  body(c.view).scrollTop = 2400;
  await tick(10);
  assert.equal(img.src, '', 'dropRow must explicitly disconnect the old image request');
  img.dispatch('load');
  assert.equal(img.dataset.thumbLoaded, undefined, 'a recycled image must have no landing point');
});

test('thumbnail errors restore the type icon instead of leaving an empty leading slot', async () => {
  const c = load([file('broken.jpg', 'image/jpeg')]);
  await tick();
  const row = rows(c.view)[0];
  const wrapper = lead(row);
  const img = images(c.view)[0];
  assert.ok(img, 'sanity: the media row must start with a thumbnail');
  img.dispatch('error');
  assert.equal(byClass(wrapper, 'fk-item-lead--thumb').length, 0);
  assert.equal(images(c.view).length, 0, 'the failed image must be removed');
  assert.ok(icons(wrapper).length > 0, 'the leading type icon must be restored');
});

test('thumbnail content keeps the fixed 8dp shape instead of the leading clip path', () => {
  const css = fs.readFileSync(path.join(WEB, 'panels.css'), 'utf8');
  const rule = css.match(/\.fk-item-lead--thumb\s*\{[^}]*\}/);
  assert.ok(rule, 'the thumbnail leading rule is missing');
  assert.match(rule[0], /clip-path:\s*none/);
  assert.match(rule[0], /border-radius:\s*var\(--flikky-shape-xs\)/);
});
