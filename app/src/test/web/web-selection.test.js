const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const base = read('base.css');
const chat = read('chat.css');
const panels = read('panels.css');
const pages = read('pages.css');

const SHEETS = { 'base.css': base, 'chat.css': chat, 'panels.css': panels, 'pages.css': pages };

/** 取出某条规则块（第一处匹配）。 */
function rule(css, sel) {
  const re = new RegExp(sel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*\\{[^}]*\\}');
  const m = css.match(re);
  assert.ok(m, `rule not found: ${sel}`);
  return m[0];
}

test('icon glyphs are generated content, not DOM text', () => {
  // 手机浏览器的长按取词层（小米、华为、多数第三方 Android 浏览器）直接读 DOM
  // 文本，**不理 user-select** —— 桌面守规矩所以只在手机上复现。既然靠 CSS 拦不住，
  // 就让那段文本不存在：字形改由 ::before 的 content: attr(data-icon) 生成，
  // 伪元素内容规范层面就不可选中、不可复制。
  assert.match(base, /\.material-symbols-outlined::before\s*\{\s*content:\s*attr\(data-icon\)/);

  // 每个静态图标 span 都必须带 data-icon 且自身为空。漏一个是「图标不显示」——
  // 一眼可见，不是静默降级，但还是钉住，免得改动时反复踩。
  for (const page of fs.readdirSync(WEB).filter((f) => f.endsWith('.html'))) {
    const html = fs.readFileSync(path.join(WEB, page), 'utf8');
    for (const m of html.matchAll(/<span([^>]*class="material-symbols-outlined"[^>]*)>([^<]*)<\/span>/g)) {
      assert.match(m[1], /data-icon="[a-z0-9_]+"/, `${page}: icon span without data-icon: ${m[0]}`);
      assert.equal(m[2].trim(), '', `${page}: icon span still carries text: ${m[0]}`);
    }
  }

  // 动态建的图标同理。按「变量名」查而不是按某个固定的工厂形状 —— export.js 是
  // 内联建的、后面没有 return，第一版按形状匹配的断言在它上面直接找不到目标。
  let iconVars = 0;
  for (const js of ['app.js', 'export.js', 'panel-favorites.js', 'panel-settings.js']) {
    const src = fs.readFileSync(path.join(WEB, js), 'utf8');
    const named = [...src.matchAll(/(\w+)\.className = 'material-symbols-outlined'/g)].map((m) => m[1]);
    assert.ok(named.length > 0, `${js}: no element is given the icon class — did it move?`);
    for (const v of new Set(named)) {
      iconVars += 1;
      const esc = v.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      assert.equal(
        new RegExp(`${esc}\\.textContent\\s*=`).test(src), false,
        `${js}: ${v}.textContent is assigned — that puts the glyph back into DOM text`,
      );
      assert.match(
        src, new RegExp(`${esc}\\.dataset\\.icon\\s*=`),
        `${js}: ${v} never gets data-icon, so it would render as an empty box`,
      );
    }
  }
  assert.ok(iconVars >= 4, `expected at least one icon element per script, found ${iconVars}`);
});

test('icon ligatures cannot be selected, with the vendor prefix', () => {
  // Material Symbols 的图标**本体就是文本**（连字）。长按会选出 "arrow_upward"
  // 这种内部标识符 —— 用户实测把它当成消息发出去过（Screenshot_27）。
  // 无前缀那条一直都在；缺的是 -webkit-，小米自带浏览器这类较旧的 Chromium 分支
  // 只认带前缀的，所以「已经写了 user-select:none」并不等于线上真的关掉了。
  const icon = rule(base, '.material-symbols-outlined');
  assert.match(icon, /-webkit-user-select:\s*none/);
  assert.match(icon, /(?<!-webkit-)user-select:\s*none/);
  // iOS 长按的「拷贝/查找」浮层单靠 user-select 关不掉。
  assert.match(icon, /-webkit-touch-callout:\s*none/);
});

test('the default is non-selectable, decided once at the body', () => {
  // 取「默认关 + 内容白名单」而不是「默认开 + 逐个图标关」：后者每加一个元素都要
  // 记得关，漏一个就复现；前者漏一个只是少一处可复制。
  // 必须锚到行首：`html, body { height: 100% }` 里也有一个 "body"，
  // 不锚就会先匹配到那一条，断言变成对着 reset 规则找 user-select。
  const m = base.match(/^body\s*\{[^}]*\}/m);
  assert.ok(m, 'no standalone body rule in base.css');
  const body = m[0];
  assert.match(body, /-webkit-user-select:\s*none/);
  assert.match(body, /(?<!-webkit-)user-select:\s*none/);
});

