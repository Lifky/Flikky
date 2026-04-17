# Flikky v1 设计文档

- 日期：2026-04-17
- 范围：v1「最小连通闭环」
- 状态：待评审

## 1. 产品定位

Flikky 是一个**局域网内、手机 ↔ 电脑浏览器**的文件/文本传输工具。手机端为原生 Android 应用（承担服务端角色），电脑端为普通浏览器（零安装），通过同一 WiFi 直连，不经过互联网。

**与现有类竞品的差异：**
- 电脑端零安装（对比 LocalSend、Snapdrop 等也是零安装，但 Flikky 会加「会话归档」）
- 会话可归档、可回看、可按会话导出（后续版本）
- 甲方强调：**稳定性**与**安全性**

## 2. v1 交付范围

### 2.1 In Scope
1. 启动/停止服务（前台服务 + 通知栏）
2. 手机端展示接入信息：`http://<WiFi IP>:<port>` + 6位 PIN
3. 电脑浏览器输 URL → 输 PIN → 进入聊天页
4. 文本消息收发（双向，实时）
5. 文件传输（双向：电脑上传到手机 / 手机推送到电脑下载）
6. APP 底部状态栏：服务时长、已传文件数、当前速率（单位：KB/s 或 MB/s）
7. 单次会话结束即销毁：停服后内存数据不保留

### 2.2 Out of Scope（后续版本）
- 会话列表与历史回看（v1.1）
- 多会话批量导出（v1.2）
- 消息撤回（v1.3）
- HTTPS 自签证书（v2）
- 加密本地存储（v2）
- 多浏览器同时接入（v2+）
- 文本字符数统计、一键复制按钮（可放入 v1.1 随「消息气泡组件」一起完善）

### 2.3 非目标（明确不做）
- 跨网段、跨 WiFi、互联网中继
- P2P NAT 穿透
- 移动端互传（Android ↔ Android）

## 3. 用户流程

### 3.1 首次使用
1. 用户打开 APP，看到 HomeScreen 只有一个「启动服务」按钮
2. 点击后权限确认（通知权限、前台服务权限）
3. 进入 ServingScreen，大字展示 URL + PIN
4. 用户在同 WiFi 电脑打开浏览器，输入 URL
5. 浏览器出现登录页，要求输 PIN
6. PIN 正确：进入聊天页，手机 APP 进入「已连接」态
7. 双方可互发文本、传文件
8. 用户在 APP 点「停止服务」或关闭 APP：浏览器页面提示会话已结束

### 3.2 异常路径
- PIN 错 3 次 → 服务端锁定该浏览器 IP 30 秒；5 次 → 自动停服
- 电脑与手机 WiFi 断开 → 手机端保留服务，通知栏提示「客户端离线」，重连后继续
- 手机端进程被系统杀死 → 前台服务理论上被 Android 保活；一旦被杀，会话直接终止（v1 不恢复）

## 4. 技术架构

### 4.1 整体分层

```
UI 层         : Jetpack Compose + Material 3
状态层        : ViewModel + StateFlow
服务层        : TransferService (ForegroundService)
网络/业务层   : KtorServer (CIO) + SessionState + PinAuth + NetworkInfo
资源层        : assets/web/ (电脑端静态前端)
```

### 4.2 模块划分

v1 保持**单 Gradle 模块**（`:app`），按包组织：

```
com.example.flikky
├── MainActivity.kt
├── ui/
│   ├── home/          HomeScreen + HomeViewModel
│   ├── serving/       ServingScreen + ServingViewModel
│   ├── components/    共享组件（StatusBar、MessageBubble 等）
│   └── theme/         Color / Type / Theme
├── service/
│   ├── TransferService.kt          前台服务
│   └── NotificationHelper.kt       通知栏
├── server/
│   ├── KtorServer.kt               服务器生命周期
│   ├── routes/                     路由：Auth / Message / Upload / WS
│   ├── PinAuth.kt                  PIN 生成与 token 签发
│   └── dto/                        序列化数据类
├── session/
│   ├── SessionState.kt             内存会话状态（StateFlow）
│   └── TransferStats.kt            传输统计（速率计算）
├── network/
│   └── NetworkInfo.kt              WiFi IP 获取
└── util/
    └── IdGen.kt                    PIN / token / 消息 ID 生成
```

### 4.3 关键组件

#### TransferService（前台服务）
- 继承 `Service`，`startForeground` 启动通知
- 协程作用域：`CoroutineScope(SupervisorJob() + Dispatchers.IO)`
- 生命周期：`onCreate` 启动 Ktor；`onDestroy` 关闭 Ktor、取消协程、清理 SessionState
- 监听 SessionState 状态变化 → 更新通知栏文案

