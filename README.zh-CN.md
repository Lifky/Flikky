# Flikky

[English](./README.md) | **简体中文**

安卓手机与浏览器之间的局域网文件与消息互传。接收端零安装、零联网，专为临时即用的共享场景设计。

手机启动内嵌 HTTP 服务器，同 WiFi 下任意浏览器打开手机上展示的 URL，输入一次性 6 位 PIN 后即建立会话，文本与文件可双向实时传输。

## 当前状态

- **v1.0** — 最小连通闭环：启动服务、URL + PIN 配对、双向文本与文件、MD3 聊天气泡、前台服务常驻通知、完整安全基线。
- **v1.1** — *开发中* — 会话归档（Room）+ 主页会话列表，支持重命名 / 置顶 / 删除。

设计文档见 `docs/superpowers/specs/`，复盘与决策记录见 `docs/notes/`。

## 亮点

- **电脑端零安装**：一个浏览器就够。不用装应用、不用装插件、不用注册账号。
- **只走局域网**：服务器仅绑定当前 WiFi IPv4 接口，绝不绑 `0.0.0.0`，不发任何外网请求。
- **PIN 单次使用**：认证成功即作废，后续必须换新 PIN。错 3 次锁该 IP 30 秒，错 5 次终止服务。
- **浏览器端加固**：严格 CSP、`X-Frame-Options: DENY`、`nosniff`、`Referrer-Policy: no-referrer`、`HttpOnly` + `SameSite=Strict` Cookie、只用 `textContent`（禁 `innerHTML`）、文件经 Blob URL 下载。
- **原生 MD3 视觉**：手机端 Jetpack Compose，浏览器端 [mdui](https://github.com/zdhxiong/mdui) Web Components 组件库——离线打包进 APK，不走 CDN。
- **锁屏感知的通知**：通知栏只显示 URL，绝不显示 PIN——锁屏是物理世界的攻击面。

## v1 已知限制（设计内取舍，非缺陷）

- HTTP 明文传输（HTTPS 自签证书在 v2 里加）。
- 消息只存内存，服务停止即丢。v1.1 加持久化。
- 浏览器 → 手机上传的瞬时速率只在传输结束时记录一次（Ktor 3 multipart 当前 API 未暴露流式 `tee`），传输过程中速率栏会显示 0。
- WiFi 切换后服务器不会自动重绑，需手动重启服务。

## 技术栈

| 层        | 选型                                                          |
| --------- | ------------------------------------------------------------- |
| 语言      | Kotlin 2.2                                                    |
| 手机 UI   | Jetpack Compose + Material 3                                  |
| HTTP 服务 | Ktor 3（CIO 引擎），内嵌于前台服务                            |
| WebSocket | Ktor WebSockets（`pingPeriodMillis = 15_000`）                |
| 浏览器 UI | 原生 HTML/CSS/JS + mdui Web Components                        |
| 测试      | JUnit 4 + MockK + Turbine + ktor-server-test-host             |
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
├── ui/          Compose 屏幕与 ViewModel（home、serving、components）
├── service/     前台服务、控制器、通知
├── server/      Ktor 服务器、路由、DTO、PIN 认证
├── session/     内存状态与消息模型
├── network/     WiFi IPv4 获取
├── util/        纯 Kotlin 工具（不依赖 Android）
└── di/          ServiceLocator
app/src/main/assets/web/   浏览器前端（含离线打包的 mdui）
docs/superpowers/          设计文档、实施计划、验收清单
docs/notes/                复盘、踩坑记录、决策记录
```

## 致谢

- [Ktor](https://ktor.io/) —— 内嵌的 HTTP/WebSocket 引擎
- [mdui](https://github.com/zdhxiong/mdui) —— Material Design 3 Web Components，以 MIT 许可离线打包

## 许可证

MIT —— 见 [LICENSE](./LICENSE)。
