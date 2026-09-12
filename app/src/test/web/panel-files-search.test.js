const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument, byClass } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const LF = String.fromCharCode(10);

/*
 * 浏览器端文件面板的关键词过滤（只过滤当前目录，不递归 —— spec §4.1）。
 *
 * 三处容易错的地方：
 *   · 搜索框必须在**滚动容器外面**：renderShell 每次换目录都清空 body，
 *     放里面会连同焦点一起被摧毁；
 *   · 换目录清空关键词，否则新目录看起来像空的；
 *   · 「没有匹配的文件」与「这个文件夹是空的」是两句不同的话。
 */

// 造一次失败响应：`{ __fail: { status, code } }` 代替正常的 NDJSON 列举。
// 引导态（403/404）走的是 renderGuidance，那条路径此前没有任何测试覆盖。
function load(listings, opts) {
  const doc = createDocument();
  const view = doc.register('view-files');
  const o = opts || {};
  const asked = [];
  const ctx = {
    document: doc,
    window: {
      flikkyPanels: {},
      flikky: { fileSymbolName: () => 'draft' },
      flikkyI18n: {
        t: (k, v) => (v && typeof v.count === 'number' ? k + ':' + v.count : k),
        onChange: () => {},
      },
    },
    localStorage: {
      getItem: () => null,
      setItem: () => {},
    },
    TextDecoder: TextDecoder,
    requestAnimationFrame: (fn) => { fn(0); return 1; },
    fetch: (url) => {
      asked.push(url);
      const at = decodeURIComponent((url.split('path=')[1] || ''));
      const bodyText = listings[at];
      assert.ok(bodyText !== undefined, 'no fixture for path=' + JSON.stringify(at));
      if (bodyText && bodyText.__fail) {
        return Promise.resolve({
          ok: false,
          status: bodyText.__fail.status,
          json: () => Promise.resolve({ code: bodyText.__fail.code }),
          text: () => Promise.resolve(JSON.stringify({ code: bodyText.__fail.code })),
          body: null,
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        body: {
          getReader: () => {
            let step = 0;
            return {
              read: () => {
                if (step > 0) return Promise.resolve({ value: undefined, done: true });
                step += 1;
                return Promise.resolve({ value: Buffer.from(bodyText, 'utf8'), done: false });
              },
              cancel: () => {},
            };
          },
        },
      });
    },
    console: console,
  };
  ctx.window.document = doc;
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'sort.js'), 'utf8'), ctx);
  vm.runInContext(fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8'), ctx);
  return { doc, view, api: ctx.window.flikkyPanels.files, asked, listings };
}

const tick = async (n = 60) => { for (let k = 0; k < n; k += 1) await Promise.resolve(); };
const rows = (v) => byClass(v, 'fk-item');
const body = (v) => byClass(v, 'fk-panel-body')[0];
const titles = (v) => rows(v).map((r) => byClass(r, 'fk-item-title')[0].textContent);
const searchInput = (v) =>
  byClass(v, 'fk-search')[0].children.filter((c) => c.tagName === 'INPUT')[0];

function line(name, isDir) {
  return JSON.stringify({ name: name, isDir: !!isDir, size: 1, mtime: 0, mime: null });
}

function listing(atPath, names) {
  const out = [JSON.stringify({ path: atPath })];
  names.forEach((n) => out.push(line(n.replace(/\/$/, ''), n.endsWith('/'))));
  out.push(JSON.stringify({ done: true }));
  return out.join(LF) + LF;
}

const FIXTURES = {
  '': listing('', ['Pictures/', 'report.pdf', 'Report-old.pdf', 'notes.txt']),
  Pictures: listing('Pictures', ['shot.png']),
};

async function opened() {
  // 浅拷贝：用例会往里塞失败响应（见文件末尾的引导态两条），
  // 直接用模块级常量会让状态在用例之间串。
  const c = load(Object.assign({}, FIXTURES));
  c.api.mount(c.view);
  body(c.view).clientHeight = 600;
  c.api.setEnabled(true);
  await tick();
  return c;
}