#### KtorServer（CIO 引擎）
- 绑定地址：**仅当前 WiFi 接口的 IPv4 地址**（不绑 0.0.0.0，避免借蜂窝或其他接口暴露）
- 端口：8080 起，占用则自动 +1，上限 8099
- 路由：
  - `GET /` → 登录页 HTML（含 PIN 输入表单）
  - `POST /api/auth` → 验 PIN，成功返 token（HTTP-Only Cookie + JSON 返回）
  - `GET /app` → 聊天页 HTML（需 Cookie）
  - `GET /static/*` → CSS/JS 静态资源
  - `POST /api/messages` → 发文本消息
  - `GET /api/messages?since=<id>` → 拉历史（用于初次进入补齐）
  - `POST /api/files` (multipart) → 电脑端上传文件到手机
  - `GET /api/files/{id}` → 下载手机端推送的文件
  - `WS /ws` → 实时双向通道（新消息/状态/进度推送）

#### PinAuth
- 每次启动服务生成：
  - 6 位数字 PIN（`SecureRandom.nextInt(1_000_000)`，不足 6 位前补 0）
  - 服务唯一 token（128 位随机熵，base64url 编码约 22 字符）
- 输入 PIN 正确 → 签发 session token → **PIN 立即作废**（单次使用）
- v1 规则：PIN 单次使用 + 单 token 活跃。一旦客户端跑路且 Cookie 丢失，只能重启服务；v2 再考虑「设备替换」流程
- 错误计数：3 次错误锁 30 秒，5 次错误直接停服并要求重启

#### SessionState
- 数据类：`serviceStartedAt`、`fileCount`、`bytesTransferred`、`messages: List<Message>`、`clientConnected: Boolean`
- 暴露为 `StateFlow<SessionState>`，UI 和通知栏同步消费
- **v1 不持久化**：服务停即丢

#### TransferStats（速率计算）
- 滑动窗口：最近 1 秒的字节数作为瞬时速率
- 暴露 `Flow<Long>` 供 UI / WebSocket 推送

### 4.4 数据流

**文本消息示例（电脑 → 手机）：**
```
Browser input
  ↓ fetch POST /api/messages {text}
KtorServer route
  ↓ SessionState.addMessage(Message(id, from=BROWSER, ...))
StateFlow emits
  ├→ Android UI (ServingScreen) recomposes
  └→ WebSocket broadcast to browser → 浏览器对端气泡刷新
```

**文件传输示例（手机 → 电脑）：**
```
User picks file in Android
  ↓ ContentResolver.openInputStream(uri)
SessionState.addPendingPush(fileId, name, size, uri)
  ↓ WebSocket notify browser: {type: "file_offered", id, name, size}
Browser auto-fetch GET /api/files/{id}
  ↓ KtorServer streams InputStream → response body
  ↓ 浏览器端以 Blob 接收 → 触发下载（a.download=）
完成后 SessionState.markSent(fileId)
  ↓ UI + 状态栏更新
```

## 5. 电脑端前端

- **纯原生 HTML + CSS + JS**，不引入任何框架（体积小、依赖可控、审计容易）
- MD3 视觉通过手写 CSS 变量实现核心：
  - 颜色：primary / surface / surface-variant / on-* 系列
  - 圆角：16dp 卡片 / 8dp 按钮 / 24dp FAB
  - Elevation：4 级 box-shadow
  - Typography：系统字体栈 + MD3 type scale
- 页面：
  - `login.html`：PIN 输入（6 位分格输入框）
  - `app.html`：顶部状态栏 + 消息列表 + 底部输入区（文本 + 文件附件按钮 + 拖拽上传）
- 构建：**不需要构建步骤**，文件直接放 `app/src/main/assets/web/`
- 安全加固：
  - CSP header：`default-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'`
  - 文件下载用 Blob URL，不在 DOM 中长留
  - 消息渲染用 `textContent`，杜绝 HTML 注入
  - 不使用 `localStorage` 存敏感数据（token 放 HTTP-Only Cookie）

## 6. 安全模型

### 6.1 威胁与缓解

| 威胁 | 缓解措施 | 缓解强度 |
|---|---|---|
| 外网接入 | 仅绑定 WiFi 网卡 IPv4，不监听 0.0.0.0 | 强 |
| 同 WiFi 猜 PIN 暴力 | 6位PIN（百万分之一）+ PIN单次使用 + 3次锁30秒 + 5次停服 | 强 |
| 浏览器同源其他页面窃取 | 同源策略 + HTTP-Only Cookie + CSP | 强 |
| 中间人/抓包 | v1 **不处理**（HTTP 明文）；v2 升 HTTPS 自签 | 无（v1 已知限制） |
| **浏览器插件读 DOM** | CSP + Blob URL 下载 + textContent 渲染；**但插件 content-script 有权限读取任何页面数据，无法根本杜绝** | 弱（诚实披露） |
| 服务残留 | 前台服务 + 通知栏强提示；APP 被杀即服务终止 | 强 |
| 文件跨越 Android 权限边界 | 所有上传文件写入 app 内部目录 `context.filesDir`，不落外存 | 强 |

