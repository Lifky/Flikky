const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const html = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const shell = fs.readFileSync(path.join(WEB, 'shell.css'), 'utf8');
const base = fs.readFileSync(path.join(WEB, 'base.css'), 'utf8');
const tokens = fs.readFileSync(path.join(WEB, 'tokens.css'), 'utf8');
const springs = fs.readFileSync(path.join(WEB, 'springs.css'), 'utf8');

test('shell carries the four layout state attributes', () => {
  const m = html.match(/<div id="shell"[^>]*>/);
  assert.ok(m, 'no #shell element');
  for (const attr of ['data-rail-side', 'data-swap', 'data-panel', 'data-mobile-dest']) {
    assert.match(m[0], new RegExp(attr), attr);
  }
});

test('chat-list-shell still directly wraps #list', () => {
  assert.match(html, /<div id="chat-list-shell" class="chat-list-shell">\s*<div id="list" class="chat-list"><\/div>/);
});

test('panels round only their top corners and sit flush to the bottom edge', () => {
  const rule = shell.match(/\.fk-pillar\s*\{[^}]*\}/)[0];
  assert.match(rule, /border-radius:\s*var\(--flikky-shape-xl-increased\)\s+var\(--flikky-shape-xl-increased\)\s+0\s+0/);
});

test('nothing in the shell casts a shadow', () => {
  const shadows = [...shell.matchAll(/box-shadow:\s*([^;]+);/g)]
    .map((m) => m[1].trim())
    .filter((v) => v !== 'none');
  assert.deepEqual(shadows, [], `unexpected shadows: ${shadows.join(' | ')}`);
});

test('rail is not a card: no background, no radius', () => {
  const rule = shell.match(/\.fk-pillar--rail\s*\{[^}]*\}/)[0];
  assert.match(rule, /background:\s*none/);
  assert.match(rule, /border-radius:\s*0/);
  assert.match(rule, /justify-content:\s*center/);
});

test('rail-side padding is mirrored so both gutters read equal', () => {
  assert.match(shell, /\.fk-shell\[data-rail-side="right"\]\s*\{[^}]*padding:/);
});

test('desktop rail has no chat destination', () => {
  const rail = html.match(/<nav class="fk-pillar fk-pillar--rail"[\s\S]*?<\/nav>/)[0];
  assert.equal(false, /data-dest="chat"/.test(rail));
});

test('collapsing the panel is what the toggle does — the rail is never hidden', () => {
  assert.match(shell, /\.fk-shell\[data-panel="hidden"\]\s+\.fk-pillar--panel/);
  assert.equal(false, /data-rail="hidden"/.test(shell));
});

test('mobile drops the rail, the splitter and the collapse button', () => {
  const mq = shell.match(/@media \(max-width:\s*839px\)\s*\{[\s\S]*?\n\}/)[0];
  assert.match(mq, /\.fk-pillar--rail[\s\S]*display:\s*none/);
  assert.match(mq, /\.fk-panel-collapse\s*\{\s*display:\s*none/);
});

test('the mobile navbar display override comes after its base rule', () => {
  const base = shell.lastIndexOf('.fk-navbar {');
  const override = shell.lastIndexOf('.fk-navbar { display: flex;');
  assert.ok(override > base || /max-width:\s*839px\)\s*\{\s*\.fk-navbar\s*\{\s*display:\s*flex/.test(shell.slice(base)),
    'navbar display:flex must win by source order');
});

test('scrollbars are hidden until hover', () => {
  assert.match(shell, /\.flikky-scroll\b/);
  assert.match(shell, /scrollbar-color:\s*transparent transparent/);
  assert.match(shell, /\.flikky-scroll:hover[\s\S]*scrollbar-color:/);
});

test('shell geometry tokens are defined where the shell lives', () => {
  for (const [name, value] of [
    ['--flikky-shell-pad', '20px'],
    ['--flikky-shell-gap', '12px'],
    ['--flikky-splitter-w', '16px'],
    ['--flikky-rail-w', '96px'],
    ['--flikky-navbar-h', '64px'],
    ['--flikky-indicator-w', '56px'],
    ['--flikky-indicator-h', '32px'],
  ]) {
    assert.match(shell, new RegExp(`${name}:\\s*${value}`), `${name} must be defined in shell.css`);
  }
  assert.match(shell, /--flikky-pillar-radius:\s*var\(--flikky-shape-xl-increased\)/);
});

test('every --flikky- variable the shell uses is defined somewhere', () => {
  const defined = new Set([
    ...[...base.matchAll(/(--flikky-[a-z0-9-]+)\s*:/g)].map((m) => m[1]),
    ...[...shell.matchAll(/(--flikky-[a-z0-9-]+)\s*:/g)].map((m) => m[1]),
    ...[...tokens.matchAll(/(--flikky-[a-z0-9-]+)\s*:/g)].map((m) => m[1]),
    ...[...springs.matchAll(/(--flikky-[a-z0-9-]+)\s*:/g)].map((m) => m[1]),
  ]);
  const used = [...shell.matchAll(/var\((--flikky-[a-z0-9-]+)/g)].map((m) => m[1]);
  const missing = [...new Set(used)].filter((v) => !defined.has(v));
  assert.deepEqual(missing, [], `undefined CSS variables: ${missing.join(', ')}`);
});