async function type(c, text) {
  const input = searchInput(c.view);
  input.value = text;
  input.dispatch('input');
  await tick();
}

test('搜索框在滚动容器**内**的 sticky 头块里，且换目录不重建', async () => {
  // 这一条上一版断言的是「在容器外」，理由是 renderShell 会 `textContent = ''`。
  // **那个结论被装机反馈推翻了**：容器有 scrollbar-gutter: stable，内容右侧
  // 让出了一条滚动条的位置，容器外的元素拿不到那条槽位 ——
  // 于是右边缘与列表差一条滚动条宽（「左侧对齐、右侧没对齐」）。
  //
  // 现在搜索行住在容器内的 sticky 头块里，宽度自动与列表一致；
  // 焦点靠 renderShell 只清头块以外的东西来保住（clearBelowSticky）。
  // 「不丢焦点」这个要求没变，换了个办法满足。
  const c = await opened();
  const search = byClass(c.view, 'fk-search')[0];
  assert.ok(search, '没有搜索框');

  let node = search.parentNode;
  let insideBody = false;
  while (node) {
    if (node === body(c.view)) insideBody = true;
    node = node.parentNode;
  }
  assert.equal(insideBody, true, '搜索框必须在滚动容器内，否则宽度与列表差一条滚动条');

  // 换个目录，它必须还是**同一个节点**（重建就等于丢焦点）。
  c.api.navigate('Pictures');
  await tick();
  assert.equal(byClass(c.view, 'fk-search')[0], search, '换目录后搜索框被重建了');
});

test('换目录只清列表，不清 sticky 头块', async () => {
  // clearBelowSticky 的本体断言。第一版用 `bodyEl.lastChild` 写循环 ——
  // mini-dom 只有 firstChild，于是清理**静默失效**，换目录后 body 里堆了
  // 两份列表（打点才发现）。第二版「取出去再放回来」把头块挪到了末尾，
  // 下一轮就认不出它。
  const c = await opened();
  const sticky = byClass(c.view, 'fk-files-sticky')[0];
  assert.ok(sticky, '没有 sticky 头块');

  c.api.navigate('Pictures');
  await tick();

  assert.equal(byClass(c.view, 'fk-files-sticky')[0], sticky, '头块被重建了');
  assert.equal(
    byClass(c.view, 'fk-files-list').length,
    1,
    '换目录后残留了上一个目录的列表',
  );
  assert.equal(body(c.view).children[0], sticky, '头块必须留在第一位，否则下次认不出它');
});

test('输入关键词只过滤当前目录', async () => {
  const c = await opened();
  // 目录优先；文件按 NAME_ORDER：notes < report-old < report（lowercase 后比码位）。
  assert.deepEqual(titles(c.view), ['Pictures', 'notes.txt', 'Report-old.pdf', 'report.pdf']);

  await type(c, 'report');

  // 不区分大小写；目录不匹配所以不出现。
  assert.deepEqual(titles(c.view), ['Report-old.pdf', 'report.pdf']);
});

test('关键词也能匹配到目录', async () => {
  const c = await opened();
  await type(c, 'pict');
  assert.deepEqual(titles(c.view), ['Pictures']);
});

test('过滤时不重新请求 —— 条目已经在内存里', async () => {
  const c = await opened();
  const before = c.asked.length;
  await type(c, 'report');
  assert.equal(c.asked.length, before);
});

test('换目录清空关键词', async () => {
  // 带着上个目录的词进新目录，看到的是一个「空目录」假象。
  const c = await opened();
  await type(c, 'report');
  assert.deepEqual(titles(c.view), ['Report-old.pdf', 'report.pdf']);

  c.api.navigate('Pictures');
  await tick();

  assert.equal(searchInput(c.view).value, '', '输入框里的词没被清掉');
  assert.deepEqual(titles(c.view), ['shot.png'], '新目录必须完整显示');
});

