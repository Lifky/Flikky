const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const scan = require('./scan.js');
const { createDocument } = require('./mini-dom.js');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const app = read('app.js');
const start = app.indexOf('    // ---- lightbox fullscreen preview');
const end = app.indexOf('    // ---- three-pane shell', start);
assert.ok(start >= 0 && end > start, 'sanity: the existing lightbox source slice must be present');
const lightboxSource = app.slice(start, end);

function loadLightbox() {
  const doc = createDocument();
  const lightbox = doc.register('lightbox');
  lightbox.hidden = true;
  const content = doc.register('lightbox-content');
  content.querySelector = (selector) => content.children.find(
    (child) => child.tagName === String(selector).toUpperCase(),
  ) || null;
  lightbox.appendChild(content);
  lightbox.appendChild(doc.register('lightbox-close', 'button'));
  doc.removeEventListener = () => {};
  const preloads = [];
  function Image() {
    const image = doc.createElement('img');
    preloads.push(image);
    return image;
  }
  const ctx = { document: doc, Image };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(lightboxSource + '\nglobalThis.openLightboxForTest = openLightbox;', ctx);
  return { doc, lightbox, content, preloads, open: ctx.openLightboxForTest };
}

function trackSource(el) {
  const values = [];
  let value = '';
  Object.defineProperty(el, 'src', {
    configurable: true,
    get: () => value,
    set: (next) => {
      value = next === null || next === undefined ? '' : String(next);
      values.push(value);
    },
  });
  return values;
}

test('image lightbox shows the thumbnail until the full image has loaded', () => {
  const c = loadLightbox();
  const originalCreate = c.doc.createElement.bind(c.doc);
  const sourceHistories = [];
  c.doc.createElement = (tag) => {
    const el = originalCreate(tag);
    if (String(tag).toLowerCase() === 'img') sourceHistories.push(trackSource(el));
    return el;
  };
  c.open({ kind: 'image', fullUrl: '/full', thumbnailUrl: '/thumb' });
  const visible = c.content.children[0];
  assert.equal(visible.tagName, 'IMG');
  assert.deepEqual(sourceHistories[0], ['/thumb'], 'opening must paint the cached thumbnail immediately');
  assert.equal(c.preloads.length, 1, 'the original image loads off-screen');
  assert.equal(c.preloads[0].src, '/full');

  c.preloads[0].dispatch('load');
  assert.deepEqual(sourceHistories[0], ['/thumb', '/full']);
  assert.equal(sourceHistories[0].includes(''), false, 'the displayed image must never flash an empty source');
});

test('video lightbox uses the thumbnail as poster without image preloading', () => {
  const c = loadLightbox();
  c.open({ kind: 'video', fullUrl: '/movie', thumbnailUrl: '/poster' });
  const video = c.content.children[0];
  assert.equal(video.tagName, 'VIDEO');
  assert.equal(video.src, '/movie');
  assert.equal(video.poster, '/poster');
  assert.equal(c.preloads.length, 0);
});

test('session, storage and favorite callers provide their authenticated media urls', () => {
  const sources = {
    session: scan.scrub(app),
    storage: scan.scrub(read('panel-files.js')),
    favorite: scan.scrub(read('panel-favorites.js')),
  };
  assert.ok(sources.session.indexOf('openLightbox({') >= 0,
    'sanity: the session preview call must be present');
  assert.ok(sources.session.indexOf('`/api/files/${fileId}?inline=1`') >= 0);
  assert.ok(sources.session.indexOf('`/api/files/${fileId}/thumb`') >= 0);
  assert.ok(sources.storage.indexOf("downloadUrl(path) + '&inline=1'") >= 0);
  assert.ok(sources.storage.indexOf('thumbnailUrl(path)') >= 0);
  assert.ok(sources.favorite.indexOf('`/api/favorites/${item.id}/file?inline=1`') >= 0);
  assert.ok(sources.favorite.indexOf('`/api/favorites/${item.id}/thumb`') >= 0);
});

test('the existing lightbox remains the only full-screen preview implementation', () => {
  const scrubbedApp = scan.scrub(app);
  const panels = scan.scrub(read('panel-files.js') + '\n' + read('panel-favorites.js'));
  assert.equal(scrubbedApp.split('function openLightbox(').length - 1, 1,
    'there must be exactly one lightbox function');
  assert.ok(scan.scrub(lightboxSource).indexOf("createElement('video')") >= 0,
    'sanity: the guarded lightbox slice must contain its video renderer');
  assert.ok(scan.scrub(lightboxSource).indexOf("createElement('img')") >= 0,
    'sanity: the guarded lightbox slice must contain its image renderer');
  assert.equal(panels.indexOf("createElement('video')") >= 0, false,
    'panels must route into the shared lightbox instead of creating a preview');
  assert.equal(panels.indexOf("className = 'lightbox-media'") >= 0, false,
    'panels must not copy the full-screen preview structure');
});

/*
 * ── 打开与变清晰的过渡（2026-09-12 装机反馈）──────────────────────────
 *
 * 此前 `.lightbox` 一条动效都没有：`hidden = false` 之后整块 85% 黑遮罩
 * 连同图片瞬间出现。渐进式加载的第二段（缩略图 → 原图）同样是硬切。
 *
 * 两段动效**刻意分开档位**：
 *   · scrim 只有明暗变化 → effects 档（231ms）
 *   · 媒体有尺寸变化    → spatial 档（317ms）
 * 混成一条会让「幕布拉上」与「画面推出」同速，看起来像整块图片被贴上来。
 */

