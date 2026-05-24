# Flikky

[English](./README.md) | **简体中文**

安卓手机与浏览器之间的局域网文件与消息互传。接收端零安装、零联网，专为临时即用的共享场景设计。

手机启动内嵌 HTTP 服务器，同 WiFi 下任意浏览器打开手机上展示的 URL，输入一次性 6 位 PIN 后即建立会话，文本与文件可双向实时传输。

## 当前状态

- **v1.0** — 最小连通闭环：启动服务、URL + PIN 配对、双向文本与文件、MD3 聊天气泡、前台服务常驻通知、完整安全基线。
- **v1.1** — *已发布（2026-04-21）* — Room 会话归档、主页会话列表（置顶 / 重命名 / 删除）、历史详情只读查看、APP 启动时 crash-recovery、FIFO 保留最近 20 条非置顶会话（置顶不占配额）。
- **v1.2** — *已发布（2026-05-13）* — 多会话批量导出到 PC（流式 zip）、浏览器上传实时进度气泡、mdui snackbar、WiFi 自动重绑 + 状态 banner、进行中会话主页交互（继续 / 行内停止）、WS 应用层心跳 + server_stopped 区分主动停止与网络断开。
- **v1.3** — *已发布（2026-05-24）* — 跨会话消息搜索（FTS4 + LIKE fallback）、进行中服务消息撤回（真删 + 双端二次确认 + 即时同步）、History 单条消息删除、应用层 ping/pong 替代被动心跳、闭包死引用系统性审计 + 回归保护、导出页 WS 健康检测 + 取消导出 dialog。

设计文档与复盘/验收清单保存在本地的 `docs/others/`（已 gitignored），公开仓库仅含源码。

## 进度

### feat

- [x] 内嵌 HTTP server，仅绑定当前 WiFi IPv4 接口 *(v1.0)*
- [x] URL + 单次 PIN 配对，错 3 次锁 IP 30 秒，错 5 次终止服务 *(v1.0)*
- [x] 双向文本 / 文件实时传输（HTTP + WebSocket） *(v1.0)*
- [x] 前台服务 + 常驻通知（通知只显示 URL，不显示 PIN） *(v1.0)*
- [x] 手机端 MD3 聊天气泡（Jetpack Compose） *(v1.0)*
- [x] 浏览器端原生 JS + 离线打包的 mdui Web Components *(v1.0)*
- [x] 安全基线：严格 CSP、`X-Frame-Options: DENY`、`nosniff`、`Referrer-Policy: no-referrer`、`HttpOnly` + `SameSite=Strict` Cookie、只用 `textContent`、文件经 Blob URL 下载 *(v1.0)*
- [x] Room 会话归档：`SessionEntity` + `MessageEntity`（单表 + kind 判别，`FOREIGN KEY ... ON DELETE CASCADE`） *(v1.1)*
- [x] 主页会话列表 + 长按菜单（置顶 / 重命名 / 删除） *(v1.1)*
- [x] History 页（单会话只读时间线） *(v1.1)*
- [x] FIFO 保留最近 20 条非置顶会话；置顶不占配额 *(v1.1)*
- [x] APP 启动时 crash-recovery：`finalizeOrphans()` 补齐未关闭会话、回滚空会话、清理孤立文件目录 *(v1.1)*
- [x] 文字消息可复制（长按触发系统复制 / 全选菜单） *(v1.1)*
- [x] 多会话批量导出到 PC，流式 zip（`messages.txt` + `messages.json` + `files/`） *(v1.2)*
- [x] `/api/export/info` 概览 endpoint + 浏览器 `/export` 页（mdui 风格） *(v1.2)*
- [x] 导出完成「保留本地 / 删除本地」二择，删除带 AlertDialog 二次确认 *(v1.2)*
- [x] 浏览器上传实时进度气泡（XHR `upload.onprogress` + X-Client-Id senderId dedup） *(v1.2)*
- [x] WiFi 自动重绑：`ConnectivityManager.NetworkCallback` → 停 / 重建 Ktor，banner 报 Lost / Switching / Switched *(v1.2)*
- [x] 进行中会话主页交互：点击进 ServingScreen 继续 / 行内「停止」按钮 / FAB 切换"继续服务" *(v1.2)*
- [x] WS 应用层心跳（4 秒 frame 超时）+ `server_stopped` event 区分用户主动停止与网络断开 *(v1.2)*
- [x] 跨会话消息搜索：FTS4 全文索引 + LIKE fallback（CJK 走 LIKE），搜索屏 debounce 输入 + 命中跳转 History + 滚动高亮 *(v1.3)*
- [x] 进行中服务消息撤回：长按→确认 AlertDialog→真删 + 两端节点即时消失 + snackbar 提醒；History 单条消息删除 *(v1.3)*
- [x] 应用层 ping/pong + 2 秒 frame 超时：断网 ~2 秒感知，替代 v1.2 的被动心跳 *(v1.3)*
- [x] 闭包死引用系统性审计：TransferControllerRebindReferenceTest 回归保护 + CLAUDE.md 规范 *(v1.3)*
- [x] 导出页健康检测：WS 连接（ping/pong）+ fetch 探测、取消导出 dialog、下载开始清理 *(v1.3)*
- [x] `senderId` 全链路：手机端 `phone-{ANDROID_ID}` 跨重启不变，浏览器 `X-Client-Id` 按会话；撤回鉴权按 senderId 匹配 *(v1.3)*
- [ ] 从 zip 导入回 APP *(v1.4，backlog B8)*
- [ ] HTTPS 自签证书 *(v2)*
- [ ] 本地归档 at-rest encryption *(v2)*

