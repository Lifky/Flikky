const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEB = path.join(__dirname, '../../main/assets/web');
const chat = fs.readFileSync(path.join(WEB, 'chat.css'), 'utf8');
const appHtml = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const appJs = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');

function rule(sel) {
  const re = new RegExp(sel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*\\{[^}]*\\}');
  const m = chat.match(re);
  assert.ok(m, `rule not found: ${sel}`);
  return m[0];
}

test('bubble colors match the App verbatim', () => {
  // 事实源 ui/components/MessageBubble.kt:80 —— me = primary/on-primary，
  // them = surface-container-high/on-surface。双端一致不靠记忆，靠这条断言。
  assert.match(rule('.bubble-row.me .bubble'), /background:\s*rgb\(var\(--mdui-color-primary\)\)/);
  assert.match(rule('.bubble-row.them .bubble'), /surface-container-high/);
});

test('message spacing separates groups from runs', () => {
  // 组内 4px、组间 16px。旧版统一 2px 再叠 -1px 负边距，相邻消息几乎贴死。
  assert.match(chat, /\.bubble-row\s*\{[^}]*margin-bottom:\s*var\(--flikky-space-lg\)/);
  assert.match(chat, /\.bubble-row\.grouped-(start|mid)[\s\S]{0,120}margin-bottom:\s*var\(--flikky-space-xs\)/);
});

test('inline action bar hugs its own bubble, not the next message', () => {
  const r = rule('body[data-action-style="INLINE"] .msg-actions');
  assert.match(r, /margin-top:\s*2px/);
  assert.match(chat, /data-action-style="INLINE"\]\s*\.bubble-row:has\(\.msg-actions\)[\s\S]{0,120}margin-bottom:/);
});

test('the time divider matches SessionTimeDivider.kt', () => {
  // RoundedCornerShape(8.dp)、padding h10 v3、labelSmall、onSurfaceVariant
  const r = rule('.time-divider-pill');
  assert.match(r, /border-radius:\s*var\(--flikky-shape-xs\)/);
  assert.match(r, /padding:\s*3px 10px/);
  assert.match(r, /on-surface-variant/);
});

test('the connection chip is an assist chip, 8dp, with no invented dot', () => {
  assert.match(rule('#conn'), /border-radius:\s*var\(--flikky-shape-xs\)/);
  assert.equal(false, /conn-dot/.test(chat));
});

test('the file icon container reuses the official Cookie9Sided shape', () => {
  assert.match(rule('.file-bubble .file-icon'), /clip-path:\s*url\(#flikky-cookie9\)/);
});

test('the dock keeps one constant radius and matches the FAB height', () => {
  // 「单行全圆 ↔ 多行 28px」的形变已删除：插值区间几乎全落在肉眼无差别的大半径段，
  // 观感就是最后一下突然跳变。
  const r = rule('.fk-dock');
  assert.match(r, /border-radius:\s*var\(--flikky-shape-xl\)/);
  assert.equal(false, /data-multiline[\s\S]{0,80}border-radius/.test(chat));
  assert.match(rule('.fk-fab'), /height:\s*56px/);
});

test('the send button is a circle', () => {
  assert.match(rule('#send-btn'), /border-radius:\s*50%/);
});

test('press morph uses percentages so the whole interpolation is visible', () => {
  // 写 999px 时按钮在半径 ≥ 半宽都长得一样，前 90% 动画无变化、最后一瞬跳变。
  assert.match(chat, /--flikky-morph-round:\s*50%/);
  assert.equal(false, /:active\s*\{[^}]*border-radius:\s*999px/.test(chat));
  // 光有 token 不够——真正驱动动画的每条 :active border-radius 规则都必须引用
  // --flikky-morph-* token，不能塞字面长度，否则 token 存在但没人用它。
  const activeBlocks = chat.match(/[^\n{]+:active\s*\{[^}]*\}/g) || [];
  const withRadius = activeBlocks.filter((b) => /border-radius:/.test(b));
  assert.ok(withRadius.length > 0, 'expected at least one :active rule to set border-radius');
  for (const block of withRadius) {
    assert.match(block, /border-radius:\s*var\(--flikky-morph-[\w-]+\)/, block);
  }
});

test('press-in and release use different timings so the morph tracks the finger', () => {
  // 只截一条完整规则体（#send-btn:active），而不是任意 200 字符的窗口——窗口会跨到
  // 下一条无关规则，让「按下/松开不同时长」这条断言在两条规则共享一个 transition 时也能通过。
  const active = chat.match(/#send-btn:active\s*\{[^}]*\}/)?.[0] ?? '';
  assert.match(active, /border-radius:\s*var\(--flikky-morph-round-pressed\)/);
  assert.match(active, /transition:\s*border-radius\s+var\(--flikky-morph-press-dur\)/);
});

test('the FAB morphs rounder on press — opposite direction from circles', () => {
  // 它静止是圆角正方形，按下应该更圆。圆的变方、方的变圆，都朝对立形状走。
  const active = chat.match(/\.fk-fab:active\s*\{[^}]*\}/)[0];
  assert.match(active, /border-radius:\s*var\(--flikky-morph-sq-pressed\)/);
  assert.match(chat, /--flikky-morph-sq:\s*28%/);
  assert.match(chat, /--flikky-morph-sq-pressed:\s*50%/);
  // F1 那整类 bug 的守门员：未注册的自定义属性不可插值，transition 挂上去只会在
  // 时长结束时突跳，不是形变。.msg-actions mdui-button-icon 上的 --shape-corner 是
  // 已知、记录在案、留给 Task 4b 处理的同类缺陷（本轮 F1d 明确要求不动它），所以这里
  // 先剥掉那条已知例外，再断言剩下的 chat.css 里不会再出现同类写法。
  const withoutKnownException = chat.replace(
    /\.msg-actions mdui-button-icon(?::active)?\s*\{[^}]*\}/g,
    '',
  );
  // 只抓「被过渡的属性名本身是自定义属性」（transition: --x 或列表里 , --x），
  // 不能用 /transition:[^;]*--/ 之类的宽松写法——那会连
  // `transition: border-radius var(--flikky-...)` 这种完全正常、值里引用了变量
  // 但过渡的是原生 border-radius 的写法也一起误判为踩坑。
  assert.equal(
    false,
    /(?:transition(?:-property)?:|,)\s*--[\w-]+[\s;]/.test(withoutKnownException),
    'no transition on a custom property outside the known .msg-actions carve-out (the F1 bug class)',
  );
});

test('enter animation is scoped to newly added rows only', () => {
  // 挂在 .bubble-row 上会让每次重建列表时整屏消息重播动画 —— 视觉上就是「闪一下」。
  assert.match(chat, /\.bubble-row--enter[\s\S]{0,160}animation/);
  const base = chat.match(/^\.bubble-row\s*\{[^}]*\}/m)[0];
  assert.equal(false, /animation/.test(base));
});

