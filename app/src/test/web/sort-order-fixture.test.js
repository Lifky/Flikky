const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEB = path.join(__dirname, '../../main/assets/web');
const FIXTURE = path.join(__dirname, '../resources/sort-order.json');

function loadSort(localStorageStub) {
  const src = fs.readFileSync(path.join(WEB, 'sort.js'), 'utf8');
  const context = { window: {} };
  if (localStorageStub) context.localStorage = localStorageStub;
  vm.createContext(context);
  vm.runInContext(src, context);
  return context.window.flikkySort;
}

/**
 * 把 VM 里造出来的 spec 对象搬回宿主 realm。
 *
 * `vm.runInContext` 里的 `{...}` 带的是**那个 context 的 Object.prototype**，而
 * `node:assert/strict` 的 deepEqual 会比对象原型，于是「结构完全相同」也报
 * 「not reference-equal」。只比字段就跟 realm 无关了。
 */
function plain(spec) {
  return spec === null || spec === undefined ? spec : { key: spec.key, desc: spec.desc };
}

const fixture = JSON.parse(fs.readFileSync(FIXTURE, 'utf8'));

/**
 * fixture 里**会被列出来**的那些条目，每次调用返回**一份新的**。
 *
 * 不能共享一个模块级数组：`sortEntries` 一旦漏了 `.slice()` 就会原地排序，
 * 而第一条测试跑完就把共享数组排成了 SIZE:desc —— 后面那条「不修改入参」
 * 于是拿到一个已经排好的数组，再排一次没有变化，断言照样通过。
 * 逼红实测时这一条零条红，就是这么来的。
 *
 * sort.js 只排序、不过滤隐藏项（服务端已经滤过了，浏览器拿不到隐藏项），
 * 所以这里显式剔除；`hasHidden` 保证 fixture 真的含隐藏项，免得这层悄悄失效。
 */
function entries() {
  return fixture.entries.filter((e) => !e.name.startsWith('.')).map((e) => ({ ...e }));
}

const hasHidden = fixture.entries.some((e) => e.name.startsWith('.'));

test('sort.js 复现共享 fixture 里的每一种顺序', () => {
  const sort = loadSort();
  assert.ok(hasHidden, 'fixture 里应当含隐藏项，否则「隐藏项不出现」那一层没被覆盖');
  assert.equal(Object.keys(fixture.expected).length, 6, 'fixture 必须覆盖 3 键 × 2 方向');
  for (const [raw, names] of Object.entries(fixture.expected)) {
    const spec = sort.parse(raw);
    assert.ok(spec, `fixture 里的键名 ${raw} 解析不了`);
    assert.deepEqual(
      sort.sortEntries(entries(), spec).map((e) => e.name),
      names,
      `${raw} 的顺序与 fixture 不一致`,
    );
  }
});

test('parse 与 Kotlin 侧一样拒绝不认识的输入', () => {
  const sort = loadSort();
  const bad = [null, '', '   ', 'NAME', 'NAME:', 'NOPE:asc', 'NAME:sideways', 'NAME:asc:extra'];
  for (const raw of bad) {
    assert.equal(sort.parse(raw), null, `${JSON.stringify(raw)} 不该被解析成功`);
  }
  for (const key of sort.KEYS) {
    for (const desc of [true, false]) {
      assert.deepEqual(plain(sort.parse(sort.format({ key, desc }))), { key, desc });
    }
  }
});

test('natural 与 tap 的语义与 Kotlin 侧一致', () => {
  const sort = loadSort();
  assert.equal(sort.natural('NAME').desc, false);
  assert.equal(sort.natural('TIME').desc, true);
  assert.equal(sort.natural('SIZE').desc, true);
  // 点当前键翻转
  assert.deepEqual(plain(sort.tap({ key: 'SIZE', desc: true }, 'SIZE')), { key: 'SIZE', desc: false });
  // 点新键用新键的自然方向，不继承当前方向
  assert.deepEqual(plain(sort.tap({ key: 'SIZE', desc: true }, 'NAME')), { key: 'NAME', desc: false });
});

test('compareName 大小写不敏感优先于码位 —— 与 NAME_ORDER 逐字对应', () => {
  // 这一条把「先 lowercase 再比」与「直接按码位比」分开：ASCII 序会把
  // Beta(B=66) 排到 alpha(97) 前面，大小写不敏感则不会。
  const sort = loadSort();
  assert.ok(sort.compareName('alpha', 'Beta') < 0);
  // 相等时按原串兜底，R(82) < r(114)
  assert.ok(sort.compareName('README', 'readme') < 0);
});

test('sortEntries 不修改入参 —— 调用方还握着服务端给的原始顺序', () => {
  const sort = loadSort();
  const input = entries();
  const before = input.map((e) => e.name);
  sort.sortEntries(input, { key: 'SIZE', desc: true });
  assert.deepEqual(input.map((e) => e.name), before);
});

test('filterEntries 按名称做 trim + 不区分大小写的子串匹配，目录也参与', () => {
  const sort = loadSort();
  const names = (q) => sort.filterEntries(entries(), q).map((e) => e.name);
  assert.deepEqual(names('  READ  '), ['README', 'readme']);
  assert.deepEqual(names('alph'), ['Alpha']);       // 目录也能被过滤到
  assert.deepEqual(names(''), entries().map((e) => e.name));
  assert.deepEqual(names('  '), entries().map((e) => e.name));
  assert.deepEqual(names('没有这个'), []);
});

test('load 在 localStorage 抛异常时回落到默认值而不是崩掉', () => {
  const boom = {
    getItem() { throw new Error('私密模式'); },
    setItem() { throw new Error('私密模式'); },
  };
  const sort = loadSort(boom);
  const fallback = { key: 'NAME', desc: false };
  assert.deepEqual(sort.load('flikky_sort_files', fallback), fallback);
  sort.save('flikky_sort_files', { key: 'SIZE', desc: true });   // 不许抛
});

test('load 读回 save 写下的值', () => {
  const store = {};
  const stub = {
    getItem(k) { return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null; },
    setItem(k, v) { store[k] = String(v); },
  };
  const sort = loadSort(stub);
  sort.save('flikky_sort_files', { key: 'SIZE', desc: true });
  assert.equal(store.flikky_sort_files, 'SIZE:desc');
  assert.deepEqual(
    plain(sort.load('flikky_sort_files', { key: 'NAME', desc: false })),
    { key: 'SIZE', desc: true },
  );
});
