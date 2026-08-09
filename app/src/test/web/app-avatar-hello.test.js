const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const appJsPath = path.resolve(__dirname, '../../main/assets/web/app.js');
const appJs = fs.readFileSync(appJsPath, 'utf8');

const helloStart = appJs.indexOf('function sendClientHello');
const helloEnd = appJs.indexOf('// Init header avatar on load.');
assert.notEqual(helloStart, -1, 'sendClientHello marker must exist');
assert.notEqual(helloEnd, -1, 'hello end marker must exist');
const helloSlice = appJs.slice(helloStart, helloEnd);

function sentFrames(invoke) {
    const frames = [];
    const context = {
        JSON,
        myAvatarKey: 'icon:star',
        currentWs: { readyState: 1, send(frame) { frames.push(frame); } },
    };
    vm.runInNewContext(`${helloSlice}\n${invoke}`, context);
    return frames.map((frame) => JSON.parse(frame));
}

test('connect announce sends explicit false', () => {
    const frames = sentFrames('sendClientHello(false);');
    assert.equal(frames.length, 1);
    assert.deepEqual(frames[0], {
        type: 'client_hello',
        avatarKey: 'icon:star',
        explicit: false,
    });
});

test('user pick sends explicit true', () => {
    const frames = sentFrames('sendClientHello(true);');
    assert.deepEqual(frames[0], {
        type: 'client_hello',
        avatarKey: 'icon:star',
        explicit: true,
    });
});

test('call sites pass the right explicit flag', () => {
    const selectAvatarBody = appJs.slice(
        appJs.indexOf('function selectAvatar'),
        appJs.indexOf('function sendClientHello'),
    );
    assert.ok(selectAvatarBody.includes('sendClientHello(true)'), 'selectAvatar must send explicit:true');
    assert.ok(appJs.includes('sendClientHello(false)'), 'connect announce must send explicit:false');
    assert.ok(!appJs.includes('sendClientHello();'), 'no call site may omit the explicit flag');
});
