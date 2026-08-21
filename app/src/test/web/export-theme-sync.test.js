const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const exportJsPath = path.resolve(__dirname, '../../main/assets/web/export.js');
const exportJs = fs.readFileSync(exportJsPath, 'utf8');
const themeStart = exportJs.indexOf('let lastThemeKey = null;');
const themeEnd = exportJs.indexOf('async function loadInfo()');

assert.notEqual(themeStart, -1, 'export theme sync start marker must exist');
assert.notEqual(themeEnd, -1, 'export theme sync end marker must exist');

const themeSource = exportJs.slice(themeStart, themeEnd);

async function runFetchExportTheme(themeResponse) {
    const calls = [];
    const fetches = [];
    const attrs = {};
    const context = {
        window: {},
        location: { href: '' },
        // v1.19.0: applyTheme 现在还要往 <html> 上写 data-amoled（AMOLED 纯黑三态）。
        // 写属性不是 mdui 调用，不会进 calls，所以下面原有的 deepEqual 断言不受影响；
        // 但少了这个替身，applyTheme 会在触及 mdui 之前就抛，calls 变成空数组。
        document: {
            documentElement: {
                setAttribute(name, value) { attrs[name] = String(value); },
                getAttribute(name) { return name in attrs ? attrs[name] : null; },
            },
        },
        fetch: async (url, options) => {
            fetches.push({ url, options });
            return {
                ok: true,
                status: 200,
                json: async () => themeResponse,
            };
        },
    };
    context.window = context;
    context.mdui = {
        setTheme(value) { calls.push(['setTheme', value]); },
        setColorScheme(value) { calls.push(['setColorScheme', value]); },
        removeColorScheme() { calls.push(['removeColorScheme']); },
    };

    vm.runInNewContext(
        `${themeSource}\nglobalThis.themePromise = fetchExportTheme();`,
        context,
    );
    await context.themePromise;
    return { calls, fetches, attrs };
}

test('export page fetches peer-info and applies the phone theme', async () => {
    const { calls, fetches, attrs } = await runFetchExportTheme({
        themeSeed: '#33618D',
        themeDark: true,
    });
    assert.equal(attrs['data-amoled'], '0', 'dark without amoled must not go pure black');

    assert.equal(fetches.length, 1);
    assert.equal(fetches[0].url, '/api/peer-info');
    assert.equal(fetches[0].options.cache, 'no-store');
    assert.equal(fetches[0].options.credentials, 'same-origin');
    assert.deepEqual(calls, [
        ['setTheme', 'dark'],
        ['setColorScheme', '#33618D'],
    ]);
});

test('export page clears custom colors when the phone uses dynamic color', async () => {
    const { calls } = await runFetchExportTheme({
        themeSeed: null,
        themeDark: false,
    });

    assert.deepEqual(calls, [
        ['setTheme', 'light'],
        ['removeColorScheme'],
    ]);
});

test('the export page goes pure black when the phone has AMOLED on', async () => {
    const { calls, attrs } = await runFetchExportTheme({
        themeSeed: '#33618D',
        themeDark: true,
        amoled: true,
    });

    // mdui 侧仍然只是 dark —— 纯黑是 tokens.css 的 [data-amoled="1"] 那一层做的，
    // 不是另一个 mdui 主题。
    assert.deepEqual(calls, [
        ['setTheme', 'dark'],
        ['setColorScheme', '#33618D'],
    ]);
    assert.equal(attrs['data-amoled'], '1');
});

test('AMOLED on a light phone theme stays light', async () => {
    const { calls, attrs } = await runFetchExportTheme({
        themeSeed: '#33618D',
        themeDark: false,
        amoled: true,
    });

    assert.deepEqual(calls, [
        ['setTheme', 'light'],
        ['setColorScheme', '#33618D'],
    ]);
    assert.equal(attrs['data-amoled'], '0');
});
