const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// 多台浏览器同在一个会话（v1.23 装机反馈）：每台要认得「哪条是我发的、别的是谁发的」。
// 规则与手机端 ui/components/SenderLabel.kt 一致。
const SRC = fs.readFileSync(path.join(__dirname, '../../main/assets/web/peers.js'), 'utf8');

function load() {
    const context = {};
    vm.createContext(context);
    vm.runInContext(SRC, context);
    return context.flikkyPeers;
}

test('a message is mine only when this device sent it', () => {
    const p = load();
    assert.equal(p.senderKey('BROWSER', 'dev-me', 'dev-me'), p.ME);
    assert.equal(p.senderKey('BROWSER', 'dev-b', 'dev-me'), 'dev-b');
    assert.equal(p.senderKey('PHONE', null, 'dev-me'), p.PHONE);
});

test('without device information the old single-browser rule still applies', () => {
    const p = load();
    // v1.23 之前的消息没有发送设备；或者还不知道自己是哪台：照旧当作自己发的。
    assert.equal(p.senderKey('BROWSER', null, 'dev-me'), p.ME);
    assert.equal(p.senderKey('BROWSER', 'dev-b', null), p.ME);
});

test('names follow the phone setting', () => {
    const p = load();
    assert.equal(p.namesEnabled('AUTO', 1), false);
    assert.equal(p.namesEnabled('AUTO', 2), true);
    assert.equal(p.namesEnabled('ALWAYS', 1), true);
    assert.equal(p.namesEnabled('OFF', 9), false);
    assert.equal(p.namesEnabled('nonsense', 2), true, 'unknown values fall back to AUTO');
});

// 装机反馈：自己这一侧也标名字（别人都有，自己没有反而别扭）；常驻按钮时每条都标，悬浮工具栏时只标首条。
test('with the floating toolbar a name heads each run, my own side included', () => {
    const p = load();
    const me = p.ME;
    const senders = ['dev-b', 'dev-b', p.PHONE, me, me];
    assert.deepEqual(p.labelPlan(senders, [false, false, false, false, false], false), [true, false, true, true, false]);
});

test('with inline buttons every message carries its name', () => {
    const p = load();
    assert.deepEqual(p.labelPlan(['dev-b', 'dev-b', p.ME], [false, false, false], true), [true, true, true]);
});

test('a time divider starts a new run', () => {
    const p = load();
    assert.deepEqual(p.labelPlan(['dev-b', 'dev-b'], [false, true], false), [true, true]);
});

test('roster entries resolve names and avatars, with safe fallbacks', () => {
    const p = load();
    const roster = p.parseRoster({ devices: [
        { deviceId: 'dev-b', name: '', defaultLabel: 'Edge · Windows', defaultOrdinal: 2, avatarKey: 'icon:face', online: true, present: true },
        { deviceId: 'dev-c', name: '书房', defaultLabel: null, defaultOrdinal: 1, avatarKey: 'icon:star', online: false, present: false },
    ] });
    assert.equal(roster.size, 2);
    assert.equal(p.displayName(roster.get('dev-b'), '浏览器'), 'Edge 2');
    assert.equal(p.displayName(roster.get('dev-c'), '浏览器'), '书房');
    assert.equal(p.displayName(undefined, '浏览器'), '浏览器');
    assert.equal(p.parseRoster(null).size, 0);
    assert.equal(p.parseRoster({ devices: [{ name: 'no id' }] }).size, 0, 'entries without an id are dropped');
});
