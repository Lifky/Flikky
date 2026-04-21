# Flikky

[English](./README.md) | **简体中文**

安卓手机与浏览器之间的局域网文件与消息互传。接收端零安装、零联网，专为临时即用的共享场景设计。

手机启动内嵌 HTTP 服务器，同 WiFi 下任意浏览器打开手机上展示的 URL，输入一次性 6 位 PIN 后即建立会话，文本与文件可双向实时传输。

## 当前状态

- **v1.0** — 最小连通闭环：启动服务、URL + PIN 配对、双向文本与文件、MD3 聊天气泡、前台服务常驻通知、完整安全基线。
- **v1.1** — *已发布（2026-04-21）* — Room 会话归档、主页会话列表（置顶 / 重命名 / 删除）、历史详情只读查看、APP 启动时 crash-recovery、FIFO 保留最近 20 条非置顶会话（置顶不占配额）。

设计文档见 `docs/superpowers/specs/`，复盘与决策记录见 `docs/notes/`。

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
- [ ] 多会话批量导出到电脑 *(v1.2 规划)*
- [ ] 浏览器上传大文件的实时进度气泡 *(v1.2，backlog B1)*
- [ ] 消息搜索 *(v1.2 / v1.3)*
- [ ] 消息撤回 *(v1.3)*
- [ ] HTTPS 自签证书 *(v2)*
- [ ] 本地归档 at-rest encryption *(v2)*

### opt

- [x] 浏览器端从手写 CSS 迁到 mdui Web Components *(v1.0 后期)*
- [x] 通知栏不显示 PIN——锁屏是物理世界的攻击面 *(v1.0 后期)*
- [x] 文件按 `sessionId` 分目录（`filesDir/sessions/{id}/files/{fileId}`），FIFO 淘汰即 `rm -rf` *(v1.1)*
- [x] `SessionEntity` 冗余聚合字段（`messageCount` / `fileCount` / `totalBytes` / `previewText`），主页列表不必反向扫 messages 表 *(v1.1)*
- [x] Ktor multipart 解除默认 50 MiB 上限（LAN 单用户场景，磁盘空间才是真天花板） *(v1.1)*
- [ ] 手机推送文件的 tee 改异步（消除选中到可下载的拷贝延迟） *(v1.2，backlog)*
- [ ] 浏览器端 native `alert` 替换为 mdui snackbar *(v1.2，backlog B2)*
- [ ] WiFi 切换后服务器自动重绑 *(post-v1.1)*

### fix

- [x] v1.0-rc1：`staticResources` 从 JVM classpath 而不是 Android assets 读；缺 `POST_NOTIFICATIONS` runtime 请求；登录页 JS 没加载导致表单默认提交
- [x] v1.1 T8：AGP 9 + 内置 Kotlin 封禁 `kotlin.sourceSets` DSL → 加 `android.disallowKotlinSourceSets=false`
- [x] v1.1 T9 / T10：Robolectric 4.14 最高只支持 SDK 33 且不再传递 `androidx.test:core`
- [x] v1.1 T11：`HomeViewModel(app, repo = ServiceLocator.repository)` 让 `AndroidViewModelFactory` 反射构造失败 → 加 `@JvmOverloads`
- [x] v1.1 T12：v1.1 改了文件路径但 `file_paths.xml` 还是 v1.0 的 `transfer/` → `FileProvider.getUriForFile` 抛异常；同时给 `openFile` 加 try/catch 兜底
- [x] v1.1 T13：`FileRoutes` POST 只更新内存 session、漏调 DB 持久化 → `endSession` 把"浏览器只发文件"的会话当空会话回滚，文件一起删 → 给 `fileRoutes` 加 `onPersist`，从 `KtorServer` 串进来
- [x] v1.1 T14：Ktor 3.0 默认 `receiveMultipart()` 静默上限 50 MiB → 改 `formFieldLimit = Long.MAX_VALUE`；浏览器 `fetch` 失败时 alert 暴露非 2xx 响应

## 亮点

- **电脑端零安装**：一个浏览器就够。不用装应用、不用装插件、不用注册账号。
- **只走局域网**：服务器仅绑定当前 WiFi IPv4 接口，绝不绑 `0.0.0.0`，不发任何外网请求。
- **PIN 单次使用**：认证成功即作废，后续必须换新 PIN。错 3 次锁该 IP 30 秒，错 5 次终止服务。
- **浏览器端加固**：严格 CSP、`X-Frame-Options: DENY`、`nosniff`、`Referrer-Policy: no-referrer`、`HttpOnly` + `SameSite=Strict` Cookie、只用 `textContent`（禁 `innerHTML`）、文件经 Blob URL 下载。
- **原生 MD3 视觉**：手机端 Jetpack Compose，浏览器端 [mdui](https://github.com/zdhxiong/mdui) Web Components 组件库——离线打包进 APK，不走 CDN。
- **锁屏感知的通知**：通知栏只显示 URL，绝不显示 PIN——锁屏是物理世界的攻击面。

## 已知限制（设计内取舍，非缺陷）

- HTTP 明文传输（HTTPS 自签证书在 v2 里加）。
- 浏览器 → 手机上传的瞬时速率只在传输结束时记录一次（Ktor 3 multipart 当前 API 未暴露流式 `tee`），传输过程中速率栏会显示 0。
- WiFi 切换后服务器不会自动重绑，需手动重启服务。
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

每一项选择的「为什么」见 `docs/notes/decisions.md`。

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
├── ui/          Compose Screen 与 ViewModel（home、serving、history、components）
├── service/     前台服务、controller、通知
├── server/      Ktor server、routes、DTO、PIN 认证
├── session/     内存状态与 Message 模型
├── data/        Room DB、Entity、DAO、SessionRepository、SessionFileStore
├── network/     WiFi IPv4 获取
├── util/        纯 Kotlin 工具（不依赖 Android 框架）
└── di/          ServiceLocator
app/src/main/assets/web/   浏览器前端（含离线打包的 mdui）
docs/superpowers/          设计文档、实施计划、验收清单
docs/notes/                复盘、踩坑记录、决策记录、backlog
```

## 致谢

- [Ktor](https://ktor.io/) —— 内嵌的 HTTP/WebSocket 引擎
- [mdui](https://github.com/zdhxiong/mdui) —— Material Design 3 Web Components，以 MIT 许可离线打包

## 许可证

MIT —— 见 [LICENSE](./LICENSE)。
