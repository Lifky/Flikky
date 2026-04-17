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

**选：** 所有消息只在内存 `SessionState` 里，服务停止即丢。
**拒：** v1 就做 Room/SQLite 落盘。

**理由：** 避免 v1 就引入数据库层（DB schema 设计、迁移策略、加密与否）。v1.1 专门做归档时再引入。
