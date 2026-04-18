# Flikky v1 人工验收清单

> 运行前：确保手机与电脑处于同一 WiFi；手机已赋予通知权限。

## 基本功能

- [正确] 点"启动服务"后，ServingScreen 显示 URL 和 6 位 PIN
- [正确，PIN可以显示让用户去APP里看，通知栏不显式] 通知栏出现常驻通知，显示 URL 和 PIN
- [正确] 电脑 Chrome 访问 URL，看到 MD3 风格登录页（紫色主题、分格 PIN 输入）
- [正确] 输错 PIN 3 次：页面提示"尝试过多，30 秒后再试"
- [正确] 输对 PIN：跳转到聊天页；手机端"等待连接…"变"已连接"
- [正确] 电脑发文本"hello"：手机消息列表出现；浏览器气泡为右侧紫色
- [浏览器有气泡显示且正确。手机端发送的或收到的信息无气泡，但能收到信息且有消息开头区分（如“BROWSER：”、“PHONE：”）] 手机端输入消息并点发送：浏览器收到；手机气泡视觉上区分（origin=PHONE）
- [正确，电脑上传文件成功，手机收到消息但手机无法下载，计数正确] 电脑上传 1 MB 文件：上传成功，手机消息列表多出一条文件消息，APP 底部文件计数 +1
- [正确，手机发送文件到电脑，电脑端收到文件且可以下载] 手机点"附件"选文件：浏览器收到文件提示气泡，点击可下载
- [正确，uptime 每秒 +1，fileCount 正确，rate在传输文件时非零] 浏览器顶部状态：uptime 每秒 +1、fileCount 正确、rate 非零
- [正确] 点"停止服务"：通知栏消失；浏览器 WebSocket `已断开`、重试失败
- [正确] 重启服务后 PIN 变新值；旧浏览器 Cookie 失效，重新走登录

## 安全

- [正确] 同网段但 IP 不同的另一台设备能访问（可接入）
- [正确] 不同 WiFi 的设备无法 ping 通服务 IP:Port
- [正确] 输错 PIN 5 次（包含锁定期间的尝试）：服务终止，再输正确 PIN 也被拒
- [正确] 隐私/无痕窗口访问功能一切正常（登录 + 聊天 + 上传）
- [正确] 响应头包含 `Content-Security-Policy`、`X-Frame-Options: DENY`（DevTools Network 面板验证）
- [正确] `Set-Cookie: flikky_token` 含 `HttpOnly`、`SameSite=Strict`

## 稳定性

- [正确] APP 切后台 10 分钟：服务仍运行（前台服务保活）
- [正确] WiFi 中途切换：浏览器 `已断开`（v1 不自动重绑，v1.1 迭代）
- [正确] 大文件 100 MB 上传：完成、APP 不 OOM

记录验证日期与设备：4-18-test2-Android15

---

## 2026-04-18 基于 test2 反馈的修复（待 test3 复验）

- **F1 通知栏未展示 URL/PIN**：`NotificationHelper` 加 `BigTextStyle`，`TransferService` 文案换行成 "URL ... / PIN ..."。折叠时仍是一行，下拉展开完整可见。
- **F2 手机端消息无气泡**：重写 `ServingScreen` 消息项为 MD3 气泡（PHONE 右对齐主色，BROWSER 左对齐 surfaceContainerHigh），文件气泡带 📄 图标、文件名、大小和提示行。
- **F3 手机端无法下载浏览器上传的文件**：配置 FileProvider（`res/xml/file_paths.xml` 暴露 `filesDir/transfer`）；`ServingViewModel.openFile()` 对 `origin=BROWSER & status=COMPLETED` 的文件发 `ACTION_VIEW`，气泡点击即可调用系统选择器打开。手机自己发出的文件气泡保持不可点击（用户已有源文件）。

提交：`ed79a0b` `935c4b2` `b40153c`。