test('editable controls opt back in, or you cannot select inside the composer', () => {
  // 祖先的 user-select:none 在部分引擎里会连输入框内的选词/拖选一起禁掉。
  const inputs = base.match(/input,\s*\n\s*textarea,\s*\n\s*\[contenteditable="true"\]\s*\{[^}]*\}/);
  assert.ok(inputs, 'no user-select opt-in for input/textarea/contenteditable');
  assert.match(inputs[0], /-webkit-user-select:\s*text/);
  assert.match(inputs[0], /(?<!-webkit-)user-select:\s*text/);
});

test('user content is selectable — a received message exists to be copied', () => {
  // 会话页：文本气泡、文件名、文件大小、媒体标题。
  const white = chat.match(/\.bubble,\s*\n\.file-bubble a,\s*\n\.file-bubble \.size,\s*\n\.thumb-caption\s*\{[^}]*\}/);
  assert.ok(white, 'chat.css has no content white-list');
  assert.match(white[0], /-webkit-user-select:\s*text/);
  assert.match(white[0], /(?<!-webkit-)user-select:\s*text/);

  // 收藏行的标题/副标题是用户存进来的内容；设置面板只开「关于」那一处副标题
  // （版本号与 GitHub 地址，提 issue 要贴）。
  assert.match(panels, /#fav-list \.fk-item-title,[\s\S]{0,120}user-select:\s*text/);
  assert.match(panels, /#view-settings \.fk-item-sub/);

  // 导出页：摘要数字与会话名。会话行正文在 mdui-list-item 的 shadow root 里，
  // 而 user-select 是继承属性，写在宿主上就能穿进去。
  assert.match(pages, /\.fk-summary-value,\s*\n#session-list\s*\{[^}]*user-select:\s*text/);
});

test('no white-list selector uses a descendant wildcard', () => {
  // 这条是上面那套能成立的前提。user-select 是继承属性，而图标上是一条**直接
  // 声明** —— 直接声明优先于继承，所以白名单把祖先设成 text 不会渗进图标。
  // 但只要白名单写成 `.file-bubble *`，它就直接匹配到图标元素本身，变成同特异性
  // 竞争，而 base.css 更早加载会输给它 —— 图标又变回可选，而且很难察觉。
  for (const [name, css] of Object.entries(SHEETS)) {
    const bad = [...css.matchAll(/([^\n{}]*\*[^\n{}]*)\{[^}]*user-select:\s*text/g)].map((m) => m[1].trim());
    assert.deepEqual(bad, [], `${name}: wildcard selector grants user-select to icons: ${bad.join(' | ')}`);
  }
});

test('chrome that used to be copyable no longer is', () => {
  // 这些都不在白名单里，也没有自己的 user-select 声明 —— 于是从 body 继承到 none。
  // 断言方式是「它们没有把自己开回来」，这正是继承规则下唯一需要保证的事。
  const chromeSelectors = [
    '.header-peer-name',   // 对方名：界面标签
    '.fk-chat-stats',      // 运行时长/文件数/速率
    '.chat-list-watermark',
    '.fk-navbar-item',
    '.fk-rail-item',
    '.fk-panel-title',
    '.fk-pin-cell',        // 登录页格子只是 input 的显示镜像
  ];
  for (const sel of chromeSelectors) {
    const re = new RegExp(sel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[^{]*\\{[^}]*user-select:\\s*text');
    for (const [name, css] of Object.entries(SHEETS)) {
      assert.equal(re.test(css), false, `${name}: ${sel} must not be selectable — it is chrome, not content`);
    }
  }
});

test('the avatar stays non-selectable even though its glyph is text', () => {
  // 浏览器端头像用 .avatar-circle，字符头像的内容是一个真字符、图标头像是连字。
  // 两者都是身份标识而非内容。App 端的对应修法是 Avatar.kt 里的 DisableSelection。
  assert.match(rule(chat, '.avatar-circle'), /user-select:\s*none/);
});
