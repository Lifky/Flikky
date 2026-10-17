const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { functionBody } = require('./scan.js');

// 「请求加入」入口页（v1.23 多设备，用户裁决）：会话里已有设备时，新设备点「请求加入」，等手机允许。
const WEB = path.join(__dirname, '../../main/assets/web');
const joinSrc = fs.readFileSync(path.join(WEB, 'join.js'), 'utf8');
const loginSrc = fs.readFileSync(path.join(WEB, 'login.js'), 'utf8');
const html = fs.readFileSync(path.join(WEB, 'login.html'), 'utf8');

function load() {
    const c = {};
    vm.createContext(c);
    vm.runInContext(joinSrc, c);
    return c.flikkyJoin;
}

test('server answers map to what the page should show', () => {
    const j = load();
    assert.equal(j.outcome(202, { status: 'pending' }), 'waiting');
    assert.equal(j.outcome(200, { status: 'approved', redirectTo: '/app' }), 'approved');
    assert.equal(j.outcome(403, { status: 'denied' }), 'denied');
    assert.equal(j.outcome(410, { status: 'expired' }), 'expired');
    assert.equal(j.outcome(410, { status: 'unknown' }), 'expired', 'a lost request reads as expired: just ask again');
    assert.equal(j.outcome(429, { status: 'busy' }), 'busy');
    assert.equal(j.outcome(409, { status: 'not_needed' }), 'not_needed');
    assert.equal(j.outcome(500, {}), 'error');
});

test('every state tells the user what happens next', () => {
    const j = load();
    for (const state of ['idle', 'waiting', 'denied', 'expired', 'busy', 'error']) {
        const v = j.view(state);
        assert.ok(v.textKey.startsWith('login.join.'), state);
        assert.ok(v.buttonKey.startsWith('login.join.'), state);
    }
    assert.equal(j.view('waiting').buttonDisabled, true, 'no double requests while waiting');
    assert.equal(j.view('denied').buttonDisabled, false, 'a refused page can ask again');
    assert.equal(j.view('idle').polling, false);
    assert.equal(j.view('waiting').polling, true);
});

test('the page loads join.js before login.js and has a hidden join panel', () => {
    const j = html.indexOf('/static/join.js');
    assert.ok(j > 0 && j < html.indexOf('/static/login.js'));
    assert.match(html, /<section[^>]*id="join-panel"[^>]*hidden/);
    assert.match(html, /id="join-text"[^>]*aria-live="polite"/);
});

test('login.js asks which way in, polls while waiting and renders text safely', () => {
    assert.match(loginSrc, /fetch\('\/api\/join\/state'\)/);
    assert.match(loginSrc, /fetch\('\/api\/join', \{ method: 'POST'/);
    assert.match(loginSrc, /fetch\('\/api\/join\/status', \{ method: 'POST'/);
    assert.doesNotMatch(loginSrc, /innerHTML/);
    const render = functionBody(loginSrc, 'function renderJoin(');
    assert.match(render, /textContent/);
    // PIN 已被用掉 = 会话里已有设备：直接切到「请求加入」，不再让人对着失效的 PIN 发呆。
    assert.match(loginSrc, /err === 'pin_consumed'\) showJoin\(\)/);
});
