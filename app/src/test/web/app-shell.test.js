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
  // Two independent lookups instead of lastIndexOf on a short substring — ".fk-navbar {"
  // is a literal prefix of the override rule too, so a single shared search string can
  // resolve to the wrong occurrence. Matching each rule's own shape and comparing
  // match().index keeps this meaningful without constraining shell.css's formatting.
  const baseMatch = shell.match(/\.fk-navbar\s*\{[^}]*display:\s*none[^}]*\}/);
  assert.ok(baseMatch, 'no base .fk-navbar rule with display: none');
  const overrideMatch = shell.match(/@media \(max-width:\s*839px\)\s*\{\s*\.fk-navbar\s*\{\s*display:\s*flex/);
  assert.ok(overrideMatch, 'no mobile .fk-navbar display:flex override inside the 839px media query');
  assert.ok(overrideMatch.index > baseMatch.index,
    'navbar display:flex override must come after the base rule by source order');
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

// v1.19.0 items 4/5/8/10 each add or remove <link>/<script> tags; nothing before this
// caught a page referencing a static file that doesn't exist on disk (see app.css
// being dropped from app.html's <head> in the Task 3 first pass). This only checks
// "everything referenced exists" — not the converse — because several later tasks
// legitimately land a file a commit before wiring it up.
test('every /static/ href and src referenced by a page exists on disk', () => {
  const pages = ['app.html', 'login.html', 'export.html'];
  for (const page of pages) {
    const source = fs.readFileSync(path.join(WEB, page), 'utf8');
    const refs = [
      ...[...source.matchAll(/href="\/static\/([^"]+\.css)"/g)].map((m) => m[1]),
      ...[...source.matchAll(/src="\/static\/([^"]+\.js)"/g)].map((m) => m[1]),
    ];
    for (const ref of refs) {
      const resolved = path.join(WEB, ref);
      assert.ok(fs.existsSync(resolved), `${page} references missing static file: /static/${ref}`);
    }
  }
});
