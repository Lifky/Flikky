const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');
const tokensCss = fs.readFileSync(path.join(webDir, 'tokens.css'), 'utf8');

const start = appJs.indexOf('    // 气泡圆角双端联动');
const end = appJs.indexOf('    function resolvePhoneAvatarKey', start);
assert.ok(start >= 0 && end > start, 'bubble radius helper not found in app.js');
const slice = appJs.slice(start, end);

const defaultRadius = tokensCss.match(/--flikky-bubble-radius:\s*([^;]+);/);
assert.ok(defaultRadius, 'default bubble radius token not found');

test('an omitted serialized default restores the initial browser bubble radius', () => {
    const properties = new Map();
    const context = {
        document: {
            documentElement: {
                style: {
                    setProperty(name, value) { properties.set(name, value); },
                },
            },
        },
    };
    vm.createContext(context);
    vm.runInContext(
        `${slice}
        applyBubbleRadius(24);
        applyBubbleRadius(undefined);
        `,
        context,
    );

    assert.equal(properties.get('--flikky-bubble-radius'), defaultRadius[1].trim());
});
