/**
 * 「请求加入」入口页的状态规则（v1.23 多设备）：会话里已有设备时，新设备点「请求加入」，等手机允许。
 *
 * 纯逻辑、不碰 DOM；login.js 负责把它接到页面上。请求凭证在 HttpOnly Cookie 里，这里读不到也不需要。
 */
(function (global) {
    'use strict';

    /** 服务端应答 → 页面状态。丢了的请求（unknown）与过期同样处理：再请求一次就好。 */
    function outcome(httpStatus, body) {
        const status = body && typeof body.status === 'string' ? body.status : '';
        if (httpStatus === 200 && status === 'approved') return 'approved';
        if (httpStatus === 202) return 'waiting';
        if (httpStatus === 403) return 'denied';
        if (httpStatus === 410) return 'expired';
        if (httpStatus === 429) return 'busy';
        if (httpStatus === 409) return 'not_needed';
        return 'error';
    }

    /** 比对码（v1.24 D82）：只认 4 位数字，别的一律不显示。 */
    function codeFrom(body) {
        const code = body && typeof body.code === 'string' ? body.code : '';
        return /^\d{4}$/.test(code) ? code : '';
    }

    /** 读屏逐位念：「4 8 2 1」，而不是「四千八百二十一」。 */
    function spokenCode(code) {
        return String(code).split('').join(' ');
    }

    const VIEWS = {
        idle: { textKey: 'login.join.intro', buttonKey: 'login.join.request', buttonDisabled: false, polling: false },
        waiting: { textKey: 'login.join.waiting', buttonKey: 'login.join.waiting_button', buttonDisabled: true, polling: true, showsCode: true },
        denied: { textKey: 'login.join.denied', buttonKey: 'login.join.retry', buttonDisabled: false, polling: false },
        expired: { textKey: 'login.join.expired', buttonKey: 'login.join.retry', buttonDisabled: false, polling: false },
        busy: { textKey: 'login.join.busy', buttonKey: 'login.join.retry', buttonDisabled: false, polling: false },
        error: { textKey: 'login.join.error', buttonKey: 'login.join.retry', buttonDisabled: false, polling: false },
    };

    function view(state) {
        return VIEWS[state] || VIEWS.idle;
    }

    global.flikkyJoin = { outcome, view, codeFrom, spokenCode, POLL_MS: 1500 };
})(globalThis);
