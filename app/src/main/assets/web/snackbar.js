// snackbar.js — thin wrapper around <mdui-snackbar id="snackbar"> that gives
// every page a uniform toast helper. Falls back to console when the element
// is missing so scripts that happen to run on a page without a snackbar slot
// still log something useful instead of crashing.
//
// Security: messages are written via textContent, never innerHTML, per the
// browser-side red lines in CLAUDE.md. No HTML is ever parsed from these
// arguments, even though mdui-snackbar itself uses shadow DOM internally.
(function (global) {
    function resolveSnackbar() {
        return document.getElementById('snackbar');
    }

    function show(level, text) {
        const msg = text == null ? '' : String(text);
        const el = resolveSnackbar();
        if (!el) {
            if (level === 'error') console.error(msg);
            else console.log(msg);
            return;
        }
        // The default slot is the message body. Setting textContent replaces
        // any existing children safely; mdui re-lays out when `open` toggles.
        el.textContent = msg;
        // Toggling `open` to false first lets consecutive calls re-trigger
        // the open animation even when the prior toast hasn't auto-closed.
        if (el.open) el.open = false;
        el.open = true;
    }

    const api = {
        showError: (text) => show('error', text),
        showInfo: (text) => show('info', text),
    };

    global.flikky = global.flikky || {};
    global.flikky.showError = api.showError;
    global.flikky.showInfo = api.showInfo;
})(window);
