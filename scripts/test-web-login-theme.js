const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');
const loginPath = path.join(root, 'app/src/main/assets/web/login.js');

class Element {
  constructor() {
    this.listeners = new Map();
    this.value = '';
    this.error = false;
    this.helper = '';
    this.disabled = false;
    this.focusCalls = 0;
  }

  addEventListener(type, listener) {
    this.listeners.set(type, listener);
  }

  focus() {
    this.focusCalls += 1;
  }
}

function assertDeepEqual(actual, expected, message) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nactual:   ${JSON.stringify(actual)}`);
  }
}

async function flushMicrotasks() {
  for (let i = 0; i < 6; i += 1) {
    await Promise.resolve();
  }
}

async function runLoginThemeTest() {
  const elements = {
    'pin-form': new Element(),
    'pin-input': new Element(),
    'submit-btn': new Element(),
  };
  const calls = [];
  const mdui = {
    setTheme(theme) {
      calls.push(['setTheme', theme]);
    },
    setColorScheme(seed) {
      calls.push(['setColorScheme', seed]);
    },
    removeColorScheme() {
      calls.push(['removeColorScheme']);
    },
  };
  const context = {
    document: {
      getElementById(id) {
        return elements[id] || null;
      },
    },
    window: { location: { href: '' }, mdui },
    mdui,
    fetch(url) {
      calls.push(['fetch', url]);
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ themeSeed: '#6750A4', themeDark: true }),
      });
    },
    setTimeout(fn) {
      fn();
      return 1;
    },
    clearTimeout() {},
    console,
    Promise,
  };

  vm.runInNewContext(fs.readFileSync(loginPath, 'utf8'), context, { filename: loginPath });
  await flushMicrotasks();

  assertDeepEqual(
    calls.filter((call) => call[0] === 'fetch'),
    [['fetch', '/api/web-theme']],
    'login page should fetch public theme before PIN auth',
  );
  assertDeepEqual(
    calls.filter((call) => call[0] !== 'fetch'),
    [['setTheme', 'dark'], ['setColorScheme', '#6750A4']],
    'login page should apply App theme through MDUI APIs',
  );
}

runLoginThemeTest()
  .then(() => console.log('web login theme test passed'))
  .catch((err) => {
    console.error(err.stack || err.message || err);
    process.exit(1);
  });
