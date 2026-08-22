const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass, byRole, MutationObserver } = require('./mini-dom');

const WEB = path.join(__dirname, '../../main/assets/web');
const src = fs.readFileSync(path.join(WEB, 'panel-favorites.js'), 'utf8');
const html = fs.readFileSync(path.join(WEB, 'app.html'), 'utf8');
const i18n = fs.readFileSync(path.join(WEB, 'i18n.js'), 'utf8');

const SKELETON = ['fav-refresh', 'fav-search', 'fav-chips', 'fav-list',
                  'fav-toolbar', 'fav-count', 'fav-clear', 'fav-save-selected'];

const GROUPS = [
  { id: 3, name: '常用片段', sortOrder: 0 },
  { id: 5, name: '安装包', sortOrder: 1 },
];
const ITEMS = [
  { id: 11, kind: 'TEXT', text: 'ssh root@192.168.1.9', fileName: null,
    fileSize: null, mime: null, groupId: 3, createdAt: 1755300000000 },
  { id: 12, kind: 'FILE', text: null, fileName: 'app-release.apk',
    fileSize: 305459, mime: 'application/vnd.android.package-archive',
    groupId: 5, createdAt: 1755310000000 },
  { id: 13, kind: 'FILE', text: null, fileName: 'Screenshot_1.PNG',
    fileSize: 220100, mime: 'image/png', groupId: null, createdAt: 1755320000000 },
];

function buildSkeleton(doc) {
  const root = doc.register('view-favorites');
  const head = doc.createElement('div');
  head.className = 'fk-panel-head';
  head.appendChild(doc.register('fav-refresh', 'button'));
  root.appendChild(head);
  const search = doc.createElement('div');
  search.className = 'fk-search';
  search.appendChild(doc.register('fav-search', 'input'));
  root.appendChild(search);
  root.appendChild(doc.register('fav-chips'));
  root.appendChild(doc.register('fav-list'));
  const toolbar = doc.register('fav-toolbar');
  toolbar.hidden = true;
  toolbar.appendChild(doc.register('fav-count', 'span'));
  toolbar.appendChild(doc.register('fav-clear', 'button'));
  toolbar.appendChild(doc.register('fav-save-selected', 'button'));
  root.appendChild(toolbar);
  return root;
}

// status/throwNetwork 让同一个假 fetch 既能模拟 404（功能关闭的唯一信号）、
// 也能模拟其它非 200（比如 500）、也能模拟纯网络异常（fetch 直接 reject）——
// 三种在生产代码里必须落到不同状态机分支的情形，测试夹具得能分得开。
function load({
  response = { groups: GROUPS, items: ITEMS },
  status = 200,
  throwNetwork = false,
  clipboard = true,
} = {}) {
  const doc = createDocument();
  const root = buildSkeleton(doc);
  const fetched = [];
  const copied = [];
  const execCommands = [];
  const timeouts = [];
  doc.execCommand = (name) => { execCommands.push(name); return true; };

  const ctx = {
    console,
    document: doc,
    MutationObserver,
    setTimeout(fn, delay) { timeouts.push(delay); fn(); return 1; },
    clearTimeout() {},
    Promise,
    fetch(url, opts) {
      fetched.push({ url, opts: opts || {} });
      if (throwNetwork) return Promise.reject(new Error('offline'));
      return Promise.resolve({
        ok: status >= 200 && status < 300,
        status,
        json: () => Promise.resolve(response),
      });
    },
    navigator: clipboard
      ? { clipboard: { writeText(text) { copied.push(text); return Promise.resolve(); } } }
      : {},
    // onChange 必须像真实 i18n.js 一样「订阅时立刻同步调用一次」——面板依赖这一点
    // 完成「mount 后第一时间画出 loading」，而不依赖 fetch 的 await 先返回
    // （这正是 Task 5 的教训：面板脱离 peer-info 的到达顺序自己站得住）。
    // t() 必须像真实 i18n.js 一样做 {placeholder} 插值：选中计数走的是
    // t('app.favorites.selected', { count }) 而不是字符串拼接，中英两种语言
    // 数字的位置不同（「已选 N 项」/「N selected」），拼接表达不了。
    flikkyI18n: {
      t: (key, values) => {
        const template = key === 'app.favorites.selected' ? '已选 {count} 项' : key;
        return values ? template.replace(/\{(\w+)\}/g, (_m, k) => values[k]) : template;
      },
      onChange(cb) { cb(); return () => {}; },
    },
  };
  ctx.window = ctx;
  ctx.globalThis = ctx;
  vm.runInNewContext(src, ctx, { filename: 'panel-favorites.js' });

  const api = ctx.window.flikkyPanels && ctx.window.flikkyPanels.favorites;
  assert.ok(api, 'panel-favorites.js must register window.flikkyPanels.favorites');
  return { doc, root, api, fetched, copied, execCommands, timeouts };
}

