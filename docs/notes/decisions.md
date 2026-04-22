# 技术与产品决策记录

每条决策：选的什么、拒绝了什么、理由。以后回头看要能判断"这个前提还成立吗"。

---

## D1. v1 范围 = 最小连通闭环

**选：** 只做 启动服务 → URL+PIN 接入 → 双向文本 → 双向文件 → 底部状态栏。
**拒：** 会话列表、消息撤回、加密存储、历史导出。

**理由：** 项目风险集中在"通道稳定 + 安全基线 + 交互骨架"三件事。把这三件打通是一切后续工作的前置条件，越早发现问题越好。剩余功能按 v1.1/1.2/1.3/2.0 串起来，每版独立验收。

**何时重新评估：** 如果甲方在 v1 验收时给出高优先级的新需求（比如"必须先有历史记录"），需要重排路线图。

---

## D2. 认证 = URL + 6 位 PIN（单次使用）

**选：** 手机显示 `http://IP:PORT` + 6 位 PIN；浏览器输 URL 后再输 PIN；PIN 认证成功即作废。
**拒：**
- URL 里含一次性长 token（太长、凭 URL 即有全部权限）
- 二维码（电脑通常没摄像头）
- 手机端弹窗批准（多一步、非必要）

**理由：** 最少步骤 + 防 LAN 内窥探 + 无需扫码设备。单次使用避免 PIN 泄露后反复被尝试。

**风险：** PIN 只有 100 万种可能——3 次锁 30 秒 + 5 次终止让暴力破解不可行。

---

## D3. v1 传输层 = HTTP 明文

**选：** HTTP + PIN + Cookie，v2 再升 HTTPS 自签。
**拒：** 一开始就上 HTTPS 自签（浏览器红屏警告、证书生成与指纹信任流程复杂）。

**理由：** LAN 内威胁模型下，PIN 单次使用 + 短会话大幅降低明文被利用的价值；开发体量显著降低；v2 升 HTTPS 时再处理证书与指纹信任。

**必须同时做：** 文档中明确披露此限制——不能骗甲方说安全到家。

---

## D4. HTTP 服务器 = Ktor 3 + CIO 引擎

**选：** Ktor 3 + CIO。
**拒：** NanoHTTPD、AndServer、http4k。

**理由：**
- 原生 Kotlin 协程、WebSocket 一流支持（撤回/实时状态/进度推送都要用）
- JetBrains 长期维护
- CIO 引擎避开 Netty 在 Android 上的依赖问题

**要注意的事：** 3.x 相对 2.x 有 API 减法（见 `traps-and-fixes.md` T1/T2）。写代码时照着当前版本文档对，不凭记忆。

---

## D5. 电脑端前端 = 纯原生 HTML/CSS/JS

**选：** 不引 React/Vue/Svelte，不引 Material Web Components，手写 MD3 风格 CSS。
**拒：** 引框架即便轻量（Preact、Alpine）。

**理由：**
- 电脑端资源被打包进 APK，体积越小越好
- 无构建步骤、无依赖审计成本
- 代码量小（两页 + WS + 上传），框架的抽象收益低于复杂度
- 安全审计更容易

**何时重新评估：** 若后续页面变多（如会话列表、历史详情、设置页），可能需要轻量模板层。

---

## D6. 单 Gradle 模块 + 包内分层

**选：** `:app` 一个模块，按包（`ui/service/server/session/network/util/di`）分层。
**拒：** 多模块（`:core`/`:server`/`:ui` 等）。

**理由：** v1 体量不够大到需要模块隔离。多模块的收益（编译并行、依赖隔离、复用）在单设备单 APK 的场景里几乎为零。包内分层已经提供了足够清晰的边界。

**何时重新评估：** 如果以后要把 server 部分单独做成库供其他 APP 复用，再拆模块。

---

## D7. JUnit 4（不升 JUnit 5）

**选：** 沿用 Android Studio 模板里的 JUnit 4 + MockK + Turbine。
**拒：** 引入 `de.mannodermaus.android-junit5` 插件迁到 JUnit 5。

**理由：** JUnit 5 在 Android 上需要额外插件配置；v1 单测需求简单，JUnit 4 够用；降低构建脆弱性。

