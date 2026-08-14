const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const webDir = path.join(__dirname, '../../main/assets/web');
const appJs = fs.readFileSync(path.join(webDir, 'app.js'), 'utf8');

const start = appJs.indexOf('const TIME_DIVIDER_GAP_MS');
const end = appJs.indexOf('// Wrap a bubble div');
assert.ok(start >= 0 && end > start, 'session timestamp helpers not found in app.js');
const slice = appJs.slice(start, end);

function createElement() {
    return {
        children: [],
        className: '',
        textContent: '',
        appendChild(child) { this.children.push(child); },
    };
}

function evaluate(expression, context = {}) {
    vm.createContext(context);
    vm.runInContext(`${slice}\nglobalThis.result = ${expression};`, context);
    return context.result;
}

test('formatSessionTimestamp uses fixed padded yy MM dd HH mm', () => {
    const first = new Date(2026, 7, 14, 13, 49).getTime();
    const padded = new Date(2031, 0, 5, 8, 7).getTime();
    assert.equal(evaluate(`formatSessionTimestamp(${first})`), '26/08/14 13:49');
    assert.equal(evaluate(`formatSessionTimestamp(${padded})`), '31/01/05 08:07');
});

test('maybeInsertTimeDivider anchors gaps to the last inserted divider', () => {
    const list = createElement();
    const context = {
        document: { createElement },
        list,
    };
    vm.createContext(context);
    vm.runInContext(
        `${slice}
        maybeInsertTimeDivider(1000000);
        maybeInsertTimeDivider(1240000);
        maybeInsertTimeDivider(1300000);
        maybeInsertTimeDivider(1540000);
        maybeInsertTimeDivider(1600000);
        globalThis.count = list.children.length;
        `,
        context,
    );

    assert.equal(context.count, 3);
});