test('过滤无命中时说「没有匹配」，不说「文件夹是空的」', async () => {
  const c = await opened();
  await type(c, '不存在的东西');

  assert.deepEqual(titles(c.view), []);
  const notice = byClass(c.view, 'fk-panel-notice')[0];
  assert.ok(notice, '应当有一句提示');
  assert.equal(
    notice.textContent,
    'app.files.searchEmpty',
    '无命中与空目录是两句不同的话，实际：' + notice.textContent,
  );
});

test('清空关键词后完整列表回来', async () => {
  const c = await opened();
  await type(c, 'report');
  await type(c, '');
  assert.deepEqual(titles(c.view), ['Pictures', 'notes.txt', 'Report-old.pdf', 'report.pdf']);
});

test('过滤态下的全选只选中可见的那些', async () => {
  // 「全部」的范围随过滤走。选中整个目录会让用户以为他选的是屏幕上那几个。
  const c = await opened();
  await type(c, 'report');

  const selectAll = byClass(c.view, 'fk-icon-btn')
    .filter((b) => b.children.some((x) => x.getAttribute('data-icon') === 'select_all'))[0];
  assert.ok(selectAll, '没有全选按钮');
  selectAll.dispatch('click');
  await tick();

  const count = byClass(c.view, 'fk-toolbar-count')[0];
  assert.ok(count, '没有选择工具栏');
  assert.ok(
    count.textContent.indexOf('2') >= 0,
    '全选应当只选中过滤后可见的 2 项，实际：' + count.textContent,
  );
});

test('加载中的计数在 sticky 头块里，完成后只留列表尾那一处', async () => {
  // 用户裁决 2026-09-08：两处都显示「共 N 项」= 同一句话同屏说两次。
  // 头块那句只在加载中出现 —— 它消失本身就是「加载完了」的信号，不用读字。
  const c = await opened();

  // 本 harness 的列表一次到达即完成，所以这里看的是**完成态**。
  const head = byClass(c.view, 'fk-files-loading-count')[0];
  assert.ok(head, '头块里没有加载计数元素');
  assert.equal(head.hidden, true, '完成后头块那句必须收起');

  const footer = byClass(c.view, 'fk-files-footer')[0];
  assert.ok(footer, '列表尾没有页脚');
  assert.equal(footer.textContent, 'app.files.total:4', '尾部应当说「共 N 项」');
});

test('加载计数与页脚成对更新 —— 读的是同一份数据', () => {
  // 分开调用的话总有一处会被漏掉，那正是 v1.20.0 那批
  // 「一份状态没跟着它的依据一起更新」缺陷的形状。
  const src = fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8');
  const at = src.indexOf('function syncCounts');
  assert.ok(at > 0, 'syncCounts 不存在');
  const fn = src.slice(at, src.indexOf('function ', src.indexOf('{', at)));
  assert.ok(fn.indexOf('syncFooter()') >= 0, 'syncCounts 没有更新页脚');
  assert.ok(fn.indexOf('syncLoadingCount()') >= 0, 'syncCounts 没有更新头块计数');
});

