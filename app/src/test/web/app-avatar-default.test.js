const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const appJsPath = path.resolve(__dirname, '../../main/assets/web/app.js');
const appJs = fs.readFileSync(appJsPath, 'utf8');
const avatarBootstrapStart = appJs.indexOf("const AVATAR_DEFAULT_BROWSER =");
const avatarBootstrapEnd = appJs.indexOf('let myAvatarKey = readMyAvatarKey();');

assert.notEqual(avatarBootstrapStart, -1, 'avatar bootstrap start marker must exist');
assert.notEqual(avatarBootstrapEnd, -1, 'avatar bootstrap end marker must exist');

const avatarBootstrap = appJs.slice(avatarBootstrapStart, avatarBootstrapEnd);
const avatarGroupingStart = appJs.indexOf("let avatarGrouping =");
const avatarGroupingEnd = appJs.indexOf('function shouldShowAvatarForRow');

assert.notEqual(avatarGroupingStart, -1, 'avatar grouping start marker must exist');
assert.notEqual(avatarGroupingEnd, -1, 'avatar grouping end marker must exist');

const avatarGroupingBootstrap = appJs.slice(avatarGroupingStart, avatarGroupingEnd);

function readMyAvatarKey(entries = {}) {
    const values = new Map(Object.entries(entries));
    const context = {
        localStorage: {
            getItem(key) {
                return values.has(key) ? values.get(key) : null;
            },
        },
    };

    vm.runInNewContext(
        `${avatarBootstrap}\nglobalThis.result = readMyAvatarKey();`,
        context,
    );
    return context.result;
}

test('new browser defaults to the desktop avatar', () => {
    assert.equal(readMyAvatarKey(), 'icon:desktop_windows');
});

test('explicit legacy avatar zero is still migrated', () => {
    assert.equal(readMyAvatarKey({ flikky_avatar: '0' }), 'icon:person');
});

test('browser avatar grouping fallback defaults to every message', () => {
    const context = {};
    vm.runInNewContext(
        `${avatarGroupingBootstrap}
        globalThis.initialAvatarGrouping = avatarGrouping;
        globalThis.invalidAvatarGrouping = normalizeAvatarGrouping('unknown');`,
        context,
    );

    assert.equal(context.initialAvatarGrouping, 'EACH');
    assert.equal(context.invalidAvatarGrouping, 'EACH');
});
