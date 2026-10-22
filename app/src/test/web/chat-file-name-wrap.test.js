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

// v1.24 第二轮装机（Screenshot_3/4）：大小放在名字右侧单独占一列，名字被挤窄、连「app-」都单独一行。
// App 端是「图标 | 名字 / 下一行大小」。浏览器照同一结构排：两列网格，图标跨两行。
test('the size sits under the name, as in the App', () => {
    const grid = ruleBlock(css, '.file-bubble:not(.media)');
    assert.ok(grid.includes('display: grid'), 'classic file bubbles lay out as a grid');
    assert.ok(grid.includes('grid-template-columns: auto minmax(0, 1fr)'), 'icon column + a name column that may shrink');
    assert.ok(ruleBlock(css, '.file-bubble:not(.media) > :first-child').includes('grid-row: span 2'), 'the icon spans the name and size rows');
    for (const sel of ['.file-bubble a', '.file-bubble .size']) {
        assert.ok(ruleBlock(css, sel).includes('grid-column: 2'), `${sel} stays in the text column`);
    }
    const fullWidth = ruleBlock(css, '.file-bubble .progress-bar, .file-bubble .progress-pct, .file-bubble .retry-hint');
    assert.ok(fullWidth.includes('grid-column: 1 / -1'), 'progress and retry run under both columns');
});

// 同轮：短文件名「app-release_filetest.txt」也被折成「app- / release_…」。气泡的 max-width: 78% 相对所在行，
// 而行原先按内容收缩 —— 宽度又由气泡自己决定，于是气泡被压到自身宽度的 78% 左右。行撑满列表，78% 才有确定的参照。
test('a bubble row spans the list so the 78% cap is measured against the list', () => {
    assert.ok(ruleBlock(css, '.bubble-row').includes('width: 100%'));
});