---

## D8a. 电脑端组件库改用 mdui（2026-04-17 修订 D5）

**背景：** v1 rc1 的手写 MD3 CSS 在实际浏览器里视觉"不够 Material"，留白、elevation、交互态都显得业余。

**选：** 用 `mdui` v2（https://github.com/zdhxiong/mdui）作为组件库，2 个文件（`mdui.css` 22KB + `mdui.global.js` 354KB）**本地打包**进 APK 的 `assets/web/vendor/`。
**拒：** 继续手写；或引入 React 生态的 Material UI/shadcn（体量大且需构建）。

**理由：**
- mdui 是原生 Web Components，`<mdui-button>`/`<mdui-text-field>`/`<mdui-card>` 等，无需构建工具
- 完整 MD3 色彩系统 + 暗色主题自动切换，比手写 CSS 专业
- 380KB 可接受（APK 当前 ~34MB）
- 本地打包 = 不破坏"不联网"红线

**副作用：**
- CSP 需要加 `'unsafe-inline'` 到 `style-src`——mdui 的 JS 会给宿主元素设 inline style。XSS 防护从 script-src 那一侧仍然强（只允许 `'self'`）
- 部分 `<style>` 和 `style="..."` 改成 mdui 的 attribute（如 `variant="filled"`），但我仍留了少数 inline style 处理间距

**何时重新评估：** 若 mdui 的 bundle 在某次升级后体积翻倍，或项目移除 "离线打包" 要求时。

---

## D8. v1 不持久化消息，只在内存里

**选：** 所有消息只在内存 `SessionState` 里，服务停止即丢。
**拒：** v1 就做 Room/SQLite 落盘。

**理由：** 避免 v1 就引入数据库层（DB schema 设计、迁移策略、加密与否）。v1.1 专门做归档时再引入。

---

## D9. 通知栏显示 URL，不显示 PIN（2026-04-18 用户反馈）

**选：** 前台服务通知栏文案为 "URL  http://IP:PORT / PIN  打开 APP 查看"。
**拒：** 通知栏里直接显示 6 位 PIN。

**理由：** 锁屏 / 通知托盘会被旁边的人瞟到。URL 无所谓（LAN 内任意设备都能发现），但 PIN 是一次性密钥，一旦被旁观者记下再配合 URL 就能直接进入。把 PIN 限定在解锁 APP 之后可见，缩小物理世界攻击面。

**副作用：** 用户不能"锁屏就看 PIN"，多一步解锁 APP 的操作。这个代价可接受——URL 仍可直接扫到，只有 PIN 需要解锁。

**何时重新评估：** 如果产品定位从"安全优先"转为"便捷优先"（比如在受信任的家庭 WiFi 场景），可以加一个设置开关让用户自己选。

---

## D10. v1.1 持久层 = Room（2026-04-18）

**选：** Room 2.7 + KSP。
**拒：** JSON/JSONL 文件；裸 SQLite。

**理由：** 用户选型。Room 为 v1.3 消息搜索 / 撤销铺路，SQL 查询能力与 schema 迁移机制是将来用得上的基础设施；代价是 KSP 编译与 schema 管理。

**何时重新评估：** 如果 v1.3 计划被长期推迟 / 产品转向纯文件导出语义，JSON 方案仍是更轻的备选。

---

## D11. 文件按 sessionId 分目录

`filesDir/sessions/{id}/files/{fileId}`。**理由：** v1.2 "导出单会话"时直接 tar/zip 一个目录；FIFO 删除就是 rm -rf。后续加密（v2）对一整个目录加密也自然。

---

## D12. 手机推送文件同步 tee

浏览器下载前先把 content:// URI 完整拷到 sessions/{id}/files/。**理由：** 持久副本让离线回看成立；代价是大文件选中到可下载之间有拷贝延迟。v1.2 可以改成"先给浏览器响应流、后台异步 tee"以消除延迟。

---

## D13. SessionEntity 冗余字段（messageCount / fileCount / totalBytes / previewText）

**理由：** 列表高频读、消息表大，每次扫 messages 汇总不合算。update 时机集中在 endSession（以及 finalizeOrphans 收尾），不影响消息写入路径。

