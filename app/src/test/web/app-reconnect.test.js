const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody } = require('./scan.js');
const source = fs.readFileSync(path.join(__dirname, '../../main/assets/web/app.js'), 'utf8');

function connection() {
  const sockets = [], events = [], changes = [], timers = [];
  const context = {
    currentWs: null, reconnectTimer: null, serverStopped: false, hadConnected: false,
    peerAppearanceRevision: 0,
    location: { protocol: 'http:', host: 'phone' },
    WebSocket: function () { this.readyState = 0; sockets.push(this); },
    setWebConnectionActive: v => changes.push(v),
    setConnectionWatermarkState() {}, setConn() {}, setSendEnabled() {}, startHeartbeat() {},
    sendClientHello() {}, fetchPeerInfo: async () => {}, loadHistory: async () => {},
    hideConnectionDialog() {}, clearTimeout() {}, window: {},
    setTimeout: (fn, ms) => { timers.push({ fn, ms }); return timers.length; },
    enterDisconnected: () => changes.push(false),
    onWsEvent: event => events.push(event),
  };
  vm.createContext(context);
  vm.runInContext(functionBody(source, 'function openWs('), context);
  return { context, sockets, events, changes, timers };
}

test('old socket events cannot revive or stop its replacement', () => {
  const c = connection();
  c.context.openWs();
  const old = c.sockets[0];
  c.context.currentWs = null;
  c.context.openWs();
  c.sockets[1].onopen();
  old.onopen();
  old.onmessage({ data: JSON.stringify({ type: 'server_stopped' }) });
  assert.deepEqual(c.changes, [true]);
  assert.deepEqual(c.events, []);
});

test('a stopped service cannot create another socket', () => {
  const c = connection();
  c.context.serverStopped = true;
  c.context.openWs();
  assert.equal(c.sockets.length, 0);
});

test('a socket stuck connecting times out into the bounded reconnect path', () => {
  const c = connection();
  c.context.openWs();
  assert.equal(c.timers.length, 1);
  assert.ok(c.timers[0].ms <= 5000);
  c.timers[0].fn();
  assert.deepEqual(c.changes, [false]);
});

test('new settings win over a peer-info response requested before the push', async () => {
  let resolve;
  const applied = [];
  const context = {
    peerAppearanceRevision: 0,
    fetch: () => new Promise(done => { resolve = done; }),
    applyPeerAppearance: data => applied.push(data), t: key => key,
  };
  vm.createContext(context);
  vm.runInContext(functionBody(source, 'async function fetchPeerInfo('), context);
  const pending = context.fetchPeerInfo();
  context.peerAppearanceRevision++;
  resolve({ ok: true, json: async () => ({ languageTag: 'zh-CN' }) });
  await pending;
  assert.deepEqual(applied, []);
});

test('reconnect reconciles offline completion and recall without deleting newer live messages', async () => {
  let resolve;
  const completed = [], removed = [];
  const nodes = new Map(['1', '2'].map(id => [id, {
    dataset: { messageId: id }, classList: { contains: name => name === 'transferring' },
  }]));
  const context = {
    currentWs: {}, serverStopped: false, seen: new Set(['file_added:1', 'text_added:2']),
    recalledMessageIds: new Set(),
    list: {
      querySelectorAll: () => [...nodes.values()],
      querySelector: selector => nodes.get(selector.match(/"(\d+)"/)[1]),
    },
    fetch: () => new Promise(done => { resolve = done; }),
    removeMessageNode: id => { removed.push(String(id)); nodes.delete(String(id)); },
    markBubbleCompleted: (node, dto) => completed.push(dto.id),
    markBubbleFailedNoRetry() {}, refreshSaveAllFab() {},
  };
  vm.createContext(context);
  const reconcile = source.includes('function reconcileHistorySnapshot(')
    ? functionBody(source, 'function reconcileHistorySnapshot(') : '';
  vm.runInContext(reconcile + '\n' + functionBody(source, 'async function loadHistory('), context);
  const pending = context.loadHistory();
  nodes.set('3', { dataset: { messageId: '3' } }); // WS delivery during the HTTP request.
  resolve({ ok: true, json: async () => ({ ordered: [{ kind: 'file', id: 1, status: 'COMPLETED' }] }) });
  await pending;
  assert.deepEqual(completed, [1]);
  assert.deepEqual(removed, ['2']);
  assert.ok(nodes.has('3'));
});
