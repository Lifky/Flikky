# Flikky

[English](./README.md) | **简体中文**

安卓手机与浏览器之间的局域网文件与消息互传。接收端零安装、零联网，专为临时即用的共享场景设计。

手机启动内嵌 HTTP 服务器，同 WiFi 下任意浏览器打开手机上展示的 URL，输入一次性 6 位 PIN 后即建立会话，文本与文件可双向实时传输。

## 当前状态

- **v1.0** — 最小连通闭环：启动服务、URL + PIN 配对、双向文本与文件、MD3 聊天气泡、前台服务常驻通知、完整安全基线。
- **v1.1** — *已发布（2026-04-21）* — Room 会话归档、主页会话列表（置顶 / 重命名 / 删除）、历史详情只读查看、APP 启动时 crash-recovery、FIFO 保留最近 20 条非置顶会话（置顶不占配额）。
- **v1.2** — *已发布（2026-05-13）* — 多会话批量导出到 PC（流式 zip）、浏览器上传实时进度气泡、mdui snackbar、WiFi 自动重绑 + 状态 banner、进行中会话主页交互（继续 / 行内停止）、WS 应用层心跳 + server_stopped 区分主动停止与网络断开。
- **v1.3** — *已发布（2026-05-24）* — 跨会话消息搜索（FTS4 + LIKE fallback）、进行中服务消息撤回（真删 + 双端二次确认 + 即时同步）、History 单条消息删除、应用层 ping/pong 替代被动心跳、闭包死引用系统性审计 + 回归保护、导出页 WS 健康检测 + 取消导出 dialog。
- **v1.4.0** — *已发布（2026-06-04）* — 文件传输异步化（双向即时 IN_PROGRESS 气泡 + 进度条 + 失败态 `传输失败`/`发送失败` 反馈 + 上传中断自动清理）、从 zip 导入回 APP（向后兼容 v1.2/v1.3 格式 + name+startedAt 重复检测 + 导入后 FIFO sweep）、导出格式 `relativePath` 去重修复（messages.json 与 zip entry 对齐，版本号升至 1.4）。
- **v1.5.0** — *已发布（2026-06-08）* — UI/UX 大改：底部导航（传输 / 设置）、基于 DataStore 的完整设置体系 + 即时换肤（Material You 动态色 + 4 套暖调预设 + 三态深色 + AMOLED）、APP/对端预设头像与进行中会话背景**两端同步**（`GET /api/peer-info` + WS `client_hello`）、长按消息操作栏（复制 / 撤回 / 打开 / 删除带撤销，逐个错位弹出）、可配置 History 保存数量（含 `0`=不保存、`-1`=无限制）、可编辑本机名称、消息撤回 Beta 开关、emoji→Material 图标全量迁移与 +1 档圆角形状。
- **v1.5.1** — *已发布（2026-06-16）* — 浏览器对话页滚动修复（mdui 固定 top-app-bar 给 `<body>` 注入 `padding-top`，与 `100vh` flex 外壳冲突 → 作用域化的 `body.chat-page` 覆盖 + `100dvh`），并清理了 v1.5.0 遗留的若干 cosmetic 小问题。
- **v1.6.0** — *已发布（2026-06-16）* — **会话体验重构**：顶部上下文自适应（配对前连接卡片 → 连上后 spring 塌缩为纤细对端头部）、悬浮消息工具栏（单击召唤 / 长按选词 / 单击空白清除）+ 设置开关切换为气泡旁常驻操作栏、两端统一四角等圆角气泡（默认 18dp）+ 圆角 slider、头像显示设置（组内首条 / 末条 / 每条）、输入区重做（输入框 + add 底部面板的文件/图片方卡 + 圆形发送）+ 第二行统计兼作 snackbar 落区、会话背景去渐变改为主题派生纯色 + 自定义色相 slider、等待连接加载指示、停止服务移至头部、「允许会话中返回」开关（默认拦截返回；会话运行期间锁定设置入口），以及修正的 edge-to-edge IME inset 让输入行紧贴键盘上方。应用 `versionName`/`versionCode` 现已纳入维护（此前一直冻结在 1.0/1）。
- **v1.7.0** — *已发布（2026-06-18）* — **主页重构**：去掉标题栏，改为大号 MD3 `SearchBar` 原地展开为**真全屏**，同时搜索**会话名 + 消息内容**（FTS），结果分「会话」「消息」两组；导入入口收进 overflow 菜单。**长按是进多选的唯一入口**，选中态用**无 Checkbox 的纯色三态**（`primaryContainer` 填充）+ TalkBack 选中语义；多选时底部导航被**自适应操作栏**（置顶智能切换 / 单选重命名 / 导出 / 批量删除）顶替。退役独立搜索路由。系统栏现与 App 颜色对齐（`isNavigationBarContrastEnforced=false` + `isAppearanceLightNavigationBars`）。
- **v1.8.0** — *已发布（2026-06-24）* — **设计系统与布局打底**：T 恤尺码 Spacing/Sizes token（全 UI 字面量迁移）、完整 MD3 type scale + 语义 typography 扩展（CJK 段落折行）、内联 shape 换成 `MaterialTheme.shapes`、抽出公共组件（OptionCard / ConfirmDialog / RenameDialog）+ 底栏图标统一到单一 drawable 源、设置页重组为六大逻辑区 + Large 标题栏 + 每行 leading icon + M3 segmented 列表观感、搜索框展开动画顺滑且贴齐屏幕边缘、宽屏内容区 600dp 上限并将主页/设置/历史/服务/导出居中。*(与 v1.9.0 同批发布；此里程碑 tag 内部 versionName 仍是 1.7.0——版本号 bump 落在 v1.9.0 发布时。)*
- **v1.9.0** — *已发布（2026-06-24）* — **会话分组系统**：主页新增文件夹式 filter chip（固定「全部」+ 自定义分组，单选），底层走 Room v4 migration（`session_groups` 表 + `sessions.groupId`）；当前分组态持久化于 DataStore，会话归入「启动那一刻所在的分组」。组内按 置顶 → 今天 → 昨天 → 更早 分桶。长按自定义 chip 弹统一管理框（改名 / 上下移排序 / 删除带撤销）；「全部」是虚拟、不可删、不可移的 chip。多选改为 MD3 floating toolbar（胶囊、纯图标：置顶 / 重命名 / **移动到分组** / 导出 / 删除）顶替整宽底栏；**移动到分组**弹底部 sheet（自定义分组 + 「全部」移出分组）。设置 polish：Radio 整行可点、气泡圆角 Slider 独占整宽一行、去掉「主题 / 深色模式」与 leading icon 重复的右侧图标。整体取代 v1.8.0 的排序/分组 chip。应用 `versionName` / `versionCode` → 1.9.0 / 10900。
- **v1.9.1** — *已发布（2026-06-24）* — 设置对话框与列表 polish：单选对话框（深色模式 / 消息操作样式 / 头像显示 / 历史保存数量）重做为共用的 `ChoiceDialog`，选项行整宽、≥56dp 高、铺到对话框内边、整行一个统一 ripple（Radio 仅作视觉指示）——取代原先「行矮、内缩、只有圆点可点」的样式；并给设置项标题/副标题与右侧 `Switch` 之间加固定间隔，长文案不再顶到开关上。应用 `versionName` / `versionCode` → 1.9.1 / 10901。
- **v1.10.0** — *已发布（2026-06-26）* — **收藏 / 弹药库**：底部导航新增**收藏** tab（传输 / 收藏 / 设置），底层走 Room v5 migration。给 TEXT 或已完成 FILE 气泡（进行中会话或 history 皆可）点收藏会留一份**独立快照副本**——不与源消息建外键，因此源消息被删、整个源会话被删、乃至 FIFO 淘汰后收藏副本仍在；文件复制进收藏专属目录、经 FileProvider 打开。星标可逆（实心 / 空心回显经 `(sourceSessionId, sourceMessageId)` 在 live + history 两端同步）；点收藏时弹底部 sheet 选合集（全部 + 已有 + 内联新建）。收藏拥有独立合集（chip 行、长按管理框、隔离的搜索 + 多选），与会话分组**完全独立**——删合集只把其下收藏 re-home 到「全部」，不级联删除。本版还把首页 / 收藏 / 消息三处 floating toolbar 统一到共用的 `FlikkyFloatingToolbar` 胶囊规格，并在启动时用持久化最大 id 给消息 id 计数器播种，重启后 id 不再相撞。应用 `versionName` / `versionCode` → 1.10.0 / 11000。
- **v1.10.1** — *已发布（2026-06-27）* — **收藏快速发送 & 图标全量焕新**：每条收藏新增一键发送，把快照推进进行中会话——收藏页行内、以及会话页输入行 `★` 拉起的全新**弹药箱** `ModalBottomSheet` 皆可——文本走 `sendText`、文件走新增的 `offerStoredFile` 重载流式拷贝收藏副本，门槛与会话发送键一致同为 `clientConnected`，并带一行「最近使用」快捷发送（仅持久化 id 到 DataStore）。发送图标复用会话发送键的 `ic_arrow_upward`（不是纸飞机）。另外，全部 32 个 vector drawable 迁移到**官方 Material Symbols**（opsz24 / wght400、经 `translateY(960)` group 逐字保留官方 path）——取代旧版 Material Icons 与一个与官方对不上的手搓 `ic_send_outline`——且首页 / 收藏多选工具栏改为内容区悬浮（更轻的胶囊、内层 Scaffold 不再双算底部 inset）。应用 `versionName` / `versionCode` → 1.10.1 / 11001。
- **v1.11.0** — *已发布（2026-06-30）* — **Motion 与视觉系统大改**：App 根改用 `MaterialExpressiveTheme`（**material3 1.5.0-alpha22**）+ `MotionScheme.expressive()`，新增 `ui/theme/Motion.kt` token 层把官方物理弹簧（spatial / effects × 默认 / 快 / 慢）包在**全局动画速度**之下（设置 → 动画速度：关闭 / 慢 / 标准 / 快，持久化于 DataStore；「关闭」即 reduce-motion，且始终尊重系统 animator-duration-scale）。在此地基上：MD3 导航转场（tab fade-through + push/pop shared-axis）、**predictive-back** 手势、列表增删/重排动画（`animateItem`）、服务页连接头部的 spatial-spring 高度形变、floating toolbar 迁到官方 `HorizontalFloatingToolbar`、内联 tween/spring 字面量全部 token 化。**Color**：4 套暖调预设替换为 **8 套自定义 MTB 主题**（淡曙红 / 丹紫红 / 橙皮黄 / 秋葵黄 / 安安蓝 / 珠母灰 / 鹦鹉绿 / 芥花紫），每套含完整 light/dark × 标准/中/高对比度 role 映射，新增**对比度档**设置（跟随系统 / 标准 / 中 / 高，系统对比度经 `UiModeManager` 读取），并实现**双端配色对齐**——手机当前主题的 seed + 深浅推给浏览器，浏览器重算出同色相的 mdui 调色板（同一 Material Color Utilities seed → 同色相）。**Lists**：设置 / 传输 / 收藏列表迁到官方 M3 Expressive `SegmentedListItem`（`segmentedShapes` 角形 + `SegmentedGap`），多选带官方内建的选中弹簧，容器色统一 `surfaceContainer`，终于让设置列表在亮色下有层次。待连接的三个动作（复制地址 + 两个停止服务）改为 filled-tonal 底色。应用 `versionName` / `versionCode` → 1.11.0 / 11100。

