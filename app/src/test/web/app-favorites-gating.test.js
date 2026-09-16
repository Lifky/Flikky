const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDocument } = require('./mini-dom.js');

const web = path.join(__dirname, '../../main/assets/web');
const source = fs.readFileSync(path.join(web, 'app.js'), 'utf8');
const html = fs.readFileSync(path.join(web, 'app.html'), 'utf8');

// Run the actual shell and peer-info feature updates against DOM elements. CSS
// cannot make .hidden true: reproducing that distinction catches Screenshot_16.
function load() {
  const document = createDocument();
  const shell = document.register('shell');
  shell.dataset.panel = 'shown';
  shell.dataset.mobileDest = 'chat';
  const views = ['files', 'album', 'favorites', 'settings'].map((dest) => {
    const view = document.register('view-' + dest);
    view.hidden = /\bhidden\b/.test(html.match(new RegExp(`<[^>]+id="view-${dest}"[^>]*>`))[0]);
    return view;
  });
  const buttons = [...html.matchAll(/<button[^>]*class="(fk-rail-item|fk-navbar-item)"[^>]*data-dest="(\w+)"[^>]*>/g)]
    .map(([tag, kind, dest]) => {
      const button = document.createElement('button');
      button.className = kind;
      button.dataset.dest = dest;
      button.hidden = /\bhidden\b/.test(tag);
      return button;
    });
  document.querySelectorAll = (selector) => {
    if (selector === '.fk-view') return views;
    if (selector.startsWith('[data-dest=')) return buttons.filter((b) => selector.includes('"' + b.dataset.dest + '"'));
    return buttons.filter((b) => '.' + b.className === selector);
  };
  document.querySelector = (selector) => selector === '.fk-view:not([hidden])'
    ? views.find((v) => !v.hidden) : null;
  const context = { document, window: {}, localStorage: { setItem() {} } };
  vm.createContext(context);
  const shellCode = source.slice(source.indexOf("    const shell = document.getElementById('shell');"), source.indexOf('    window.flikky = window.flikky || {};'));
  const settingsCode = source.slice(source.indexOf("        if (Object.prototype.hasOwnProperty.call(data, 'favoriteEnabled'))"), source.indexOf("        if (Object.prototype.hasOwnProperty.call(data, 'showHiddenFiles'))"));
  vm.runInContext(shellCode + '\nfunction receiveSettings(data) {\n' + settingsCode + '\napplyDefaultFocusOnce();\n}', context);
  return {
    shell, views, buttons,
    api: context.window,
    receive: (data) => context.receiveSettings(Object.assign({ storageBrowsingEnabled: false, albumBrowsingEnabled: false }, data)),
    active: () => views.find((v) => !v.hidden)?.id,
  };
}

test('disabled favorites never becomes the initial desktop panel', () => {
  const c = load();
  c.receive({ favoriteEnabled: false });
  assert.equal(c.active(), 'view-settings');
  assert.ok(c.buttons.filter((b) => b.dataset.dest === 'favorites').every((b) => b.hidden));
  assert.equal(c.shell.dataset.mobileDest, 'chat');
});

test('turning favorites off replaces its panel and can be reversed', () => {
  const c = load();
  c.receive({ favoriteEnabled: true });
  c.api.selectDest('favorites');
  c.receive({ favoriteEnabled: false, albumBrowsingEnabled: true });
  assert.notEqual(c.active(), 'view-favorites');
  c.api.selectDest('favorites');
  assert.notEqual(c.active(), 'view-favorites');
  c.receive({ favoriteEnabled: true });
  c.api.selectDest('favorites');
  assert.equal(c.active(), 'view-favorites');
});

test('a collapsed favorites panel cannot reappear after the feature is disabled', () => {
  const c = load();
  c.receive({ favoriteEnabled: true });
  c.api.setPanel(false);
  c.receive({ favoriteEnabled: false });
  assert.equal(c.shell.dataset.panel, 'hidden');
  c.api.setPanel(true);
  assert.equal(c.active(), 'view-settings');
  assert.equal(c.shell.dataset.mobileDest, 'chat');
});
