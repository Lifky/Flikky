const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SRC = fs.readFileSync(path.join(__dirname, '../../main/assets/web/device-self.js'), 'utf8');

function load() {
    const context = {};
    vm.createContext(context);
    vm.runInContext(SRC, context);
    return context.flikkyDeviceSelf;
}

function memoryStorage(initial = {}) {
    const store = { ...initial };
    return {
        store,
        getItem: (k) => (Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null),
        setItem: (k, v) => { store[k] = String(v); },
        removeItem: (k) => { delete store[k]; },
    };
}

test('names are trimmed, stripped of control chars and cut without splitting an emoji', () => {
    const self = load();
    assert.equal(self.clampName('  我的\u0000电脑\n '), '我的电脑');
    assert.equal(self.clampName('a'.repeat(25)), 'a'.repeat(20));
    assert.equal(self.clampName('a'.repeat(19) + '😀'), 'a'.repeat(19));
    assert.equal(self.clampName('a'.repeat(18) + '😀'), 'a'.repeat(18) + '😀');
    assert.equal(self.clampName(null), '');
});

test('the hello frame carries the remembered name and avatar choice', () => {
    const self = load();
    const storage = memoryStorage({ flikky_device_name: '客厅电脑', flikky_avatar_explicit: '1' });
    assert.deepEqual(JSON.parse(JSON.stringify(self.helloFrame(storage, 'icon:star'))), {
        type: 'client_hello', avatarKey: 'icon:star', avatarExplicit: true, deviceName: '客厅电脑',
    });
    assert.equal(self.helloFrame(memoryStorage(), 'icon:star').avatarExplicit, false);
});

test('an update from the phone is written back to local storage', () => {
    const self = load();
    const storage = memoryStorage();
    const applied = self.applyUpdate(storage, {
        deviceId: 'dev-1', name: '张三的笔记本', defaultLabel: 'Chrome · Windows', defaultOrdinal: 2,
        avatarKey: 'icon:star', avatarExplicit: true,
    });
    assert.equal(applied.name, '张三的笔记本');
    assert.equal(storage.store.flikky_device_name, '张三的笔记本');
    assert.equal(storage.store.flikky_avatar_key, 'icon:star');
    assert.equal(storage.store.flikky_avatar_explicit, '1');
});

test('an empty name clears it and a non-explicit avatar drops the explicit mark', () => {
    const self = load();
    const storage = memoryStorage({ flikky_device_name: 'old', flikky_avatar_explicit: '1' });
    self.applyUpdate(storage, { deviceId: 'd', name: '', defaultLabel: null, defaultOrdinal: 1, avatarKey: 'icon:face', avatarExplicit: false });
    assert.equal(storage.store.flikky_device_name, undefined);
    assert.equal(storage.store.flikky_avatar_explicit, undefined);
    assert.equal(storage.store.flikky_avatar_key, 'icon:face');
});

test('a malformed update is ignored', () => {
    const self = load();
    const storage = memoryStorage({ flikky_device_name: 'keep' });
    assert.equal(self.applyUpdate(storage, null), null);
    assert.equal(self.applyUpdate(storage, { name: 'x' }), null);
    assert.equal(storage.store.flikky_device_name, 'keep');
});

test('display names fall back to the default label with its ordinal', () => {
    const self = load();
    assert.equal(self.displayName({ name: '张三', defaultLabel: 'Chrome · Windows', defaultOrdinal: 2 }, '浏览器'), '张三');
    assert.equal(self.displayName({ name: '', defaultLabel: 'Chrome · Windows', defaultOrdinal: 2 }, '浏览器'), 'Chrome · Windows 2');
    assert.equal(self.displayName({ name: '', defaultLabel: null, defaultOrdinal: 1 }, '浏览器'), '浏览器');
    assert.equal(self.displayName(null, '浏览器'), '浏览器');
});

test('close codes tell a disconnect from an expired sign-in', () => {
    const self = load();
    assert.equal(self.closeKind(4403), 'revoked');
    assert.equal(self.closeKind(4401), 'unauthorized');
    assert.equal(self.closeKind(1000), null);
    assert.equal(self.closeKind(undefined), null);
});

test('a storage that throws (private mode) never breaks the page', () => {
    const self = load();
    const broken = { getItem() { throw new Error('denied'); }, setItem() { throw new Error('denied'); }, removeItem() { throw new Error('denied'); } };
    assert.equal(self.storedName(broken), '');
    assert.doesNotThrow(() => self.applyUpdate(broken, { deviceId: 'd', name: 'x', avatarKey: 'icon:star', avatarExplicit: true }));
});

test('the module never touches innerHTML', () => {
    assert.equal(/innerHTML/.test(SRC), false);
});

// v1.23 审查 Important #1：预填的默认名可能超过 20 字（「Samsung Internet · Android」）。
// 不改直接确定，必须识别为「沿用默认名」，而不是截成一个怪名字存下来。
test('confirming an unedited over-length default keeps the default', () => {
    const self = load();
    const longDefault = 'Samsung Internet · Android 2';
    assert.equal(self.nameToSave(longDefault, longDefault), '');
    assert.equal(self.nameToSave('  ' + longDefault + ' ', longDefault), '');
    assert.equal(self.nameToSave('客厅电脑', longDefault), '客厅电脑');
    assert.equal(self.nameToSave('a'.repeat(25), 'Chrome · Windows'), 'a'.repeat(20));
});