const chatCss = scan.stripBlockComments(read('chat.css'));

/** 取某条规则的规则体。整条选择器相等，不用子串 —— 子串会把派生选择器搞混。 */
function rule(css, selector) {
  const hits = [];
  const re = /([^{}]+)\{([^}]*)\}/g;
  let m = re.exec(css);
  while (m) {
    if (m[1].trim() === selector) hits.push(m[2]);
    m = re.exec(css);
  }
  return hits.length ? hits[hits.length - 1] : null;
}

test('the lightbox scrim and its media animate on separate specs', () => {
  const scrim = rule(chatCss, '.lightbox');
  const media = rule(chatCss, '.lightbox-media');
  assert.ok(scrim, '.lightbox 的规则不见了');
  assert.ok(media, '.lightbox-media 的规则不见了');

  assert.match(scrim, /animation:\s*flikky-lightbox-in/, 'scrim 没有进场动效 —— 打开会是硬切');
  assert.match(media, /animation:\s*flikky-lightbox-media-in/, '媒体没有进场动效');

  // **档位要从 animation 那条声明里取，不是在整个规则体里 `match`。**
  // 规则体里还有别的属性也可能提到弹簧 token，整体 match 会让
  // 「把档位换掉」这个改动匹配到旁边那一处而不红（逼红实测零条红）。
  const specOf = (body) => {
    const m = body.match(/animation:\s*[\w-]+\s+([\s\S]*?);/);
    assert.ok(m, '取不到 animation 声明：' + body);
    return m[1];
  };
  const scrimSpec = specOf(scrim);
  const mediaSpec = specOf(media);

  assert.ok(
    scrimSpec.indexOf('effects-default') >= 0 && scrimSpec.indexOf('spatial') < 0,
    'scrim 用的不是 effects 档。它只有明暗变化，走 spatial 档会比媒体还慢：' + scrimSpec,
  );
  assert.ok(
    mediaSpec.indexOf('spatial-default') >= 0 && mediaSpec.indexOf('effects') < 0,
    '媒体用的不是 spatial 档 —— 有尺寸变化的一律走它（项目动效语汇）：' + mediaSpec,
  );
  assert.notEqual(scrimSpec.trim(), mediaSpec.trim(), 'scrim 与媒体用了同一条时长/曲线 —— 两段动效同速就失去分层的意义');
});

test('the media scales up only slightly', () => {
  // 幅度必须小：全屏尺寸上的大幅缩放会显得图片「飞过来」，
  // 而这只是一次打开，不是转场。0.9~0.99 之间。
  const kf = chatCss.match(/@keyframes\s+flikky-lightbox-media-in\s*\{([\s\S]*?)\}\s*\}/);
  const block = kf ? kf[1] : (chatCss.match(/@keyframes\s+flikky-lightbox-media-in\s*\{([\s\S]*?)\n\}/) || [])[1];
  assert.ok(block, 'flikky-lightbox-media-in 的关键帧不见了');
  const scale = block.match(/scale\(\s*(\.\d+|0\.\d+)\s*\)/);
  assert.ok(scale, '媒体进场没有 scale —— 只有淡入的话「推出来」那一下就没了');
  const v = parseFloat(scale[1]);
  assert.ok(v >= 0.9 && v < 1, `起始 scale 应当在 0.9~1 之间，实际 ${v}（幅度大了会像图片飞过来）`);
});

test('swapping the thumbnail for the full image fades', () => {
  // 渐进式加载的第二段。不淡的话「模糊 → 清晰」是硬切，比没有渐进式还突兀。
  const sharpen = rule(chatCss, ".lightbox-media[data-full='1']");
  assert.ok(sharpen, "缺少 .lightbox-media[data-full='1'] 规则 —— 换原图那一下会是硬切");
  assert.match(sharpen, /animation:\s*flikky-lightbox-sharpen/, '换原图没有淡入');

  // JS 侧必须真的打这个标记，否则上面那条 CSS 永远不生效。
  assert.match(
    lightboxSource,
    /dataset\.full\s*=\s*['"]1['"]/,
    'openLightbox 没有在原图 onload 后打 data-full —— CSS 那条规则是死规则',
  );
  // 而且必须在 onload 里打，不能在设 src 时就打（那时还是缩略图）。
  const onload = lightboxSource.slice(lightboxSource.indexOf('full.addEventListener'));
  assert.match(
    onload.slice(0, 400),
    /dataset\.full/,
    'data-full 不是在原图 onload 后打的 —— 提前打等于给缩略图淡入',
  );
});

test('reduced motion turns all three off', () => {
  // 无障碍：三条动效都要能被关掉，与项目里其他动效同一处理。
  const reduced = chatCss.slice(chatCss.indexOf('prefers-reduced-motion'));
  assert.ok(reduced.length > 0, 'chat.css 里没有 prefers-reduced-motion 块');
  ['.lightbox', '.lightbox-media'].forEach((sel) => {
    assert.ok(
      reduced.indexOf(sel) >= 0,
      `${sel} 没有在 reduced-motion 下关掉动效`,
    );
  });
});
