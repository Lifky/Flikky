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
