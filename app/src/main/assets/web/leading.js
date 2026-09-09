(function (root) {
  'use strict';

  const types = root.flikkyLeadingTypes || [];
  const other = types.find((type) => type.id === 'other');
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
    const id = typeof shapeId === 'string' && shapeId.trim() ? shapeId.trim() : 'cookie9Sided';
    style.setProperty('--flikky-leading-clip', `url(#flikky-shape-${id})`);
  }

  root.flikkyLeading = Object.freeze({ types, typeOf, applyShape });
  applyShape();
})(globalThis);
