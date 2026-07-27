# 更新日志

[English](./CHANGELOG.md) · **简体中文**

本文件记录 Flikky 每个版本面向用户的变更，格式参照 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。版本号遵循 `x.y.z`：x 重大架构变更、y 功能新增、z Bug 修复。日期为 tag 创建日期。

## [v1.15.0](https://github.com/Lifky/Flikky/releases/tag/v1.15.0) · 2026-07-23

### 新增
- App 与浏览器端完整英文本地化；语言设置在手机与浏览器之间双端同步
- 本版起在 GitHub Releases 提供正式签名 APK 下载（`Flikky_{版本号}_release.apk`）

### 改进
- 默认主题改为淡曙红
- 空态（无会话/无收藏等）排版统一
- 语言切换过程更流畅（声明 `screenLayout` 配置变更，消除切换闪屏）
- PIN 复制体验优化；移除 PIN 登录页冗余隐私提示

### 修复
- 导出归档中的主题与头像默认值与 App 实际默认一致
- 设置导出不再包含 beta 阶段的内部名称

## [v1.14.0](https://github.com/Lifky/Flikky/tree/v1.14.0) · 2026-07-16

### 新增
- 完整备份 scope：会话、收藏、设置、全部数据四种范围均可导出并重新导入（ZIP schema v2）
- 导出目的地新增 Android 本机存储（此前仅支持浏览器下载）
- 页面上下文归档入口：在会话/收藏页面就地发起对应范围的导出

### 改进
- 「允许撤回」设置在手机与浏览器两端强制生效
- 标签、头像与 History 操作项对齐打磨

## [v1.13.0](https://github.com/Lifky/Flikky/tree/v1.13.0) · 2026-07-05

### 新增
- PIN 认证可在设置中关闭（默认仍开启；关闭后同一局域网内可直接访问，见 README 安全模型）
- 脱离会话本地添加文本/文件收藏

### 改进
- 设置行附件居中对齐；添加文本收藏 sheet 细节打磨

## [v1.12.0](https://github.com/Lifky/Flikky/tree/v1.12.0) · 2026-07-02

### 新增
- 双端共享 design token：`tokens.css` 由 App 端 Kotlin 常量生成，形状/间距/字体单一事实来源
- 头像系统重做：预设 icon 头像、填充 icon 头像、单字符头像；Material Symbols 可变字体离线打包
- 会话中快捷设置：气泡圆角与深色模式可在传输页直接调整
- 气泡圆角、头像分组等外观设置通过 peer-info 同步到浏览器端
- 浏览器端撤回菜单与头像选择器改用官方 mdui 组件

### 修复
- 登录页在认证前即应用手机主题
- 服务停止后浏览器水印状态正确更新
- 撤回后浏览器端分组头像正确重排；分组更新不再丢失手机头像
- 传输页消息自动滚动到底部

## [v1.11.0](https://github.com/Lifky/Flikky/tree/v1.11.0) · 2026-06-30

### 新增
- M3 Expressive Motion 全面接入：页面转场（fade-through / shared-axis）、预测性返回手势、列表增删/重排动画、导航栏与 FAB 显隐动画
- 全局动画速度设置：关 / 慢 / 标准 / 快
- 8 套自定义预设主题（替换原 4 套）+ 对比度级别
- 浏览器端主题与手机当前主题实时对齐

### 改进
- 主页/收藏/设置列表迁移到官方 M3 Expressive 分段列表组件，带选中弹簧动效
- 浮动工具栏迁移到官方 `HorizontalFloatingToolbar`
- 等待连接页三个操作按钮改为 filled-tonal 样式

### 修复
- 搜索栏边距弹簧过冲为负值导致的崩溃

## [v1.10.1](https://github.com/Lifky/Flikky/tree/v1.10.1) · 2026-06-27

### 新增
- 会话中快捷发送收藏：底部 sheet 含最近使用（5 项）、快捷搜索、切换合集

### 改进
- 全部图标 drawable 迁移到官方 Material Symbols path

### 修复
- 快捷发送与会话内发送使用同一发送通道，状态一致
- 内层 Scaffold 重复消费底部 inset 导致的布局问题
- 多选工具栏改为内容层悬浮，胶囊样式减淡

## [v1.10.0](https://github.com/Lifky/Flikky/tree/v1.10.0) · 2026-06-26

### 新增
- 收藏功能（代号：弹药箱）：收藏消息/文件为独立快照、收藏合集（分组）、独立收藏 tab、消息长按收藏入口

### 修复
- 消息 id 计数器启动时从持久化最大值恢复，避免导入后 id 冲突

## [v1.9.1](https://github.com/Lifky/Flikky/tree/v1.9.1) · 2026-06-24

### 改进
- 设置页对话框与列表交互打磨：整行可点选中单选项、滑杆独立成行、去除重复图标

## [v1.9.0](https://github.com/Lifky/Flikky/tree/v1.9.0) · 2026-06-24

### 新增
- 会话分组：主页分组 chip 行、统一管理对话框、批量移动到分组、组内按日期分桶展示
- 新会话自动归入当前激活分组；删除分组时会话自动解绑（可撤销）

### 改进
- 多选操作改为悬浮工具栏

## [v1.8.0](https://github.com/Lifky/Flikky/tree/v1.8.0) · 2026-06-23

### 新增
- 主页排序/分组 chip 行与分组渲染，偏好持久化
- 完整 MD3 type scale（含 CJK 段落断行）与间距/尺寸 token 体系

### 改进
- 设置页改为 M3 分段式列表，重组为六个逻辑分区，新增标题栏与行首图标
- 宽屏设备上主页/设置/传输/历史/导出页内容宽度设上限并居中
- 主页搜索栏展开动画与边距打磨

