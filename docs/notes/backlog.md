# Flikky 未来版本候选 backlog

v1.x 交付过程中用户反馈、测试暴露、我们自己想到但当版不做的改进点都记在这里。排版本时从这里挑，挑完删掉或移到对应版本的 spec。

格式：每条写清楚**场景 → 痛点 → 候选方案 → 目标版本**。

---

## B1. 大文件上传进度可视化（用户反馈，v1.2 候选）

**场景：** 浏览器选择大文件（>100 MB）上传到手机，fetch 期间消息列表没有任何消息泡，用户不知道传到哪了、也不知道是不是在传。

**痛点：** v1.1 的 UI 约定是"HTTP 完成后服务端 WS 广播 `file_added` → 前端 append 消息泡"。大文件走完 fetch 可能要十几秒甚至几十秒，这段时间里没有任何前馈，体验上像卡死。小文件因为很快到达，感觉不出问题。

**候选方向：**

1. **前端乐观 UI（本地先泡）**
   - 按下上传时立即在本地 message list 追加一条 status=`uploading` 的占位泡（带文件名、总大小、进度条）。
   - 真正开始 fetch 后用 `XMLHttpRequest.upload.onprogress` 或 `fetch + ReadableStream` 更新本地泡的进度。
   - HTTP 响应返回后，用服务器返回的 `fileId` 替换占位泡为正式泡（避免 WS 重复 append）。
   - 优点：不需要改服务端；浏览器原生能拿到上传字节数。
   - 难点：`fetch` 原生不给 upload progress，需要切到 `XMLHttpRequest`；去重逻辑要做对（已经 append 过的 `file_added` WS 事件要忽略）。

2. **服务端分块广播 WS 进度**
   - `post("/api/files")` handler 用 buffered read，每接收 1 MB 就 broadcast 一次 `file_progress` 事件。
   - 所有客户端（包括手机端 APP、上传端浏览器）都能看到进度。
   - 代价：服务端实现更复杂；WS 流量放大；对前端去重逻辑要求更高。

3. **混合方案（推荐起点）**
   - 前端用方向 1 在本地显示自己的上传进度；
   - 服务端收完整个文件后广播 `file_added`（现状不变），前端替换占位泡。
   - 手机端 APP 看不到浏览器上传的进度（只看到最终文件），但用户反馈里没要求这个，先不做。

**涉及改动：**
- 前端 `app.js` 的 `sendFile` 改用 XHR + progress；renderFileBubble 增加 status 字段（uploading/completed）；style.css 加进度条样式。
- 手机端 APP UI 不用改（它本来就只在 WS 广播后收到完整消息）。

**预估工作量：** 中等。1-2 天（含测试）。放到 v1.2 专版（与"多会话导出"并行）。

---

## B2. 浏览器端 alert 替换为 mdui snackbar（延伸自 T14 修复）

**场景：** T14 修复中给 `sendFile` 加的上传失败提示用的是原生 `alert()`，体验粗糙。

**候选方向：** 用 mdui 的 snackbar 组件替换，和登录错误、PIN 锁定等现有提示风格统一。

**预估工作量：** 小（<半天）。可以在 v1.2 或任何顺手的改动里带上。

---

_更多条目请在发现时追加，不要批量攒一起。_
