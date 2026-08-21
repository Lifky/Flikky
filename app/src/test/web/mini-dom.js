/**
 * 迷你假 DOM —— 供面板脚本的行为测试使用。
 *
 * 为什么不用 jsdom：本项目浏览器端一律零依赖（无构建步骤、无 node_modules），
 * 测试只用 Node 内建 node:test + vm。为一个测试引入 jsdom 会给一个刻意
 * 保持「纯静态资产」的前端塞进依赖树。
 *
 * 这里只实现面板脚本真正用到的那一小片 API。刻意不实现选择器引擎：
 * 面板脚本自己维护 Map/Set 状态、不反查 DOM，测试侧用下面的 findAll() 遍历树。
 *
 * 本文件相对 Task 6 计划原文有两处新增：
 * 1) MutationObserver（brief D6 明确允许：「如果 mini-dom.js 需要一个
 *    MutationObserver stub 给 D4 用，就加一个」）。只实现「观察某节点的
 *    attributeFilter」这一个子集——面板只用它来盯 document.body 上的
 *    data-favorite-enabled。
 * 2) FakeElement.firstChild 只读访问器——面板脚本统一用
 *    `while (el.firstChild) el.removeChild(...)` 清空重建子树（跟
 *    panel-settings.js 是同一手法），计划原文的 FakeElement 没有这个访问器，
 *    导致这个循环一次都不会跑，旧内容永远清不掉。
 */
