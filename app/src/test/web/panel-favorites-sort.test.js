const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEB = path.join(__dirname, '../../main/assets/web');
const FIXTURE = path.join(__dirname, '../resources/sort-order.json');

/*
 * 浏览器端收藏面板的排序，与 App 端 `FavoritesListOrder` 是同一套语义的两份实现。
 *
 * 由 `app/src/test/resources/sort-order.json` 的 `favorites` 段钉死 ——
 * Kotlin 侧 `FavoritesListOrderTest` 读的是**同一份文件**。
 * 期望顺序是手工推导的独立 oracle，不是跑一遍实现录下来的：
 * 录下来的 fixture 只能发现两端不一致，发现不了两端一起错。
 */

function loadPanel() {
  const ctx = {
    window: { flikkyPanels: {} },
    document: {
      createElement: () => ({
        children: [],
        classList: { add() {}, remove() {}, toggle() {} },
        appendChild() {},
        setAttribute() {},
        addEventListener() {},
      }),
      getElementById: () => null,
    },
    localStorage: { getItem: () => null, setItem: () => {} },
    console: console,
  };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'sort.js'), 'utf8'), ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-favorites.js'), 'utf8'), ctx);
  return ctx.window.flikkyPanels.favorites;
}

const fixture = JSON.parse(fs.readFileSync(FIXTURE, 'utf8')).favorites;

/** fixture 的一项 → 浏览器端收藏项的字段形状（fileName / text / fileSize）。 */
function toItem(row) {
  return row.kind === 'FILE'
    ? { id: row.id, kind: 'FILE', fileName: row.name, text: null,
        fileSize: row.size, createdAt: row.createdAt, groupId: null }
    : { id: row.id, kind: 'TEXT', fileName: null, text: row.name,
        fileSize: null, createdAt: row.createdAt, groupId: null };
}

/** 每次返回一份新的：漏了 slice() 的原地排序会污染后面的用例。 */
const items = () => fixture.items.map(toItem);

test('sortFavorites 复现共享 fixture 里的每一种顺序', () => {
  const api = loadPanel();
  assert.ok(api && api.sortFavorites, 'sortFavorites 没有导出');
  assert.equal(Object.keys(fixture.expected).length, 6, 'fixture 必须覆盖 3 键 × 2 方向');

  for (const [raw, ids] of Object.entries(fixture.expected)) {
    const spec = { key: raw.split(':')[0], desc: raw.split(':')[1] === 'desc' };
    assert.deepEqual(
      api.sortFavorites(items(), spec).map((i) => i.id),
      ids,
      raw + ' 的顺序与 fixture 不一致',
    );
  }
});

test('名称键用原始值，不是本地化占位串', () => {
  // 空名回落到「未命名文件」这类占位串的话，**切换语言会改变排序**。
  const api = loadPanel();
  const byName = api.sortFavorites(items(), { key: 'NAME', desc: false });
  assert.equal(byName[0].id, 4, '空名的那一项应当排在最前（空串最小）');
});

test('没有文件的项按大小排时两个方向都在末尾', () => {
  // 「没有大小」不是「大小为 0」。升序时把一堆文本收藏顶到最前面没有意义。
  const api = loadPanel();
  const asc = api.sortFavorites(items(), { key: 'SIZE', desc: false }).map((i) => i.id);
  const desc = api.sortFavorites(items(), { key: 'SIZE', desc: true }).map((i) => i.id);
  assert.equal(asc[asc.length - 1], 2, '升序时无大小的项也该在末尾');
  assert.equal(desc[desc.length - 1], 2, '降序时无大小的项也该在末尾');
});

test('名称比较走共享的 compareName，不是 localeCompare', () => {
  // fixture 里的名字在两种比较器下恰好同序，分不开它们（逼红实测零条红）。
  // 这一条用 ASCII 序与 locale 序**相反**的一对：
  // localeCompare 下 'alpha' < 'Beta'；纯码位序下 'Beta'(B=66) < 'alpha'(97)。
  // compareName 先 lowercase，所以结果与 localeCompare 这一例恰好相同 ——
  // 真正的区别在**同名不同大小写**时谁在前，一并钉住。
  const api = loadPanel();
  const pair = [
    { id: 10, kind: 'FILE', fileName: 'Beta', fileSize: 1, createdAt: 1, groupId: null },
    { id: 11, kind: 'FILE', fileName: 'alpha', fileSize: 1, createdAt: 1, groupId: null },
  ];
  assert.deepEqual(
    api.sortFavorites(pair, { key: 'NAME', desc: false }).map((i) => i.id),
    [11, 10],
    '大小写不敏感：alpha 应当在 Beta 之前',
  );

  const sameName = [
    { id: 20, kind: 'FILE', fileName: 'note', fileSize: 1, createdAt: 1, groupId: null },
    { id: 21, kind: 'FILE', fileName: 'Note', fileSize: 1, createdAt: 1, groupId: null },
  ];
  assert.deepEqual(
    api.sortFavorites(sameName, { key: 'NAME', desc: false }).map((i) => i.id),
    [21, 20],
    '同名不同大小写时按原串兜底：Note(N=78) 在 note(110) 之前',
  );
});

test('sortFavorites 不修改入参', () => {
  const api = loadPanel();
  const input = items();
  const before = input.map((i) => i.id);
  api.sortFavorites(input, { key: 'SIZE', desc: true });
  assert.deepEqual(input.map((i) => i.id), before);
});
