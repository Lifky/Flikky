/**
 * 把一个 listing 对象转成服务端真正会发的 NDJSON 正文。
 *
 * 测试里必须喂真格式：客户端只走 `?stream=1` 这一条路（读不了流就把同一个响应
 * 整体取文本，用同一个解析器跑），所以喂整块 JSON 的假响应测的是一条不存在的路径。
 */
function ndjson(listing) {
  const lines = [JSON.stringify({ path: listing.path })];
  (listing.entries || []).forEach((e) => lines.push(JSON.stringify(e)));
  lines.push(JSON.stringify({ done: true }));
  return lines.join(String.fromCharCode(10)) + String.fromCharCode(10);
}

module.exports = { ndjson: ndjson };
