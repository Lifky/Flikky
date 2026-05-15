# Flikky

Android LAN 文件传输 APP。设计文档：`docs/others/superpowers/specs/2026-04-17-flikky-v1-design.md`

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

### 跨 Wi-Fi rebind 的引用规范

`TransferService` 监听 Wi-Fi 变化，IP 切换时会停旧 `KtorServer` + 起新 `KtorServer`。期间 `WsHub` / `routes` 整组被替换；只有 `TransferService` field（`ktor` / `controller` / `pinAuth` 等）跨实例存活。

任何在 `TransferService` 内构造、生命周期跨过 rebind 的对象，**禁止直接持有 `KtorServer` 内部成员的引用**。必须通过 `() -> T?` lambda 间接访问，每次调用现取。

✅ 正确：
```kotlin
controller = TransferController(
    wsHub = { ktor?.wsHub },   // lambda：调用时解析当前 field
    ...
)
scope.launch {
    while (isActive) {
        ktor?.wsHub?.broadcast("status", payload)   // field 直接访问
        delay(1000)
    }
}
```

❌ 错误：
```kotlin
val server = buildTransferKtor(...)   // startTransfer 局部变量
controller = TransferController(
    wsHub = server.wsHub,   // 闭包捕获死引用，rebind 后指向已废弃的 hub
    ...
)
scope.launch {
    while (isActive) {
        server.wsHub.broadcast(...)   // 同理
        delay(1000)
    }
}
```

v1.2 因这个 bug 累计踩坑三次（`statusBroadcastJob` / `TransferController.wsHub` / 边界回调）；现有保护单测见 `service/TransferControllerRebindReferenceTest.kt`，复盘见 `docs/others/notes/retrospective.md` v1.2 段。新增任何"跨 rebind 存活"的依赖必须遵守本规范并补单测。

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
