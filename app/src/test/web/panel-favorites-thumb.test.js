const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const scan = require('./scan.js');
const { createDocument, byClass, MutationObserver } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const leadingTypes = read('leading-types.js');
const leading = read('leading.js');
const favorites = read('panel-favorites.js');

function buildSkeleton(doc) {
  const root = doc.register('view-favorites');
  root.appendChild(doc.register('fav-refresh', 'button'));
  root.appendChild(doc.register('fav-search', 'input'));
  root.appendChild(doc.register('fav-chips'));
  root.appendChild(doc.register('fav-list'));
  const toolbar = doc.register('fav-toolbar');
  toolbar.appendChild(doc.register('fav-count', 'span'));
  toolbar.appendChild(doc.register('fav-clear', 'button'));
  toolbar.appendChild(doc.register('fav-save-selected', 'button'));
  root.appendChild(toolbar);
  return root;
}

function load(items) {
  const doc = createDocument();
  const root = buildSkeleton(doc);
  const requestedThumbs = [];
  const previews = [];
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
    MutationObserver,
    Promise,
    setTimeout: (fn) => { fn(); return 1; },
    clearTimeout() {},
    navigator: {},
    fetch: () => Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ groups: [], items }),
    }),
    flikkyI18n: { t: (key) => key, onChange: (fn) => { fn(); return () => {}; } },
  };
  ctx.window = ctx;
  ctx.globalThis = ctx;
  ctx.flikkyPanels = {};
  ctx.flikky = {
    fileSymbolName: () => 'draft',
    mediaKind: (mime) => {
      const value = String(mime || '').toLowerCase();
      if (['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(value)) return 'image';
      if (['video/mp4', 'video/webm', 'video/3gpp', 'video/quicktime', 'video/x-matroska'].includes(value)) {
        return 'video';
      }
      return null;
    },
    openLightbox: (options) => { previews.push(options); },
  };
  vm.createContext(ctx);
  vm.runInContext(leadingTypes, ctx, { filename: 'leading-types.js' });
  vm.runInContext(leading, ctx, { filename: 'leading.js' });
  vm.runInContext(favorites, ctx, { filename: 'panel-favorites.js' });
  ctx.flikkyPanels.favorites.mount(root);
  return { doc, requestedThumbs, previews };
}

const flush = async (n = 12) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };
const file = (id, name, mime) => ({
  id,
  kind: 'FILE',
  text: null,
  fileName: name,
  fileSize: 10,
  mime,
  groupId: null,
  createdAt: id,
});

test('favorite rows request thumbnails only for media files', async () => {
  const c = load([
    file(7, 'photo.jpg', 'image/jpeg'),
    file(8, 'scan.tiff', 'image/tiff'),
    file(9, 'notes.pdf', 'application/pdf'),
    { id: 10, kind: 'TEXT', text: 'hello', fileName: null, fileSize: null,
      mime: null, groupId: null, createdAt: 10 },
  ]);
  await flush();
  assert.deepEqual(c.requestedThumbs, ['/api/favorites/7/thumb']);
  const images = c.doc.created.filter((el) => el.tagName === 'IMG');
  assert.equal(images.length, 1, 'only the allowlisted media favorite gets an image');
  assert.equal(images[0].getAttribute('alt'), 'photo.jpg');
});

test('favorite thumbnail errors restore the existing type icon', async () => {
  const c = load([file(7, 'broken.jpg', 'image/jpeg')]);
  await flush();
  const image = c.doc.created.find((el) => el.tagName === 'IMG');
  assert.ok(image, 'sanity: a media favorite must create a thumbnail image');
  const lead = image.parentNode;
  image.dispatch('error');
  assert.equal(byClass(lead, 'fk-item-lead--thumb').length, 0);
  assert.equal(lead.children.some((el) => el.tagName === 'IMG'), false);
  assert.ok(byClass(lead, 'material-symbols-outlined').length > 0,
    'the failed thumbnail must leave the original file-type icon');
});

test('clicking a favorite thumbnail previews it without selecting its row', async () => {
  const c = load([file(7, 'photo.jpg', 'image/jpeg')]);
  await flush();
  const image = c.doc.created.find((el) => el.tagName === 'IMG');
  const row = byClass(c.doc.getElementById('fav-list'), 'fk-item')[0];
  image.click();
  assert.deepEqual(JSON.parse(JSON.stringify(c.previews)), [{
    kind: 'image',
    fullUrl: '/api/favorites/7/file?inline=1',
    thumbnailUrl: '/api/favorites/7/thumb',
  }]);
  assert.equal(row.getAttribute('aria-selected'), 'false',
    'thumbnail click must not bubble into the row-selection action');
});

test('storage and favorites share the one thumbnail lifecycle implementation', () => {
  const shared = scan.scrub(read('leading.js'));
  const panels = [
    ['panel-files.js', scan.scrub(read('panel-files.js'))],
    ['panel-favorites.js', scan.scrub(read('panel-favorites.js'))],
  ];
  assert.ok(shared.indexOf('function attachThumbnail(') >= 0,
    'sanity: the shared thumbnail helper source slice must exist');
  assert.ok(shared.indexOf("createElement('img')") >= 0,
    'sanity: the shared helper must own the actual image creation');
  for (const [name, source] of panels) {
    assert.ok(source.indexOf('attachThumbnail(') >= 0,
      name + ' must call the shared thumbnail helper');
    assert.equal(source.indexOf("createElement('img')") >= 0, false,
      name + ' must not copy the image lifecycle');
    assert.equal(source.indexOf("classList.add('fk-item-lead--thumb')") >= 0, false,
      name + ' must not copy the thumbnail container wiring');
  }
});