const KEBAB = (s) => s.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`);

/** 真实 DOM 里会反射到属性的那几个 —— 让 el.href = x 与 getAttribute('href') 一致，
 *  否则测试会因为「实现用了属性、断言读了属性名」这种无关差异而假红。 */
const REFLECTED = ['href', 'download', 'src', 'alt', 'title', 'type', 'placeholder', 'role'];

class FakeElement {
  constructor(tagName, doc) {
    this.tagName = String(tagName).toUpperCase();
    this.ownerDocument = doc;
    this.children = [];
    this.parentNode = null;
    this._attrs = new Map();
    this._classes = new Set();
    this._text = '';
    this._id = '';
    this.hidden = false;
    this.disabled = false;
    this.value = '';
    this.checked = false;
    this.clickCount = 0;
    this.focusCount = 0;
    this.listeners = new Map();
    this.style = { setProperty() {}, removeProperty() {} };

    const self = this;
    this.classList = {
      add(...cls) { cls.forEach((c) => self._classes.add(c)); },
      remove(...cls) { cls.forEach((c) => self._classes.delete(c)); },
      contains(c) { return self._classes.has(c); },
      toggle(c, on) {
        const want = on === undefined ? !self._classes.has(c) : !!on;
        if (want) self._classes.add(c); else self._classes.delete(c);
        return want;
      },
    };
    this.dataset = new Proxy({}, {
      get: (_t, p) => {
        const v = self.getAttribute(`data-${KEBAB(String(p))}`);
        return v === null ? undefined : v;
      },
      set: (_t, p, v) => { self.setAttribute(`data-${KEBAB(String(p))}`, v); return true; },
      has: (_t, p) => self._attrs.has(`data-${KEBAB(String(p))}`),
      deleteProperty: (_t, p) => { self._attrs.delete(`data-${KEBAB(String(p))}`); return true; },
      ownKeys: () => [...self._attrs.keys()]
        .filter((k) => k.startsWith('data-'))
        .map((k) => k.slice(5).replace(/-([a-z])/g, (_m, c) => c.toUpperCase())),
      getOwnPropertyDescriptor: () => ({ enumerable: true, configurable: true }),
    });
  }

  get id() { return this._id; }
  set id(v) {
    this._id = String(v);
    this._attrs.set('id', this._id);
    if (this.ownerDocument) this.ownerDocument._byId.set(this._id, this);
  }

  get className() { return [...this._classes].join(' '); }
  set className(v) { this._classes = new Set(String(v).split(/\s+/).filter(Boolean)); }

  /** 真实 DOM 有 firstChild——面板脚本（panel-settings.js 和本任务的
   *  panel-favorites.js）统一用 `while (el.firstChild) el.removeChild(...)`
   *  清空重建，这里补上这个只读访问器，不然那个循环永远不会执行一次。 */
  get firstChild() { return this.children.length ? this.children[0] : null; }

  get textContent() {
    if (this.children.length === 0) return this._text;
    return this.children.map((c) => c.textContent).join('');
  }
  set textContent(v) {
    this.children.forEach((c) => { c.parentNode = null; });
    this.children = [];
    this._text = v === null || v === undefined ? '' : String(v);
  }

  setAttribute(name, value) {
    if (name === 'id') { this.id = value; return; }
    if (name === 'class') { this.className = value; return; }
    const next = String(value);
    const changed = this._attrs.get(name) !== next;
    this._attrs.set(name, next);
    // D4 用的 MutationObserver 挂在这里通知——真实 DOM 也是 setAttribute 才触发一次变更记录。
    if (changed && this._observers) {
      this._observers.slice().forEach((obs) => obs._notify(name));
    }
  }
  getAttribute(name) {
    if (name === 'class') return this.className;
    return this._attrs.has(name) ? this._attrs.get(name) : null;
  }
  removeAttribute(name) { this._attrs.delete(name); }
  hasAttribute(name) { return this._attrs.has(name); }

  appendChild(child) {
    child.parentNode = this;
    this.children.push(child);
    return child;
  }
  removeChild(child) {
    this.children = this.children.filter((c) => c !== child);
    child.parentNode = null;
    return child;
  }
  remove() { if (this.parentNode) this.parentNode.removeChild(this); }
  replaceChildren(...next) {
    this.textContent = '';
    next.forEach((n) => this.appendChild(n));
  }

  addEventListener(type, fn) {
    if (!this.listeners.has(type)) this.listeners.set(type, []);
    this.listeners.get(type).push(fn);
  }
  removeEventListener(type, fn) {
    const list = this.listeners.get(type) || [];
    this.listeners.set(type, list.filter((f) => f !== fn));
  }
  /** 触发一个事件；返回 event 对象供断言 preventDefault 之类。 */
  dispatch(type, extra = {}) {
    let defaultPrevented = false;
    let propagationStopped = false;
    const ev = Object.assign({
      type,
      target: this,
      currentTarget: this,
      preventDefault() { defaultPrevented = true; },
      stopPropagation() { propagationStopped = true; },
      get defaultPrevented() { return defaultPrevented; },
      get propagationStopped() { return propagationStopped; },
    }, extra);
    (this.listeners.get(type) || []).forEach((fn) => fn(ev));
    // 冒泡：面板里「点行选中 / 点行尾按钮不选中」靠 stopPropagation 区分，必须模拟。
    if (!propagationStopped && this.parentNode) {
      this.parentNode.dispatch(type, Object.assign({}, extra, { target: ev.target }));
    }
    return ev;
  }
  click() { this.clickCount += 1; this.dispatch('click'); }
  focus() { this.focusCount += 1; }
}

REFLECTED.forEach((name) => {
  Object.defineProperty(FakeElement.prototype, name, {
    get() { return this.getAttribute(name); },
    set(v) { this.setAttribute(name, v); },
    configurable: true,
  });
});

/** 极简 MutationObserver：只支持 { attributes: true, attributeFilter } 这一种用法，
 *  面板对 document.body 的 data-favorite-enabled 观察只需要这一个子集。 */
class FakeMutationObserver {
  constructor(callback) {
    this._callback = callback;
    this._target = null;
    this._filter = null;
  }
  observe(target, options) {
    this._target = target;
    this._filter = (options && options.attributeFilter) || null;
    if (!target._observers) target._observers = [];
    target._observers.push(this);
  }
  disconnect() {
    if (this._target && this._target._observers) {
      this._target._observers = this._target._observers.filter((o) => o !== this);
    }
    this._target = null;
  }
  _notify(attributeName) {
    if (this._filter && !this._filter.includes(attributeName)) return;
    this._callback([{ type: 'attributes', target: this._target, attributeName }], this);
  }
}

function createDocument() {
  const doc = {
    _byId: new Map(),
    created: [],
    createElement(tag) {
      const el = new FakeElement(tag, doc);
      doc.created.push(el);
      return el;
    },
    createTextNode(text) {
      const el = new FakeElement('#text', doc);
      el.textContent = text;
      return el;
    },
    getElementById(id) { return doc._byId.get(id) || null; },
    /** 造一个「本来写在 app.html 里」的静态节点，并按 id 注册。 */
    register(id, tag = 'div') {
      const el = doc.createElement(tag);
      el.id = id;
      return el;
    },
    addEventListener() {},
    execCommand() { return false; },
  };
  doc.documentElement = doc.createElement('html');
  doc.body = doc.createElement('body');
  return doc;
}

/** 深度优先遍历，收集满足条件的节点。 */
function findAll(root, predicate) {
  const out = [];
  (function walk(node) {
    if (predicate(node)) out.push(node);
    node.children.forEach(walk);
  })(root);
  return out;
}

const byClass = (root, cls) => findAll(root, (n) => n.classList.contains(cls));
const byRole = (root, role) => findAll(root, (n) => n.getAttribute('data-role') === role);

module.exports = { FakeElement, createDocument, findAll, byClass, byRole, MutationObserver: FakeMutationObserver };
