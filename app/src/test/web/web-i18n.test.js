const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const i18nPath = path.resolve(__dirname, '../../main/assets/web/i18n.js');

function loadI18n(querySelectorAll = () => []) {
    const source = fs.readFileSync(i18nPath, 'utf8');
    const documentElement = {
        lang: '',
        setAttribute(name, value) {
            if (name === 'lang') this.lang = value;
        },
    };
    const context = {
        document: {
            documentElement,
            querySelectorAll,
        },
        fetch: async () => ({ ok: false }),
        setInterval() { return 1; },
        clearInterval() {},
    };
    context.window = context;
    vm.runInNewContext(source, context);
    return { i18n: context.flikkyI18n, documentElement, source };
}

test('English can be selected and interpolated', () => {
    const { i18n, documentElement } = loadI18n();

    i18n.setLanguage('en-US');

    assert.equal(documentElement.lang, 'en');
    assert.equal(i18n.t('app.peer_from', { device: 'Pixel' }), 'From Pixel');
    assert.equal(i18n.count('export.sessions', 1), '1 session');
    assert.equal(i18n.count('export.sessions', 2), '2 sessions');
});

test('unsupported tags fall back to the default Chinese language', () => {
    const { i18n, documentElement } = loadI18n();

    i18n.setLanguage('fr-FR');

    assert.equal(documentElement.lang, 'zh-CN');
    assert.equal(i18n.t('login.submit'), '进入');
});

test('translations never use innerHTML', () => {
    const { source } = loadI18n();
    assert.equal(source.includes('innerHTML'), false);
});

test('PIN login card carries no privacy advisory', () => {
    // faa22ba 的决定是「登录页不放隐私/安全劝告」（原文是「建议使用无痕窗口」）。
    // 那次顺带钉上的 login.description 是附带品 —— 它当时本来就没被任何页面引用。
    // v1.19.0 删掉了 <h2>「输入 PIN 码」，说明文字成了页面上唯一一句告诉用户
    // 该做什么的话，所以这一条放开；隐私劝告的禁令原样保留。
    const html = fs.readFileSync(
        path.resolve(__dirname, '../../main/assets/web/login.html'),
        'utf8',
    );
    const { source: i18nSource } = loadI18n();

    assert.equal(html.includes('data-i18n="login.privacy_tip"'), false);
    assert.equal(i18nSource.includes("'login.privacy_tip'"), false);
});

test('the login page explains itself in exactly one line', () => {
    // 六个空格子 + 一个灰掉的按钮，不配文字就是一道谜题。反过来，说明多于一句
    // 又会把这页变回旧版那种「标题 + 说明 + 提示」三段式。
    const html = fs.readFileSync(
        path.resolve(__dirname, '../../main/assets/web/login.html'),
        'utf8',
    );
    const paragraphs = html.match(/<p\b[^>]*data-i18n="[^"]+"/g) || [];
    assert.equal(paragraphs.length, 1, `login.html renders ${paragraphs.length} explanatory paragraphs`);
    assert.match(html, /data-i18n="login\.description"/);
});

test('polling the same language does not overwrite dynamic page state', () => {
    let writes = 0;
    let value = '';
    const element = {
        getAttribute(name) {
            return name === 'data-i18n' ? 'login.submit' : null;
        },
        set textContent(next) {
            writes += 1;
            value = next;
        },
        get textContent() { return value; },
    };
    const { i18n } = loadI18n((selector) => selector === '[data-i18n]' ? [element] : []);
    assert.equal(writes, 1);

    element.textContent = 'Working…';
    const writesAfterDynamicUpdate = writes;
    i18n.setLanguage('zh-CN');

    assert.equal(writes, writesAfterDynamicUpdate);
    assert.equal(element.textContent, 'Working…');
});

// 从 i18n.js 的字典字面量里直接取 key 集合，而不是走 t()。
// 原因见下面那条断言的注释：entryFor 的 zh-CN 回退让 t() 永远看不出「只缺英文」。
function dictionaryKeys(source) {
    const zhStart = source.indexOf("'zh-CN': {");
    const enStart = source.indexOf('\n        en: {');
    const dictEnd = source.indexOf('\n    };');
    assert.ok(zhStart >= 0 && enStart > zhStart && dictEnd > enStart, 'dictionary layout changed');
    const keysIn = (block) =>
        new Set([...block.matchAll(/^\s*'([a-z0-9_.]+)':/gm)].map((m) => m[1]));
    return {
        'zh-CN': keysIn(source.slice(zhStart, enStart)),
        en: keysIn(source.slice(enStart, dictEnd)),
    };
}

test('both dictionaries define exactly the same keys', () => {
    // 这条替代了旧的 `assert.notEqual(i18n.t(key), key)` 写法，那种写法查不出
    // 「只缺英文」：entryFor 是 translations[cur][key] ?? translations['zh-CN'][key] ?? key，
    // 缺英文时 t() 返回的是中文原文，!== key，断言照样通过。整份英文字典全删都能绿。
    const { source } = loadI18n();
    const dicts = dictionaryKeys(source);
    const onlyZh = [...dicts['zh-CN']].filter((k) => !dicts.en.has(k));
    const onlyEn = [...dicts.en].filter((k) => !dicts['zh-CN'].has(k));
    assert.deepEqual(onlyZh, [], `missing from en: ${onlyZh.join(', ')}`);
    assert.deepEqual(onlyEn, [], `missing from zh-CN: ${onlyEn.join(', ')}`);
});

test('every translation key referenced by a page exists in the dictionaries', () => {
    const webDir = path.resolve(__dirname, '../../main/assets/web');
    // 自动枚举而不是硬编码清单：写死清单的失败方式是「新增了 panel-*.js 却忘了加进来」，
    // 于是新文件里的拼错 key 完全没人管。i18n.js 自己是字典而不是消费方，排除。
    const pageFiles = fs
        .readdirSync(webDir)
        .filter((f) => (f.endsWith('.js') || f.endsWith('.html')) && f !== 'i18n.js')
        .sort();
    assert.ok(pageFiles.length >= 8, `expected to find the page files, got ${pageFiles.join(', ')}`);

    const keyPattern = /['"]((?:common|login|app|export)\.[a-z0-9_]+(?:\.[a-z0-9_]+)*)['"]/g;
    const keys = new Set();
    for (const file of pageFiles) {
        const source = fs.readFileSync(path.join(webDir, file), 'utf8');
        for (const match of source.matchAll(keyPattern)) keys.add(match[1]);
    }

    const { source } = loadI18n();
    const dicts = dictionaryKeys(source);
    for (const key of keys) {
        assert.ok(dicts['zh-CN'].has(key), `zh-CN is missing ${key}`);
        assert.ok(dicts.en.has(key), `en is missing ${key}`);
    }
});

test('every web page loads translations before its page script', () => {
    const webDir = path.resolve(__dirname, '../../main/assets/web');
    for (const page of ['login', 'app', 'export']) {
        const html = fs.readFileSync(path.join(webDir, `${page}.html`), 'utf8');
        const i18nIndex = html.indexOf('/static/i18n.js');
        const pageScriptIndex = html.indexOf(`/static/${page}.js`);
        assert.notEqual(i18nIndex, -1, `${page}.html must load translations`);
        assert.ok(i18nIndex < pageScriptIndex, `${page}.html must load translations first`);
    }
});