设计文档与复盘/验收清单保存在本地的 `docs/others/`（已 gitignored），公开仓库仅含源码。

## 进度

### feat

- [x] 内嵌 HTTP server，仅绑定当前 WiFi IPv4 接口 *(v1.0)*
- [x] URL + 单次 PIN 配对，错 3 次锁 IP 30 秒，错 5 次终止服务 *(v1.0)*
- [x] 双向文本 / 文件实时传输（HTTP + WebSocket） *(v1.0)*
- [x] 前台服务 + 常驻通知（通知只显示 URL，不显示 PIN） *(v1.0)*
- [x] 手机端 MD3 聊天气泡（Jetpack Compose） *(v1.0)*
- [x] 浏览器端原生 JS + 离线打包的 mdui Web Components *(v1.0)*
- [x] 安全基线：严格 CSP、`X-Frame-Options: DENY`、`nosniff`、`Referrer-Policy: no-referrer`、`HttpOnly` + `SameSite=Strict` Cookie、只用 `textContent`、文件经 Blob URL 下载 *(v1.0)*
- [x] Room 会话归档：`SessionEntity` + `MessageEntity`（单表 + kind 判别，`FOREIGN KEY ... ON DELETE CASCADE`） *(v1.1)*
- [x] 主页会话列表 + 长按菜单（置顶 / 重命名 / 删除） *(v1.1)*
- [x] History 页（单会话只读时间线） *(v1.1)*
- [x] FIFO 保留最近 20 条非置顶会话；置顶不占配额 *(v1.1)*
- [x] APP 启动时 crash-recovery：`finalizeOrphans()` 补齐未关闭会话、回滚空会话、清理孤立文件目录 *(v1.1)*
- [x] 文字消息可复制（长按触发系统复制 / 全选菜单） *(v1.1)*
- [x] 多会话批量导出到 PC，流式 zip（`messages.txt` + `messages.json` + `files/`） *(v1.2)*
- [x] `/api/export/info` 概览 endpoint + 浏览器 `/export` 页（mdui 风格） *(v1.2)*
- [x] 导出完成「保留本地 / 删除本地」二择，删除带 AlertDialog 二次确认 *(v1.2)*
- [x] 浏览器上传实时进度气泡（XHR `upload.onprogress` + X-Client-Id senderId dedup） *(v1.2)*
- [x] WiFi 自动重绑：`ConnectivityManager.NetworkCallback` → 停 / 重建 Ktor，banner 报 Lost / Switching / Switched *(v1.2)*
- [x] 进行中会话主页交互：点击进 ServingScreen 继续 / 行内「停止」按钮 / FAB 切换"继续服务" *(v1.2)*
- [x] WS 应用层心跳（4 秒 frame 超时）+ `server_stopped` event 区分用户主动停止与网络断开 *(v1.2)*
- [x] 跨会话消息搜索：FTS4 全文索引 + LIKE fallback（CJK 走 LIKE），搜索屏 debounce 输入 + 命中跳转 History + 滚动高亮 *(v1.3)*
- [x] 进行中服务消息撤回：长按→确认 AlertDialog→真删 + 两端节点即时消失 + snackbar 提醒；History 单条消息删除 *(v1.3)*
- [x] 应用层 ping/pong + 2 秒 frame 超时：断网 ~2 秒感知，替代 v1.2 的被动心跳 *(v1.3)*
- [x] 闭包死引用系统性审计：TransferControllerRebindReferenceTest 回归保护 + CLAUDE.md 规范 *(v1.3)*
- [x] 导出页健康检测：WS 连接（ping/pong）+ fetch 探测、取消导出 dialog、下载开始清理 *(v1.3)*
- [x] `senderId` 全链路：手机端 `phone-{ANDROID_ID}` 跨重启不变，浏览器 `X-Client-Id` 按会话；撤回鉴权按 senderId 匹配 *(v1.3)*
- [x] 文件传输异步化：手机→浏览器、浏览器→手机双向即时 IN_PROGRESS 气泡 + 5% 间隔进度广播 + `file_ready`/`file_removed` 事件；失败态 `传输失败`/`发送失败` 反馈 *(v1.4.0)*
- [x] 从 zip 导入回 APP：`ZipImporter` 解析 + 向后兼容 v1.2/v1.3（replay `nextUniqueName` 推断路径）、name+startedAt 重复检测、导入后 FIFO sweep、导入入口 + Loading dialog + snackbar 总结 *(v1.4.0)*
- [x] 底部导航（传输 / 设置），`alwaysShowLabel=false` 让未选中 tab 只显图标；详情页自动隐藏底栏；两 tab 各自保留 back-stack 状态 *(v1.5.0)*
- [x] 基于 DataStore 的设置体系 + 即时换肤（零重启）：Material You 动态色 + 4 套暖调预设（珊瑚橙 / 蘑菇棕 / 黛尾绿 / 雾霭蓝）、三态深色 + AMOLED 纯黑、CompositionLocal 黄金链 *(v1.5.0)*
- [x] 长按消息操作栏：复制 / 撤回（Beta）/ 打开 / 删除带撤销，从气泡下方逐个错位 scale-in 弹出 *(v1.5.0)*
- [x] APP/对端预设头像（12 个）+ 进行中会话背景（默认 / 空白 / 纯色 / 渐变），**两端同步**（`GET /api/peer-info` + WS `client_hello`），浏览器端带头像选择器 *(v1.5.0)*
- [x] 设置内可配置 History 保存数量（默认 20，`0`=不保存，`-1`=无限制）；对端头像编号持久化，History 正确还原浏览器侧头像 *(v1.5.0)*
- [x] emoji → Material 图标全量迁移（`material-icons-core` + 离线打包的 Symbols vector）、+1 档圆角 MD3 形状、气泡 CJK 段落折行 *(v1.5.0)*
- [x] 顶部上下文自适应：配对前 `ConnectionInfoCard`（URL + 复制 + 大号 PIN），连上后 spring 塌缩为纤细对端头部（头像 + 名称 + 已连接 + 停止）；与导出页共用 *(v1.6.0)*
- [x] 悬浮消息工具栏（默认）：单击气泡召唤、长按起原生选词、单击空白清除；设置开关可切换为气泡旁常驻操作栏；进行中会话与 History 都适用 *(v1.6.0)*
- [x] 两端统一四角等圆角气泡（默认 18dp）+ 设置内圆角 slider（8–28dp） *(v1.6.0)*
- [x] 头像显示设置：同来源连续消息组内首条 / 末条 / 每条显示头像 *(v1.6.0)*
- [x] 输入区重做：输入框 + add 按钮（底部面板含文件 / 图片方卡）+ 圆形上箭头发送，外加第二行统计（运行 / 文件 / 速率）兼作 snackbar 落区 *(v1.6.0)*
- [x] 会话背景：移除渐变；主题派生纯色预设 + 自定义色相 slider（恒为可读极浅色） *(v1.6.0)*
- [x] 「允许会话中返回」设置（默认关 → 返回被拦截并弹引导 snackbar）；传输会话运行期间锁定底栏「设置」入口 *(v1.6.0)*
- [x] 等待连接加载指示；停止服务移入头部 *(v1.6.0)*
- [x] 主页顶栏改大号 MD3 `SearchBar`（去标题）原地展开为真全屏；同时搜会话名 + 消息内容（FTS），分「会话」「消息」两组；导入迁入 overflow 菜单 *(v1.7.0)*
- [x] 长按是进多选的唯一入口；无 Checkbox 的纯色三态选中（`primaryContainer` 填充）+ TalkBack 的 `selected` / `stateDescription` 语义 *(v1.7.0)*
- [x] 自适应多选操作栏——置顶（智能切换）/ 重命名（仅单选）/ 导出 / 删除（批量）；多选时顶替底部导航；退役独立搜索路由 *(v1.7.0)*
- [x] 设计系统 token：T 恤尺码 Spacing scale + Sizes token、完整 MD3 type scale + 语义 typography 扩展、内联 shape 换成 `MaterialTheme.shapes` *(v1.8.0)*
- [x] 设置页重组为六大逻辑区 + Large MD3 标题栏 + 每行 leading icon + M3 segmented 列表观感 *(v1.8.0)*
- [x] 宽屏内容区 600dp 上限，将主页 / 设置 / 历史 / 服务 / 导出居中不破版 *(v1.8.0)*
- [x] 会话分组系统：文件夹式 filter chip（固定「全部」+ 自定义分组，单选），底层 Room v4 `session_groups` migration；当前分组态持久化于 DataStore，会话归入「启动那一刻所在的分组」；组内 置顶 / 今天 / 昨天 / 更早 分桶 *(v1.9.0)*
- [x] 长按自定义 chip → 统一管理框（改名 / 上下移排序 / 删除带撤销）；「全部」为虚拟、不可删、不可移的 chip *(v1.9.0)*
- [x] 多选 floating toolbar（MD3 胶囊、纯图标）+ **移动到分组**动作 → 底部 sheet（自定义分组 + 「全部」移出分组），一次 UPDATE 批量改 `groupId` *(v1.9.0)*
- [x] 全局动画速度（关闭 / 慢 / 标准 / 快）设置，持久化于 DataStore 并经 `Motion` token 层接到 `MotionScheme.expressive()`；「关闭」即 reduce-motion，且始终尊重系统 animator-duration-scale *(v1.11.0)*
- [x] MD3 导航转场：tab 切换 fade-through，路由 push/pop 走 shared-axis（前进 / 后退方向相反）；predictive-back 手势接路由 pop + sheet / 多选 dismiss *(v1.11.0)*
- [x] 列表增删 / 重排动画走官方 `animateItem`（位移 spatial 弹簧、淡入淡出 effects 弹簧），覆盖 home / serving / favorites / history *(v1.11.0)*
- [x] 8 套自定义 MTB 主题（淡曙红 / 丹紫红 / 橙皮黄 / 秋葵黄 / 安安蓝 / 珠母灰 / 鹦鹉绿 / 芥花紫）替换 4 套暖调预设，每套含完整 light/dark × 标准/中/高对比度 role 映射；新增对比度档设置（跟随系统 / 标准 / 中 / 高，系统对比度经 `UiModeManager` 读取） *(v1.11.0)*
- [x] 双端配色对齐：手机当前主题 seed + 深浅经 `peer-info` 推给浏览器，浏览器重算出同色相 mdui 调色板（同一 Material Color Utilities seed → 同色相） *(v1.11.0)*
- [x] 设置 / 传输 / 收藏列表迁到官方 M3 Expressive `SegmentedListItem`（`segmentedShapes` + `SegmentedGap`），多选带官方内建选中弹簧，容器统一 `surfaceContainer` 让亮色有层次 *(v1.11.0)*
- [ ] HTTPS 自签证书 *(v2)*
- [ ] 本地归档 at-rest encryption *(v2)*