test('the user-approved press-morph travel and duration are restored (F2)', () => {
  // 28% 是每个圆形按钮按压形变的可见位移；38% 只是这段位移的一半，肉眼几乎看不出来。
  // 400ms 才读得出「按得越久越方」，300ms 只是一闪。
  assert.match(chat, /--flikky-morph-round-pressed:\s*28%/);
  assert.match(chat, /--flikky-morph-press-dur:\s*400ms/);
});

test('the dock inset is not a magic number — it aligns with the screen-edge token (F4)', () => {
  assert.match(chat, /--flikky-dock-inset:\s*var\(--flikky-space-lg\)/);
});

test('the file icon renders at 24px even as a grandchild of .file-icon (F5)', () => {
  assert.match(
    rule('.file-bubble .file-icon .material-symbols-outlined'),
    /font-size:\s*24px/,
  );
});

test('the chat list does not smooth-scroll, so auto-scroll cannot fight the next append (F6)', () => {
  assert.equal(false, /scroll-behavior/.test(chat));
});

test('the [hidden] attribute actually hides the FAB despite display:grid (F1c)', () => {
  // display:grid 在特异性上盖掉 UA 自带的 [hidden] { display:none }，必须显式写回。
  assert.match(rule('.fk-fab[hidden]'), /display:\s*none/);
});

test('the save-all trigger is a plain button, not mdui-fab (F1a)', () => {
  // mdui-fab 的 --shape-corner-normal 是未注册自定义属性，transition 不会插值，
  // 按压形变做不出来——必须是能原生 transition border-radius 的普通元素。
  assert.match(appHtml, /class="fk-fab" id="save-all-fab"/);
  assert.equal(false, /<mdui-fab/.test(appHtml));
});

test('the save-all menu describes what it actually is: a disclosure, not an ARIA menu (G4)', () => {
  // ARIA 的 menu 契约要求方向键导航 / Home-End / roving tabindex——一概没实现。
  // 两个按钮走原生 Tab 顺序就够了；FAB 用 aria-controls 关联菜单容器即可，
  // 不再谎称 role="menu"。
  assert.match(appHtml, /id="save-all-fab"[^>]*aria-controls="save-all-dropdown"/s);
  assert.equal(false, /role="menu"/.test(appHtml), 'no element should promise ARIA menu semantics we do not implement');
});

test('below 840px the dock and FAB menu rise above the fixed navbar (G1)', () => {
  // 绝对定位元素的 bottom 相对父级 padding box 解析，shell.css 给 .fk-pillar--chat
  // 加的 padding-bottom 对它无效——.fk-dock-row 必须在 chat.css 自己的移动端媒体
  // 查询里叠加 --flikky-navbar-h + safe-area-inset-bottom，否则输入坞和 FAB 会被
  // 固定导航栏整个盖住。
  const media = chat.match(/@media \(max-width: 839px\) \{[\s\S]*\}\s*$/);
  assert.ok(media, 'mobile (<840px) media block not found');
  const block = media[0];
  const dockRow = block.match(/\.fk-dock-row\s*\{[^}]*\}/)?.[0] ?? '';
  assert.match(dockRow, /bottom:[^;]*--flikky-navbar-h/, 'dock row must clear the fixed navbar height');
  assert.match(dockRow, /bottom:[^;]*safe-area-inset-bottom/, 'dock row must clear the home-indicator inset');
});

