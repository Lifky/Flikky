const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/assets/web/i18n.js'), 'utf8');
const tick = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
function load(fetchImpl) {
  const timers = new Map();
  const events = new Map();
  const calls = [];
  let timerId = 0;
  const document = {
    hidden: false, documentElement: { setAttribute() {} }, querySelectorAll: () => [],
    addEventListener: (name, fn) => events.set(name, fn),
  };
  const context = {
    window: {}, document, AbortController,
    fetch: (url, options) => {
      calls.push({ url, options });
      return fetchImpl ? fetchImpl(url, options) : Promise.resolve({ ok: true, json: async () => ({ languageTag: 'en' }) });
    },
    setTimeout: (fn, ms) => { timers.set(++timerId, { fn, ms }); return timerId; },
    clearTimeout: (id) => timers.delete(id),
    setInterval: (fn, ms) => { timers.set(++timerId, { fn, ms }); return timerId; },
    clearInterval: (id) => timers.delete(id),
  };
  vm.runInNewContext(source, context);
  return { api: context.window.flikkyI18n, document, calls, timers, events };
}

test('disconnect aborts the request and stops all language polling until reconnection', async () => {
  let resolve;
  const c = load(() => new Promise((done) => { resolve = done; }));
  assert.equal(typeof c.api.setConnected, 'function');
  c.api.setConnected(false);
  assert.equal(c.calls[0].options.signal.aborted, true);
  resolve({ ok: true, json: async () => ({ languageTag: 'en' }) });
  await tick();
  assert.equal(c.api.language, 'zh-CN', 'a late response must not update a stopped session');
  assert.equal(c.timers.size, 0);
  await c.api.refresh();
  assert.equal(c.calls.length, 1);
  c.api.setConnected(true);
  assert.equal(c.calls.length, 2);
});

test('visible connected pages poll gently, hidden pages stop, and updates never overlap', async () => {
  const c = load();
  c.api.refresh();
  assert.equal(c.calls.length, 1);
  await tick();
  assert.equal(c.api.language, 'en');
  assert.equal(c.timers.size, 1);
  assert.ok([...c.timers.values()][0].ms >= 30000);
  c.document.hidden = true;
  c.events.get('visibilitychange')();
  assert.equal(c.timers.size, 0);
  c.document.hidden = false;
  c.events.get('visibilitychange')();
  await tick();
  assert.equal(c.calls.length, 2);
  c.api.setConnected(false);
  c.events.get('visibilitychange')();
  assert.equal(c.calls.length, 2);
});

test('an unreachable phone stops polling instead of retrying forever', async () => {
  const c = load(() => Promise.reject(new Error('offline')));
  await tick();
  assert.equal(c.timers.size, 0);
});

test('pushed language is immediate and a pending poll cannot revert it', async () => {
  let resolve;
  const c = load(() => new Promise(done => { resolve = done; }));
  c.api.applyServerLanguage('en');
  assert.equal(c.api.language, 'en');
  assert.equal(c.calls[0].options.signal.aborted, true);
  resolve({ ok: true, json: async () => ({ languageTag: 'zh-CN' }) });
  await tick();
  assert.equal(c.api.language, 'en');
  assert.equal(c.calls.length, 1, 'a push does not need another HTTP request');
  assert.equal(c.timers.size, 1);
  c.api.setConnected(false);
  c.api.applyServerLanguage('zh-CN');
  assert.equal(c.api.language, 'en', 'late events cannot revive a disconnected page');
  assert.equal(c.timers.size, 0);
});
