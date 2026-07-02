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
    this.style = {};
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
  const document = {
    body: new Element('body'),
    createElement: (tag) => new Element(tag),
    getElementById: (id) => ids.get(id) || null,
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
    '$1setSendEnabled(false);\n$1window.__flikkyWebTest = { renderText, removeMessageNode, list };\n})();',
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
  return list.children.map((row) => {
    const marker = row.children[0];
    if (marker.classList.contains('avatar-circle')) return 'avatar';
    if (marker.classList.contains('avatar-spacer')) return 'spacer';
    return marker.className || marker.tagName;
  });
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

console.log('web avatar reflow test passed');