**副作用：** 如果将来需要"实时看到当前消息数"，得切换到观测 messages 表 count 派生。

---

## D14. 单表 messages + kind 判别

两种消息同表，用 `kind` 列区分。**理由：** 历史页按 timestamp 混排，单表一次查询；两表 UNION/内存合并得不偿失。

**副作用：** null 字段较多。

---

## D15. FIFO 触发点 = beginSession 开始时

不是 endSession 结束时。**理由：** 避免本次刚结束的会话被自己的 FIFO 判定误伤（配额正好卡在 20 的极端情况）。

---

## D16. 空会话回滚位置 = endSession

不是"延迟 insert 会话行"。**理由：** 消息收发路径不引入额外同步或重试；结束时统一判。

---

## D17. v1.1 保留上限 = 20（硬编码）

不做设置页。**理由：** 以 FIFO 20 条应付先；若未来用户反馈需要可配置，v1.2 加设置页 + SharedPreferences。

---

## D18. v1.2 导出服务 = `TransferService` + `ACTION_EXPORT` mode（2026-04-22）

不起独立 `ExportService`，复用 `TransferService` 加一个 `ACTION_EXPORT` 分支，与 `ACTION_START` 互斥。

**理由：** 独立 Service 意味着多一套 notification / PIN / 端口 / lifecycle——v1.x 单用户场景用户一次只干一件事；互斥是需求而非限制。

**副作用：** 导出期间不能同时开传输；v1.3 再看要不要并行。

---

## D19. 导出交付 = 流式单 zip + messages.txt + messages.json 双格式（2026-04-22）

勾选多会话 → 一个 zip 下载。zip 内 `sessions/{id}_{safeName}/{messages.txt, messages.json, files/}`，根 `README.txt`。

**理由：** 一次点击拿到一份可存档归档；txt 人看、json 机器读（v1.3 可能的"导回 APP"留路径）；流式避免整份载内存。

**副作用：** 中途取消 → 半个 zip，无断点续传。LAN 单用户重来一次就行，接受。

---

## D20. 导出完成 = "保留 / 删除" 二择 + 删除二次确认（2026-04-22）

`DoneScreen` 两按钮；点"删除本地"额外弹 `AlertDialog` 确认。

**理由：** 删除不可逆；完成页选择项放这里而非勾选时，避免误操作；视觉突出"保留本地"降低误删；点删除多一层确认与可逆动作（退出多选等）分档。

---

## D21. B1 上传进度 = 前端乐观 UI + `XMLHttpRequest.upload.onprogress`（2026-04-22）

不走服务端分块广播 WS 进度方案。浏览器本地立刻 append uploading 泡 + XHR progress 更新；服务端返回 fileId → 泡上 `data-file-id`；WS `file_added` 按 fileId dedup。

**理由：** 服务端分块广播引入多客户端流量放大 + dedup 复杂度；XHR 是浏览器原生唯一能拿 upload progress 的 API；dedup key 直接用服务端返回的 fileId，不引入新状态。

**副作用：** 手机端 APP 看不到浏览器上传中间进度（只看到最终文件）。用户反馈未要求，接受。

---

## D22. Wi-Fi 切换 = 停旧 Ktor + 起新 Ktor，不做连接迁移（2026-04-22）

`ConnectivityManager.NetworkCallback` 检测到 IPv4 变化 → stop Ktor → `KtorServer(host = newIp)` 新实例 → start → 广播新 URL 给 UI。旧浏览器连接必然断，用户需在新 URL 重新打开。

**理由：** Ktor CIO 没有 rebind 能力，host 是构造参数必须重构实例；尝试"跨 IP 保留 WS"复杂度远超收益。

**副作用：** 切换期间 < 1s 断连窗口；浏览器用户手动再开一次。`Lost` 不主动停 Ktor（等 IP 回来）。

---

## D23. 导出期间不做"暂停传输 → 导出 → 恢复"的状态保存（2026-04-22）

同 D18 呼应，一次一件事。

**理由：** 保存 running session state + 恢复传输状态会显著增加 TransferService 复杂度；v1.2 流程里不是高频需求。并行等 v1.3。
