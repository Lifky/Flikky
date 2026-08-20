const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const chat = fs.readFileSync(path.join(WEB, 'chat.css'), 'utf8');

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
});

test('press-in and release use different timings so the morph tracks the finger', () => {
  assert.match(chat, /:active\s*\{[^}]*border-radius[\s\S]{0,200}transition:\s*border-radius\s+var\(--flikky-morph-press-dur\)/);
});

test('the FAB morphs rounder on press — opposite direction from circles', () => {
  // 它静止是圆角正方形，按下应该更圆。圆的变方、方的变圆，都朝对立形状走。
  const active = chat.match(/\.fk-fab:active\s*\{[^}]*\}/)[0];
  assert.match(active, /var\(--flikky-morph-sq-pressed\)/);
  assert.match(chat, /--flikky-morph-sq:\s*28%/);
  assert.match(chat, /--flikky-morph-sq-pressed:\s*50%/);
});

test('enter animation is scoped to newly added rows only', () => {
  // 挂在 .bubble-row 上会让每次重建列表时整屏消息重播动画 —— 视觉上就是「闪一下」。
  assert.match(chat, /\.bubble-row--enter[\s\S]{0,160}animation/);
  const base = chat.match(/^\.bubble-row\s*\{[^}]*\}/m)[0];
  assert.equal(false, /animation/.test(base));
});