const flush = async (n = 12) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };

const rowsOf = (doc) => byClass(doc.getElementById('fav-list'), 'fk-item');
const rowById = (doc, id) => rowsOf(doc).find((r) => r.getAttribute('data-fav-id') === String(id));
const anchorHrefs = (doc) => doc.created
  .filter((e) => e.tagName === 'A' && e.getAttribute('href'))
  .map((e) => e.getAttribute('href'));

async function mounted(opts) {
  const ctx = load(opts);
  ctx.api.mount(ctx.root);
  await flush();
  return ctx;
}

test('app.html carries the favorites skeleton and loads the panel from /static', () => {
  for (const id of SKELETON) {
    assert.match(html, new RegExp(`id="${id}"`), `app.html is missing #${id}`);
  }
  assert.match(html, /<script src="\/static\/panel-favorites\.js"><\/script>/);
});

test('the panel never renders through the raw-HTML-injection API', () => {
  assert.equal(src.includes('innerHTML'), false);
});

test('the panel does not query the DOM back for state', () => {
  // 选中集活在 JS 的 Set 里；从 DOM 属性读回状态既慢又容易和渲染不同步。
  assert.equal(/querySelector/.test(src), false);
});

test('mount fetches the favorites contract with same-origin credentials', async () => {
  const { fetched } = await mounted();
  assert.deepEqual(fetched.map((f) => f.url), ['/api/favorites']);
  assert.equal(fetched[0].opts.credentials, 'same-origin');
});

test('the very first paint is a loading state, never an error or disabled state', () => {
  // 不 flush：断言发生在 fetch 的 promise 还没解决的那一刻，正是「mount 同步
  // 返回」到「await 的 fetch 真正 resolve」之间的窗口——D4 要求这段时间只能是
  // loading，不能是空白、不能是错误态、也不能是 disabled 态。
  const ctx = load();
  ctx.api.mount(ctx.root);
  const list = ctx.doc.getElementById('fav-list');
  assert.equal(byClass(list, 'fk-error').length, 0, 'must not show an error before any response');
  assert.equal(list.textContent.includes('app.favorites.disabled'), false);
  assert.equal(list.textContent.includes('app.favorites.loadFailed'), false);
  assert.ok(list.children.length > 0, 'a loading affordance must already be painted');
});

test('filterFavorites matches both text and file names, case-insensitively', () => {
  const { api } = load();
  const ids = (query, groupId = 'all') =>
    api.filterFavorites(ITEMS, { groupId, query }).map((i) => i.id);
  assert.deepEqual(ids('SSH'), [11]);
  assert.deepEqual(ids('APK'), [12]);
  assert.deepEqual(ids('screenshot_1.png'), [13]);
  assert.deepEqual(ids('', 5), [12]);
  assert.deepEqual(ids('   '), [11, 12, 13], 'blank query must not filter everything out');
  assert.deepEqual(ids('nope'), []);
});

test('group chips come from the response and lead with an all-items chip', async () => {
  const { doc } = await mounted();
  const chips = doc.getElementById('fav-chips').children;
  assert.equal(chips.length, GROUPS.length + 1);
  assert.equal(chips[0].getAttribute('data-group-id'), 'all');
  assert.deepEqual(chips.slice(1).map((c) => c.getAttribute('data-group-id')), ['3', '5']);
  assert.deepEqual(chips.slice(1).map((c) => c.textContent), ['常用片段', '安装包']);
  // 官方 mdui-chip 的 filter 变体，不是自绘按钮（决策 D30：库更强的地方用库）。
  // tagName 是大写的（与真实 DOM 一致）
  assert.deepEqual(chips.map((c) => c.tagName), new Array(chips.length).fill('MDUI-CHIP'));
  assert.deepEqual(chips.map((c) => c.getAttribute('variant')), new Array(chips.length).fill('filter'));
  assert.equal(chips[0].getAttribute('selected'), '', 'the all-items chip starts selected');
  assert.equal(chips[1].getAttribute('selected'), null);
});

test('searching and chip filtering compose', async () => {
  const { doc } = await mounted();
  const search = doc.getElementById('fav-search');
  search.value = 'app';
  search.dispatch('input');
  assert.deepEqual(rowsOf(doc).map((r) => r.getAttribute('data-fav-id')), ['12']);

  search.value = '';
  search.dispatch('input');
  // mdui-chip 的选中变化走 change，不是 click。
  doc.getElementById('fav-chips').children[1].dispatch('change');   // 「常用片段」
  assert.deepEqual(rowsOf(doc).map((r) => r.getAttribute('data-fav-id')), ['11']);
});

