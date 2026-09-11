const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const scan = require('./scan.js');
const { createDocument } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const app = read('app.js');
const start = app.indexOf('    // ---- lightbox fullscreen preview');
const end = app.indexOf('    // ---- three-pane shell', start);
assert.ok(start >= 0 && end > start, 'sanity: the existing lightbox source slice must be present');
const lightboxSource = app.slice(start, end);

function loadLightbox() {
  const doc = createDocument();
  const lightbox = doc.register('lightbox');
  lightbox.hidden = true;
  const content = doc.register('lightbox-content');
  content.querySelector = (selector) => content.children.find(
    (child) => child.tagName === String(selector).toUpperCase(),
  ) || null;
  lightbox.appendChild(content);
  lightbox.appendChild(doc.register('lightbox-close', 'button'));
  doc.removeEventListener = () => {};
  const preloads = [];
  function Image() {
    const image = doc.createElement('img');
    preloads.push(image);
    return image;
  }
  const ctx = { document: doc, Image };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(lightboxSource + '\nglobalThis.openLightboxForTest = openLightbox;', ctx);
  return { doc, lightbox, content, preloads, open: ctx.openLightboxForTest };
}

function trackSource(el) {
  const values = [];
  let value = '';
  Object.defineProperty(el, 'src', {
    configurable: true,
    get: () => value,
    set: (next) => {
      value = next === null || next === undefined ? '' : String(next);
      values.push(value);
    },
  });
  return values;
}

test('image lightbox shows the thumbnail until the full image has loaded', () => {
  const c = loadLightbox();
  const originalCreate = c.doc.createElement.bind(c.doc);
  const sourceHistories = [];
  c.doc.createElement = (tag) => {
    const el = originalCreate(tag);
    if (String(tag).toLowerCase() === 'img') sourceHistories.push(trackSource(el));
    return el;
  };
  c.open({ kind: 'image', fullUrl: '/full', thumbnailUrl: '/thumb' });
  const visible = c.content.children[0];
  assert.equal(visible.tagName, 'IMG');
  assert.deepEqual(sourceHistories[0], ['/thumb'], 'opening must paint the cached thumbnail immediately');
  assert.equal(c.preloads.length, 1, 'the original image loads off-screen');
  assert.equal(c.preloads[0].src, '/full');

  c.preloads[0].dispatch('load');
  assert.deepEqual(sourceHistories[0], ['/thumb', '/full']);
  assert.equal(sourceHistories[0].includes(''), false, 'the displayed image must never flash an empty source');
});

test('video lightbox uses the thumbnail as poster without image preloading', () => {
  const c = loadLightbox();
  c.open({ kind: 'video', fullUrl: '/movie', thumbnailUrl: '/poster' });
  const video = c.content.children[0];
  assert.equal(video.tagName, 'VIDEO');
  assert.equal(video.src, '/movie');
  assert.equal(video.poster, '/poster');
  assert.equal(c.preloads.length, 0);
});

test('session, storage and favorite callers provide their authenticated media urls', () => {
  const sources = {
    session: scan.scrub(app),
    storage: scan.scrub(read('panel-files.js')),
    favorite: scan.scrub(read('panel-favorites.js')),
  };
  assert.ok(sources.session.indexOf('openLightbox({') >= 0,
    'sanity: the session preview call must be present');
  assert.ok(sources.session.indexOf('`/api/files/${fileId}?inline=1`') >= 0);
  assert.ok(sources.session.indexOf('`/api/files/${fileId}/thumb`') >= 0);
  assert.ok(sources.storage.indexOf("downloadUrl(path) + '&inline=1'") >= 0);
  assert.ok(sources.storage.indexOf('thumbnailUrl(path)') >= 0);
  assert.ok(sources.favorite.indexOf('`/api/favorites/${item.id}/file?inline=1`') >= 0);
  assert.ok(sources.favorite.indexOf('`/api/favorites/${item.id}/thumb`') >= 0);
});

test('the existing lightbox remains the only full-screen preview implementation', () => {
  const scrubbedApp = scan.scrub(app);
  const panels = scan.scrub(read('panel-files.js') + '\n' + read('panel-favorites.js'));
  assert.equal(scrubbedApp.split('function openLightbox(').length - 1, 1,
    'there must be exactly one lightbox function');
  assert.ok(scan.scrub(lightboxSource).indexOf("createElement('video')") >= 0,
    'sanity: the guarded lightbox slice must contain its video renderer');
  assert.ok(scan.scrub(lightboxSource).indexOf("createElement('img')") >= 0,
    'sanity: the guarded lightbox slice must contain its image renderer');
  assert.equal(panels.indexOf("createElement('video')") >= 0, false,
    'panels must route into the shared lightbox instead of creating a preview');
  assert.equal(panels.indexOf("className = 'lightbox-media'") >= 0, false,
    'panels must not copy the full-screen preview structure');
});
