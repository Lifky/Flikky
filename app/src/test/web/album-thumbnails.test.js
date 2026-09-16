const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const file = path.join(__dirname, '../../main/assets/web/album-thumbnails.js');
const tick = async () => { for (let i = 0; i < 30; i++) await Promise.resolve(); };

function load(options = {}) {
  const calls = [];
  const context = { window: {}, AbortController, fetch: (url, init) => {
    calls.push({ url, init });
    return options.fetch ? options.fetch(url, init) : Promise.resolve({ ok: true, blob: async () => new Blob(['image']) });
  } };
  vm.runInNewContext(fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '', context);
  assert.equal(typeof context.window.createAlbumThumbnailCache, 'function');
  return { cache: context.window.createAlbumThumbnailCache(options), calls };
}

test('revisiting a thumbnail reuses its bytes and concurrent requests share one download', async () => {
  const { cache, calls } = load();
  const [first, second] = await Promise.all([cache.get('img:1'), cache.get('img:1')]);
  assert.equal(first, second);
  assert.equal(await cache.get('img:1'), first);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.cache, 'no-store');
  assert.equal(calls[0].init.credentials, 'same-origin');
});

test('cache evicts least recently used thumbnails at both byte and entry limits', async () => {
  for (const options of [{ maxBytes: 10 }, { maxEntries: 2 }]) {
    const { cache, calls } = load(options);
    await cache.get('a'); await cache.get('b'); await cache.get('a'); await cache.get('c');
    await cache.get('a');
    assert.equal(calls.length, 3, 'recently used thumbnail was evicted');
    await cache.get('b');
    assert.equal(calls.length, 4, 'old thumbnail was kept past the cache limit');
  }
});

test('disconnect aborts pending downloads and late responses cannot restore cleared bytes', async () => {
  let resolve;
  const { cache, calls } = load({ fetch: () => new Promise((r) => { resolve = r; }) });
  const pending = cache.get('a');
  cache.clear();
  assert.equal(calls[0].init.signal.aborted, true);
  resolve({ ok: true, blob: async () => new Blob(['old']) });
  assert.equal(await pending, null);
  const next = cache.get('a');
  assert.equal(calls.length, 2);
  resolve({ ok: true, blob: async () => new Blob(['new']) });
  assert.equal(await (await next).text(), 'new');
});

test('only six downloads run at once and offscreen queued images are skipped', async () => {
  const resolvers = [];
  let visible = true;
  const { cache, calls } = load({ fetch: () => new Promise((r) => resolvers.push(r)) });
  const pending = Array.from({ length: 20 }, (_, i) => cache.get(String(i), () => visible));
  assert.equal(calls.length, 6);
  visible = false;
  resolvers.forEach((resolve) => resolve({ ok: true, blob: async () => new Blob(['image']) }));
  await Promise.all(pending);
  await tick();
  assert.equal(calls.length, 6);
});
