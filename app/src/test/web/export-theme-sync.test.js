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
    const context = {
        window: {},
        location: { href: '' },
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
    return { calls, fetches };
}

test('export page fetches peer-info and applies the phone theme', async () => {
    const { calls, fetches } = await runFetchExportTheme({
        themeSeed: '#33618D',
        themeDark: true,
    });

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