### opt

- [x] 浏览器端从手写 CSS 迁到 mdui Web Components *(v1.0 后期)*
- [x] 通知栏不显示 PIN——锁屏是物理世界的攻击面 *(v1.0 后期)*
- [x] 文件按 `sessionId` 分目录（`filesDir/sessions/{id}/files/{fileId}`），FIFO 淘汰即 `rm -rf` *(v1.1)*
- [x] `SessionEntity` 冗余聚合字段（`messageCount` / `fileCount` / `totalBytes` / `previewText`），主页列表不必反向扫 messages 表 *(v1.1)*
- [x] Ktor multipart 解除默认 50 MiB 上限（LAN 单用户场景，磁盘空间才是真天花板） *(v1.1)*
- [x] 浏览器端 native `alert` 替换为 mdui snackbar（`window.flikky.showError/showInfo`） *(v1.2)*
- [x] 通知栏文案在 WiFi rebind 后刷新到新 IP *(v1.2)*
- [x] `/api/messages` 返回时间戳合并排序的 `ordered` 视图，刷新后文本/文件按时间顺序混排 *(v1.2)*
- [x] `MAX_RECONNECT_ATTEMPTS` 上限 + 应用层心跳检测死 WS *(v1.2)*
- [x] 浏览器断网 UI 即时更新（不等 TCP close），heartbeat 检测到超时立即 disable 按钮 + banner *(v1.3)*
- [x] 文件下载 `Content-Disposition` 使用原始文件名而非 UUID *(v1.3)*
- [x] 手机推送文件的 tee 改异步：立即广播 IN_PROGRESS + 后台协程拷贝带进度，消除"选中到可下载"的同步拷贝阻塞 *(v1.4.0)*
- [x] 设置经 DataStore Preferences 持久化；主题走 `StateFlow<FlikkySettings>`，`MaterialTheme` 观察它——主题 / 深色 / AMOLED 切换原地重组，不重建 Activity *(v1.5.0)*
- [x] `peerInfoProvider` 在调用时读 `@Volatile` settings 快照，跨 WiFi rebind 仍正确（KtorServer 被重建，lambda 存活于 TransferService field） *(v1.5.0)*
- [x] `SessionState.addMessage` 把内存列表保持时间戳有序（二分插入），撤销恢复的消息回到原位置，而单调递增的新消息仍追加末尾 *(v1.5.0)*
- [x] 正确的 edge-to-edge IME 处理：`adjustResize` + `padding(innerPadding)` + `consumeWindowInsets(innerPadding)` + `imePadding()`，ime inset 只生效一次，输入行紧贴键盘上方 *(v1.6.0)*
- [x] 应用 `versionName` / `versionCode` 纳入维护（1.6.0 / 10600，公式 `major*10000+minor*100+patch`）——此前从项目之初一直冻结在 `1.0` / `1`，导致安装器永远显示 1.0 *(v1.6.0)*
- [x] 搜索的会话名组与消息组由同一 debounce 后的 query 驱动，两组锁步更新，无匹配提示不再在 debounce 中途闪烁 *(v1.7.0)*
- [x] 真全屏搜索（逐目的地 padding：主页目的地 escape 顶部 status bar inset、展开时隐藏 FAB + 底栏），SearchBar 铺到状态栏/导航栏之下、无侧缝 *(v1.7.0)*
- [x] 应用 `versionName` / `versionCode` 1.7.0 / 10700 *(v1.7.0)*
- [x] 设置对话框 Radio 整行可点（整行套 `selectable(role=RadioButton)` 并置于 `selectableGroup` 内），不再只有圆点是命中区 *(v1.9.0)*
- [x] `SettingItem` 新增可选整宽 content 槽；气泡圆角 Slider 移过去铺满整行（取代局促的 160dp）；去掉「主题 / 深色模式」与 leading icon 重复的右侧图标 *(v1.9.0)*
- [x] material3 1.4.0 stable 把 `HorizontalFloatingToolbar` 关在 internal 的 `ExperimentalMaterial3ExpressiveApi` 后，floating toolbar 改用稳定组件按同一 MD3 规格手搓（胶囊 `Surface` + `IconButton`） *(v1.9.0)*
- [x] 应用 `versionName` / `versionCode` 1.9.0 / 10900 *(v1.9.0)*
- [x] 收藏 / 弹药库：底部导航新增「收藏」tab，底层走 Room v5 migration；给 TEXT 或已完成 FILE 气泡点收藏会留一份**独立快照副本**（不与源建外键、文件复制进收藏专属目录、经 FileProvider 打开），源消息 / 源会话被删及 FIFO 淘汰后副本仍在 *(v1.10.0)*
- [x] 星标可逆，经 `(sourceSessionId, sourceMessageId)` 在进行中会话 + history 两端同步；点收藏弹底部 sheet 选合集（全部 + 已有 + 内联新建） *(v1.10.0)*
- [x] 收藏合集（chip 行、长按管理框、隔离的搜索 + 多选）与会话分组完全独立；删合集只把其下收藏 re-home 到「全部」，不级联删除 *(v1.10.0)*
- [x] 共用 `FlikkyFloatingToolbar` 胶囊把首页 / 收藏 / 消息三处 floating toolbar 统一到同一 MD3 规格 *(v1.10.0)*
- [x] 启动时用持久化最大 id 给消息 id 计数器播种，重启后 id 不再相撞 *(v1.10.0)*
- [x] 应用 `versionName` / `versionCode` 1.10.0 / 11000 *(v1.10.0)*
- [x] 收藏快速发送：每条收藏（收藏页行内 + 会话页 `★` 拉起的弹药箱底部 sheet）一键把快照推进进行中会话（文本 → `sendText`、文件 → 新增 `offerStoredFile` 重载），门槛 `clientConnected`；带一行「最近使用」持久化 id 到 DataStore *(v1.10.1)*
- [x] 全部 32 个 vector drawable 迁移到官方 Material Symbols（opsz24 / wght400，经 `translateY(960)` group 逐字保留官方 path）——取代旧版 Material Icons + 手搓的 `ic_send_outline` *(v1.10.1)*
- [x] 应用 `versionName` / `versionCode` 1.10.1 / 11001 *(v1.10.1)*
- [x] `ui/theme/Motion.kt` 是官方 `MaterialTheme.motionScheme`（spatial / effects × 默认 / 快 / 慢）的薄适配层，经 `LocalMotionScale` = min(用户速度, 系统 animator-duration-scale) 缩放；内联 tween/spring 字面量 token 化；`LocalClipboardManager` → `LocalClipboard` *(v1.11.0)*
- [x] floating toolbar 从手搓 `Surface` + `Row` 迁到官方 `HorizontalFloatingToolbar`（material3 1.5.0-alpha22，`ExperimentalMaterial3ExpressiveApi`）；服务页连接头部高度走 spatial 弹簧形变 *(v1.11.0)*
- [x] 待连接的三个动作（复制地址 + 两个停止服务）改 filled-tonal 底色；停止服务用 errorContainer tonal 配色保留危险语义 *(v1.11.0)*
- [x] 应用 `versionName` / `versionCode` 1.11.0 / 11100 *(v1.11.0)*

