const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');
const loginPath = path.join(root, 'app/src/main/assets/web/login.js');

class Element {
  constructor() {
    this.listeners = new Map();
    this.value = '';
    // v1.19.0: the PIN field is a plain <input> now, not an <mdui-text-field>.
    // Errors go to #helper's textContent and #pin's data-error, so the stand-in
    // needs both — without dataset, login.js throws before the theme fetch and
    // this whole script fails on an unrelated change.
    this.textContent = '';
    this.dataset = {};
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
    pin: new Element(),
    helper: new Element(),
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
  const themeAttrs = {};
  const context = {
    document: {
      // v1.19.0: applyTheme 还要往 <html> 上写 data-amoled。少了这个替身，
      // 它会在触及 mdui 之前抛异常，下面那条 calls 断言就只是空数组比空数组。
      documentElement: {
        setAttribute(name, value) { themeAttrs[name] = String(value); },
        getAttribute(name) { return name in themeAttrs ? themeAttrs[name] : null; },
      },
      getElementById(id) {
        return elements[id] || null;
      },
      // The six .fk-pin-cell divs are display-only. Returning none is a valid
      // shape for this script — it asserts theme-sync ordering, not rendering —
      // and login.js must tolerate it rather than assume six cells exist.
      querySelectorAll() {
        return [];
      },
      addEventListener() {},
    },
    window: {
      location: { href: '' },
      mdui,
      flikkyI18n: {
        t(key) { return key; },
        onChange() {},
      },
    },
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
  // The stubbed /api/web-theme response carries themeDark without amoled, so the
  // page must land on plain dark — a missing field may not read as "on".
  assertDeepEqual(
    themeAttrs['data-amoled'],
    '0',
    'login page should treat a missing amoled field as off',
  );
}

runLoginThemeTest()
  .then(() => console.log('web login theme test passed'))
  .catch((err) => {
    console.error(err.stack || err.message || err);
    process.exit(1);
  });
