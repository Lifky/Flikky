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

// v1.23 审查（重新定级为 Important）：中文输入法里按回车是「上屏候选词」，不能顺手把半截名字提交出去。
test('enter while an IME is composing does not submit the name', () => {
    const listeners = {};
    const on = (el) => Object.assign(el, {
        addEventListener(type, fn) { (listeners[type] = listeners[type] || []).push(fn); },
        removeEventListener(type, fn) { listeners[type] = (listeners[type] || []).filter((f) => f !== fn); },
    });
    const input = on({ value: '' });
    const okBtn = { addEventListener() {}, removeEventListener() {} };
    const dialog = { open: false, querySelector: () => okBtn, addEventListener() {}, removeEventListener() {} };
    const puts = [];
    const context = {
        document: { getElementById: (id) => (id === 'device-name-dialog' ? dialog : input) },
        deviceSelf: deviceSelf(), mySelf: null, t: (k) => k,
        myDisplayName: () => 'Chrome · Windows', putSelf: (body) => puts.push(body),
    };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'function isImeComposing('), context);
    vm.runInContext(functionBody(source, 'function openDeviceNameDialog('), context);
    context.openDeviceNameDialog();
    input.value = '客厅';
    listeners.keydown.forEach((fn) => fn({ key: 'Enter', isComposing: true }));
    // Safari：compositionend 之后才派发 keydown，isComposing 已是 false，keyCode 仍是 229。
    listeners.keydown.forEach((fn) => fn({ key: 'Enter', isComposing: false, keyCode: 229 }));
    assert.deepEqual(puts, []);
    assert.equal(dialog.open, true);
    listeners.keydown.forEach((fn) => fn({ key: 'Enter', isComposing: false }));
    assert.deepEqual(JSON.parse(JSON.stringify(puts)), [{ name: '客厅' }]);
});

// 装机 Screenshot_1：outlined 文本框的浮动标签骑在边框上，超出组件顶边半个字高；
// mdui-dialog 正文槽会裁掉溢出部分，标签上半截就被切了。给输入框顶部留出标签的高度。
test('the name field leaves room above it for the floating label', () => {
    const css = fs.readFileSync(path.join(WEB, 'chat.css'), 'utf8');
    const rule = css.match(/#device-name-input\s*\{([^}]*)\}/);
    assert.ok(rule, 'chat.css must style #device-name-input');
    assert.match(rule[1], /margin-top\s*:/);
});

// v1.24（B59#1）：改名框用 Esc / 点遮罩关闭时也要清理监听器。之前只有「取消 / 确定」会清理，
// 下次打开再点确定，就会把上一次留下的回调一起触发，发出重复的 PUT。
test('closing the rename dialog any way leaves no stale listeners behind', () => {
    const make = () => {
        const listeners = {};
        return {
            listeners,
            addEventListener(type, fn) { (listeners[type] = listeners[type] || []).push(fn); },
            removeEventListener(type, fn) { listeners[type] = (listeners[type] || []).filter((f) => f !== fn); },
            fire(type, ev) { (listeners[type] || []).slice().forEach((fn) => fn(ev || {})); },
        };
    };
    const input = Object.assign(make(), { value: '' });
    const okBtn = make();
    const cancelBtn = make();
    const dialog = Object.assign(make(), {
        open: false,
        querySelector: (sel) => (sel.includes('confirm') ? okBtn : cancelBtn),
    });
    const puts = [];
    const context = {
        document: { getElementById: (id) => (id === 'device-name-dialog' ? dialog : input) },
        deviceSelf: deviceSelf(), mySelf: null, t: (k) => k,
        myDisplayName: () => 'Chrome', putSelf: (body) => puts.push(body),
    };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'function isImeComposing('), context);
    vm.runInContext(functionBody(source, 'function openDeviceNameDialog('), context);

    context.openDeviceNameDialog();
    // Esc / 点遮罩：mdui 直接关掉对话框并派发 closed，不经过我们的按钮。
    dialog.open = false;
    dialog.fire('closed', { target: dialog });
    context.openDeviceNameDialog();
    input.value = '客厅';
    okBtn.fire('click');
    assert.deepEqual(JSON.parse(JSON.stringify(puts)), [{ name: '客厅' }]);
    for (const el of [okBtn, cancelBtn, input, dialog]) {
        for (const [type, fns] of Object.entries(el.listeners)) assert.equal(fns.length, 0, `stale ${type} listener`);
    }
});
