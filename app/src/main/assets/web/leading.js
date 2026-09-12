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
    // **不设 `loading = 'lazy'`。** 下一行把它设为 hidden，而 base.css 的全局 reset
    // 是 `[hidden]:not([data-hidden-animated]) { display: none !important }` ——
    // 一张 display:none 的图片永远不进视口，于是惰性加载**永不发起请求**：
    // load 事件不触发 → 类型图标不被移除 → 看不到缩略图；img 又是 display:none、
    // 接不到点击 → 也点不开预览。装机反馈的两个症状是同一个根因（2026-09-12）。
    //
    // 惰性加载在这里本来也没有收益：列表是虚拟化的，只有视口附近的行存在于 DOM 里
    //（设计 §9 风险 4 已经预判过这一点）。普通 <img> 即使 display:none 也会照常加载，
    // 所以去掉 lazy 就够了，hidden 保留 —— 它是「加载完成前不露出半张图」的手段。
    img.hidden = true;
    img.dataset.forPath = path;

    img.addEventListener('load', function () {
      if (img.dataset.forPath !== path || !img.src) return;
      Array.prototype.slice.call(lead.children).forEach(function (child) {
        if (child !== img) lead.removeChild(child);
      });
      // 形状类**到这里才加**，不在建元素时加：`.fk-item-lead--thumb` 含
      // `clip-path: none`，提前加会让类型图标在等图期间先失去 M3 异形、
      // 图到了再变成圆角方块 —— 一次可见的形状闪跳。加载成功才换形状，
      // 失败路径则从头到尾都是异形图标，不闪。
      lead.classList.add('fk-item-lead--thumb');
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
