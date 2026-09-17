const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody } = require('./scan.js');

const read = (name) => fs.readFileSync(path.join(__dirname, '../../main/assets/web', name + '.js'), 'utf8');

test('phone stop event immediately stops app language polling and album requests', () => {
  const changes = [];
  const source = read('app');
  const context = {
    peerAppearanceRevision: 0,
    i18n: { setConnected: (value) => changes.push(['language', value]) },
    window: { flikkyPanels: { album: { setConnected: (value) => changes.push(['album', value]) } } },
    stopHeartbeat() {}, setConnectionWatermarkState() {}, showConnectionDialog() {}, setSendEnabled() {},
  };
  vm.createContext(context);
  vm.runInContext(functionBody(source, 'function setWebConnectionActive(') + '\n' +
    functionBody(source, 'function onWsEvent('), context);
  context.onWsEvent({ type: 'server_stopped' });
  assert.deepEqual(changes, [['language', false], ['album', false]]);
});

test('export socket starts language sync and phone stop prevents later restart', () => {
  const changes = [];
  const sockets = [];
  const source = read('export');
  const context = {
    i18n: { setConnected: (value) => changes.push(value) },
    exportWs: null, exportServerStopped: false, downloadStarted: false, healthDisconnected: false,
    EXPORT_WS_PING_INTERVAL: 3000,
    location: { protocol: 'http:', host: 'phone' },
    WebSocket: function () { this.close = () => {}; sockets.push(this); },
    setInterval: () => 1, clearTimeout() {}, exportWsPingTimeout: null,
    stopExportWsPing() {}, stopHealthProbe() {}, disableDownloadWith() {}, hideExportBanner() {}, showCancelDialog() {},
  };
  vm.createContext(context);
  vm.runInContext(functionBody(source, 'function openExportWs('), context);
  context.openExportWs();
  sockets[0].onopen();
  sockets[0].onmessage({ data: JSON.stringify({ type: 'server_stopped' }) });
  context.openExportWs();
  sockets[0].onopen(); // A late event must not revive a stopped session.
  assert.deepEqual(changes, [true, false]);
  assert.equal(sockets.length, 1);
});
