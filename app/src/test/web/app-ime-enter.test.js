const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody, scrub } = require('./scan.js');

// v1.24（B59#7）：用中文输入法打字时，回车是「上屏」，不是「发送」。
// 之前发送框只看 e.key === 'Enter'，macOS 上选词的回车会把半截消息直接发出去。
const WEB = path.join(__dirname, '../../main/assets/web');
const source = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');

function isImeComposing() {
    const body = functionBody(source, 'function isImeComposing(');
    assert.ok(body, 'app.js defines isImeComposing');
    const c = {};
    vm.createContext(c);
    vm.runInContext(body, c);
    return c.isImeComposing;
}

test('composition keys are recognised in every browser', () => {
    const f = isImeComposing();
    assert.equal(f({ key: 'Enter', isComposing: true, keyCode: 13 }), true, 'Chrome / Firefox');
    assert.equal(f({ key: 'Enter', isComposing: false, keyCode: 229 }), true, 'Safari fires keydown after compositionend');
    assert.equal(f({ key: 'Enter', isComposing: false, keyCode: 13 }), false, 'a plain Enter still sends');
    assert.equal(f(null), false);
});

test('the send box and the rename dialog both skip composition Enter', () => {
    const code = scrub(source);
    assert.ok(code.includes("if (e.key === 'Enter' && !e.shiftKey && !isImeComposing(e))"), 'send box');
    assert.ok(code.includes("if (e.key === 'Enter' && !isImeComposing(e)) submit();"), 'rename dialog');
});