test('the dead pre-v1.2 avatar block is deleted (B1)', () => {
  // AVATAR_BG / AVATAR_EMOJI / myAvatarId / phoneAvatarId (the let) / makeAvatarEl
  // only ever existed inside the commented-out M9b block. AVATAR_BG/AVATAR_EMOJI
  // are the two identifiers unique to that block (myAvatarId has no other users
  // either, but phoneAvatarId also names an unrelated JSON field on the
  // peer-info payload, so it is not safe to grep bare here).
  assert.equal(false, /AVATAR_BG/.test(appJs));
  assert.equal(false, /AVATAR_EMOJI/.test(appJs));
});

function sliceFunction(startMarker, endMarker) {
  const start = appJs.indexOf(startMarker);
  assert.ok(start >= 0, `${startMarker} not found in app.js`);
  const end = appJs.indexOf(endMarker, start);
  assert.ok(end > start, `${startMarker} is incomplete`);
  return appJs.slice(start, end);
}

test('autoGrowInput resets height before reading scrollHeight, so shrinking works (B2)', () => {
  const slice = sliceFunction('function autoGrowInput', 'function refreshSendReady');

  // 证明"先归零"真的有意义：函数体必须对 height 赋值两次。只赋值一次（不归零直接
  // 读 scrollHeight）会让高度只单调增长、删字不缩回去，这条断言就会失败。
  const assignments = slice.match(/\.style\.height\s*=/g) || [];
  assert.equal(assignments.length, 2, 'autoGrowInput must reset height before reading scrollHeight');

  const input = { style: {}, scrollHeight: 60 };
  const context = { input };
  vm.createContext(context);
  vm.runInContext(`${slice}\nautoGrowInput();`, context);
  assert.equal(context.input.style.height, '60px');
});

test('the input dock grows on the input event, not keyup (B2)', () => {
  // input 而不是 keyup：粘贴、IME 拼字、自动填充都不会触发 keyup。
  assert.match(
    appJs,
    /input\.addEventListener\('input',\s*\(\)\s*=>\s*\{[^}]*autoGrowInput\(\)[^}]*\}\);/,
  );
  assert.equal(false, /input\.addEventListener\('keyup'/.test(appJs));
});

test('refreshSendReady requires both a live connection and non-empty content (B3)', () => {
  function computeReady(disabled, value) {
    const slice = sliceFunction('function refreshSendReady', 'function formatSize');
    const sendBtn = { disabled, dataset: {} };
    const input = { value };
    const context = { sendBtn, input };
    vm.createContext(context);
    vm.runInContext(`${slice}\nrefreshSendReady();`, context);
    return context.sendBtn.dataset.ready;
  }

  assert.equal(computeReady(true, ''), 'false', 'disconnected + empty');
  // The case that fails if someone drops the `!sendBtn.disabled` term: there is
  // text, so a content-only check would say true, but disconnected must win.
  assert.equal(computeReady(true, 'hi'), 'false', 'disconnected + text must still read as not-ready');
  assert.equal(computeReady(false, ''), 'false', 'connected + empty');
  assert.equal(computeReady(false, 'hi'), 'true', 'connected + text');
});

test('refreshSendReady treats whitespace-only input as empty (B3)', () => {
  const slice = sliceFunction('function refreshSendReady', 'function formatSize');
  const sendBtn = { disabled: false, dataset: {} };
  const input = { value: '   ' };
  const context = { sendBtn, input };
  vm.createContext(context);
  vm.runInContext(`${slice}\nrefreshSendReady();`, context);
  assert.equal(context.sendBtn.dataset.ready, 'false');
});

test('setSendEnabled resets the send-ready state on every connection change (B3)', () => {
  const slice = sliceFunction('function setSendEnabled', 'function autoGrowInput');
  assert.match(slice, /refreshSendReady\(\);/);
});

test('sendText recomputes the ready state on every exit path (B3)', () => {
  // sendText's finally sets sendBtn.disabled directly instead of going through
  // setSendEnabled, and clearing input.value programmatically fires no `input`
  // event — so without an explicit call here the button stays primary-coloured
  // over an empty box until the next keystroke. finally (not the success branch)
  // is the right home: it also covers send-failure and disconnect.
  const slice = sliceFunction('async function sendText', 'function sendFile');
  assert.match(
    slice,
    /finally \{[\s\S]*?sendBtn\.disabled = !wsConnected;[\s\S]*?refreshSendReady\(\);/,
  );
});
