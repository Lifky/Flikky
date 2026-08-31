/* ============================================================================
 * 源码扫描类测试的共用件：去注释 / 取规则块 / 取标签。
 *
 * **一个正则、一个反斜杠都不用**，这是本文件存在的唯一理由。
 * 这些测试文件由脚本写出来，而 heredoc 与 python 对反斜杠的处理都不可靠：
 * 有时吃掉一层（拼出的正则静默变成通配，断言空转全绿），有时又加倍一层
 * （直接 SyntaxError）。v1.20.0 为此返工三次，两种症状都遇到了。
 * 纯字符串操作没有转义，也就无从出错。
 * ==========================================================================*/

var LF = String.fromCharCode(10);
var SLASH = String.fromCharCode(47);
var STAR = String.fromCharCode(42);

/** 去掉块注释。 */
function stripBlockComments(src) {
  var out = '';
  var i = 0;
  var open = SLASH + STAR;
  var close = STAR + SLASH;
  while (i < src.length) {
    var start = src.indexOf(open, i);
    if (start < 0) { out += src.slice(i); break; }
    out += src.slice(i, start);
    var end = src.indexOf(close, start + 2);
    if (end < 0) break;
    i = end + 2;
  }
  return out;
}

/** 去掉整行的行注释（行内尾注释保留——去掉它要判断字符串字面量，得不偿失）。 */
function stripLineComments(src) {
  var marker = SLASH + SLASH;
  return src.split(LF).filter(function (line) {
    return line.trim().indexOf(marker) !== 0;
  }).join(LF);
}

/** 去掉 import 行。文件级 contains 命中 import 是 v1.20.0 踩过的假绿之一。 */
function stripImports(src) {
  return src.split(LF).filter(function (line) {
    return line.trim().indexOf('import ') !== 0;
  }).join(LF);
}

/** 注释 + import 全去。源码守卫的默认预处理。 */
function scrub(src) {
  return stripImports(stripLineComments(stripBlockComments(src)));
}

/** 取 `selector {` 到下一个 `}` 之间的声明块；找不到返回空串。 */
function ruleBlock(css, selector) {
  var at = css.indexOf(selector + ' {');
  if (at < 0) return '';
  var end = css.indexOf('}', at);
  return end < 0 ? '' : css.slice(at, end);
}

/**
 * 取一个函数从签名到其结束大括号的正文。
 * `indent` 是函数所在层级的缩进宽度（IIFE 里的顶层函数是 4，默认值就是 4）。
 */
function functionBody(src, signature, indent) {
  var at = src.indexOf(signature);
  if (at < 0) return '';
  var width = indent === undefined ? 4 : indent;
  var pad = '';
  while (pad.length < width) pad += ' ';
  var closer = LF + pad + '}';
  var end = src.indexOf(closer, at);
  return end < 0 ? src.slice(at) : src.slice(at, end + closer.length);
}

/** HTML 里所有开标签的属性串。 */
function openTags(html) {
  return html.split('<').slice(1).map(function (s) {
    var gt = s.indexOf('>');
    return gt < 0 ? '' : s.slice(0, gt);
  }).filter(Boolean);
}

/** 标签是否带**裸** hidden 属性。不会误伤 aria-hidden。 */
function hasBareHidden(tag) {
  return tag.slice(-7) === ' hidden' || tag.indexOf(' hidden ') >= 0;
}

/** 标签上的 class 列表。 */
function classesOf(tag) {
  var at = tag.indexOf('class="');
  if (at < 0) return [];
  var rest = tag.slice(at + 7);
  return rest.slice(0, rest.indexOf('"')).split(' ').filter(Boolean);
}

module.exports = {
  LF: LF,
  stripBlockComments: stripBlockComments,
  stripLineComments: stripLineComments,
  stripImports: stripImports,
  scrub: scrub,
  ruleBlock: ruleBlock,
  functionBody: functionBody,
  openTags: openTags,
  hasBareHidden: hasBareHidden,
  classesOf: classesOf,
};
