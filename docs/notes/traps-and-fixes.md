# 踩坑与修复

格式：每条一个坑 → 现象 → 原因 → 修复。越具体越有价值。

---

## T1. Ktor 3.x WebSockets 不用 `Duration`

**现象：** plan 里 `pingPeriod = Duration.ofSeconds(15)` 编译不通过。

**原因：** Ktor 3.x 的 `WebSocketOptions` 用裸 `Long`（单位毫秒），不再是 `java.time.Duration` / `kotlin.time.Duration`。

**修复：**
```kotlin
install(WebSockets) {
    pingPeriodMillis = 15_000L
    timeoutMillis = 30_000L
}
```

**教训：** Ktor 主版本升级（2.x → 3.x）对配置 API 做了减法，写 plan 里的示例代码要照着实际版本文档对一遍。

---

## T2. Ktor 3.x Multipart 没有 `toInputStream()`

**现象：** plan 里 `part.provider().toInputStream()` 报"unresolved"。

**原因：** Ktor 3.x 的 `PartData.FileItem.provider` 返回 `() -> ByteReadChannel`，没有 `toInputStream()` 扩展。

**修复：**
```kotlin
savedSize = part.provider().copyAndClose(target.writeChannel())
```
需要 `import io.ktor.util.cio.writeChannel` 和 `import io.ktor.utils.io.copyAndClose`。

**副作用：** `copyAndClose` 是一次性拷贝，无中间回调——大文件上传过程中 `stats.recordBytes` 只在结束时记一次，瞬时速率显示会为 0。v1 接受这个折中，v1.1 可写 `CountingByteWriteChannel` 包装。

---

## T3. Ktor `staticResources` 在 Android 上读不到 assets ⭐️

**现象：** `<link rel="stylesheet" href="/static/app.css">` 返回 404；浏览器页面完全无样式；JS 也不加载，于是 form submit 走默认行为刷新页面——用户看到"输 PIN 后回到登录页"的假象。

**原因：** `staticResources("/static", "web")` 从 **JVM classpath** 的 `web/` 包下找资源。Android APK 把资源放在 `context.assets`，完全不在 classpath。

**修复：** 用显式路由 + `context.assets.open()` 读文件：
```kotlin
get("/static/{path...}") {
    val parts = call.parameters.getAll("path").orEmpty()
    if (parts.isEmpty()) { call.respond(HttpStatusCode.NotFound); return@get }
    val rel = parts.joinToString("/")
    if (rel.contains("..")) { call.respond(HttpStatusCode.Forbidden); return@get }
    try {
        val bytes = readAsset("web/$rel")
        call.respondBytes(bytes, contentTypeFor(rel))
    } catch (_: FileNotFoundException) {
        call.respond(HttpStatusCode.NotFound)
    }
}
```

**教训：** 在 Android 上用任何"Java 服务器库"时，资源路径假设要特别小心。classpath ≠ assets ≠ filesDir。**下次 plan 里出现 `staticResources` 之类的 API，先在项目初期做一次 assembleDebug + 真机端到端测试**，而不是等把整条流水线都写完才暴露。

---

## T4. PinAuth 锁定期间的失败不计入 `wrongTotal` 会留后门

**现象：** spec 写"5 次错误 → 终止"，但 3 次错误触发锁定后，后续输错的尝试在我的实现里直接 `return Locked`，`wrongTotal` 不再增加。于是恶意者可以"错 3 次 → 等解锁 → 再错 3 次"无限循环，永远达不到 5。

**修复：** 锁定期间仍计数到 `wrongTotal`（但不计 `wrongInWindow`）；锁定期间的正确 PIN 也按 Wrong 处理。

**教训：** 写计数器类逻辑，把"所有会让攻击者得利的路径"画出来。单测用"3 次锁 + 2 次再错"这样的组合明确测掉。

---

## T5. POST_NOTIFICATIONS 权限时机

**现象：** Android 13+ 上通知栏不显示。

**原因：** 我把权限请求放在 `MainActivity.onCreate`，用户打开 APP 就弹对话框。在没上下文的情况下用户容易点拒绝，后续 `startForeground` 仍会运行服务，但通知被系统抑制。

**修复：** 把权限请求移到点"启动服务"按钮时；被拒时给 Toast 告知"服务仍运行、通知不显示"。

**教训：** 运行时权限要在"用户理解你为何需要它"的瞬间请求，而不是抢跑到 onCreate。

---

## T6. `JAVA_HOME` 被错粘两路径

**现象：** `./gradlew` 命令行报找不到 JDK。

**原因：** 系统环境变量 `JAVA_HOME = C:\Program Files\Java\jdk1.8.0_331\bin;C:\Program Files\Java\jdk-17`，两路径用分号粘一起。

**修复（临时）：** 每条 gradle 命令前加前缀：
```
JAVA_HOME="/c/Program Files/Java/jdk-17" ./gradlew ...
```

**修复（根治）：** 把系统变量改成 `C:\Program Files\Java\jdk-17`，删掉旧的 1.8 段。Android Studio 用自己 bundled JDK 不受影响。

---

## T7a. 引入 Web Components 库时的 CSP

**现象：** 用 mdui 后，很多组件的 layout 错乱。

**原因：** 我的 CSP 是 `style-src 'self'` 没有 `'unsafe-inline'`。mdui 的 JS 会给宿主元素设 inline style（比如 `<mdui-button>` 的内部 layout/theming），被 CSP 拦截。

**修复：** `style-src 'self' 'unsafe-inline'`。注意 Shadow DOM 里的样式不受页面 CSP 约束（所以 mdui 组件内部 CSS 本身没问题），但它会向宿主 DOM 注入 inline style。

**安全影响：** `script-src 'self'` 仍然严格，XSS 主防线没破。`'unsafe-inline'` 只放开了 style 注入，攻击者能做的最多是 UI 变形，不能执行 JS。

**教训：** 任何时候引入第三方 Web Components 库，第一件事就是把 CSP 从 `'self'` 降级到至少 `'self' 'unsafe-inline'` for style-src——别指望第三方组件库会按纯 CSP 友好的方式写。

---

## T7. 误用 `git add -A`

**现象：** 一次 `git add -A && git commit` 把 `.idea/`、`app/release/*.apk`（26MB 二进制）拉进仓库。

**修复：** `git reset --soft HEAD~1` 回退提交，加 `.idea/`/`/app/release/`/`*.apk`/`*.aab` 到 `.gitignore`，只精确 add 修改的源码文件。

**教训：** `-A` 方便但会吞掉所有未跟踪文件。写过 .gitignore 也抵不过这一枪。**只用精确路径 add，例外极少**。
