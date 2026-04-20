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

---

## T8. AGP 9 + KSP2 踩坑组合 ⭐️

**现象一：** `ksp = "2.2.10-1.0.30"` 在 Maven Central 找不到；build 报 "Plugin with id 'com.google.devtools.ksp' not found"。

**原因：** KSP 的版本号命名是 `<kotlin-version>-<ksp-series-version>`。`x.y.z-1.0.w` 是 KSP1 系列；KSP1 从 Kotlin 2.3+/AGP 9+ 不再发布。对 Kotlin 2.2.10 + AGP 9.1.1 正确的是 **KSP2** 系列 `2.2.10-2.0.2`（2025-08）。

**修复：** `libs.versions.toml` 里 `ksp = "2.2.10-2.0.2"`。

**现象二：** 换对版本后 assembleDebug 又报 "Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin"。

**原因：** AGP 9 把 Kotlin 编译整合进 Android plugin（"built-in Kotlin"），默认禁止外部修改 `kotlin.sourceSets`。但 KSP2 需要这个 DSL 把生成目录挂到编译里。

**修复：** `gradle.properties` 里加：
```properties
android.disallowKotlinSourceSets=false
```

**教训：** 工具链新主版本（Kotlin 2.2 + AGP 9 + KSP2）组合落地时，**先跑一次 minimal smoke case**（Room 的 `@Entity` + `@Database` 能 KSP 生成）再往上堆业务。我的 v1.1 plan 里 Task 2 只要求"`assembleDebug` 通过"而没要求"KSP 生成一个 @Database 通过"，所以第一次 wire 时没早暴露这两个坑——下次写 plan 里这类任务要把 smoke case 放在一起。

---

## T9. Robolectric 4.14 不支持 targetSdk 36

**现象：** Robolectric 测试跑起来立刻 `IllegalArgumentException: SDK 36 not supported`。

**原因：** Robolectric 4.14（2025-01）只打包到 SDK 35 的 Android 资源。targetSdk = 36 默认让 Robolectric 尝试用 SDK 36 的壳子，没有就炸。

**修复：** 所有 Robolectric 测试类加类级注解：
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class YourTest { ... }
```
`33` 是项目 minSdk，也是 Robolectric 4.14 稳定支持的版本。DAO / Repository 测试不依赖 SDK 语义，这个 pin 无副作用；要测 SDK-specific behavior 的用 androidTest 跑真机即可。

**未来：** 升到 Robolectric 4.15+ 后 SDK 36 支持会进来；那时可以去掉 `@Config`。

---

## T10. Robolectric 还要 `androidx.test:core` 作为独立 testImpl

**现象：** `ApplicationProvider.getApplicationContext<Context>()` 在测试里 `ClassNotFoundException`。

**原因：** Robolectric 4.14 **不再**传递 `androidx.test:core`（历史上是会传）。`ApplicationProvider` 在 `androidx.test:core` 里，需要显式声明。

**修复：** `libs.versions.toml` + `app/build.gradle.kts` 加：
```toml
androidxTest = "1.5.0"
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTest" }
```
```kotlin
testImplementation(libs.androidx.test.core)
```

**教训：** JVM 单测用 Robolectric 时要把 `androidx.test:core` 当一等依赖，不要假设传递进来。

---

## T11. AndroidViewModel 默认工厂反射，Kotlin 默认参数的构造函数炸

**现象：** APP 启动进入首页立即崩溃。
```
Caused by: java.lang.NoSuchMethodException:
com.example.flikky.ui.home.HomeViewModel.<init> [class android.app.Application]
```

**原因：** `AndroidViewModelFactory` 通过 `getConstructor(Application::class.java)` 反射查找匹配的构造函数。
`HomeViewModel(app, repository = ServiceLocator.repository)` 看似"只要 app 也能用"，但 Kotlin 默认参数**不会**在字节码里生成 `(Application)` 单参版本——它生成的是 `(Application, SessionRepository)` 加一个带 synthetic marker int 的 `$default` 合成构造函数，两者都不匹配 `(Application)` 签名。JVM 单测直接 `HomeViewModel(app, repo)` 不走反射，所以测不出来——这是**集成死角**。

**修复：** 加 `@JvmOverloads`：
```kotlin
class HomeViewModel @JvmOverloads constructor(
    app: Application,
    private val repository: SessionRepository = ServiceLocator.repository,
) : AndroidViewModel(app)
```
Kotlin 会同时生成 `(Application)` 和 `(Application, SessionRepository)` 两个真实 JVM 构造函数。前者给默认工厂反射匹配，后者给测试注入。

**教训：** 任何依靠默认 `ViewModelProvider` 实例化的 `AndroidViewModel` 子类，构造函数若带默认参数的额外依赖，必须加 `@JvmOverloads`——否则反射只认最严格签名。或者把依赖改成属性初始化、不走构造函数。单测覆盖不到这条路径，一定要有装机冒烟或 Compose UI 测试。
