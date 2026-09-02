const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { ndjson } = require('./ndjson.js');
const { createDocument, byClass } = require('./mini-dom');

const WEB = path.join(__dirname, '../../main/assets/web');
const src = fs.readFileSync(path.join(WEB, 'panel-files.js'), 'utf8');

const OK_LISTING = {
  path: 'DCIM',
  entries: [
    { name: 'a.png', isDir: false, size: 100, mtime: 1, mime: 'image/png', childCount: null, restricted: false },
  ],
};

/**
 * replies 是一串按调用顺序消费的应答，让一个用例可以先成功、再失败——
 * 「断线时不清空已有列表」这类断言必须先有列表才测得出来。
 */
function load({ replies = [{ status: 200, body: OK_LISTING }] } = {}) {
  const doc = createDocument();
  const root = doc.register('view-files');
  const fetched = [];
  const errors = [];
  const infos = [];
  let i = 0;
  const ctx = {
    console,
    document: doc,
    setTimeout(fn) { fn(); return 1; },
    clearTimeout() {},
    Promise,
    encodeURIComponent,
    fetch(url, opts) {
      fetched.push({ url, opts: opts || {} });
      // 跑飞检测：失败分支若自动重发请求就会死循环（plan 原案的「400 退回根目录并重拉」
      // 与「404 重拉当前目录」都会）。不设这道闸，症状是 node 直接 heap out of memory，
      // 读日志的人根本看不出是循环。设了，就是一句人话。
      if (fetched.length > 40) {
        throw new Error('runaway request loop: ' + fetched.length
          + ' requests, last=' + url + ' — a failure branch is re-requesting on its own');
      }
      const r = replies[Math.min(i, replies.length - 1)];
      i += 1;
      if (r.throwNetwork) return Promise.reject(new Error('offline'));
      return Promise.resolve({
        ok: r.status >= 200 && r.status < 300,
        status: r.status,
        json: () => Promise.resolve(r.body === undefined ? {} : r.body),
        text: () => Promise.resolve(ndjson(r.body || { path: '', entries: [] })),
      });
    },
    flikkyI18n: {
      t: (key, values) => (values ? key + ':' + JSON.stringify(values) : key),
      onChange(cb) { cb(); return () => {}; },
    },
  };
  ctx.window = ctx;
  ctx.globalThis = ctx;
  ctx.window.flikky = {
    fileSymbolName: (mime) => 'sym(' + (mime || '') + ')',
    formatSize: (b) => b + 'B',
    showError: (t) => errors.push(t),
    showInfo: (t) => infos.push(t),
  };
  vm.runInNewContext(src, ctx, { filename: 'panel-files.js' });
  const api = ctx.window.flikkyPanels.files;
  return { doc, root, api, fetched, errors, infos, ctx };
}

const flush = async (n = 16) => { for (let i = 0; i < n; i += 1) await Promise.resolve(); };
const view = (doc) => doc.getElementById('view-files');
const rows = (doc) => byClass(view(doc), 'fk-item');
const buttonsIn = (n) => (n ? n.children.filter((c) => c.tagName === 'BUTTON') : []);
const guidance = (doc) => byClass(view(doc), 'fk-guidance')[0];

async function mounted(opts) {
  const c = load(opts);
  c.api.mount(c.root);
  // mount 刻意不发请求（装机验收缺陷 1b：它在 peer-info 之前跑，不知道主开关状态，
  // 关闭时那一次请求会撞 404 并被报成「这个位置已经不存在了」）。
  // 第一次加载由主开关驱动，测试里显式打开。
  c.api.setEnabled(true);
  await flush();
  return c;
}

test('every storage request rides the session cookie', async () => {
  const { fetched } = await mounted();
  assert.ok(fetched.length >= 1, 'the panel never fetched');
  for (const f of fetched) {
    assert.equal(f.opts.credentials, 'same-origin',
      'without credentials the request is anonymous and gets a 401');
  }
});

test('a missing permission renders guidance, not an error, and offers no retry', async () => {
  const { doc, errors } = await mounted({
    replies: [{ status: 403, body: { code: 'storage_permission_required' } }],
  });
  const g = guidance(doc);
  assert.ok(g, 'no guidance block for storage_permission_required');
  assert.match(g.textContent, /needPermission/,
    'guidance must explain what to do on the phone');
  // 用户要做的事在手机上。给「重试」按钮会让人以为点它能解决。
  assert.equal(buttonsIn(g).length, 0, 'guidance must not offer a retry button');
  // 也不该弹 snackbar —— 这不是一次失败，是一个需要用户去别处操作的状态。
  assert.deepEqual(errors, [], 'permission guidance must not also raise an error toast');
  assert.equal(rows(doc).length, 0, 'no rows when we have no permission');
});