test('an unknown groupId still renders, filed under the ungrouped section', async () => {
  // item 13 的 groupId 是 null，不在 GROUPS[3,5] 里——必须仍然出现，
  // 归到「未分组」标题下，不能被丢弃或让渲染抛错。
  const { doc } = await mounted();
  assert.ok(rowById(doc, '13'), 'unknown-groupId item must still render');
  const titles = byClass(doc.getElementById('fav-list'), 'fk-section-title')
    .map((t) => t.textContent);
  assert.ok(titles.includes('app.favorites.ungrouped'), 'ungrouped section title must appear');
});

test('a text favorite copies through the async clipboard API', async () => {
  const { doc, copied } = await mounted();
  const copy = byRole(rowById(doc, '11'), 'copy')[0];
  assert.ok(copy, 'text favorite must expose a copy action');
  copy.click();
  await flush();
  assert.deepEqual(copied, ['ssh root@192.168.1.9']);
});

test('clipboard-less browsers fall back to execCommand instead of doing nothing', async () => {
  // http:// 上下文里 navigator.clipboard 直接不存在 —— 本项目正是明文 HTTP。
  const { doc, execCommands } = await mounted({ clipboard: false });
  byRole(rowById(doc, '11'), 'copy')[0].click();
  await flush();
  assert.deepEqual(execCommands, ['copy']);
});

test('a file favorite saves through the authenticated streaming route', async () => {
  const { doc } = await mounted();
  byRole(rowById(doc, '12'), 'save')[0].click();
  await flush();
  assert.deepEqual(anchorHrefs(doc), ['/api/favorites/12/file']);
  const anchor = doc.created.find((e) => e.tagName === 'A' && e.getAttribute('href'));
  assert.equal(anchor.getAttribute('download'), 'app-release.apk');
  assert.equal(anchor.clickCount, 1);
});

test('only file rows carry a checkbox', async () => {
  // spec §4.2：文本收藏没有多选语义，行首方块只属于文件行。
  const { doc } = await mounted();
  const withCheck = rowsOf(doc)
    .filter((r) => byClass(r, 'fk-check').length > 0)
    .map((r) => r.getAttribute('data-fav-id'));
  assert.deepEqual(withCheck, ['12', '13']);
});

test('selecting file rows floats the toolbar with a live count', async () => {
  const { doc } = await mounted();
  const toolbar = doc.getElementById('fav-toolbar');
  const count = doc.getElementById('fav-count');
  assert.equal(toolbar.hidden, true);

  rowById(doc, '12').click();
  assert.equal(toolbar.hidden, false);
  assert.match(count.textContent, /1/);

  rowById(doc, '13').click();
  assert.match(count.textContent, /2/);

  rowById(doc, '13').click();                 // 再点一次 = 取消选中
  assert.match(count.textContent, /1/);

  doc.getElementById('fav-clear').click();
  assert.equal(toolbar.hidden, true);
  assert.equal(rowById(doc, '12').getAttribute('aria-selected'), 'false');
});

test('clicking a text row selects nothing', async () => {
  const { doc } = await mounted();
  rowById(doc, '11').click();
  assert.equal(doc.getElementById('fav-toolbar').hidden, true);
});

test('the row-tail buttons do not also toggle selection', async () => {
  // 行尾按钮必须 stopPropagation，否则「保存」会顺手把这一行选中。
  const { doc } = await mounted();
  byRole(rowById(doc, '12'), 'save')[0].click();
  await flush();
  assert.equal(doc.getElementById('fav-toolbar').hidden, true);
});

test('saving the selection downloads every selected file exactly once, spaced apart', async () => {
  const { doc, timeouts } = await mounted();
  rowById(doc, '12').click();
  rowById(doc, '13').click();
  doc.getElementById('fav-save-selected').click();
  await flush();
  assert.deepEqual(anchorHrefs(doc), ['/api/favorites/12/file', '/api/favorites/13/file']);
  // D9：复用 app.js 的 saveAllIndividually 节奏——逐个点击之间相隔 ~350ms，
  // 不是同一 tick 里连点 N 次。
  assert.ok(timeouts.includes(350), `expected a 350ms cadence, got ${timeouts.join(',')}`);
});

test('an empty response renders an empty state, not a blank pane', async () => {
  const { doc } = await mounted({ response: { groups: [], items: [] } });
  assert.equal(byClass(doc.getElementById('fav-list'), 'fk-empty').length, 1);
});