### 6.2 必须告知甲方的限制

**v1 不能防御浏览器插件窃取。** HTTPS 也挡不住插件——插件本身就有读取页面 DOM 的权限。我们只能用 CSP + Blob URL + textContent 等手段**降低插件拿到数据的便利性**。要彻底规避，只能要求用户：
- 在隐私/无痕窗口访问（插件默认不运行）
- 或在专用浏览器（例如一个没装任何插件的 Chrome profile）访问

这一点会在 APP 的接入页加一条小字提示，也会在交付文档中明确。

### 6.3 token 与 cookie 规则

- token：128 位随机 base64url
- Cookie：`HttpOnly; SameSite=Strict; Path=/`；不设 `Secure`（因为 v1 是 HTTP）
- 生命周期：与服务同生同死
- 轮换：无（v1 单会话）

## 7. 错误处理与稳定性

| 场景 | 处理 |
|---|---|
| 端口被占 | 自动 +1 重试，8080–8099 都占用则报错提示切换 WiFi 网段 |
| WiFi 中途切换 | 服务继续绑老 IP，通知栏提示「IP 已变化，请重新连接」，下版迭代自动重绑 |
| 文件上传中断 | v1 不支持断点续传；失败即整个文件丢弃，通知对端「传输失败」 |
| Ktor 抛异常 | Server 层 `StatusPages` 统一捕获 → 返回 JSON 错误体；Service 层日志记录 |
| 协程泄漏 | 所有协程挂在 TransferService 的 SupervisorJob 下，Service 销毁即全部取消 |
| 内存压力（大文件） | 全程流式：`InputStream` → OutputStream，不 buffer 全量进内存 |

## 8. 测试策略

v1 以**人工验收 + 单元测试 + 少量仪器测试**三层覆盖：

1. **单元测试**（`app/src/test/`，JUnit + MockK；具体 JUnit 版本在实施计划中定）：
   - `PinAuth`：PIN 生成唯一性、token 校验、错误计数、锁定恢复
   - `TransferStats`：滑动窗口速率计算
   - `SessionState`：消息增删、StateFlow 发射时机
2. **仪器测试**（`app/src/androidTest/`）：
   - `KtorServerTest`：启动 → `OkHttp` 客户端走完整鉴权 + 发消息 + 传小文件流程
   - 重点验证：绑定地址正确、PIN 错 3 次锁定、Cookie 下发
3. **人工验收清单**（交付前必跑）：
   - 手机 ↔ Windows Chrome
   - 手机 ↔ macOS Safari
   - 手机 ↔ Android 浏览器（作为对端）
   - 小文件（<1MB）、中文件（~100MB）、大文件（~1GB）
   - WiFi 中途切断 → 是否能看到正确错误
   - 浏览器隐私窗口访问 → 功能正常
   - APP 后台 10 分钟 → 服务是否存活

## 9. 版本路线（参考）

| 版本 | 内容 |
|---|---|
| **v1 本文档** | 最小连通闭环（本设计） |
| v1.1 | 会话结束归档到本地 + 主页会话列表（只展示） |
| v1.2 | 单/多会话历史导出到电脑 |
| v1.3 | 消息撤回 + 消息气泡字符统计 + 一键复制 |
| v2.0 | HTTPS 自签 + 可选本地加密存储 |
| v2.x | 多浏览器并发、大文件断点续传 |

## 10. 已知风险

1. **CIO 引擎在低端 Android 设备上的稳定性**：需在一台 Android 13 低端机上跑 30 分钟压力测试验证（作为 v1 收尾前必做项）
2. **Android 15+ 前台服务策略**：本项目以 `mediaProjection` 或 `dataSync` 类型声明前台服务，需研究 `FOREGROUND_SERVICE_TYPE_DATA_SYNC` 的限制
3. **WiFi 接口枚举**：部分厂商 ROM 对 `ConnectivityManager` / `WifiManager` 行为不一致，IP 获取可能需要多路 fallback

## 11. 决策记录

| 决策 | 选择 | 理由 |
|---|---|---|
| v1 范围 | 最小连通闭环 | 先验证通道/安全/交互骨架，降低交付风险 |
| 接入方式 | URL + 6位PIN | 简单、防 LAN 内窥探、无需扫码设备 |
| 传输层 | HTTP | v1 降开发成本；HTTPS 已规划到 v2 |
| 电脑端 UI | MD3 同源 | 品牌感统一 |
| 服务器库 | Ktor + CIO | 协程原生、WebSocket 一流、后续扩展省成本 |
| 前端框架 | 无框架纯原生 | 体积小、依赖可控、审计容易 |