### fix

- [x] v1.0-rc1：`staticResources` 从 JVM classpath 而不是 Android assets 读；缺 `POST_NOTIFICATIONS` runtime 请求；登录页 JS 没加载导致表单默认提交
- [x] v1.1 T8：AGP 9 + 内置 Kotlin 封禁 `kotlin.sourceSets` DSL → 加 `android.disallowKotlinSourceSets=false`
- [x] v1.1 T9 / T10：Robolectric 4.14 最高只支持 SDK 33 且不再传递 `androidx.test:core`
- [x] v1.1 T11：`HomeViewModel(app, repo = ServiceLocator.repository)` 让 `AndroidViewModelFactory` 反射构造失败 → 加 `@JvmOverloads`
- [x] v1.1 T12：v1.1 改了文件路径但 `file_paths.xml` 还是 v1.0 的 `transfer/` → `FileProvider.getUriForFile` 抛异常；同时给 `openFile` 加 try/catch 兜底
- [x] v1.1 T13：`FileRoutes` POST 只更新内存 session、漏调 DB 持久化 → `endSession` 把"浏览器只发文件"的会话当空会话回滚，文件一起删 → 给 `fileRoutes` 加 `onPersist`，从 `KtorServer` 串进来
- [x] v1.1 T14：Ktor 3.0 默认 `receiveMultipart()` 静默上限 50 MiB → 改 `formFieldLimit = Long.MAX_VALUE`；浏览器 `fetch` 失败时 alert 暴露非 2xx 响应
- [x] v1.2 ServiceLocator.reset 替换 instance → HomeViewModel 缓存死引用 → 二次导出崩溃 + 停服后 UI 仍认为传输在跑。reset 现在复用 instance + clearExport 由具体调用方控制
- [x] v1.2 `ForegroundServiceDidNotStartInTime`：early-return 路径漏调 startForeground。`onStartCommand` 顶端无条件 startForeground 占槽位，业务路径决定保留或 stopForeground + stopSelf
- [x] v1.2 浏览器 PIN 登录后在 Export mode 也跳 `/app`：`AuthResponse.redirectTo` 按 ServiceMode 返回
- [x] v1.2 浏览器上传成功后双消息泡（uploader 收到自己的 WS 广播）：POST 透传 `X-Client-Id` header，broadcast payload 携带 senderId；浏览器 WS 收到 senderId == myClientId 跳过
- [x] v1.2 闭包死引用一族 bug：`statusBroadcastJob` / `TransferController.wsHub` 在 startTransfer 时 capture 局部 `server.wsHub`，rebind 后还朝旧 hub 广播。两处改为 lambda 从 field-level 取 `ktor?.wsHub`
- [x] v1.2 浏览器拿着死的 half-open WS（OS 没立即撕 TCP，`readyState` 仍 OPEN）：应用层 4 秒 frame 超时主动 close 触发重连
- [x] v1.2 用户主动停止后浏览器死循环重连：服务端 close WS 前发 `server_stopped` event，浏览器 set flag 跳过 reconnect timer；兜底 `MAX_RECONNECT_ATTEMPTS = 6`
- [x] v1.3 FTS4 `categories='L* N* Co'` 在 Android SQLite（无 ICU）崩溃。回退到 `remove_diacritics=1`；CJK 搜索走 LIKE fallback
- [x] v1.3 撤回功能从 History 软删 + 占位符 → ServingScreen 真删 + 即时消失 + 双端二次确认 dialog（验收反馈驱动的大返工）
- [x] v1.3 `ws.close()` 在 TCP 半开时阻塞 30-60 秒才触发 onclose，UI 反馈严重滞后。heartbeat 检测到超时后立即更新 UI + 丢弃旧 WS + 启动重连
- [x] v1.3 导出页 WS 用 frame 超时但 export mode 无 status broadcast → 即时断开循环。改为 ping/pong
- [x] v1.3 导出页下载/取消后没关 WS → server 停掉触发重连循环。下载和 `server_stopped` 现在关 WS + 停探测
- [x] v1.3 文件下载保存名是 UUID 而非原始文件名。`Content-Disposition` 从 session 内存查原始文件名
- [x] v1.4.0 浏览器上传大文件期间手机端看不到气泡：`FileRoutes` POST 收到 file part header 即建 IN_PROGRESS 消息广播，边收边报进度，完成再转 COMPLETED
- [x] v1.4.0 上传中断（浏览器刷新 / WiFi 断）残留 IN_PROGRESS 消息：multipart 接收包 try-catch，断开时标 FAILED + 删残留文件 + 广播 `file_removed`
- [x] v1.4.0 WiFi 断开浏览器上传反馈滞后：浏览器维护 `activeUploads[]`，`enterDisconnected` 立即 abort 所有在飞 XHR，不等 TCP 超时
- [x] v1.4.0 History 中手机发送的文件无法打开：去掉 `origin==BROWSER` 限制，所有 COMPLETED 文件统一可点击打开
- [x] v1.4.0 导出 `messages.json` 的 `relativePath` 与实际 zip entry 名不一致（v1.2 遗留）：`ZipExporter` 先算去重名再传给 formatter
- [x] v1.4.0 WiFi 断开手机端「附件」按钮仍可点：与发送按钮一致受 `ui.clientConnected` 控制
- [x] v1.5.0 装机导入卡死：`importSessions` 在 `withContext(Dispatchers.IO)` 内读取保留上限（DataStore `first()`），把 DataStore 1.1 的单线程 actor 拖死锁。改为进入 IO 上下文前先读上限
- [x] v1.5.0 完成会话后 History 数量闪现 N+1——FIFO sweep 只在下次启服时跑。现在 `endSession` 后立即 sweep，且设置内调小保留数量会立刻清理
- [x] v1.5.0 己方消息因冗余 `senderId` 校验无法撤回（"只能撤回自己发的消息"）；既然 UI 只在己方消息上显示撤回按钮，服务端校验属多余，已去掉（单 PIN 单用户模型）
- [x] v1.5.0 进行中会话的删除→撤销把消息丢到列表末尾；`addMessage` 改时间戳有序插入，撤销后回到原位置
- [x] v1.5.0 History 显示错误的浏览器侧头像（对端头像编号只在内存）——新增 `peerAvatarId` 列（DB v2→3 migration），`endSession` 时持久化、History 读回
- [x] v1.5.0 删除 / 撤回 snackbar 占位挤动会话内容、可能挡住输入框；现在两端都改为悬浮在输入框上方（手机端 Compose overlay，浏览器端 mdui 偏移）
- [x] v1.5.1 浏览器对话页无法滚动：mdui 固定 top-app-bar 给 `<body>` 注入 `padding-top`，与 `100vh` flex 外壳冲突 → 作用域化 `body.chat-page` 覆盖 + `100dvh`
- [x] v1.6.0 IME inset 双重计算：未设 `windowSoftInputMode` 时 Activity 用 adjustPan 平移窗口，而列又应用了一次 ime inset，导致输入行被顶到顶部、留出键盘高度空白；改用规范的 `adjustResize` + `consumeWindowInsets` 写法（只生效一次）
- [x] v1.6.0 未连接时禁用消息输入框（与 add / 发送一致），无连接态不可编辑、不弹键盘
- [x] v1.6.0 背景选择面板：点选项不再关闭面板、自定义色相 slider 从当前背景回读、主题派生重复色块去重（如珊瑚 / 蘑菇）
- [x] v1.6.0 连接卡片 URL 改为整行居中 + 下方独立复制按钮，长 URL 不再与图标错位
- [x] v1.7.0 搜索展开非真全屏（FAB / 底部导航透出、状态栏背景不变、左右有缝）；修为真 edge-to-edge 全屏
- [x] v1.7.0 系统导航栏 / 手势线背景与 App 颜色不一致（对比度浮层）；用 `isNavigationBarContrastEnforced=false` + `isAppearanceLightNavigationBars` 修复
- [x] v1.7.0 搜索文件命中图标统一为 `ic_description`，与消息文件气泡一致
- [x] v1.10.1 收藏发送图标误用纸飞机（导航栏「传输」tab glyph）；改用会话发送键的 `ic_arrow_upward`，且收藏页发送门槛统一为 `clientConnected`（原 `currentSessionId != null`）
- [x] v1.10.1 首页 / 收藏多选工具栏改为内容区悬浮（更轻的胶囊）；内层 Scaffold 不再双算底部 inset

