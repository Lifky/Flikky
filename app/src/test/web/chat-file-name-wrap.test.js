const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { ruleBlock, stripBlockComments } = require('./scan.js');

// v1.24 装机反馈（Screenshot_1/2）：文件名里一长串没有空格的字符无处可断，把气泡撑出屏幕，
// 整页要左右滑动才能看全。App 端同样的文件名在气泡里折行。
const css = stripBlockComments(fs.readFileSync(path.join(__dirname, '../../main/assets/web/chat.css'), 'utf8'));

test('a long file name wraps inside the bubble instead of stretching it', () => {
    const name = ruleBlock(css, '.file-bubble a');
    assert.ok(name.includes('overflow-wrap: anywhere'), 'the name may break anywhere');
    assert.ok(name.includes('min-width: 0'), 'and the flex item may shrink below its longest run');
});

test('the size stays on one line next to the name', () => {
    const size = ruleBlock(css, '.file-bubble .size');
    assert.ok(size.includes('white-space: nowrap'), '108.7 MB must not split over two lines');
    assert.ok(size.includes('flex-shrink: 0'));
});
