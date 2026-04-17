# Flikky

Android LAN 文件传输 APP。设计文档：`docs/superpowers/specs/2026-04-17-flikky-v1-design.md`

## 技术栈
- Kotlin 2.2 + Jetpack Compose + Material 3
- Ktor 3（CIO）做内嵌 HTTP 服务器
- 前台服务 + WebSocket
- 电脑端：纯原生 HTML/CSS/JS，位于 `app/src/main/assets/web/`

## 包结构约定
- `ui/` — Compose Screen + ViewModel，按功能子模块（home/serving/components）
- `service/` — Android Service 与通知
- `server/` — Ktor 服务器、路由、DTO、鉴权
- `session/` — 会话内存状态与数据模型
- `network/` — 平台网络信息（WiFi IP 等）
- `util/` — 与 Android 无关的通用工具

新增代码必须归入上述包之一；找不到归属说明职责不清，先讨论再落文件。

## 开发约定
- TDD 优先：先写测试再写实现，单测用 JUnit4 + MockK
- 小步提交：一个任务一个 commit，消息用英文
- 纯逻辑类（`util`/`session`/`server/Pin*`/`server/dto`）必须不依赖 Android 框架，可在 `test/` 跑
- Android 平台类（`service/`/`network/`/`ui/`）用仪器测试 `androidTest/`
- 不提交 `.idea/`、`local.properties`、`/build/`
- 不把 Android Context 穿透到 `server/` 包——服务器代码应通过接口接收依赖

## 安全红线
- 服务器只绑 WiFi 网卡 IPv4，不监听 0.0.0.0
- PIN 单次使用，认证成功后立刻作废
- 浏览器端所有文本渲染用 `textContent`（禁止 `innerHTML`）
- 文件通过 Blob URL 下载，不长留 DOM
- CSP header：`default-src 'self'; object-src 'none'; base-uri 'none'`
- 敏感数据不入 `localStorage`；token 走 HTTP-Only Cookie
- v1 明确用 HTTP 明文，所有文档需如实披露此限制

## 构建与运行
- `./gradlew build` — 编译 + 单测
- `./gradlew connectedAndroidTest` — 仪器测试（需连接设备）
- `./gradlew installDebug` — 安装到已连接设备
