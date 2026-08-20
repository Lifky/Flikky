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

  // Node.prototype.contains — ancestor-or-self check, distinct from
  // classList.contains(name) above (same method name, different object).
  // v1.19.0 fix round 2 (G5) needs this on app.js's real DOM; the mock lacked it entirely.
  contains(other) {
    let node = other;
    while (node) {
      if (node === this) return true;
      node = node.parentNode;
    }
    return false;
  }

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
    createTextNode: (text) => {
      const node = new Element('#text');
      node.textContent = text;
      return node;
    },
    getElementById: (id) => ids.get(id) || Array.from(ids.values()).map((el) => findById(el, id)).find(Boolean) || null,
    addEventListener() {},
    removeEventListener() {},
  };
  // Real browsers default document.activeElement to <body> when nothing has focus;
  // this mock never had the property at all. focus() above is a no-op, so this stays
  // static — good enough since nothing here exercises focus-shifting behavior.
  document.activeElement = document.body;
  document.body.dataset.actionStyle = 'INLINE';

  [
    'chat-list-shell',
    'list',
    'text-input',
    'send-btn',
    'file-btn',
    'file-picker',
    'drop-overlay',
    'conn',
    'uptime',
    'count',
    'rate',
    'save-all-dropdown',
    'save-all-fab',
    'save-all-each',
    'save-all-each-label',
    'save-all-zip',
    'my-avatar-btn',
    'peer-avatar',
  ].forEach((id) => {
    // v1.19.0: 新输入坞用原生 <textarea>——mdui-text-field 的 filled/outlined 皮肤会和自研输入坞打架。
    ids.set(id, new Element(id === 'text-input' ? 'textarea' : 'div'));
  });
  ids.get('chat-list-shell').appendChild(ids.get('list'));

  document.querySelector = (...args) => document.body.querySelector(...args);
  document.querySelectorAll = (...args) => document.body.querySelectorAll(...args);
  return document;
}

function loadAppForTest() {
  const appPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'web', 'app.js');
  const source = fs.readFileSync(appPath, 'utf8');
  const patched = source.replace(
    /(\s*)setSendEnabled\(false\);\s*loadHistory\(\)\.then\(openWs\);\s*\}\)\(\);/,
    "$1setSendEnabled(false);\n$1window.__flikkyWebTest = { renderText, removeMessageNode, onWsEvent, buildMessageActions, list, body: document.body, root: document.documentElement, peerAvatar: document.getElementById('peer-avatar') };\n})();",
  );

  if (patched === source) {
    throw new Error('Could not install test hook into app.js');
  }

  const document = createDocument();
  const localStorageData = new Map();
  const flikkyI18n = {
    t(key, values = {}) {
      if (key === 'app.phone') return '手机';
      if (key === 'app.connected') return '已连接';
      if (key === 'app.disconnected') return '已断开';
      if (key === 'app.watermark') return `${values.status} · ${values.device}`;
      return key;
    },
    count(key, count) { return `${count} ${key}`; },
    onChange() {},
  };
  const context = {
    document,
    navigator: { userAgent: '' },
    location: { protocol: 'http:', host: 'localhost' },
    window: {
      flikky: {},
      flikkyI18n,
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
  app.onWsEvent({ type: 'settings_changed', payload: { avatarGrouping: 'FIRST' } });
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

function runRecallFeatureGateTest() {
  const app = loadAppForTest();
  const bubble = app.renderText({ id: 1, content: 'copy me' }, true);

  assertDeepEqual(
    Array.from(app.buildMessageActions(bubble, false), (action) => action.kind),
    ['copy'],
    'recall action must stay hidden when recall is disabled',
  );

  app.onWsEvent({ type: 'settings_changed', payload: { recallEnabled: true } });
  assertDeepEqual(
    Array.from(app.buildMessageActions(bubble, true), (action) => action.kind),
    ['copy', 'recall'],
    'recall action should be available when recall is enabled',
  );
}

runRecallFeatureGateTest();

function runPhoneFileReadyActionsTest() {
  const app = loadAppForTest();
  app.onWsEvent({
    type: 'file_added',
    payload: {
      id: 7,
      origin: 'PHONE',
      fileId: 'phone-video',
      name: 'video-test.mp4',
      sizeBytes: 1024,
      mime: 'video/mp4',
      status: 'IN_PROGRESS',
    },
  });

  const bubble = app.list.querySelector('[data-message-id="7"]');
  app.onWsEvent({
    type: 'file_ready',
    payload: {
      messageId: 7,
      fileId: 'phone-video',
      name: 'video-test.mp4',
      sizeBytes: 1024,
    },
  });

  assertDeepEqual(
    Array.from(app.buildMessageActions(bubble, true), (action) => action.kind),
    ['preview', 'download'],
    'completed phone-origin media must expose preview and download without recall',
  );
}

runPhoneFileReadyActionsTest();

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

function runAnimationSpeedSyncTest() {
  const app = loadAppForTest();
  app.onWsEvent({ type: 'settings_changed', payload: { animationSpeed: 'SLOW' } });
  assertDeepEqual(
    app.root.style['--flikky-message-enter-duration'],
    '450ms',
    'slow animation speed must lengthen Web bubble entry motion',
  );

  app.onWsEvent({ type: 'settings_changed', payload: { animationSpeed: 'OFF' } });
  assertDeepEqual(
    app.root.style['--flikky-message-enter-duration'],
    '0ms',
    'off animation speed must disable Web bubble entry motion',
  );
}

runAnimationSpeedSyncTest();

function runPartialSettingsDoesNotResetActionStyleTest() {
  const app = loadAppForTest();
  app.onWsEvent({ type: 'settings_changed', payload: { messageActionStyle: 'INLINE' } });
  assertDeepEqual(app.body.dataset.actionStyle, 'INLINE', 'initial action style from settings');

  app.onWsEvent({ type: 'settings_changed', payload: { bubbleCornerRadius: 24 } });
  assertDeepEqual(
    app.body.dataset.actionStyle,
    'INLINE',
    'partial bubble radius update must not reset the action style',
  );
}

runPartialSettingsDoesNotResetActionStyleTest();

function runServerStoppedUpdatesDefaultWatermarkTest() {
  const app = loadAppForTest();
  app.onWsEvent({
    type: 'settings_changed',
    payload: { deviceName: '我的手机', backgroundMode: 'DEFAULT' },
  });
  assertDeepEqual(watermarkText(app.list.parentNode), '已连接 · 我的手机', 'initial connected watermark');

  app.onWsEvent({ type: 'server_stopped', payload: {} });
  assertDeepEqual(watermarkText(app.list.parentNode), '已断开 · 我的手机', 'server stopped watermark');
}

runServerStoppedUpdatesDefaultWatermarkTest();

console.log('web avatar reflow test passed');