test('a filtered-to-nothing result says so, distinct from truly having no favorites', async () => {
  // D5：对一个有 40 条收藏的人说「没有收藏」是谎话——筛不出结果和真的空必须是两句话。
  const { doc } = await mounted();
  const search = doc.getElementById('fav-search');
  search.value = 'this matches nothing at all';
  search.dispatch('input');
  const empty = byClass(doc.getElementById('fav-list'), 'fk-empty');
  assert.equal(empty.length, 1);
  assert.equal(empty[0].textContent.includes('app.favorites.noMatches'), true);
  assert.equal(empty[0].textContent.includes('app.favorites.empty'), false);
});

test('404 on the list endpoint renders the disabled state, with no retry button', async () => {
  // 服务端契约：GET /api/favorites 的 404 只有一个含义——功能关闭。重试无意义。
  const { doc } = await mounted({ status: 404 });
  const list = doc.getElementById('fav-list');
  assert.equal(list.textContent.includes('app.favorites.disabled'), true);
  assert.equal(byRole(list, 'retry').length, 0, 'a disabled feature must not offer retry');
  assert.equal(byClass(list, 'fk-error').length, 0, 'disabled is not the same state as error');
});

test('a non-404 failure renders a retry affordance and the retry re-issues the request', async () => {
  const { doc, fetched } = await mounted({ status: 500 });
  const error = byClass(doc.getElementById('fav-list'), 'fk-error');
  assert.equal(error.length, 1, 'a failed load must not look like an empty list');
  const retry = byRole(error[0], 'retry')[0];
  assert.ok(retry, 'error state must offer a retry');
  retry.click();
  await flush();
  assert.equal(fetched.length, 2, 'retry must re-issue the request');
});

test('a thrown network error also renders the retry affordance, not the disabled state', async () => {
  const { doc } = await mounted({ throwNetwork: true });
  const list = doc.getElementById('fav-list');
  assert.equal(byClass(list, 'fk-error').length, 1);
  assert.equal(list.textContent.includes('app.favorites.disabled'), false);
});

test('the refresh button re-fetches without a full remount', async () => {
  const { doc, fetched } = await mounted();
  doc.getElementById('fav-refresh').click();
  await flush();
  assert.equal(fetched.length, 2);
});

test('the panel refetches when the phone toggles favorites mid-session', async () => {
  // D4：这条正是 Task 5 踩过的坑的镜像——peer-info 到达顺序不可控，面板必须
  // 自己观察 body 上已发布的 data-favorite-enabled，而不是等 app.js 另外通知它。
  const { doc, fetched } = await mounted();
  assert.equal(fetched.length, 1);
  doc.body.dataset.favoriteEnabled = '0';
  await flush();
  assert.equal(fetched.length, 2, 'a flag flip must trigger a refetch, not just a repaint');
});

test('new favorites i18n keys exist in both languages', () => {
  for (const key of [
    'app.favorites.search', 'app.favorites.allGroups', 'app.favorites.ungrouped',
    'app.favorites.copy', 'app.favorites.save', 'app.favorites.copied',
    'app.favorites.selected', 'app.favorites.saveSelected', 'app.favorites.clear',
    'app.favorites.empty', 'app.favorites.loadFailed', 'app.favorites.retry',
    'app.favorites.refresh', 'app.favorites.disabled', 'app.favorites.disabledHint',
    'app.favorites.noMatches',
  ]) {
    const hits = [...i18n.matchAll(new RegExp(`'${key.replace(/\./g, '\\.')}'`, 'g'))];
    assert.ok(hits.length >= 2, `${key} appears ${hits.length}× — need zh + en`);
  }
});

test('the favorites panel does not carry its own mime-to-icon mapping', () => {
  // 它曾自带一份简化映射，缺 audio 分支、也没有 svg 特例，于是 mp3 显示成文档、
  // svg 显示成图片 —— 与手机端（FilesListBuilder.categoryOf + iconResource）对不上。
  // 唯一事实源是 app.js 的 fileSymbolName，经 window.flikky 发布。
  assert.match(src, /window\.flikky\.fileSymbolName\(mime\)/);
  for (const name of ["'image'", "'movie'", "'audio_file'", "'description'"]) {
    assert.equal(src.includes(name), false,
      `${name} is hard-coded here — the mapping must come from app.js`);
  }
  // apk / zip 是收藏页特有的细分，共享映射里没有，保留在本地判断。
  assert.match(src, /'android'/);
  assert.match(src, /'folder_zip'/);
  // 发布点必须真的存在于 app.js，否则上面全是空转。
  const appJs = fs.readFileSync(path.join(WEB, 'app.js'), 'utf8');
  assert.match(appJs, /window\.flikky\.fileSymbolName = fileSymbolName/);
});