test('a restricted path renders its own guidance, distinct from the permission one', async () => {
  const { doc } = await mounted({
    replies: [{ status: 403, body: { code: 'storage_restricted' } }],
  });
  const g = guidance(doc);
  assert.ok(g, 'no guidance block for storage_restricted');
  // 两种 403 塌成一种，用户就分不清「去手机授权」和「系统根本不给」。
  assert.match(g.textContent, /restricted/i);
  assert.equal(/needPermission/.test(g.textContent), false,
    'a system restriction must not be reported as a missing grant');
});

test('an invalid path retreats to a safe location, and never loops', async () => {
  // 400 说明客户端记着一个非法路径，必须退回安全位置。
  // **但不能自动重发请求**：若退回去的那个路径也 400，就成了无限循环。
  const c = await mounted({
    replies: [{ status: 200, body: OK_LISTING }, { status: 400 }],
  });
  const before = c.fetched.length;
  c.api.navigate('DCIM/../../etc');
  await flush(24);
  assert.ok(c.errors.length >= 1, 'a 400 should tell the user the path was invalid');
  // 一次显式导航只许产生一次请求。多出来的就是自动重试，也就是循环的入口。
  assert.equal(c.fetched.length, before + 1,
    'a failed load must not fire a follow-up request: ' + c.fetched.map((f) => f.url).join(' | '));
  // 列表退回最后一次成功的内容，而不是空白。
  assert.equal(rows(c.doc).length, 1, 'the last good listing must stay on screen');
});

test('a vanished directory leaves you where you were, and never loops', async () => {
  const c = await mounted({
    replies: [{ status: 200, body: OK_LISTING }, { status: 404 }],
  });
  const before = c.fetched.length;
  c.api.navigate('DCIM/vanished');
  await flush(24);
  assert.ok(c.errors.length >= 1, 'a 404 should say the directory is gone');
  assert.equal(c.fetched.length, before + 1,
    'a 404 must not trigger a reload of the same path — that is an infinite loop');
  assert.equal(rows(c.doc).length, 1, 'the last good listing must stay on screen');
});

test('losing the connection keeps the last listing on screen', async () => {
  const c = await mounted({
    replies: [{ status: 200, body: OK_LISTING }, { throwNetwork: true }],
  });
  assert.equal(rows(c.doc).length, 1, 'precondition: one row rendered');
  c.api.navigate('DCIM');
  await flush();
  // 断线时清空列表，用户会以为文件都没了。保留最后一次结果。
  assert.equal(rows(c.doc).length, 1, 'the last listing must survive a dropped connection');
});

test('reconnecting returns to the root, because the phone may have changed', async () => {
  const c = await mounted({ replies: [{ status: 200, body: OK_LISTING }] });
  assert.equal(typeof c.ctx.window.flikky.resetStorageBrowser, 'function');
  c.ctx.window.flikky.resetStorageBrowser();
  c.api.navigate(undefined);
  await flush();
  const lastUrl = c.fetched[c.fetched.length - 1].url;
  assert.match(lastUrl, /path=$/, 'after a reset the next load must target the root: ' + lastUrl);
});

test('download links are authenticated same-origin URLs, properly encoded', async () => {
  const listing = {
    path: 'Download',
    entries: [
      { name: '报告 v2.pdf', isDir: false, size: 10, mtime: 1, mime: 'application/pdf', childCount: null, restricted: false },
    ],
  };
  const { doc } = await mounted({ replies: [{ status: 200, body: listing }] });
  const btn = buttonsIn(byClass(rows(doc)[0], 'fk-item-trail')[0])[0];
  assert.ok(btn, 'no download button');
  btn.dispatch('click');
  const a = doc.created.filter((e) => e.tagName === 'A' && e.getAttribute('href'));
  assert.equal(a.length, 1, 'exactly one anchor should be created per download');
  const href = a[0].getAttribute('href');
  // 鉴权同源流式 URL，不走 Blob（红线）。中文与空格必须编码过。
  assert.match(href, /^\/api\/storage\/file\?path=/);
  assert.match(href, /Download%2F/, 'the path separator must be encoded: ' + href);
  assert.equal(/ /.test(href), false, 'raw spaces in href: ' + href);
  assert.equal(a[0].getAttribute('download'), '报告 v2.pdf');
  assert.equal(/blob:/.test(src), false, 'files must stream from the authenticated URL, not a Blob');
});
