const fs = require('fs');
const path = require('path');
const vm = require('vm');

class ClassList {
  constructor(el) {
    this.el = el;
  }

  _classes() {
    return new Set((this.el.className || '').split(/\s+/).filter(Boolean));
  }

  _write(classes) {
    this.el.className = Array.from(classes).join(' ');
  }

  add(...names) {
    const classes = this._classes();
    names.forEach((name) => classes.add(name));
    this._write(classes);
  }

  remove(...names) {
    const classes = this._classes();
    names.forEach((name) => classes.delete(name));
    this._write(classes);
  }

  contains(name) {
    return this._classes().has(name);
  }
}

class Element {
  constructor(tagName) {
    this.tagName = tagName.toUpperCase();
    this.nodeName = this.tagName;
    this.children = [];
    this.childNodes = this.children;
    this.parentNode = null;
    this.dataset = {};
    this.style = {
      setProperty(name, value) {
        this[name] = value;
      },
    };
    this.attributes = {};
    this.className = '';
    this.textContent = '';
    this.value = '';
    this.disabled = false;
    this.files = [];
    this.open = false;
    this.classList = new ClassList(this);
  }

  appendChild(child) {
    if (child.parentNode) child.parentNode.removeChild(child);
    child.parentNode = this;
    this.children.push(child);
    return child;
  }

  get firstChild() {
    return this.children[0] || null;
  }

  insertBefore(child, before) {
    if (!before) return this.appendChild(child);
    if (child.parentNode) child.parentNode.removeChild(child);
    const index = this.children.indexOf(before);
    if (index < 0) return this.appendChild(child);
    child.parentNode = this;
    this.children.splice(index, 0, child);
    return child;
  }

  removeChild(child) {
    const index = this.children.indexOf(child);
    if (index >= 0) {
      this.children.splice(index, 1);
      child.parentNode = null;
    }
    return child;
  }

  remove() {
    if (this.parentNode) this.parentNode.removeChild(this);
  }

  setAttribute(name, value) {
    this.attributes[name] = String(value);
    if (name === 'class') this.className = String(value);
  }

  removeAttribute(name) {
    delete this.attributes[name];
    if (name === 'class') this.className = '';
  }

  getAttribute(name) {
    return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null;
  }

  addEventListener() {}
  removeEventListener() {}
  focus() {}
  click() {}

  closest(selector) {
    let node = this;
    while (node) {
      if (matches(node, selector)) return node;
      node = node.parentNode;
    }
    return null;
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }

  querySelectorAll(selector) {
    if (selector.includes('>')) {
      const [parentSelector, childSelector] = selector.split('>').map((part) => part.trim());
      const result = [];
      walk(this, (node) => {
        if (!matches(node, parentSelector)) return;
        node.children.forEach((child) => {
          if (matches(child, childSelector)) result.push(child);
        });
      });
      return result;
    }

    const result = [];
    walk(this, (node) => {
      if (node !== this && matches(node, selector)) result.push(node);
    });
    return result;
  }
}

function walk(root, visit) {
  root.children.forEach((child) => {
    visit(child);
    walk(child, visit);
  });
}

function matches(el, selector) {
  if (!el) return false;
  if (selector.startsWith('.')) {
    return selector.slice(1).split('.').every((name) => el.classList.contains(name));
  }
  const dataMessage = selector.match(/^\[data-message-id="([^"]+)"\]$/);
  if (dataMessage) return String(el.dataset.messageId) === dataMessage[1];
  return el.tagName.toLowerCase() === selector.toLowerCase();
}

function createDocument() {
  const ids = new Map();
  function findById(root, id) {
    if (!root) return null;
    if (root.id === id) return root;
    for (const child of root.children) {
      const found = findById(child, id);
      if (found) return found;
    }
    return null;
  }
  const document = {
    body: new Element('body'),
    documentElement: new Element('html'),
    createElement: (tag) => new Element(tag),
    getElementById: (id) => ids.get(id) || Array.from(ids.values()).map((el) => findById(el, id)).find(Boolean) || null,
    addEventListener() {},
    removeEventListener() {},
  };

  [
    'list',
    'text-input',
    'send-btn',
    'file-btn',
    'file-picker',
    'conn',
    'uptime',
    'count',
    'rate',
    'my-avatar-btn',
    'peer-avatar',
  ].forEach((id) => {
    ids.set(id, new Element(id === 'text-input' ? 'mdui-text-field' : 'div'));
  });

  document.querySelector = (...args) => document.body.querySelector(...args);
  document.querySelectorAll = (...args) => document.body.querySelectorAll(...args);
  return document;
}

