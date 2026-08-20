const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const tokens = fs.readFileSync(path.join(WEB, 'tokens.css'), 'utf8');
const springs = fs.readFileSync(path.join(WEB, 'springs.css'), 'utf8');
const shapes = fs.readFileSync(path.join(WEB, 'shapes.svg.html'), 'utf8');

test('generated files carry a do-not-edit banner', () => {
  assert.match(tokens, /DO NOT EDIT BY HAND/);
  assert.match(springs, /DO NOT EDIT BY HAND/);
});

test('six Expressive spring curves are emitted with durations', () => {
  for (const name of [
    'spatial-fast', 'spatial-default', 'spatial-slow',
    'effects-fast', 'effects-default', 'effects-slow',
  ]) {
    assert.match(springs, new RegExp(`--flikky-spring-${name}:\\s*linear\\(`), name);
    assert.match(springs, new RegExp(`--flikky-spring-${name}-dur:\\s*\\d+ms`), `${name} duration`);
  }
});

test('spatial springs overshoot past 1 — that overshoot IS the Expressive feel', () => {
  const curve = springs.match(/--flikky-spring-spatial-default:\s*linear\(([^)]*)\)/)[1];
  const peak = Math.max(...curve.split(',').map((v) => parseFloat(v)));
  assert.ok(peak > 1, `expected overshoot, peak was ${peak}`);
});

test('effects springs are critically damped — no overshoot on color/opacity', () => {
  const curve = springs.match(/--flikky-spring-effects-default:\s*linear\(([^)]*)\)/)[1];
  const peak = Math.max(...curve.split(',').map((v) => parseFloat(v)));
  assert.ok(peak <= 1.0001, `effects curve must not overshoot, peak was ${peak}`);
});

test('Expressive shape tiers are present alongside the App-derived five', () => {
  for (const k of ['xs', 'sm', 'md', 'lg', 'xl', 'xl-increased', 'xxl']) {
    assert.match(tokens, new RegExp(`--flikky-shape-${k}:`), k);
  }
});

test('semantic surface layering is defined for light, dark and amoled', () => {
  assert.match(tokens, /--flikky-page-bg:/);
  assert.match(tokens, /--flikky-pillar-bg:/);
  assert.match(tokens, /--flikky-raised-bg:/);
  assert.match(tokens, /\.mdui-theme-dark[\s\S]*--flikky-page-bg:/);
  assert.match(tokens, /\[data-amoled="1"\][\s\S]*--flikky-page-bg:\s*#000/);
});

test('the auto theme follows the system so there is no first-frame flash', () => {
  // <html class="mdui-theme-auto"> 是初始状态，applyTheme() 跑过才变成 dark/light。
  assert.match(tokens, /prefers-color-scheme:\s*dark[\s\S]*\.mdui-theme-auto[\s\S]*--flikky-page-bg:/);
});

test('reduced motion collapses the motion scale to zero', () => {
  assert.match(tokens, /prefers-reduced-motion:\s*reduce[\s\S]*--flikky-motion-scale:\s*0/);
});

test('cookie9 clipPath uses objectBoundingBox so one path fits every size', () => {
  assert.match(shapes, /clipPathUnits="objectBoundingBox"/);
  assert.match(shapes, /id="flikky-cookie9"/);
});

test('cookie9 has nine-fold symmetry: one single corner radius', () => {
  // 复刻 MaterialShapes.Cookie9Sided 时用「全局统一钳制系数」而不是逐边缩放，
  // 否则先处理的顶点会被反复缩小，出来的是半径不一的歪果子。
  const radii = new Set([...shapes.matchAll(/A\s+([\d.]+)\s+([\d.]+)/g)].map((m) => m[1]));
  assert.equal(radii.size, 1, `expected one radius, got ${[...radii].join(', ')}`);
});

test('type tokens carry an explicit font-weight so the font shorthand never resets to 400', () => {
  // font: var(--flikky-type-x) var(--flikky-font-family) 是合法的 font 简写；
  // 若主变量里没有 weight 分量，简写会把粗细悄悄压回 400 —— titleMedium/titleSmall/
  // label-* 在 Type.kt 里是 Medium(500)，漏写 weight 就会比 App 端轻一档，且没有任何
  // 测试会因此变红（外观问题，不是解析/构建错误）。
  assert.match(tokens, /--flikky-type-display-large:\s*400\s+57px\/64px;/, 'Normal -> 400');
  assert.match(tokens, /--flikky-type-title-medium:\s*500\s+16px\/24px;/, 'Medium -> 500');
  assert.match(tokens, /--flikky-type-label-large:\s*500\s+14px\/20px;/, 'Medium -> 500');
});