test('引导态不许摧毁 sticky 头块 —— 搜索框与面包屑必须活过它', async () => {
  // 2026-09-12 装机反馈：面板上方的搜索行与面包屑整块消失了（Screenshot_35）。
  //
  // 根因：`renderGuidance` 用的是 `bodyEl.textContent = ''`，把头块一起清了。
  // 而头块**只在 mount 时建一次**（搜索行住在里面，重建等于丢焦点），
  // 所以一次瞬时的引导态（权限 403 / 目录 404）就让它**永久**消失 ——
  // 之后每次渲染都只重建列表，只有刷新整页才能恢复。
  //
  // 引导态是可恢复的状态（用户去手机上授权、或换个目录），头块必须活过它。
  const c = await opened();
  const sticky = byClass(c.view, 'fk-files-sticky')[0];
  const search = byClass(c.view, 'fk-search')[0];
  assert.ok(sticky && search, '前置：头块与搜索框应当已经存在');

  // 进一个会 403 的目录（缺存储权限）。
  c.listings.Denied = { __fail: { status: 403, code: 'storage_permission_required' } };
  c.api.navigate('Denied');
  await tick();

  assert.ok(byClass(c.view, 'fk-guidance')[0], '前置：应当进入了引导态');

  // **断言布尔值，不要把 DOM 节点交给 assert.equal。** 失败时 node 会序列化两边
  // 做 diff，而 mini-dom 的节点父子互相引用 —— 序列化一棵脱离文档的子树会
  // 耗尽堆内存，于是「断言失败」变成 `FATAL ERROR: Reached heap limit`，
  // 进程被杀、只报「1 tests / 1 fail」，看不出是哪条断言（2026-09-12 逼红时踩到）。
  assert.equal(
    byClass(c.view, 'fk-files-sticky')[0] === sticky,
    true,
    '引导态把 sticky 头块清掉或重建了',
  );
  assert.equal(
    byClass(c.view, 'fk-search')[0] === search,
    true,
    '引导态把搜索框清掉或重建了 —— 焦点会跟着丢',
  );
  assert.equal(
    body(c.view).children[0] === sticky,
    true,
    '头块必须仍在第一位，否则下一轮 clearBelowSticky 认不出它',
  );
});

test('从引导态回到正常目录后，头块与列表都在', async () => {
  // 上一条只证明头块没被清掉；这一条证明它**仍然工作** ——
  // 缺陷的实际表现是「之后每次渲染都只重建列表」，所以要走一个来回。
  const c = await opened();
  c.listings.Denied = { __fail: { status: 403, code: 'storage_permission_required' } };
  c.api.navigate('Denied');
  await tick();

  c.api.navigate('Pictures');
  await tick();

  assert.ok(byClass(c.view, 'fk-files-sticky')[0], '回到正常目录后头块不见了');
  assert.equal(byClass(c.view, 'fk-guidance').length, 0, '引导态没有被清掉');
  assert.deepEqual(titles(c.view), ['shot.png'], '新目录的列表没渲染出来');
  assert.equal(
    byClass(c.view, 'fk-files-list').length,
    1,
    '残留了多份列表 —— clearBelowSticky 没生效',
  );
});

test('头块被外力清掉后能自愈 —— 纵深防御那一层', async () => {
  // `renderGuidance` 已经改成走 clearBelowSticky（上两条盯着它），
  // 但那只挡住了**已知的**那一处。`clearBelowSticky` 里还有一行
  // 「头块不在 body 里就挂回去」，防的是**以后**有人在别处再写一次
  // `bodyEl.textContent = ''` —— 头块只在 mount 时建一次，
  // 丢了就永久丢，刷新整页才能恢复，代价与收益完全不对称。
  //
  // 这一条直接模拟那个「外力」：手动清空 body，再触发一次正常渲染。
  // 没有这条断言的话，自愈那一行可以被静默删掉（逼红实测零条红）。
  const c = await opened();
  const sticky = byClass(c.view, 'fk-files-sticky')[0];
  assert.ok(sticky, '前置：头块应当存在');

  // 外力：把 body 整份清掉（就是那个反复出现的错误写法）
  const bodyEl = body(c.view);
  Array.prototype.slice.call(bodyEl.children)
    .forEach(function (ch) { bodyEl.removeChild(ch); });
  assert.equal(byClass(c.view, 'fk-files-sticky').length, 0, '前置：头块应当已被清掉');

  // 一次正常渲染就该把它接回来
  c.api.navigate('Pictures');
  await tick();

  assert.equal(
    byClass(c.view, 'fk-files-sticky')[0] === sticky,
    true,
    '头块没有自愈 —— 它只在 mount 时建一次，丢了就永久丢',
  );
  assert.equal(
    bodyEl.children[0] === sticky,
    true,
    '自愈后头块必须回到第一位，否则 clearBelowSticky 下一轮认不出它',
  );
  assert.deepEqual(titles(c.view), ['shot.png'], '自愈不该影响列表渲染');
});
