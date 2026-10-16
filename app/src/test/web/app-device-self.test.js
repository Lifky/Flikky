const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody } = require('./scan.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const source = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');
const html = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const selfSrc = fs.readFileSync(path.join(WEB, 'device-self.js'), 'utf8');

function deviceSelf() {
    const c = {};
    vm.createContext(c);
    vm.runInContext(selfSrc, c);
    return c.flikkyDeviceSelf;
}

function socketHarness() {
    const sockets = [], dialogs = [], timers = [];
    const context = {
        currentWs: null, reconnectTimer: null, serverStopped: false, deviceRevoked: false, hadConnected: true,
        reconnectAttempts: 0, MAX_RECONNECT_ATTEMPTS: 6,
        location: { protocol: 'http:', host: 'phone' },
        WebSocket: function () { this.readyState = 0; sockets.push(this); },
        deviceSelf: deviceSelf(),
        setWebConnectionActive() {}, setConnectionWatermarkState() {}, setConn() {}, setSendEnabled() {},
        startHeartbeat() {}, stopHeartbeat() {}, sendClientHello() {}, fetchPeerInfo: async () => {},
        loadHistory: async () => {}, hideConnectionDialog() {}, clearTimeout() {}, window: {},
        setTimeout: (fn, ms) => { timers.push({ fn, ms }); return timers.length; },
        enterDisconnected() {}, onWsEvent() {},
        showConnectionDialog: (kind, key) => dialogs.push([kind, key]),
    };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'function openWs('), context);
    return { context, sockets, dialogs, timers };
}

test('a socket closed as revoked shows the disconnect dialog and never reconnects', () => {
    const h = socketHarness();
    h.context.openWs();
    h.sockets[0].onclose({ code: 4403 });
    assert.deepEqual(h.dialogs, [['revoked', 'app.device.revoked']]);
    assert.equal(h.timers.filter((t) => t.ms === 1500).length, 0);
    h.context.openWs();
    assert.equal(h.sockets.length, 1, 'a revoked device must not open another socket');
});

test('a socket closed as unauthorized says the sign-in expired', () => {
    const h = socketHarness();
    h.context.openWs();
    h.sockets[0].onclose({ code: 4401 });
    assert.deepEqual(h.dialogs, [['unauthorized', 'app.device.unauthorized']]);
});

test('an ordinary close still takes the bounded reconnect path', () => {
    const h = socketHarness();
    h.context.openWs();
    h.sockets[0].onclose({ code: 1006 });
    assert.equal(h.timers.filter((t) => t.ms === 1500).length, 1);
});

test('device events are routed to the device handlers', () => {
    const calls = [];
    const context = {
        handlePong() {}, applySelf: (p) => calls.push(['self', p]), onDeviceRevoked: () => calls.push(['revoked']),
        seen: new Set(), myClientId: 'me',
    };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'function onWsEvent('), context);
    context.onWsEvent({ type: 'device_updated', payload: { deviceId: 'd' } });
    context.onWsEvent({ type: 'device_revoked', payload: {} });
    assert.deepEqual(calls, [['self', { deviceId: 'd' }], ['revoked']]);
});

test('the reconnect button appears only for revoked or expired devices', () => {
    const button = { hidden: true };
    const icon = { dataset: { icon: 'signal_disconnected' } };
    const context = {
        connectionDialog: { dataset: {}, open: false },
        connectionDialogMessage: { textContent: '' },
        connectionDialogReconnect: button,
        connectionDialogIcon: icon,
        connectionDialogState: null,
        t: (k) => k,
    };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'function showConnectionDialog('), context);
    context.showConnectionDialog('reconnecting', 'app.reconnecting_attempt');
    assert.equal(button.hidden, true);
    assert.equal(icon.dataset.icon, 'signal_disconnected');
    context.showConnectionDialog('revoked', 'app.device.revoked');
    assert.equal(button.hidden, false);
    assert.equal(icon.dataset.icon, 'link_off');
});

test('app.html loads device-self.js before app.js and has the name dialog', () => {
    const selfAt = html.indexOf('/static/device-self.js');
    assert.notEqual(selfAt, -1);
    assert.ok(selfAt < html.indexOf('/static/app.js'));
    assert.match(html, /<mdui-dialog id="device-name-dialog"/);
    assert.match(html, /id="device-name-input"[^>]*maxlength="20"/);
    assert.match(html, /id="device-name-input"[^>]*\bcounter\b/);
    assert.match(html, /id="connection-dialog-reconnect"/);
});