### opt

- [x] 浏览器端从手写 CSS 迁到 mdui Web Components *(v1.0 后期)*
- [x] 通知栏不显示 PIN——锁屏是物理世界的攻击面 *(v1.0 后期)*
- [x] 文件按 `sessionId` 分目录（`filesDir/sessions/{id}/files/{fileId}`），FIFO 淘汰即 `rm -rf` *(v1.1)*
- [x] `SessionEntity` 冗余聚合字段（`messageCount` / `fileCount` / `totalBytes` / `previewText`），主页列表不必反向扫 messages 表 *(v1.1)*
- [x] Ktor multipart 解除默认 50 MiB 上限（LAN 单用户场景，磁盘空间才是真天花板） *(v1.1)*
- [x] 浏览器端 native `alert` 替换为 mdui snackbar（`window.flikky.showError/showInfo`） *(v1.2)*
- [x] 通知栏文案在 WiFi rebind 后刷新到新 IP *(v1.2)*
- [x] `/api/messages` 返回时间戳合并排序的 `ordered` 视图，刷新后文本/文件按时间顺序混排 *(v1.2)*
- [x] `MAX_RECONNECT_ATTEMPTS` 上限 + 应用层心跳检测死 WS *(v1.2)*
- [x] 浏览器断网 UI 即时更新（不等 TCP close），heartbeat 检测到超时立即 disable 按钮 + banner *(v1.3)*
- [x] 文件下载 `Content-Disposition` 使用原始文件名而非 UUID *(v1.3)*
- [ ] 手机推送文件的 tee 改异步（消除选中到可下载的拷贝延迟） *(v1.4，backlog B7)*

### fix