function loadAppForTest() {
  const appPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'web', 'app.js');
  const source = fs.readFileSync(appPath, 'utf8');
  const patched = source.replace(
    /(\s*)setSendEnabled\(false\);\s*loadHistory\(\)\.then\(openWs\);\s*\}\)\(\);/,
    "$1setSendEnabled(false);\n$1window.__flikkyWebTest = { renderText, removeMessageNode, onWsEvent, list, peerAvatar: document.getElementById('peer-avatar') };\n})();",
  );

  if (patched === source) {
    throw new Error('Could not install test hook into app.js');
  }

  const document = createDocument();
  const localStorageData = new Map();
  const context = {
    document,
    navigator: { userAgent: '' },
    location: { protocol: 'http:', host: 'localhost' },
    window: {
      flikky: {},
      addEventListener() {},
      removeEventListener() {},
    },
    localStorage: {
      getItem: (key) => localStorageData.get(key) || null,
      setItem: (key, value) => localStorageData.set(key, String(value)),
      removeItem: (key) => localStorageData.delete(key),
    },
    setTimeout,
    clearTimeout,
    console,
  };
  context.window.document = document;
  context.window.navigator = context.navigator;
  context.window.location = context.location;
  context.window.localStorage = context.localStorage;

  vm.runInNewContext(patched, context, { filename: appPath });
  return context.window.__flikkyWebTest;
}

function avatarMarkers(list) {
  return list.children.filter((row) => row.classList.contains('bubble-row')).map((row) => {
    const marker = row.children[0];
    if (!marker) return 'empty';
    if (marker.classList.contains('avatar-circle')) return 'avatar';
    if (marker.classList.contains('avatar-spacer')) return 'spacer';
    return marker.className || marker.tagName;
  });
}

function avatarSymbolText(el) {
  const symbol = el.querySelector('.avatar-symbol');
  return symbol ? symbol.textContent : el.textContent;
}

function watermarkText(list) {
  const watermark = list.children.find((child) => child.id === 'chat-watermark');
  return watermark ? watermark.textContent : null;
}

function assertDeepEqual(actual, expected, message) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${message}\nexpected: ${JSON.stringify(expected)}\nactual:   ${JSON.stringify(actual)}`);
  }
}

function runFirstRowPromotionTest(mine) {
  const app = loadAppForTest();
  app.renderText({ id: 1, content: 'first' }, mine);
  app.renderText({ id: 2, content: 'second' }, mine);
  app.renderText({ id: 3, content: 'third' }, mine);

  assertDeepEqual(avatarMarkers(app.list), ['avatar', 'spacer', 'spacer'], 'initial first-message avatar grouping');

  app.removeMessageNode(1);

  assertDeepEqual(
    avatarMarkers(app.list),
    ['avatar', 'spacer'],
    'removing the first message should promote the next same-origin row avatar',
  );
}

runFirstRowPromotionTest(true);
runFirstRowPromotionTest(false);

function runGroupingModeTest(mode, expected) {
  const app = loadAppForTest();
  app.onWsEvent({ type: 'settings_changed', payload: { avatarGrouping: mode } });
  app.renderText({ id: 1, content: 'first' }, true);
  app.renderText({ id: 2, content: 'second' }, true);
  app.renderText({ id: 3, content: 'third' }, true);
  assertDeepEqual(avatarMarkers(app.list), expected, `${mode} avatar grouping`);
}

runGroupingModeTest('FIRST', ['avatar', 'spacer', 'spacer']);
runGroupingModeTest('LAST', ['spacer', 'spacer', 'avatar']);
runGroupingModeTest('EACH', ['avatar', 'avatar', 'avatar']);

function runPartialSettingsDoesNotResetPhoneAvatarTest() {
  const app = loadAppForTest();
  app.onWsEvent({ type: 'settings_changed', payload: { phoneAvatarKey: 'icon:palette' } });
  assertDeepEqual(avatarSymbolText(app.peerAvatar), 'palette', 'initial phone avatar from settings');

  app.onWsEvent({ type: 'settings_changed', payload: { avatarGrouping: 'LAST' } });
  assertDeepEqual(
    avatarSymbolText(app.peerAvatar),
    'palette',
    'partial avatarGrouping update must not reset phone avatar',
  );
}

runPartialSettingsDoesNotResetPhoneAvatarTest();

function runServerStoppedUpdatesDefaultWatermarkTest() {
  const app = loadAppForTest();
  app.onWsEvent({
    type: 'settings_changed',
    payload: { deviceName: '我的手机', backgroundMode: 'DEFAULT' },
  });
  assertDeepEqual(watermarkText(app.list), '已连接 · 我的手机', 'initial connected watermark');

  app.onWsEvent({ type: 'server_stopped', payload: {} });
  assertDeepEqual(watermarkText(app.list), '已断开 · 我的手机', 'server stopped watermark');
}

runServerStoppedUpdatesDefaultWatermarkTest();

console.log('web avatar reflow test passed');
