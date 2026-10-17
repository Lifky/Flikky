const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody } = require('./scan.js');

// 多台浏览器同在一个会话（v1.23 装机反馈）：A 改头像 B 看不到、两台的消息都画在右边、气泡上没有名字。
// peers.js 管规则；这里守 app.js 有没有把规则接到渲染上。
const WEB = path.join(__dirname, '../../main/assets/web');
const source = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');
const html = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const peersSrc = fs.readFileSync(path.join(WEB, 'peers.js'), 'utf8');

function peers() {
    const c = {};
    vm.createContext(c);
    vm.runInContext(peersSrc, c);
    return c.flikkyPeers;
}

test('app.html loads peers.js before app.js', () => {
    const p = html.indexOf('/static/peers.js');
    assert.ok(p > 0, 'peers.js must be loaded');
    assert.ok(p < html.indexOf('/static/app.js'));
});

test('other devices resolve to their own avatar and name', () => {
    const fp = peers();
    const context = {
        peersLib: fp,
        peerRoster: fp.parseRoster({ devices: [
            { deviceId: 'dev-b', name: '书房', defaultLabel: null, defaultOrdinal: 1, avatarKey: 'emoji:🐱', online: true, present: true },
        ] }),
        myAvatarKey: 'icon:desktop_windows',
        phoneAvatarKey: 'icon:smartphone',
        phoneDisplayName: '我的手机',
        AVATAR_DEFAULT_BROWSER: 'icon:desktop_windows',
        t: (k) => (k === 'app.device.browser' ? '浏览器' : k),
    };
    vm.createContext(context);
    vm.runInContext(
        functionBody(source, 'function avatarKeyForSender(') + '\n' + functionBody(source, 'function senderDisplayName('),
        context,
    );
    assert.equal(context.avatarKeyForSender('dev-b'), 'emoji:🐱');
    assert.equal(context.avatarKeyForSender(fp.ME), 'icon:desktop_windows');
    assert.equal(context.avatarKeyForSender(fp.PHONE), 'icon:smartphone');
    assert.equal(context.avatarKeyForSender('dev-gone'), 'icon:desktop_windows');
    assert.equal(context.senderDisplayName('dev-b'), '书房');
    assert.equal(context.senderDisplayName(fp.PHONE), '我的手机');
    assert.equal(context.senderDisplayName('dev-gone'), '浏览器');
});

test('the roster event and the history response both teach the page who it is', () => {
    const onWs = functionBody(source, 'function onWsEvent(');
    assert.match(onWs, /ev\.type === 'peers'/);
    assert.match(onWs, /applyRoster\(ev\.payload\)/);
    const apply = functionBody(source, 'function applyRoster(');
    assert.match(apply, /selfDeviceId/);
    assert.match(apply, /reflowMessageAvatars\(\)/);
    const history = functionBody(source, 'function loadHistory(');
    assert.ok(history.indexOf('selfDeviceId') > 0, 'loadHistory must read selfDeviceId');
    assert.ok(history.indexOf('selfDeviceId') < history.indexOf('replayingHistory = true'),
        'it must be known before the history is replayed');
});

test('rows remember who sent them so they can be re-sided later', () => {
    const append = functionBody(source, 'function appendBubbleRow(');
    assert.match(append, /row\.dataset\.origin = /);
    assert.match(append, /row\.dataset\.senderDevice = /);
    const reflow = functionBody(source, 'function reflowMessageAvatars(');
    assert.match(reflow, /peersLib\.labelPlan\(/);
    assert.match(reflow, /applyRowSide\(/);
    assert.match(reflow, /bubble-sender/);
    assert.doesNotMatch(reflow, /innerHTML/);
});

test('the phone setting for sender names reaches the page', () => {
    const appearance = functionBody(source, 'function applyPeerAppearance(');
    assert.match(appearance, /senderNameMode/);
    assert.match(appearance, /document\.body\.dataset\.senderNames/);
});

test('a sender name is plain text', () => {
    const body = functionBody(source, 'function makeSenderLabel(');
    assert.match(body, /textContent = /);
    assert.doesNotMatch(body, /innerHTML/);
});
