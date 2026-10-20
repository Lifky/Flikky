const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody, scrub } = require('./scan.js');

// v1.24（B59#4）：凭据失效（401）时，任何接口请求都弹「登录已失效」。
// 之前只有拉历史与改名处理了 401，其余请求报一个笼统的错误，用户不知道该重新连接。
const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const source = read('app.js');

test('a 401 from any request raises the signed-out dialog', async () => {
    const kinds = [];
    const context = { fetch: async () => ({ status: 401 }), onDeviceRevoked: (k) => kinds.push(k) };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'async function apiFetch('), context);
    assert.equal((await context.apiFetch('/api/x')).status, 401, 'the caller still gets the response');
    context.fetch = async () => ({ status: 200 });
    await context.apiFetch('/api/y');
    assert.deepEqual(kinds, ['unauthorized']);
});

test('later 401s do not stack dialogs, but a disconnect still says so', () => {
    const shown = [];
    const context = {
        deviceRevoked: false,
        setWebConnectionActive() {}, setConnectionWatermarkState() {}, setSendEnabled() {},
        showConnectionDialog: (kind) => shown.push(kind),
    };
    vm.createContext(context);
    vm.runInContext(functionBody(source, 'function onDeviceRevoked('), context);
    context.onDeviceRevoked('unauthorized');
    context.onDeviceRevoked('unauthorized');
    context.onDeviceRevoked('revoked');
    assert.deepEqual(shown, ['unauthorized', 'revoked']);
});

test('every API request on the chat page goes through apiFetch', () => {
    // 去掉 apiFetch 自己与各面板里那一行回退之后，不应再剩任何直接的 fetch( 调用。
    for (const name of ['app.js', 'panel-album.js', 'panel-favorites.js', 'panel-files.js', 'album-thumbnails.js']) {
        const rest = scrub(read(name)).split('apiFetch(').join('').split('fetch(input, init)').join('');
        assert.ok(!rest.includes('fetch('), `${name} still calls fetch directly`);
    }
});

test('an upload refused with 401 raises the same dialog', () => {
    const body = functionBody(source, 'function sendFile(');
    assert.ok(body.includes('xhr.status === 401'), 'sendFile must handle 401');
    assert.ok(body.includes("onDeviceRevoked('unauthorized')"));
});
