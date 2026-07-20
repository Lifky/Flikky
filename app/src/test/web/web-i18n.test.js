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

test('PIN login card does not render helper tip paragraphs', () => {
    const html = fs.readFileSync(
        path.resolve(__dirname, '../../main/assets/web/login.html'),
        'utf8',
    );
    const { source: i18nSource } = loadI18n();

    assert.equal(html.includes('data-i18n="login.description"'), false);
    assert.equal(html.includes('data-i18n="login.privacy_tip"'), false);
    assert.equal(i18nSource.includes("'login.privacy_tip'"), false);
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

test('all page translation keys exist in both dictionaries', () => {
    const { i18n } = loadI18n();
    const webDir = path.resolve(__dirname, '../../main/assets/web');
    const pageFiles = ['login.html', 'login.js', 'app.html', 'app.js', 'export.html', 'export.js'];
    const keyPattern = /['"]((?:common|login|app|export)\.[a-z0-9_]+)['"]/g;
    const keys = new Set();
    for (const file of pageFiles) {
        const source = fs.readFileSync(path.join(webDir, file), 'utf8');
        for (const match of source.matchAll(keyPattern)) keys.add(match[1]);
    }

    for (const language of ['zh-CN', 'en']) {
        i18n.setLanguage(language);
        for (const key of keys) {
            assert.notEqual(i18n.t(key), key, `${language} is missing ${key}`);
        }
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