- [x] v1.0-rc1：`staticResources` 从 JVM classpath 而不是 Android assets 读；缺 `POST_NOTIFICATIONS` runtime 请求；登录页 JS 没加载导致表单默认提交
- [x] v1.1 T8：AGP 9 + 内置 Kotlin 封禁 `kotlin.sourceSets` DSL → 加 `android.disallowKotlinSourceSets=false`
- [x] v1.1 T9 / T10：Robolectric 4.14 最高只支持 SDK 33 且不再传递 `androidx.test:core`
- [x] v1.1 T11：`HomeViewModel(app, repo = ServiceLocator.repository)` 让 `AndroidViewModelFactory` 反射构造失败 → 加 `@JvmOverloads`
- [x] v1.1 T12：v1.1 改了文件路径但 `file_paths.xml` 还是 v1.0 的 `transfer/` → `FileProvider.getUriForFile` 抛异常；同时给 `openFile` 加 try/catch 兜底
- [x] v1.1 T13：`FileRoutes` POST 只更新内存 session、漏调 DB 持久化 → `endSession` 把"浏览器只发文件"的会话当空会话回滚，文件一起删 → 给 `fileRoutes` 加 `onPersist`，从 `KtorServer` 串进来
- [x] v1.1 T14：Ktor 3.0 默认 `receiveMultipart()` 静默上限 50 MiB → 改 `formFieldLimit = Long.MAX_VALUE`；浏览器 `fetch` 失败时 alert 暴露非 2xx 响应
- [x] v1.2 ServiceLocator.reset 替换 instance → HomeViewModel 缓存死引用 → 二次导出崩溃 + 停服后 UI 仍认为传输在跑。reset 现在复用 instance + clearExport 由具体调用方控制
- [x] v1.2 `ForegroundServiceDidNotStartInTime`：early-return 路径漏调 startForeground。`onStartCommand` 顶端无条件 startForeground 占槽位，业务路径决定保留或 stopForeground + stopSelf
- [x] v1.2 浏览器 PIN 登录后在 Export mode 也跳 `/app`：`AuthResponse.redirectTo` 按 ServiceMode 返回
- [x] v1.2 浏览器上传成功后双消息泡（uploader 收到自己的 WS 广播）：POST 透传 `X-Client-Id` header，broadcast payload 携带 senderId；浏览器 WS 收到 senderId == myClientId 跳过
- [x] v1.2 闭包死引用一族 bug：`statusBroadcastJob` / `TransferController.wsHub` 在 startTransfer 时 capture 局部 `server.wsHub`，rebind 后还朝旧 hub 广播。两处改为 lambda 从 field-level 取 `ktor?.wsHub`
- [x] v1.2 浏览器拿着死的 half-open WS（OS 没立即撕 TCP，`readyState` 仍 OPEN）：应用层 4 秒 frame 超时主动 close 触发重连
- [x] v1.2 用户主动停止后浏览器死循环重连：服务端 close WS 前发 `server_stopped` event，浏览器 set flag 跳过 reconnect timer；兜底 `MAX_RECONNECT_ATTEMPTS = 6`
- [x] v1.3 FTS4 `categories='L* N* Co'` 在 Android SQLite（无 ICU）崩溃。回退到 `remove_diacritics=1`；CJK 搜索走 LIKE fallback
- [x] v1.3 撤回功能从 History 软删 + 占位符 → ServingScreen 真删 + 即时消失 + 双端二次确认 dialog（验收反馈驱动的大返工）
- [x] v1.3 `ws.close()` 在 TCP 半开时阻塞 30-60 秒才触发 onclose，UI 反馈严重滞后。heartbeat 检测到超时后立即更新 UI + 丢弃旧 WS + 启动重连
- [x] v1.3 导出页 WS 用 frame 超时但 export mode 无 status broadcast → 即时断开循环。改为 ping/pong
- [x] v1.3 导出页下载/取消后没关 WS → server 停掉触发重连循环。下载和 `server_stopped` 现在关 WS + 停探测
- [x] v1.3 文件下载保存名是 UUID 而非原始文件名。`Content-Disposition` 从 session 内存查原始文件名

## 亮点