## [v1.7.0](https://github.com/Lifky/Flikky/tree/v1.7.0) · 2026-06-18

### 新增
- 主页就地搜索（SearchBar）：会话名与消息内容分组呈现，可定位到具体消息；独立搜索页退役
- 长按多选：tri-state 全选、自适应操作栏、批量置顶/删除/重命名

### 修复
- 搜索防抖统一为单一查询源，消除「无匹配」闪烁
- 全屏搜索的 edge-to-edge 与系统栏颜色对齐

## [v1.6.0](https://github.com/Lifky/Flikky/tree/v1.6.0) · 2026-06-16

### 新增
- 传输页自适应头部：连接卡片在客户端接入后收起为细头
- 气泡四角圆角统一自定义（双端生效）
- 头像分组模式设置（组首默认 / 组尾 / 每条）
- 浮动消息工具栏（可在设置切换浮动/内联）；长按文本选择
- 等待连接 loading 指示

### 改进
- 单行内联输入栏，停止按钮移到头部；附件 sheet 改为两张方卡
- 会话背景放弃渐变，改为主题派生纯色预设 + 色相滑杆自定义
- URL 与复制按钮改为纵向堆叠

### 修复
- IME inset 处理：输入栏正确浮在键盘上方，消除键盘间隙
- 客户端未连接时禁用消息输入
- 会话进行中拦截返回并锁定设置入口

## [v1.5.0](https://github.com/Lifky/Flikky/tree/v1.5.0) · 2026-06-08

### 新增
- 底部导航架构；设置页上线：主题 / 头像 / 会话背景 / 历史保留数量
- 4 套暖色预设主题、AMOLED 纯黑、深色模式，DataStore 驱动即时切换
- 双端头像展示：手机端设置头像，浏览器端选择头像并经 client_hello 同步
- 消息长按操作栏（阶梯入场动效）
- 新自适应启动图标

### 改进
- 主页/搜索/历史的 emoji 全部替换为 Material 图标
- 撤销删除的消息恢复到原位置

### 修复
- 降低历史保留上限后立即清扫，超限会话不再闪现
- 撤回自己消息被多余的 senderId 校验阻止
- snackbar 浮于输入栏上方，不再遮挡控件

## [v1.4.0](https://github.com/Lifky/Flikky/tree/v1.4.0) · 2026-06-04

### 新增
- ZIP 归档导入回 App
- 手机推送文件到浏览器改为异步，浏览器端显示接收进度
- 导出使用共享 JSON schema，relativePath 去重

### 修复
- 上传中断的清理与断连时 XHR abort
- 传输失败显示 FAILED 标签（放弃倒计时自动移除方案）
- 断连时禁用附件按钮

## [v1.3](https://github.com/Lifky/Flikky/tree/v1.3) · 2026-05-24

### 新增
- 消息撤回：双端长按入口、双端占位样式、硬删除 + senderId 授权、双侧确认对话框
- 消息全文搜索：FTS4 + LIKE 回退，搜索结果定位并高亮目标消息
- 应用层心跳改为 ping/pong

### 修复
- 设备上 FTS tokenizer 参数不受支持导致的崩溃
- 导出 WS 在下载开始后正确停止；`server_stopped` 时停止重连并提示
- 下载使用原始文件名（Content-Disposition）
- fileCount 统计与瞬时断连检测

## [v1.2](https://github.com/Lifky/Flikky/tree/v1.2) · 2026-05-13

### 新增
- 多会话导出：主页多选 → 浏览器下载多会话 ZIP 归档（流式生成，含 messages.txt / messages.json）
- Wi-Fi 切换自动重建服务（rebind）：IP 变化时自动重启服务器并刷新通知，浏览器自动重连
- 浏览器端上传进度气泡；mdui snackbar 替代原生 alert

### 修复
- 一系列连接鲁棒性问题：应用层心跳检测死连接、重连风暴抑制、senderId 去重、断连原因区分（服务停止 vs 网络丢失）、同 IP 恢复正确重建监听 socket
- 服务重建后广播指向新 wsHub（闭包捕获死引用问题，沉淀为项目规范）
- 每条 `onStartCommand` 路径都调用 `startForeground`

## [v1.1](https://github.com/Lifky/Flikky/tree/v1.1) · 2026-04-21

### 新增
- 会话历史：Room 归档、主页会话列表、长按重命名/置顶/删除、历史详情页
- 崩溃恢复（孤儿会话终结）、空会话回滚、FIFO 保留最近 20 个非置顶会话
- 消息气泡文本可选中复制

### 修复
- 提高 multipart formFieldLimit，解除 50 MiB 上传上限
- 点击文件崩溃与「仅浏览器上传」会话消失
- HomeViewModel 反射构造失败

### 其他
- 开源准备：LICENSE、双语 README、本地笔记不入库

## [v1.0](https://github.com/Lifky/Flikky/tree/v1.0) · 2026-04-18

首个版本，Android 手机与浏览器之间的局域网互传闭环：

- Ktor（CIO）内嵌服务器：只绑定 Wi-Fi IPv4，绝不监听 `0.0.0.0`；CSP 等安全 header 附加到所有响应
- 一次性 6 位 PIN 认证：成功即作废，错误锁定/停服；token 走 HTTP-Only Cookie
- 文本与文件双向传输（multipart 上传 / 分块下载），WebSocket 实时消息与 1Hz 状态广播（时长/文件数/速率）
- 浏览器端：mdui（MD3 Web Components）离线打包约 380KB，分段 PIN 输入登录页 + 聊天页
- 前台服务（dataSync）与通知；锁屏隐藏 PIN，引导进 App 查看
- 手机端可通过 FileProvider 打开浏览器上传的文件
