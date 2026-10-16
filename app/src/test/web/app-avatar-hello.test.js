const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const { functionBody } = require('./scan.js');

const appJs = fs.readFileSync(path.resolve(__dirname, '../../main/assets/web/app.js'), 'utf8');

test('the connect announce sends the device-self hello frame', () => {
    const frames = [];
    const context = {
        JSON,
        myAvatarKey: 'icon:star',
        localStorage: {},
        // app.js 里的局部常量叫 deviceSelf（= window.flikkyDeviceSelf）。
        deviceSelf: { helloFrame: (storage, key) => ({ type: 'client_hello', avatarKey: key, avatarExplicit: true, deviceName: '客厅电脑' }) },
        currentWs: { readyState: 1, send(frame) { frames.push(JSON.parse(frame)); } },
    };
    vm.runInNewContext(`${functionBody(appJs, 'function sendClientHello(')}\nsendClientHello();`, context);
    assert.deepEqual(frames, [{ type: 'client_hello', avatarKey: 'icon:star', avatarExplicit: true, deviceName: '客厅电脑' }]);
});

test('picking an avatar saves it as this device\'s own choice instead of re-announcing', () => {
    const body = functionBody(appJs, 'function selectAvatar(');
    assert.ok(body.includes('markAvatarExplicit'), 'selectAvatar must mark the pick as explicit');
    assert.ok(body.includes('putSelf('), 'selectAvatar must save through PUT /api/devices/self');
    assert.equal(body.includes('sendClientHello'), false, 'a pick is not an announce');
});

test('the old phone push of the browser avatar is gone', () => {
    assert.equal(appJs.includes('peer_avatar_changed'), false);
});
