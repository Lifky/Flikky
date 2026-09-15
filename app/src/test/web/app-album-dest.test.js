const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '../../main/assets/web');
const read = (name) => fs.readFileSync(path.join(WEB, name), 'utf8');
const html = read('app.html');
const appJs = read('app.js');

const buttonFor = (kind, dest) => {
  const re = new RegExp(`<button[^>]*class="${kind}"[^>]*data-dest="${dest}"[^>]*>[\\s\\S]*?<\\/button>`);
  return html.match(re);
};

test('the album destination is hidden until the peer gate opens', () => {
  for (const kind of ['fk-rail-item', 'fk-navbar-item']) {
    const button = buttonFor(kind, 'album');
    assert.ok(button, `no ${kind} for album`);
    assert.match(button[0].slice(0, button[0].indexOf('>')), / hidden$/,
      `${kind} must ship hidden before peer-info arrives`);
  }
  assert.match(html, /id="view-album"[^>]*hidden/);
  assert.match(appJs, /function applyAlbumBrowsing\(/);
});

test('closing the album gate while viewing it swaps the view but not the mobile dest', () => {
  const at = appJs.indexOf('function applyAlbumBrowsing(');
  assert.ok(at > 0, 'no applyAlbumBrowsing');
  const end = appJs.indexOf('\n    }', at);
  const body = appJs.slice(at, end);
  assert.match(body, /\[data-dest="album"\][\s\S]*?hidden = !enabled/);
  assert.match(body, /selectDest\(firstAvailableDest\(\), \{ navigate: false \}\)/);
  assert.equal(/setMobileDest\(|dataset\.mobileDest\s*=/.test(body), false,
    'a peer-info update must not navigate the narrow layout');
});

test('the album destination sits after files in the rail order', () => {
  const rail = html.match(/<div class="fk-rail-items"[\s\S]*?<\/div>/);
  assert.ok(rail, 'no rail item container');
  const dests = [...rail[0].matchAll(/data-dest="([a-z]+)"/g)].map((m) => m[1]);
  const filesAt = dests.indexOf('files');
  const albumAt = dests.indexOf('album');
  const favoritesAt = dests.indexOf('favorites');
  assert.equal(albumAt, filesAt + 1, 'rail order is ' + dests.join(', '));
  assert.ok(favoritesAt > albumAt, 'album must remain before favorites: ' + dests.join(', '));
});

test('album icons are aria-hidden and the button carries the real name', () => {
  for (const kind of ['fk-rail-item', 'fk-navbar-item']) {
    const button = buttonFor(kind, 'album');
    assert.ok(button, `no ${kind} for album`);
    assert.match(button[0], /data-icon="photo_library"[^>]*aria-hidden="true"/);
    assert.match(button[0], /data-i18n="app\.nav\.album"/,
      `${kind} has no visible, translatable name`);
  }
});

test('the destination uses aria-current, not aria-selected', () => {
  for (const kind of ['fk-rail-item', 'fk-navbar-item']) {
    const button = buttonFor(kind, 'album');
    assert.ok(button, `no ${kind} for album`);
    const open = button[0].slice(0, button[0].indexOf('>'));
    assert.match(open, /aria-current="false"/);
    assert.doesNotMatch(open, /aria-selected/);
  }
});
