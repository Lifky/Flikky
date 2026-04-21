# 复盘

## v1.0-rc1 — 2026-04-17

### 做了什么
- 从零起步到代码完整的"最小连通闭环"：手机 Android + Compose + MD3 作服务器，电脑浏览器零安装接入
- Ktor 3（CIO）+ WebSocket 做 HTTP/WS 通道；URL+PIN 认证；单次 PIN 消费；CSP + 多项 hardening headers
- 双向传输：文本即时推送；文件双向（浏览器上传手机、手机推送浏览器下载）
- 前台服务 + 通知栏；底部状态栏（运行时长 / 文件数 / 速率）
- 浏览器端纯原生 HTML/CSS/JS（不引框架），手写 MD3 风格
- 7 个里程碑、33 个计划任务、25+ 个 commit；21 个单测通过

### 工作流节奏
1. **brainstorming**：明确 v1 最小边界（URL+PIN，HTTP，MD3 同源）
2. **writing-plans**：33 个任务的详细实施计划，含完整代码与命令
3. **subagent-driven-development**：最初几个里程碑派 subagent 实施；中途触发 API 限流后，改由主代理直接写后半程。整体体感是"前期 subagent 值得，后期简单任务自己写更快"

### 实际耗时与摩擦点
- M1 Scaffolding：一把过，无阻塞
- M2 Core Logic（TDD）：21 个单测顺利；subagent 在 PinAuth 发现了 spec 里的一个语义漏洞并修了
- M3 HTTP Server：3 个子批；Ktor 3.x 的几处 API 名字与计划里写的不一致（`pingPeriodMillis` 不是 `Duration`、`copyAndClose` 不是 `toInputStream`），subagent 自行适配
- M4/M5/M6/M7：无 subagent，直接写，节奏最快
- **验收轮 1：3 处缺陷** —— 用户运行后发现：
  - 浏览器页面无样式（`/static/*` 404）
  - 通知栏不出现（POST_NOTIFICATIONS 权限问题）
  - 输 PIN 后刷新回登录页（因 JS 没加载，form 默认 submit）
  - 三个症状同源：我用了 Ktor 的 `staticResources("/static", "web")`，它从 JVM classpath 找资源——但 Android asset 不在 classpath

### 做对的事
- **分版本边界**：v1 只做最小闭环，避免一次性把需求全吞下。即便第一轮验收有缺陷，改动面也极小
- **spec + plan 双文档**：仔细写一次省很多事；subagent 不用再猜意图
- **TDD 给纯逻辑打底**：PinAuth / TransferStats / SessionState 的测试在后续集成时发挥了"压舱石"作用
- **诚实披露安全限制**：在设计文档里明说"HTTP 明文"和"浏览器插件防不住"，不糊弄甲方

### 可以改进的事
- **平台细节漏查**：写 plan 时假设 `staticResources` 能从 assets 工作——这是想当然。以后写涉及 Android/JVM 边界的代码，plan 阶段就应该验证"这个 API 真的能读到这类资源吗"
- **早期验证环节缺失**：代码跑通 `./gradlew build` 不等于真的能在设备上工作。v1 没有端到端"装机跑一遍"的迭代点，导致问题堆到 rc1 才暴露
- **subagent 任务粒度**：M3 拆成 3 个子批还是偏大；第二批触发了限流。下次考虑每批 ≤2 个任务
- **commit 纪律**：一次误用 `git add -A` 把 `.idea/` 和 26MB APK 拉进去，靠 `git reset --soft` 回退。以后只用精确路径 add

### 下一步
- 等用户基于修复的 APK 重新验收；通过则打 `v1.0` 正式 tag
- v1.1 规划：会话结束归档到本地 + 主页会话列表（只展示）

---

## v1.1 — 2026-04-21

### 做了什么
- 本地会话归档：Room 2.7 + KSP2（Kotlin 2.2.10 / AGP 9 组合）；`SessionEntity` + `MessageEntity`（单表 + kind 判别，CASCADE）
- `SessionRepository` 统一业务入口：`beginSession` / `appendMessage` / `endSession`（空会话自动回滚）/ `fifoSweep`（硬编码 20，置顶不占配额）/ `finalizeOrphans`（崩溃恢复）/ rename/pin/delete
- `SessionFileStore`：文件落到 `filesDir/sessions/{id}/files/{fileId}`，与 DB sessionId 天然对位，FIFO 即 `rm -rf`
- `TransferService` 起停时包 `begin/endSession` + FIFO 清扫；`TransferController` 给手机推送文件做 tee（内存→磁盘）+ 每条消息双写（内存 `session.addMessage` + DB `appendMessage`）
- Home 列表 + FAB + 长按菜单（置顶/重命名/删除）；History 只读查看；MainActivity 路由 `home → serving → history/{id}`
- 测试：DAO×2 + FileStore + Repository（12 例）+ Home/HistoryVM；androidTest `TransferServiceSessionArchiveTest` 端到端验归档
- 规模：7 个里程碑、31 计划任务、约 40 个 commit；纯 JVM 单测全绿

