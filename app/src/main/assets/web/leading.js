(function (root) {
  'use strict';

  const types = root.flikkyLeadingTypes || [];
  const other = types.find((type) => type.id === 'other');
  const DEFAULT_SHAPE = 'cookie9Sided';
  const RGB_HEX = /^#[0-9a-f]{6}$/i;
  if (!other) throw new Error('leading type catalogue is missing the other fallback');

  function typeOf(mime) {
    const normalized = String(mime || '').split(';', 1)[0].trim().toLowerCase();
    if (normalized === 'image/svg+xml') return other;
    return types.find((type) =>
      type.mimeExact.includes(normalized) ||
      type.mimePrefixes.some((prefix) => normalized.startsWith(prefix))) || other;
  }

  function applyShape(shapeId) {
    const style = root.document && root.document.documentElement && root.document.documentElement.style;
    if (!style) return;
    const id = typeof shapeId === 'string' && shapeId.trim() ? shapeId.trim() : DEFAULT_SHAPE;
    style.setProperty('--flikky-leading-clip', `url(#flikky-shape-${id})`);
  }

  function applyColors(colors) {
    const style = root.document && root.document.documentElement && root.document.documentElement.style;
    if (!style) return;
    const source = colors && typeof colors === 'object' ? colors : {};
    types.forEach((type) => {
      const pair = source[type.id];
      const valid = Array.isArray(pair) && pair.length >= 2 &&
        RGB_HEX.test(pair[0]) && RGB_HEX.test(pair[1]);
      const bg = `--flikky-leading-bg-${type.id}`;
      const fg = `--flikky-leading-fg-${type.id}`;
      if (valid) {
        style.setProperty(bg, pair[0]);
        style.setProperty(fg, pair[1]);
      } else {
        style.removeProperty(bg);
        style.removeProperty(fg);
      }
    });
  }

  function applyVisual(visual) {
    const value = visual && typeof visual === 'object' ? visual : {};
    applyShape(value.shape);
    applyColors(value.colors);
  }

  /**
   * Attach one authenticated thumbnail to an existing leading slot.
   *
   * The caller owns the fallback icon. This keeps the image lifecycle shared by
   * storage and favorites while letting each panel retain its own row semantics.
   */
  function attachThumbnail(lead, options) {
    if (!lead || !options) return null;
    const doc = root.document;
    if (!doc || typeof doc.createElement !== 'function') return null;

    const path = String(options.path || '');
    const img = doc.createElement('img');
    img.alt = options.alt || '';
    img.loading = 'lazy';
    img.hidden = true;
    img.dataset.forPath = path;
    lead.classList.add('fk-item-lead--thumb');

    img.addEventListener('load', function () {
      if (img.dataset.forPath !== path || !img.src) return;
      Array.prototype.slice.call(lead.children).forEach(function (child) {
        if (child !== img) lead.removeChild(child);
      });
      img.hidden = false;
      img.dataset.thumbLoaded = '1';
    });
    img.addEventListener('error', function () {
      if (img.dataset.forPath !== path || !img.src) return;
      if (typeof options.onError === 'function') options.onError();
    });
    if (typeof options.onClick === 'function') {
      img.addEventListener('click', function (event) {
        event.stopPropagation();
        options.onClick();
      });
    }
    lead.appendChild(img);
    img.src = String(options.url || '');
    return img;
  }

  root.flikkyLeading = Object.freeze({
    types,
    typeOf,
    applyShape,
    applyColors,
    applyVisual,
    attachThumbnail,
  });
  applyVisual();
})(globalThis);
