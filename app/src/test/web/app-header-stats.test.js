const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody } = require('./scan.js');

// 装机 Screenshot_4：窄屏（手机浏览器）上运行时长 / 文件数 / 速率三段把名字挤成「来…」。
// 用户裁决：窄屏默认收起，点「已连接」chip 从右向左展开，再点收回。宽屏照旧常驻。
const WEB = path.join(__dirname, '../../main/assets/web');
const css = fs.readFileSync(path.join(WEB, 'chat.css'), 'utf8');
const html = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const source = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');

// 与 app-chat-visual.test.js 的 G1 同一取法：chat.css 末尾那个移动端媒体查询块。
function narrowBlock() {
    const media = css.match(/@media \(max-width: 839px\) \{[\s\S]*\}\s*$/);
    assert.ok(media, 'mobile (<840px) media block not found');
    return media[0];
}

test('on narrow screens the stats start collapsed and open from the chip', () => {
    const block = narrowBlock();
    assert.match(block, /\.fk-chat-stats\s*\{[^}]*max-width:\s*0/);
    assert.match(block, /\.fk-chat-head\[data-stats="open"\] \.fk-chat-stats\s*\{[^}]*max-width:/);
    assert.match(block, /transition:/);
    assert.match(block, /prefers-reduced-motion/);
});

test('the chip says what it controls', () => {
    assert.match(html, /<span class="fk-chat-stats" id="chat-stats">/);
    assert.match(html, /<mdui-chip id="conn"[^>]*aria-controls="chat-stats"/);
});

test('tapping the chip toggles the stats only on narrow screens', () => {
    const body = functionBody(source, 'function toggleHeaderStats(');
    const head = { dataset: {} };
    const chip = { attrs: {}, setAttribute(k, v) { this.attrs[k] = v; } };
    let narrow = true;
    const context = {
        headerEl: head,
        conn: chip,
        window: { matchMedia: () => ({ matches: narrow }) },
    };
    vm.createContext(context);
    vm.runInContext(body, context);
    context.toggleHeaderStats();
    assert.equal(head.dataset.stats, 'open');
    assert.equal(chip.attrs['aria-expanded'], 'true');
    context.toggleHeaderStats();
    assert.equal(head.dataset.stats, 'closed');
    assert.equal(chip.attrs['aria-expanded'], 'false');
    narrow = false;
    context.toggleHeaderStats();
    assert.equal(head.dataset.stats, 'closed', 'wide screens always show the stats; the chip does nothing');
    assert.match(source, /conn\.addEventListener\('click', toggleHeaderStats\)/);
});