## 亮点

- **电脑端零安装**：一个浏览器就够。不用装应用、不用装插件、不用注册账号。
- **只走局域网**：服务器仅绑定当前 WiFi IPv4 接口，绝不绑 `0.0.0.0`，不发任何外网请求。
- **PIN 单次使用**：认证成功即作废，后续必须换新 PIN。错 3 次锁该 IP 30 秒，错 5 次终止服务。
- **浏览器端加固**：严格 CSP、`X-Frame-Options: DENY`、`nosniff`、`Referrer-Policy: no-referrer`、`HttpOnly` + `SameSite=Strict` Cookie、只用 `textContent`（禁 `innerHTML`）、文件经 Blob URL 下载。
- **原生 MD3 视觉**：手机端 Jetpack Compose，浏览器端 [mdui](https://github.com/zdhxiong/mdui) Web Components 组件库——离线打包进 APK，不走 CDN。
- **锁屏感知的通知**：通知栏只显示 URL，绝不显示 PIN——锁屏是物理世界的攻击面。

## 已知限制（设计内取舍，非缺陷）

- HTTP 明文传输（HTTPS 自签证书在 v2 里加）。
- WiFi 切换（IP 变了）会断开在飞的 WS，浏览器需打开 banner 提示的新 URL；同 IP 恢复几秒内自动重连。 *(v1.2)*
- 头像仅支持预设（图标 + 颜色）；自定义图片不在范围内。会话背景支持主题派生纯色 + 自定义色相（恒为可读极浅色）；渐变已在 v1.6.0 移除。 *(v1.6.0)*
- 气泡圆角 slider 仅在手机端生效；浏览器端暂用静态 18px。双端**配色**对齐（seed + 深浅）已在 v1.11.0 落地，但圆角尚未同步。 *(v1.11.0 后仍开放)*
- 双端配色对齐仅覆盖色相 + 深浅：浏览器拿不到对比度档，且 Material You 动态色下（无壁纸访问权）回落 mdui 默认调色板、仅跟随深浅；导出快照页未接主题。 *(v1.11.0)*

## 技术栈

| 层        | 选型                                                          |
| --------- | ------------------------------------------------------------- |
| 语言      | Kotlin 2.2                                                    |
| 构建      | AGP 9 + KSP2（`2.2.10-2.0.2`）                                |
| 手机 UI   | Jetpack Compose + Material 3 Expressive（material3 1.5.0-alpha22，`MaterialExpressiveTheme` + `MotionScheme`） |
| HTTP 服务 | Ktor 3（CIO engine），内嵌于前台服务                          |
| WebSocket | Ktor WebSockets（`pingPeriodMillis = 15_000`）                |
| 持久化    | Room 2.7（+ KSP2 代码生成）                                   |
| 设置存储  | DataStore Preferences（经 `StateFlow` 即时换肤）             |
| 浏览器 UI | 原生 HTML/CSS/JS + mdui Web Components                        |
| 测试      | JUnit 4 + MockK + Turbine + ktor-server-test-host + Robolectric 4.14（`@Config(sdk = [33])`） |
| 最低/目标 | SDK 33 / 36                                                   |
| 模块结构  | 单 `:app` 模块，不过度拆分                                    |

每一项选择的「为什么」见 `docs/others/notes/decisions.md`。

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
├── ui/          Compose Screen 与 ViewModel（home、serving、history、exporting、settings、components）
├── service/     前台服务、controller、通知、export notification text
├── server/      Ktor server、routes（含 ExportRoutes、PeerInfoRoutes）、DTO、PIN 认证、ServiceMode
├── session/     内存状态、Message 模型、NetworkStatus（+ peerAvatarId）
├── data/        Room DB、Entity、DAO、SessionRepository、SessionFileStore、settings（DataStore）
├── export/      ExportSession / ExportMode / ExportSnapshot / ZipExporter / formatters
├── network/     WiFi IPv4 获取、NetworkRebinder（rebind intent 状态机）
├── util/        纯 Kotlin 工具（不依赖 Android 框架）
└── di/          ServiceLocator
app/src/main/assets/web/   浏览器前端（含离线打包的 mdui、export.html、snackbar.js）
docs/others/               本地设计/复盘/验收清单目录（gitignored）
```

## 致谢

- [Ktor](https://ktor.io/) —— 内嵌的 HTTP/WebSocket 引擎
- [mdui](https://github.com/zdhxiong/mdui) —— Material Design 3 Web Components，以 MIT 许可离线打包

## 许可证

MIT —— 见 [LICENSE](./LICENSE)。