- **电脑端零安装**：一个浏览器就够。不用装应用、不用装插件、不用注册账号。
- **只走局域网**：服务器仅绑定当前 WiFi IPv4 接口，绝不绑 `0.0.0.0`，不发任何外网请求。
- **PIN 单次使用**：认证成功即作废，后续必须换新 PIN。错 3 次锁该 IP 30 秒，错 5 次终止服务。
- **浏览器端加固**：严格 CSP、`X-Frame-Options: DENY`、`nosniff`、`Referrer-Policy: no-referrer`、`HttpOnly` + `SameSite=Strict` Cookie、只用 `textContent`（禁 `innerHTML`）、文件经 Blob URL 下载。
- **原生 MD3 视觉**：手机端 Jetpack Compose，浏览器端 [mdui](https://github.com/zdhxiong/mdui) Web Components 组件库——离线打包进 APK，不走 CDN。
- **锁屏感知的通知**：通知栏只显示 URL，绝不显示 PIN——锁屏是物理世界的攻击面。

## 已知限制（设计内取舍，非缺陷）

- HTTP 明文传输（HTTPS 自签证书在 v2 里加）。
- 手机 → 浏览器上传的瞬时速率只在传输结束时记录一次（Ktor 3 multipart 当前 API 未暴露流式 `tee`）；浏览器 → 手机方向 v1.2 起已有实时进度气泡。
- WiFi 切换（IP 变了）会断开在飞的 WS，浏览器需打开 banner 提示的新 URL；同 IP 恢复几秒内自动重连。 *(v1.2)*
- 非置顶会话保留上限硬编码 20 条，暂不支持用户配置（设置页推迟到后续版本；要改数量需改常量并重新打包）。

## 技术栈

| 层        | 选型                                                          |
| --------- | ------------------------------------------------------------- |
| 语言      | Kotlin 2.2                                                    |
| 构建      | AGP 9 + KSP2（`2.2.10-2.0.2`）                                |
| 手机 UI   | Jetpack Compose + Material 3                                  |
| HTTP 服务 | Ktor 3（CIO engine），内嵌于前台服务                          |
| WebSocket | Ktor WebSockets（`pingPeriodMillis = 15_000`）                |
| 持久化    | Room 2.7（+ KSP2 代码生成）                                   |
| 浏览器 UI | 原生 HTML/CSS/JS + mdui Web Components                        |
| 测试      | JUnit 4 + MockK + Turbine + ktor-server-test-host + Robolectric 4.14（`@Config(sdk = [33])`） |
| 最低/目标 | SDK 33 / 36                                                   |
| 模块结构  | 单 `:app` 模块，不过度拆分                                    |

每一项选择的「为什么」见 `docs/others/notes/decisions.md`。

## 构建

```bash
./gradlew assembleDebug          # Debug APK
./gradlew testDebugUnitTest      # JVM 单元测试
./gradlew connectedAndroidTest   # 仪器测试（需连接设备）
./gradlew installDebug           # 安装到已连接设备
```

## 目录结构

```
app/src/main/java/com/example/flikky/
├── ui/          Compose Screen 与 ViewModel（home、serving、history、exporting、components）
├── service/     前台服务、controller、通知、export notification text
├── server/      Ktor server、routes（含 ExportRoutes）、DTO、PIN 认证、ServiceMode
├── session/     内存状态、Message 模型、NetworkStatus
├── data/        Room DB、Entity、DAO、SessionRepository、SessionFileStore
├── export/      ExportSession / ExportMode / ExportSnapshot / ZipExporter / formatters
├── network/     WiFi IPv4 获取、NetworkRebinder（rebind intent 状态机）
├── util/        纯 Kotlin 工具（不依赖 Android 框架）
└── di/          ServiceLocator
app/src/main/assets/web/   浏览器前端（含离线打包的 mdui、export.html、snackbar.js）
docs/others/               本地设计/复盘/验收清单目录（gitignored）
```

## 致谢

- [Ktor](https://ktor.io/) —— 内嵌的 HTTP/WebSocket 引擎
- [mdui](https://github.com/zdhxiong/mdui) —— Material Design 3 Web Components，以 MIT 许可离线打包

## 许可证

MIT —— 见 [LICENSE](./LICENSE)。