### 工作流节奏
1. **brainstorming**：确定 FIFO=20、Room（不是 JSON）、空会话回滚、sessionId 分目录四个关键点，拍板即冻结
2. **writing-plans**：31 个任务，每个任务带规格 + 实现 + 验证命令
3. **subagent-driven-development**：全程派 subagent（haiku 实现 + spec-reviewer + code-quality-reviewer 双审），主代理调度 + 决策 + 回滚

### 实际耗时与摩擦点
- **KSP 版本错**：plan 里写了不存在的 `2.2.10-1.0.30`（KSP1 编号习惯）；AGP 9 要 KSP2 的 `2.2.10-2.0.2`。触发后及时 `git reset --hard` 回退、改 plan 再重跑，没让后续 subagent 在错误基础上继续堆砌（T8）
- **AGP 9 + 内置 Kotlin 封禁 `kotlin.sourceSets`**：加 `android.disallowKotlinSourceSets=false` 绕过（T8）
- **Robolectric 4.14 不吃 SDK 36**：所有 Robolectric 测试必须 `@Config(sdk = [33])`（T9）
- **Robolectric 4.14 不再传递 `androidx.test:core`**：`ApplicationProvider` ClassNotFound，手动加 1.5.0（T10）
- **紧耦合 M5 任务故意破坏中间态编译**：TransferService → KtorServer → TransferController → MessageRoutes 四处签名联动改，过程中 compile 红，在 Task 21 重新绿回
- **验收轮 1：4 处缺陷 + 1 个 UX 小问题 + 1 个 backlog 项**
  - APP 启动即崩：`HomeViewModel(app, repo = ...)` 带默认参数的构造函数，Kotlin 不生成 `(Application)` 单参版，`AndroidViewModelFactory` 反射找不到 → `@JvmOverloads`（T11）
  - 点击文件气泡闪退：v1.1 文件路径改了，`file_paths.xml` 没跟着改，FileProvider prefix 匹配失败（T12）
  - 浏览器只发文件的会话停服后消失：`FileRoutes` 漏调 `onPersist`，`endSession` 判空回滚连文件一起删（T13）
  - 浏览器上传 >50 MiB 静默失败：Ktor 3.0 默认 `formFieldLimit = 50 MiB`，超限服务端抛错→500→前端 fetch 不检查 res.ok→UI 没反应（T14）
  - 文字消息不能复制：`SelectionContainer` 包一下即可
  - 大文件上传期间无进度（v1.2 候选，记 B1）

### 做对的事
- **决策与踩坑落盘**：D10-D17 + T8-T14 全部记到 decisions.md / traps-and-fixes.md，每条带"现象 + 原因 + 修复 + 教训"，将来主版本升 Ktor/Room/AGP 对照翻一遍就知道哪些地方要验证
- **subagent 调度节奏**：每个 subagent 做完就人工复核 + 立即提交；纠错窗口小，避免错误在 subagent 间扩散（KSP 版本事件就是这样止损的）
- **verification 不用测试代替装机**：T11（反射路径）、T12（FileProvider 路径）都是"单测全绿 + 装机炸"的类型。现在这两类都写在 traps 里，下次 plan 会主动加 smoke 测试步
- **backlog.md 新机制**：把用户反馈里"以后做"的想法收拢一个地方，不混进当版 spec，也不会忘

### 可以改进的事
- **主版本库升级没读 breaking changes**：Ktor 2→3 的 50 MiB 默认上限是 release note 里明写的，我没读就写代码，压力场景才触发。以后升主版本先扫一遍 migration guide 的 "Default behavior changed" 条
- **双写路径设计缺陷**：`session.addMessage` 和 `repository.appendMessage` 要成对，但没封装，全靠调用方自觉——`FileRoutes` 漏掉一处就引发 Bug B。v1.2 应该抽 `addAndPersist(msg)` 封装（T13 里记了）
- **前端 `await fetch` 全部没 check res.ok**：这是大文件上传看起来像"什么都没发生"的根源。登录、发消息、上传都有同样隐患。应该在 `app.js` 抽一个 fetch 包装统一处理
- **反射路径的测试盲区**：`AndroidViewModelFactory` 反射构造只在装机才会触发——JVM 单测覆盖不到。以后涉及 Android 框架反射的类要有 Compose UI test 或 Robolectric @Config 下的构造测试

### 下一步
- 打 `v1.1` tag（本地验收已过，代码不再变）
- v1.2 议题排期：backlog B1（大文件上传进度）、B2（mdui snackbar 替换 alert）、spec 原定的"多会话批量导出到电脑"
